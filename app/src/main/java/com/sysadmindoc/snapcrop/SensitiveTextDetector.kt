package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SensitiveTextResult(
    val rects: List<Rect>,
    val regexCount: Int,
    val entityCount: Int,
    val detections: List<SensitiveTextDetection>
)

internal enum class SensitiveTextCategory {
    EMAIL, PHONE, PAYMENT_CARD, IPV4, IPV6, MAC_ADDRESS, IBAN, POSTAL_ADDRESS
}

internal enum class SensitiveTextDetectionSource { REGEX, ENTITY }

internal data class SensitiveTextMatch(
    val category: SensitiveTextCategory,
    val range: IntRange,
    val source: SensitiveTextDetectionSource
)

internal data class SensitiveTextDetection(
    val category: SensitiveTextCategory,
    val bounds: Rect,
    val source: SensitiveTextDetectionSource
)

internal object SensitiveTextDetector {
    private val entityExtractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
    }

    suspend fun detect(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.LATIN,
        failOnOcrError: Boolean = false
    ): SensitiveTextResult {
        val blocks = if (failOnOcrError) {
            TextExtractor.extractOrThrow(bitmap, script)
        } else {
            TextExtractor.extract(bitmap, script)
        }
        if (blocks.isEmpty()) return SensitiveTextResult(emptyList(), 0, 0, emptyList())

        val joined = joinBlocks(blocks)
        val entityMatches = extractSensitiveEntityMatches(joined.text)
        return detectBlocks(blocks, bitmap.width, bitmap.height, entityMatches)
    }

    /** Deterministic OCR-box stage shared by production and the privacy regression corpus. */
    internal fun detectBlocks(
        blocks: List<TextBlock>,
        imageWidth: Int,
        imageHeight: Int,
        entityMatches: List<SensitiveTextMatch> = emptyList()
    ): SensitiveTextResult {
        if (blocks.isEmpty()) return SensitiveTextResult(emptyList(), 0, 0, emptyList())

        val joined = joinBlocks(blocks)
        val regexMatches = SensitiveTextPatterns.sensitiveMatches(joined.text)
        val detections = (regexMatches + entityMatches).flatMap { match ->
            joined.ranges
                .asSequence()
                .filter { (range, _) -> match.range.overlaps(range) }
                .map { (_, block) ->
                    SensitiveTextDetection(
                        match.category,
                        block.bounds.padded(imageWidth, imageHeight),
                        match.source
                    )
                }
                .toList()
        }.distinctBy { detection ->
            with(detection.bounds) {
                "${detection.category}:$left:$top:$right:$bottom:${detection.source}"
            }
        }

        val rects = detections.map(SensitiveTextDetection::bounds)
            .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }

        return SensitiveTextResult(
            rects = rects,
            regexCount = detections.count { it.source == SensitiveTextDetectionSource.REGEX },
            entityCount = detections.count { it.source == SensitiveTextDetectionSource.ENTITY },
            detections = detections
        )
    }

    private data class JoinedBlocks(
        val text: String,
        val ranges: List<Pair<IntRange, TextBlock>>
    )

    private fun joinBlocks(blocks: List<TextBlock>): JoinedBlocks {
        val blockRanges = mutableListOf<Pair<IntRange, TextBlock>>()
        val joined = buildString {
            blocks.forEachIndexed { index, block ->
                val start = length
                append(block.text)
                blockRanges.add((start until length) to block)
                if (index != blocks.lastIndex) append('\n')
            }
        }
        return JoinedBlocks(joined, blockRanges)
    }

    private suspend fun extractSensitiveEntityMatches(text: String): List<SensitiveTextMatch> {
        if (text.isBlank()) return emptyList()
        return try {
            entityExtractor.downloadModelIfNeeded().awaitResult()
            val params = EntityExtractionParams.Builder(text).build()
            entityExtractor.annotate(params).awaitResult().mapNotNull { annotation ->
                val category = annotation.entities.firstNotNullOfOrNull { it.sensitiveCategory() }
                    ?: return@mapNotNull null
                SensitiveTextMatch(
                    category,
                    annotation.start until annotation.end,
                    SensitiveTextDetectionSource.ENTITY
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun Entity.sensitiveCategory(): SensitiveTextCategory? = when (type) {
        Entity.TYPE_EMAIL -> SensitiveTextCategory.EMAIL
        Entity.TYPE_PHONE -> SensitiveTextCategory.PHONE
        Entity.TYPE_PAYMENT_CARD -> SensitiveTextCategory.PAYMENT_CARD
        Entity.TYPE_IBAN -> SensitiveTextCategory.IBAN
        Entity.TYPE_ADDRESS -> SensitiveTextCategory.POSTAL_ADDRESS
        else -> null
    }
}

internal object SensitiveTextPatterns {
    private data class CategorizedPattern(val category: SensitiveTextCategory, val regex: Regex)

    private val sensitivePatterns = listOf(
        CategorizedPattern(SensitiveTextCategory.EMAIL, Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)),
        CategorizedPattern(SensitiveTextCategory.MAC_ADDRESS, Regex("\\b(?:[0-9A-F]{2}[:-]){5}[0-9A-F]{2}\\b", RegexOption.IGNORE_CASE)),
        CategorizedPattern(SensitiveTextCategory.IBAN, Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]){11,30}\\b", RegexOption.IGNORE_CASE))
    )

    private val phoneCandidate = Regex(
        "(?<!\\d)(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,4}\\)|\\d{2,4})[\\s.-]\\d{3,4}[\\s.-]\\d{3,4}(?!\\d)"
    )
    private val ipv4Candidate = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val ipv6Candidate = Regex(
        "(?<![0-9A-F:])(?:[0-9A-F]{0,4}:){2,7}[0-9A-F]{0,4}(?![0-9A-F:])",
        RegexOption.IGNORE_CASE
    )
    private val macCandidate = Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$", RegexOption.IGNORE_CASE)
    private val cardCandidate = Regex("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)")

    // Guard against pathological OCR text causing super-linear regex cost.
    private const val MAX_SCAN_LEN = 200_000

    fun containsSensitivePattern(text: String): Boolean = sensitiveMatches(text).isNotEmpty()

    /** All character ranges in [text] that look sensitive, for mapping back to OCR block bounds. */
    fun sensitiveMatchRanges(text: String): List<IntRange> = sensitiveMatches(text).map { it.range }

    fun sensitiveMatches(text: String): List<SensitiveTextMatch> {
        if (text.length > MAX_SCAN_LEN) return emptyList()
        val matches = ArrayList<SensitiveTextMatch>()
        sensitivePatterns.forEach { pattern ->
            pattern.regex.findAll(text).forEach {
                matches.add(SensitiveTextMatch(pattern.category, it.range, SensitiveTextDetectionSource.REGEX))
            }
        }
        ipv4Candidate.findAll(text).forEach { match ->
            if (match.value.split('.').all { it.toIntOrNull() in 0..255 }) {
                matches.add(
                    SensitiveTextMatch(
                        SensitiveTextCategory.IPV4,
                        match.range,
                        SensitiveTextDetectionSource.REGEX
                    )
                )
            }
        }
        ipv6Candidate.findAll(text).forEach { match ->
            if (!macCandidate.matches(match.value)) {
                matches.add(
                    SensitiveTextMatch(
                        SensitiveTextCategory.IPV6,
                        match.range,
                        SensitiveTextDetectionSource.REGEX
                    )
                )
            }
        }
        cardCandidate.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19 && passesLuhn(digits)) {
                matches.add(
                    SensitiveTextMatch(
                        SensitiveTextCategory.PAYMENT_CARD,
                        match.range,
                        SensitiveTextDetectionSource.REGEX
                    )
                )
            }
        }
        phoneCandidate.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            val isCard = digits.length in 13..19 && passesLuhn(digits)
            val hasPhoneSyntax = match.value.startsWith('+') ||
                match.value.any { it == '(' || it == ')' || it == '-' || it == '.' }
            val isIpv4Shape = ipv4Candidate.matches(match.value)
            if (digits.length in 10..15 && hasPhoneSyntax && !isCard && !isIpv4Shape) {
                matches.add(
                    SensitiveTextMatch(
                        SensitiveTextCategory.PHONE,
                        match.range,
                        SensitiveTextDetectionSource.REGEX
                    )
                )
            }
        }
        return matches.distinctBy { Triple(it.category, it.range.first, it.range.last) }
    }

    fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum > 0 && sum % 10 == 0
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun Rect.padded(maxWidth: Int, maxHeight: Int): Rect {
    val padX = (width() * 0.08f).toInt().coerceAtLeast(8)
    val padY = (height() * 0.16f).toInt().coerceAtLeast(6)
    return Rect(
        (left - padX).coerceIn(0, maxWidth),
        (top - padY).coerceIn(0, maxHeight),
        (right + padX).coerceIn(0, maxWidth),
        (bottom + padY).coerceIn(0, maxHeight)
    )
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
