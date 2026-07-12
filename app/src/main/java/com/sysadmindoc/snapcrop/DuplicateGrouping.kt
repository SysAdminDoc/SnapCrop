package com.sysadmindoc.snapcrop

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs

enum class DuplicateSensitivity(val maxHamming: Int, val maxLumaDelta: Int, val maxDimensionDelta: Float) {
    STRICT(2, 8, 0.02f),
    BALANCED(6, 18, 0.06f),
    LOOSE(10, 32, 0.12f)
}

enum class DuplicateMatchKind { EXACT, SIMILAR }

data class DuplicateCandidate(
    val uri: Uri,
    val dateAdded: Long,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val exactSha256: String?,
    val differenceHash: Long,
    val averageLuma: Int
) {
    val identity: String get() = "${uri}\u0000$dateAdded"
    val fingerprintKey: String get() = exactSha256?.takeIf(String::isNotBlank)
        ?.let { "sha256:$it" }
        ?: "dhash:$width:$height:${differenceHash.toULong().toString(16)}:$averageLuma:$sizeBytes"
}

data class DuplicateGroup(
    val id: String,
    val kind: DuplicateMatchKind,
    val candidates: List<DuplicateCandidate>
)

data class DuplicateDismissal(val firstFingerprint: String, val secondFingerprint: String) {
    companion object {
        fun of(first: String, second: String): DuplicateDismissal =
            if (first <= second) DuplicateDismissal(first, second) else DuplicateDismissal(second, first)
    }
}

internal object DuplicateGrouping {
    fun group(
        candidates: List<DuplicateCandidate>,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>
    ): List<DuplicateGroup> {
        val usable = candidates.asSequence()
            .filter { it.width > 0 && it.height > 0 }
            .distinctBy(DuplicateCandidate::identity)
            .sortedWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
            .toList()
        val assigned = mutableSetOf<String>()
        val groups = mutableListOf<DuplicateGroup>()

        val exactGroups = usable.filter { !it.exactSha256.isNullOrBlank() }
            .groupBy { it.exactSha256 }
            .values
            .filter { it.size >= 2 }
        exactGroups.flatten().forEach { assigned += it.identity }
        exactGroups.forEach { exact ->
                val cluster = exact.toMutableList()
                usable.asSequence()
                    .filter { it.identity !in assigned }
                    .forEach { candidate ->
                        if (cluster.all { member -> similar(member, candidate, sensitivity, dismissals) }) {
                            cluster += candidate
                            assigned += candidate.identity
                        }
                    }
                groups += createGroup(
                    cluster,
                    if (cluster.size == exact.size) DuplicateMatchKind.EXACT else DuplicateMatchKind.SIMILAR
                )
            }

        usable.filter { it.identity !in assigned }.forEach { seed ->
            if (seed.identity in assigned) return@forEach
            val cluster = mutableListOf(seed)
            usable.asSequence()
                .filter { it.identity !in assigned && it.identity != seed.identity }
                .forEach { candidate ->
                    if (cluster.all { member -> similar(member, candidate, sensitivity, dismissals) }) {
                        cluster += candidate
                    }
                }
            if (cluster.size >= 2) {
                cluster.forEach { assigned += it.identity }
                val exact = cluster.mapNotNull(DuplicateCandidate::exactSha256).distinct().size == 1 &&
                    cluster.all { !it.exactSha256.isNullOrBlank() }
                groups += createGroup(cluster, if (exact) DuplicateMatchKind.EXACT else DuplicateMatchKind.SIMILAR)
            }
        }
        return groups.sortedWith(
            compareByDescending<DuplicateGroup> { it.candidates.sumOf(DuplicateCandidate::sizeBytes) }
                .thenBy(DuplicateGroup::id)
        )
    }

    fun dismissalsFor(group: DuplicateGroup): Set<DuplicateDismissal> = buildSet {
        group.candidates.forEachIndexed { index, first ->
            group.candidates.drop(index + 1).forEach { second ->
                add(DuplicateDismissal.of(first.fingerprintKey, second.fingerprintKey))
            }
        }
    }

    private fun similar(
        first: DuplicateCandidate,
        second: DuplicateCandidate,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>
    ): Boolean {
        if (!first.exactSha256.isNullOrBlank() && first.exactSha256 == second.exactSha256) return true
        if (DuplicateDismissal.of(first.fingerprintKey, second.fingerprintKey) in dismissals) return false
        val widthDelta = abs(first.width - second.width).toFloat() / maxOf(first.width, second.width)
        val heightDelta = abs(first.height - second.height).toFloat() / maxOf(first.height, second.height)
        if (maxOf(widthDelta, heightDelta) > sensitivity.maxDimensionDelta) return false
        if (abs(first.averageLuma - second.averageLuma) > sensitivity.maxLumaDelta) return false
        return java.lang.Long.bitCount(first.differenceHash xor second.differenceHash) <= sensitivity.maxHamming
    }

    private fun createGroup(candidates: List<DuplicateCandidate>, kind: DuplicateMatchKind): DuplicateGroup {
        val sorted = candidates.sortedWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sorted.joinToString("\u0001", transform = DuplicateCandidate::identity).toByteArray(StandardCharsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        return DuplicateGroup(digest, kind, sorted)
    }
}

internal object DuplicateReviewSelection {
    fun keepOldest(group: DuplicateGroup): Set<String> = removeAllExcept(
        group,
        group.candidates.minWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
    )

    fun keepNewest(group: DuplicateGroup): Set<String> = removeAllExcept(
        group,
        group.candidates.maxWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
    )

    fun toggle(group: DuplicateGroup, removals: Set<String>, identity: String): Set<String> {
        if (group.candidates.none { it.identity == identity }) return removals
        val next = removals.toMutableSet().apply {
            if (!add(identity)) remove(identity)
        }
        return if (next.size >= group.candidates.size) removals else next
    }

    private fun removeAllExcept(group: DuplicateGroup, keeper: DuplicateCandidate): Set<String> =
        group.candidates.asSequence().filter { it.identity != keeper.identity }
            .mapTo(linkedSetOf(), DuplicateCandidate::identity)
}
