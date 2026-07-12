package com.sysadmindoc.snapcrop

import android.graphics.Paint
import kotlin.math.roundToInt

enum class ReportPagePreset { A4, LETTER, CUSTOM }
enum class ReportPageOrientation { PORTRAIT, LANDSCAPE }

data class ReportPageSettings(
    val preset: ReportPagePreset = ReportPagePreset.A4,
    val orientation: ReportPageOrientation = ReportPageOrientation.PORTRAIT,
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val marginMm: Float = 15f
) {
    fun layoutOrNull(): PdfReportLayout? {
        val base = when (preset) {
            ReportPagePreset.A4 -> 210f to 297f
            ReportPagePreset.LETTER -> 215.9f to 279.4f
            ReportPagePreset.CUSTOM -> customWidthMm to customHeightMm
        }
        if (base.first !in MIN_DIMENSION_MM..MAX_DIMENSION_MM ||
            base.second !in MIN_DIMENSION_MM..MAX_DIMENSION_MM ||
            marginMm !in MIN_MARGIN_MM..MAX_MARGIN_MM
        ) return null
        val short = minOf(base.first, base.second)
        val long = maxOf(base.first, base.second)
        val (widthMm, heightMm) = when (orientation) {
            ReportPageOrientation.PORTRAIT -> short to long
            ReportPageOrientation.LANDSCAPE -> long to short
        }
        if (widthMm - marginMm * 2 < MIN_CONTENT_MM ||
            heightMm - marginMm * 2 < MIN_CONTENT_MM
        ) return null
        return PdfReportLayout(
            widthPoints = millimetersToPoints(widthMm),
            heightPoints = millimetersToPoints(heightMm),
            marginPoints = millimetersToPoints(marginMm).toFloat(),
            widthMm = widthMm,
            heightMm = heightMm,
            marginMm = marginMm
        )
    }

    companion object {
        const val MIN_DIMENSION_MM = 50f
        const val MAX_DIMENSION_MM = 500f
        const val MIN_MARGIN_MM = 5f
        const val MAX_MARGIN_MM = 50f
        const val MIN_CONTENT_MM = 120f

        internal fun millimetersToPoints(value: Float): Int = (value / 25.4f * 72f).roundToInt()
    }
}

data class PdfReportLayout(
    val widthPoints: Int,
    val heightPoints: Int,
    val marginPoints: Float,
    val widthMm: Float,
    val heightMm: Float,
    val marginMm: Float
) {
    val contentWidth: Float get() = widthPoints - marginPoints * 2
    val footerBaseline: Float get() = heightPoints - maxOf(14f, marginPoints * 0.35f)
    val contentBottom: Float get() = footerBaseline - 24f

    fun linesAvailable(startY: Float, lineHeight: Float): Int =
        ((contentBottom - startY) / lineHeight).toInt().coerceAtLeast(0)

    fun fitImage(bitmapWidth: Int, bitmapHeight: Int, top: Float): PdfRect? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val maxHeight = contentBottom - top
        if (contentWidth <= 0f || maxHeight <= 0f) return null
        val scale = minOf(contentWidth / bitmapWidth, maxHeight / bitmapHeight)
        if (!scale.isFinite() || scale <= 0f) return null
        val width = bitmapWidth * scale
        val height = bitmapHeight * scale
        val left = marginPoints + (contentWidth - width) / 2f
        val imageTop = top + (maxHeight - height) / 2f
        return PdfRect(left, imageTop, left + width, imageTop + height)
    }
}

data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal fun paginatePdfLines(heights: List<Float>, startY: Float, bottom: Float): List<IntRange> {
    if (heights.isEmpty()) return listOf(IntRange.EMPTY)
    require(startY < bottom)
    val pages = mutableListOf<IntRange>()
    var pageStart = 0
    var y = startY
    heights.forEachIndexed { index, height ->
        require(height > 0f && height <= bottom - startY)
        if (y + height > bottom) {
            pages += pageStart until index
            pageStart = index
            y = startY
        }
        y += height
    }
    pages += pageStart until heights.size
    return pages
}

internal fun wrapPdfTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
    require(maxWidth > 0f)
    val lines = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
        var current = ""
        paragraph.split(' ').filter { it.isNotBlank() }.forEach wordLoop@ { word ->
            if (paint.measureText(word) > maxWidth) {
                if (current.isNotBlank()) {
                    lines.add(current)
                    current = ""
                }
                val segments = splitPdfTokenToWidth(word, paint, maxWidth)
                lines.addAll(segments.dropLast(1))
                current = segments.lastOrNull().orEmpty()
                return@wordLoop
            }
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
        }
        if (current.isNotBlank()) lines.add(current)
        if (paragraph.isBlank()) lines.add("")
    }
    return lines.ifEmpty { listOf("") }
}

private fun splitPdfTokenToWidth(token: String, paint: Paint, maxWidth: Float): List<String> {
    if (token.isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var current = StringBuilder()
    token.codePoints().forEach { codePoint ->
        val candidate = StringBuilder(current).appendCodePoint(codePoint).toString()
        if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
            result += current.toString()
            current = StringBuilder().appendCodePoint(codePoint)
        } else {
            current.appendCodePoint(codePoint)
        }
    }
    if (current.isNotEmpty()) result += current.toString()
    return result.ifEmpty { listOf(token) }
}
