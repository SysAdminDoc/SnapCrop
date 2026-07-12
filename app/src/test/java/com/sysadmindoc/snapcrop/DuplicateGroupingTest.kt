package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
