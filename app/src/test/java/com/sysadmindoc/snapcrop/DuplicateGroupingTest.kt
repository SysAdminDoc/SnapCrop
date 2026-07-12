package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Random

@RunWith(RobolectricTestRunner::class)
class DuplicateGroupingTest {
    @Test
    fun exactBytesGroupRegardlessOfPerceptualDifference() {
        val first = candidate(1, sha = "a".repeat(64), hash = 0L, luma = 0)
        val second = candidate(2, sha = "a".repeat(64), hash = -1L, luma = 255)

        val group = DuplicateGrouping.group(listOf(first, second), DuplicateSensitivity.STRICT, emptySet()).single()

        assertEquals(DuplicateMatchKind.EXACT, group.kind)
        assertEquals(listOf(first, second), group.candidates)
    }

    @Test
    fun thresholdsProgressFromStrictToLooseAndGateDimensionsAndLuma() {
        val base = candidate(1, hash = 0L, luma = 100)
        val sixBits = candidate(2, hash = 0b11_1111L, luma = 115)
        assertTrue(DuplicateGrouping.group(listOf(base, sixBits), DuplicateSensitivity.STRICT, emptySet()).isEmpty())
        assertEquals(1, DuplicateGrouping.group(listOf(base, sixBits), DuplicateSensitivity.BALANCED, emptySet()).size)

        val wrongSize = candidate(3, hash = 0L, luma = 100, width = 800)
        assertTrue(DuplicateGrouping.group(listOf(base, wrongSize), DuplicateSensitivity.LOOSE, emptySet()).isEmpty())
        val wrongLuma = candidate(4, hash = 0L, luma = 160)
        assertTrue(DuplicateGrouping.group(listOf(base, wrongLuma), DuplicateSensitivity.LOOSE, emptySet()).isEmpty())
    }

    @Test
    fun completeLinkClusteringAvoidsTransitiveFalseGroups() {
        val first = candidate(1, hash = 0b0000L)
        val middle = candidate(2, hash = 0b0011L)
        val far = candidate(3, hash = 0b1111L)

        val groups = DuplicateGrouping.group(listOf(first, middle, far), DuplicateSensitivity.STRICT, emptySet())

        assertEquals(1, groups.size)
        assertEquals(listOf(first, middle), groups.single().candidates)
        assertFalse(groups.single().candidates.contains(far))
    }

    @Test
    fun rememberedDismissalPreventsFuturePerceptualGrouping() {
        val first = candidate(1, hash = 0L)
        val second = candidate(2, hash = 1L)
        val initial = DuplicateGrouping.group(listOf(first, second), DuplicateSensitivity.STRICT, emptySet()).single()
        val dismissals = DuplicateGrouping.dismissalsFor(initial)

        assertEquals(1, dismissals.size)
        assertTrue(DuplicateGrouping.group(listOf(first, second), DuplicateSensitivity.LOOSE, dismissals).isEmpty())
        assertEquals(dismissals.first(), DuplicateDismissal.of(second.fingerprintKey, first.fingerprintKey))

        val exactFirst = first.copy(exactSha256 = "b".repeat(64))
        val exactSecond = second.copy(exactSha256 = "b".repeat(64))
        assertEquals(
            DuplicateMatchKind.EXACT,
            DuplicateGrouping.group(listOf(exactFirst, exactSecond), DuplicateSensitivity.STRICT, dismissals).single().kind
        )
    }

    @Test
    fun keepOrderingIsStableByOriginalDateAndUri() {
        val newest = candidate(3, hash = 0L).copy(dateAdded = 30)
        val oldest = candidate(2, hash = 0L).copy(dateAdded = 10)
        val middle = candidate(1, hash = 0L).copy(dateAdded = 20)

        val ordered = DuplicateGrouping.group(
            listOf(newest, oldest, middle),
            DuplicateSensitivity.STRICT,
            emptySet()
        ).single().candidates

        assertEquals(listOf(oldest, middle, newest), ordered)
    }

    @Test
    fun reviewSelectionNeverRemovesEveryCandidate() {
        val oldest = candidate(1)
        val newest = candidate(2)
        val group = DuplicateGrouping.group(listOf(newest, oldest), DuplicateSensitivity.STRICT, emptySet()).single()

        assertEquals(setOf(newest.identity), DuplicateReviewSelection.keepOldest(group))
        assertEquals(setOf(oldest.identity), DuplicateReviewSelection.keepNewest(group))
        val oneRemoved = DuplicateReviewSelection.toggle(group, emptySet(), oldest.identity)
        assertEquals(oneRemoved, DuplicateReviewSelection.toggle(group, oneRemoved, newest.identity))
    }

    @Test
    fun dismissalFollowsContentAcrossUriChanges() {
        val first = candidate(1, sha = "a".repeat(64), hash = 0L)
        val second = candidate(2, sha = "b".repeat(64), hash = 1L)
        val group = DuplicateGrouping.group(listOf(first, second), DuplicateSensitivity.STRICT, emptySet()).single()
        val dismissals = DuplicateGrouping.dismissalsFor(group)
        val copiedFirst = first.copy(uri = Uri.parse("content://media/external/images/media/91"), dateAdded = 91)
        val copiedSecond = second.copy(uri = Uri.parse("content://media/external/images/media/92"), dateAdded = 92)

        assertTrue(DuplicateGrouping.group(listOf(copiedFirst, copiedSecond), DuplicateSensitivity.LOOSE, dismissals).isEmpty())
    }

    @Test
    fun candidateDismissalSplitsOneOutButKeepsTheRestGrouped() {
        val a = candidate(1, hash = 0b0000L)
        val b = candidate(2, hash = 0b0001L)
        val c = candidate(3, hash = 0b0010L)
        val group = DuplicateGrouping.group(listOf(a, b, c), DuplicateSensitivity.BALANCED, emptySet()).single()
        assertEquals(3, group.candidates.size)

        val dismissals = DuplicateGrouping.dismissalsForCandidate(group, b.identity)
        // Only pairs involving b are dismissed, not the a<->c pair.
        assertEquals(2, dismissals.size)
        assertTrue(DuplicateDismissal.of(b.fingerprintKey, a.fingerprintKey) in dismissals)
        assertTrue(DuplicateDismissal.of(b.fingerprintKey, c.fingerprintKey) in dismissals)
        assertFalse(DuplicateDismissal.of(a.fingerprintKey, c.fingerprintKey) in dismissals)

        val regrouped = DuplicateGrouping.group(listOf(a, b, c), DuplicateSensitivity.BALANCED, dismissals)
        assertEquals(1, regrouped.size)
        assertEquals(listOf(a, c), regrouped.single().candidates)
    }

    @Test
    fun candidateDismissalForUnknownIdentityIsEmpty() {
        val a = candidate(1, hash = 0L)
        val b = candidate(2, hash = 1L)
        val group = DuplicateGrouping.group(listOf(a, b), DuplicateSensitivity.STRICT, emptySet()).single()
        assertTrue(DuplicateGrouping.dismissalsForCandidate(group, "content://missing\u00000").isEmpty())
    }

    @Test
    fun bucketBoundariesDoNotDropValidDimensionLumaOrHashMatches() {
        val first = candidate(1, hash = 0L, luma = 100, width = 1_000).copy(height = 2_400)
        val atBalancedBoundary = candidate(
            2,
            hash = 0b11_1111L,
            luma = 118,
            width = 1_063,
        ).copy(height = 2_550)

        val group = DuplicateGrouping.group(
            listOf(first, atBalancedBoundary),
            DuplicateSensitivity.BALANCED,
            emptySet(),
        ).single()

        assertEquals(listOf(first, atBalancedBoundary), group.candidates)
    }

    @Test
    fun indexedCandidateGenerationAvoidsQuadraticSimilarityChecks() {
        val random = Random(7)
        val candidates = (1L..3_000L).map { id ->
            candidate(
                id = id,
                hash = random.nextLong(),
                luma = (id % 256).toInt(),
                width = 900 + (id % 900).toInt(),
            ).copy(height = 1_800 + (id % 1_800).toInt())
        }
        val metrics = DuplicateGroupingMetrics()

        DuplicateGrouping.group(candidates, DuplicateSensitivity.BALANCED, emptySet(), metrics)

        assertTrue(
            "Expected indexed grouping to stay below 150 checks per candidate, got ${metrics.similarityChecks}",
            metrics.similarityChecks < candidates.size * 150L,
        )
    }

    @Test
    fun indexedGroupingMatchesBruteForceCompleteLinkSemantics() {
        val random = Random(19)
        val candidates = (1L..120L).map { id ->
            candidate(
                id = id,
                sha = if (id % 30 in 0L..1L) "${id / 30}".repeat(64) else null,
                hash = random.nextInt(1 shl 12).toLong(),
                luma = 90 + random.nextInt(50),
                width = 1_000 + random.nextInt(120),
            ).copy(height = 2_300 + random.nextInt(220))
        }
        val dismissals = setOf(
            DuplicateDismissal.of(candidates[4].fingerprintKey, candidates[5].fingerprintKey),
            DuplicateDismissal.of(candidates[30].fingerprintKey, candidates[31].fingerprintKey),
        )

        DuplicateSensitivity.entries.forEach { sensitivity ->
            val actual = DuplicateGrouping.group(candidates, sensitivity, dismissals).signatures()
            assertEquals(bruteForceSignatures(candidates, sensitivity, dismissals), actual)
        }
    }

    private fun List<DuplicateGroup>.signatures(): Set<Pair<DuplicateMatchKind, Set<String>>> =
        mapTo(mutableSetOf()) { group -> group.kind to group.candidates.mapTo(mutableSetOf(), DuplicateCandidate::identity) }

    private fun bruteForceSignatures(
        candidates: List<DuplicateCandidate>,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>,
    ): Set<Pair<DuplicateMatchKind, Set<String>>> {
        val usable = candidates.asSequence()
            .filter { it.width > 0 && it.height > 0 }
            .distinctBy(DuplicateCandidate::identity)
            .sortedWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
            .toList()
        val assigned = mutableSetOf<String>()
        val groups = mutableListOf<Pair<DuplicateMatchKind, Set<String>>>()
        val exactGroups = usable.filter { !it.exactSha256.isNullOrBlank() }
            .groupBy(DuplicateCandidate::exactSha256)
            .values
            .filter { it.size >= 2 }
        exactGroups.flatten().forEach { assigned += it.identity }
        exactGroups.forEach { exact ->
            val cluster = exact.toMutableList()
            usable.filter { it.identity !in assigned }.forEach { candidate ->
                if (cluster.all { bruteSimilar(it, candidate, sensitivity, dismissals) }) {
                    cluster += candidate
                    assigned += candidate.identity
                }
            }
            groups += (if (cluster.size == exact.size) DuplicateMatchKind.EXACT else DuplicateMatchKind.SIMILAR) to
                cluster.mapTo(mutableSetOf(), DuplicateCandidate::identity)
        }
        usable.filter { it.identity !in assigned }.forEach { seed ->
            if (seed.identity in assigned) return@forEach
            val cluster = mutableListOf(seed)
            usable.filter { it.identity !in assigned && it.identity != seed.identity }.forEach { candidate ->
                if (cluster.all { bruteSimilar(it, candidate, sensitivity, dismissals) }) cluster += candidate
            }
            if (cluster.size >= 2) {
                cluster.forEach { assigned += it.identity }
                val exact = cluster.all { !it.exactSha256.isNullOrBlank() } &&
                    cluster.mapNotNull(DuplicateCandidate::exactSha256).distinct().size == 1
                groups += (if (exact) DuplicateMatchKind.EXACT else DuplicateMatchKind.SIMILAR) to
                    cluster.mapTo(mutableSetOf(), DuplicateCandidate::identity)
            }
        }
        return groups.toSet()
    }

    private fun bruteSimilar(
        first: DuplicateCandidate,
        second: DuplicateCandidate,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>,
    ): Boolean {
        if (!first.exactSha256.isNullOrBlank() && first.exactSha256 == second.exactSha256) return true
        if (DuplicateDismissal.of(first.fingerprintKey, second.fingerprintKey) in dismissals) return false
        val widthDelta = kotlin.math.abs(first.width - second.width).toFloat() / maxOf(first.width, second.width)
        val heightDelta = kotlin.math.abs(first.height - second.height).toFloat() / maxOf(first.height, second.height)
        return maxOf(widthDelta, heightDelta) <= sensitivity.maxDimensionDelta &&
            kotlin.math.abs(first.averageLuma - second.averageLuma) <= sensitivity.maxLumaDelta &&
            java.lang.Long.bitCount(first.differenceHash xor second.differenceHash) <= sensitivity.maxHamming
    }

    private fun candidate(
        id: Long,
        sha: String? = null,
        hash: Long = 0L,
        luma: Int = 100,
        width: Int = 1080
    ) = DuplicateCandidate(
        uri = Uri.parse("content://media/external/images/media/$id"),
        dateAdded = id,
        displayName = "Screenshot_$id.png",
        width = width,
        height = 2400,
        sizeBytes = 1_000 + id,
        exactSha256 = sha,
        differenceHash = hash,
        averageLuma = luma
    )
}
