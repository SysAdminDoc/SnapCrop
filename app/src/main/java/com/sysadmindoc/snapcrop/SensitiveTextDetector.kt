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

data class SensitiveTextResult(
    val rects: List<Rect>,
    val regexCount: Int,
    val entityCount: Int
)

object SensitiveTextDetector {
    private val entityExtractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
    }

    suspend fun detect(bitmap: Bitmap): SensitiveTextResult {
        val blocks = TextExtractor.extract(bitmap)
        if (blocks.isEmpty()) return SensitiveTextResult(emptyList(), 0, 0)

        val blockRanges = mutableListOf<Pair<IntRange, TextBlock>>()
        val joined = buildString {
            blocks.forEachIndexed { index, block ->
                val start = length
                append(block.text)
                val endExclusive = length
                blockRanges.add((start until endExclusive) to block)
                if (index != blocks.lastIndex) append('\n')
            }
        }

        // Match patterns over the joined cross-block text and map ranges back to overlapping blocks,
        // so a value split across two ML Kit blocks (e.g. a card number wrapping a line) is still caught.
        val regexRanges = SensitiveTextPatterns.sensitiveMatchRanges(joined)
        val regexBlocks = blockRanges
            .filter { (range, _) -> regexRanges.any { it.overlaps(range) } }
            .map { it.second }
            .toSet()

        val entityRanges = extractSensitiveEntityRanges(joined)
        val entityBlocks = blockRanges
            .filter { (range, _) -> entityRanges.any { it.overlaps(range) } }
            .map { it.second }
            .toSet()

        val rects = (regexBlocks + entityBlocks)
            .map { it.bounds.padded(bitmap.width, bitmap.height) }
            .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }

        return SensitiveTextResult(
            rects = rects,
            regexCount = regexBlocks.size,
            entityCount = entityBlocks.size
        )
    }

    private suspend fun extractSensitiveEntityRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        return try {
            entityExtractor.downloadModelIfNeeded().awaitResult()
            val params = EntityExtractionParams.Builder(text).build()
            entityExtractor.annotate(params).awaitResult()
                .filter { annotation -> annotation.entities.any { it.isSensitiveEntity() } }
                .map { annotation -> annotation.start until annotation.end }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun Entity.isSensitiveEntity(): Boolean = when (type) {
        Entity.TYPE_EMAIL,
        Entity.TYPE_PHONE,
        Entity.TYPE_PAYMENT_CARD,
        Entity.TYPE_IBAN,
        Entity.TYPE_ADDRESS -> true
        else -> false
    }
}

internal object SensitiveTextPatterns {
    private val sensitivePatterns = listOf(
        Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
        Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)"),
        Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
        Regex("\\b(?:[0-9A-F]{2}[:-]){5}[0-9A-F]{2}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:[0-9A-F]{1,4}:){2,7}[0-9A-F]{1,4}\\b", RegexOption.IGNORE_CASE)
    )

    private val cardCandidate = Regex("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)")

    // Guard against pathological OCR text causing super-linear regex cost.
    private const val MAX_SCAN_LEN = 200_000

    fun containsSensitivePattern(text: String): Boolean {
        if (text.length > MAX_SCAN_LEN) return false
        if (sensitivePatterns.any { it.containsMatchIn(text) }) return true
        return cardCandidate.findAll(text).any { match ->
            val digits = match.value.filter { it.isDigit() }
            digits.length in 13..19 && passesLuhn(digits)
        }
    }

    /** All character ranges in [text] that look sensitive, for mapping back to OCR block bounds. */
    fun sensitiveMatchRanges(text: String): List<IntRange> {
        if (text.length > MAX_SCAN_LEN) return emptyList()
        val ranges = ArrayList<IntRange>()
        sensitivePatterns.forEach { p -> p.findAll(text).forEach { ranges.add(it.range) } }
        cardCandidate.findAll(text).forEach { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && passesLuhn(digits)) ranges.add(match.range)
        }
        return ranges
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
