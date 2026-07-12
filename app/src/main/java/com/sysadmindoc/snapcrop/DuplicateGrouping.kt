package com.sysadmindoc.snapcrop

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln

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

internal data class DuplicateGroupingMetrics(var similarityChecks: Long = 0)

internal object DuplicateGrouping {
    fun group(
        candidates: List<DuplicateCandidate>,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>,
        metrics: DuplicateGroupingMetrics? = null,
    ): List<DuplicateGroup> {
        val usable = candidates.asSequence()
            .filter { it.width > 0 && it.height > 0 }
            .distinctBy(DuplicateCandidate::identity)
            .sortedWith(compareBy<DuplicateCandidate> { it.dateAdded }.thenBy { it.uri.toString() })
            .toList()
        val candidateIndex = CandidateIndex(usable, sensitivity)
        val indexByIdentity = usable.indices.associateBy { usable[it].identity }
        val assigned = BooleanArray(usable.size)
        val groups = mutableListOf<DuplicateGroup>()

        val exactGroups = usable.filter { !it.exactSha256.isNullOrBlank() }
            .groupBy { it.exactSha256 }
            .values
            .filter { it.size >= 2 }
        exactGroups.flatten().forEach { assigned[indexByIdentity.getValue(it.identity)] = true }
        exactGroups.forEach { exact ->
                val cluster = exact.toMutableList()
                candidateIndex.potentialMatches(exact.first()).forEach { candidatePosition ->
                    if (!assigned[candidatePosition]) {
                        val candidate = usable[candidatePosition]
                        if (cluster.all { member -> similar(member, candidate, sensitivity, dismissals, metrics) }) {
                            cluster += candidate
                            assigned[candidatePosition] = true
                        }
                    }
                }
                groups += createGroup(
                    cluster,
                    if (cluster.size == exact.size) DuplicateMatchKind.EXACT else DuplicateMatchKind.SIMILAR
                )
            }

        usable.indices.forEach { seedPosition ->
            if (assigned[seedPosition]) return@forEach
            val seed = usable[seedPosition]
            val cluster = mutableListOf(seed)
            candidateIndex.potentialMatches(seed).forEach { candidatePosition ->
                if (!assigned[candidatePosition] && candidatePosition != seedPosition) {
                    val candidate = usable[candidatePosition]
                    if (cluster.all { member -> similar(member, candidate, sensitivity, dismissals, metrics) }) {
                        cluster += candidate
                    }
                }
            }
            if (cluster.size >= 2) {
                cluster.forEach { assigned[indexByIdentity.getValue(it.identity)] = true }
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

    /**
     * Dismissals that split a single candidate out of a group without discarding the matches
     * among the remaining candidates: only the pairs between [identity] and every other member.
     */
    fun dismissalsForCandidate(group: DuplicateGroup, identity: String): Set<DuplicateDismissal> {
        val target = group.candidates.firstOrNull { it.identity == identity } ?: return emptySet()
        return group.candidates.asSequence()
            .filter { it.identity != identity }
            .mapTo(mutableSetOf()) { DuplicateDismissal.of(target.fingerprintKey, it.fingerprintKey) }
    }

    private fun similar(
        first: DuplicateCandidate,
        second: DuplicateCandidate,
        sensitivity: DuplicateSensitivity,
        dismissals: Set<DuplicateDismissal>,
        metrics: DuplicateGroupingMetrics?,
    ): Boolean {
        if (metrics != null) metrics.similarityChecks++
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

    /**
     * Lossless candidate index for the three perceptual gates. Logarithmic dimension cells and
     * luma cells only require adjacent-bucket probes. The hash is split into maxHamming + 1
     * prefix bands; by the pigeonhole principle, hashes within the threshold share one full band.
     */
    private class CandidateIndex(
        private val candidates: List<DuplicateCandidate>,
        private val sensitivity: DuplicateSensitivity,
    ) {
        private data class Bucket(
            val width: Int,
            val height: Int,
            val luma: Int,
            val hashBand: Int,
            val hashPrefix: Long,
        )

        private val dimensionStep = -ln(1.0 - sensitivity.maxDimensionDelta)
        private val lumaStep = sensitivity.maxLumaDelta + 1
        private val buckets = HashMap<Bucket, MutableList<Int>>()
        private val exactHashes = HashMap<String, MutableList<Int>>()

        init {
            candidates.forEachIndexed { index, candidate ->
                candidate.exactSha256?.takeIf(String::isNotBlank)?.let { sha ->
                    exactHashes.getOrPut(sha) { mutableListOf() } += index
                }
                val width = dimensionBucket(candidate.width)
                val height = dimensionBucket(candidate.height)
                val luma = Math.floorDiv(candidate.averageLuma, lumaStep)
                forEachHashPrefix(candidate.differenceHash) { band, prefix ->
                    buckets.getOrPut(Bucket(width, height, luma, band, prefix)) { mutableListOf() } += index
                }
            }
        }

        fun potentialMatches(candidate: DuplicateCandidate): IntArray {
            val matches = HashSet<Int>()
            candidate.exactSha256?.takeIf(String::isNotBlank)?.let { sha ->
                exactHashes[sha]?.let(matches::addAll)
            }
            val width = dimensionBucket(candidate.width)
            val height = dimensionBucket(candidate.height)
            val luma = Math.floorDiv(candidate.averageLuma, lumaStep)
            forEachHashPrefix(candidate.differenceHash) { band, prefix ->
                for (widthOffset in -1..1) {
                    for (heightOffset in -1..1) {
                        for (lumaOffset in -1..1) {
                            buckets[Bucket(
                                width + widthOffset,
                                height + heightOffset,
                                luma + lumaOffset,
                                band,
                                prefix,
                            )]?.let(matches::addAll)
                        }
                    }
                }
            }
            return matches.sorted().toIntArray()
        }

        private fun dimensionBucket(value: Int): Int = floor(ln(value.toDouble()) / dimensionStep).toInt()

        private inline fun forEachHashPrefix(hash: Long, action: (band: Int, prefix: Long) -> Unit) {
            val bandCount = sensitivity.maxHamming + 1
            val baseWidth = Long.SIZE_BITS / bandCount
            val widerBands = Long.SIZE_BITS % bandCount
            var offset = 0
            repeat(bandCount) { band ->
                val width = baseWidth + if (band < widerBands) 1 else 0
                val mask = (1L shl width) - 1L
                action(band, (hash ushr offset) and mask)
                offset += width
            }
        }
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
