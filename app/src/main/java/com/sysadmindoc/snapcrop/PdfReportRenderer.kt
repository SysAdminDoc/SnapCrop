package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class PdfReportRenderer(private val context: Context) {
    fun drawCoverPage(
        doc: PdfDocument,
        pageNumber: Int,
        title: String,
        notes: String,
        itemCount: Int,
        createdAt: Long,
        includeOcr: Boolean,
        layout: PdfReportLayout
    ): Int {
        data class CoverLine(val text: String?, val paint: Paint?, val height: Float)

        val lines = mutableListOf<CoverLine>()
        wrapPdfTextLines(title, titlePaint(), layout.contentWidth).forEach {
            lines += CoverLine(it, titlePaint(), 34f)
        }
        lines += CoverLine(null, null, 18f)
        val summaryLines = listOf(
            context.getString(R.string.pdf_created, formatTimestamp(createdAt)),
            context.getString(R.string.pdf_image_count, itemCount),
            context.getString(if (includeOcr) R.string.pdf_ocr_enabled else R.string.pdf_ocr_off)
        ).joinToString("\n")
        wrapPdfTextLines(summaryLines, bodyPaint(), layout.contentWidth).forEach {
            lines += CoverLine(it, bodyPaint(), 17f)
        }
        if (notes.isNotBlank()) {
            lines += CoverLine(null, null, 22f)
            wrapPdfTextLines(context.getString(R.string.pdf_notes), sectionPaint(), layout.contentWidth).forEach {
                lines += CoverLine(it, sectionPaint(), 22f)
            }
            lines += CoverLine(null, null, 6f)
            wrapPdfTextLines(notes, bodyPaint(), layout.contentWidth).forEach {
                lines += CoverLine(it, bodyPaint(), 17f)
            }
        }

        var currentPageNumber = pageNumber
        val startY = layout.marginPoints + 38f
        paginatePdfLines(lines.map(CoverLine::height), startY, layout.contentBottom).forEach { indices ->
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create()
            )
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            var y = startY
            indices.forEach { index ->
                val line = lines[index]
                line.text?.let { canvas.drawText(it, layout.marginPoints, y, checkNotNull(line.paint)) }
                y += line.height
            }
            drawPdfFooter(canvas, currentPageNumber, layout)
            doc.finishPage(page)
            currentPageNumber++
        }
        return currentPageNumber
    }

    fun drawImagePage(
        doc: PdfDocument,
        pageNumber: Int,
        itemNumber: Int,
        totalItems: Int,
        bitmap: Bitmap,
        metadata: ExportItemMetadata,
        ocrBlocks: List<TextBlock> = emptyList(),
        layout: PdfReportLayout
    ): Int {
        var currentPageNumber = pageNumber
        var page = doc.startPage(
            PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create()
        )
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = layout.marginPoints + 24f
        y = drawWrappedText(
            canvas,
            context.getString(R.string.pdf_image_of, itemNumber, totalItems),
            layout.marginPoints,
            y,
            layout.contentWidth,
            sectionPaint(),
            20f
        )
        val metaText = buildString {
            append(metadata.displayName)
            if (metadata.sourceHint.isNotBlank()) {
                append("\n").append(context.getString(R.string.pdf_source, metadata.sourceHint))
            }
            if (metadata.relativePath.isNotBlank()) {
                append("\n").append(context.getString(R.string.pdf_album, metadata.relativePath))
            }
            append("\n").append(
                context.getString(
                    R.string.pdf_dimensions,
                    metadata.width.takeIf { it > 0 } ?: bitmap.width,
                    metadata.height.takeIf { it > 0 } ?: bitmap.height
                )
            )
            if (metadata.sizeBytes > 0) {
                append("\n").append(context.getString(R.string.pdf_size, formatExportSize(metadata.sizeBytes)))
            }
            if (metadata.dateAddedSeconds > 0) {
                append("\n").append(
                    context.getString(R.string.pdf_date_added, formatTimestamp(metadata.dateAddedSeconds * 1000))
                )
            }
            if (metadata.categories.isNotEmpty()) {
                append("\n").append(context.getString(R.string.pdf_tags, metadata.categories.joinToString(", ")))
            }
        }
        y = drawWrappedText(canvas, metaText, layout.marginPoints, y + 6f, layout.contentWidth, smallPaint(), 13f)
        var imageTop = y + 24f
        var fitted = layout.fitImage(bitmap.width, bitmap.height, imageTop)
        if (fitted == null || fitted.height < MIN_REPORT_IMAGE_POINTS) {
            drawPdfFooter(canvas, currentPageNumber, layout)
            doc.finishPage(page)
            currentPageNumber++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create()
            )
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            val headingBottom = drawWrappedText(
                canvas,
                context.getString(R.string.pdf_image_of, itemNumber, totalItems),
                layout.marginPoints,
                layout.marginPoints + 24f,
                layout.contentWidth,
                sectionPaint(),
                20f
            )
            imageTop = headingBottom + 12f
            fitted = checkNotNull(layout.fitImage(bitmap.width, bitmap.height, imageTop))
        }
        val placement = checkNotNull(fitted)
        val scale = placement.width / bitmap.width
        val left = placement.left
        val top = placement.top
        val destination = RectF(placement.left, placement.top, placement.right, placement.bottom)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 218, 226)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawBitmap(bitmap, null, destination, null)
        canvas.drawRect(destination, border)
        if (ocrBlocks.isNotEmpty()) {
            val invisiblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.TRANSPARENT
                textSize = 1f
            }
            for (block in ocrBlocks) {
                val bounds = block.bounds
                val blockX = left + bounds.left * scale
                val blockY = top + bounds.top * scale
                val blockWidth = bounds.width() * scale
                val blockHeight = bounds.height() * scale
                if (blockWidth < 1f || blockHeight < 1f) continue
                val textLines = block.text.lines().filter(String::isNotBlank)
                if (textLines.isEmpty()) continue
                val lineHeight = blockHeight / textLines.size
                invisiblePaint.textSize = (lineHeight * 0.85f).coerceIn(1f, 40f)
                textLines.forEachIndexed { lineIndex, line ->
                    canvas.drawText(line, blockX, blockY + lineHeight * (lineIndex + 0.8f), invisiblePaint)
                }
            }
        }
        drawPdfFooter(canvas, currentPageNumber, layout)
        doc.finishPage(page)
        return currentPageNumber + 1
    }

    fun drawOcrAppendixPages(
        doc: PdfDocument,
        startPageNumber: Int,
        entries: List<Pair<ExportItemMetadata, String>>,
        layout: PdfReportLayout
    ): Int {
        var pageNumber = startPageNumber
        var page = doc.startPage(
            PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, pageNumber).create()
        )
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = layout.marginPoints + 24f
        val appendixLabel = context.getString(R.string.pdf_ocr_appendix)
        y = drawWrappedText(canvas, appendixLabel, layout.marginPoints, y, layout.contentWidth, sectionPaint(), 24f)
        val textPaint = smallPaint()
        val maxWidth = layout.contentWidth
        val bottom = layout.contentBottom

        fun nextPage() {
            drawPdfFooter(canvas, pageNumber, layout)
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, pageNumber).create()
            )
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = layout.marginPoints + 24f
            y = drawWrappedText(canvas, appendixLabel, layout.marginPoints, y, maxWidth, sectionPaint(), 24f)
        }

        entries.forEach { (metadata, text) ->
            val headingLines = wrapPdfTextLines(metadata.displayName, sectionPaint(), maxWidth)
            val boundedText = if (text.length > MAX_OCR_APPENDIX_CHARS) {
                text.take(MAX_OCR_APPENDIX_CHARS) + "\n" + context.getString(R.string.pdf_text_truncated)
            } else {
                text
            }
            val lines = wrapPdfTextLines(boundedText, textPaint, maxWidth)
            if (y + headingLines.size * 20f + minOf(1, lines.size) * 13f > bottom) nextPage()
            headingLines.forEach { line ->
                canvas.drawText(line, layout.marginPoints, y, sectionPaint())
                y += 20f
            }
            lines.forEach { line ->
                if (y + 13f > bottom) nextPage()
                canvas.drawText(line, layout.marginPoints, y, textPaint)
                y += 13f
            }
            y += 13f
        }
        drawPdfFooter(canvas, pageNumber, layout)
        doc.finishPage(page)
        return pageNumber + 1
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int = Int.MAX_VALUE
    ): Float {
        var y = startY
        var linesDrawn = 0
        for (line in wrapPdfTextLines(text, paint, maxWidth)) {
            if (linesDrawn >= maxLines) break
            canvas.drawText(line, x, y, paint)
            y += lineHeight
            linesDrawn++
        }
        return y
    }

    private fun drawPdfFooter(canvas: Canvas, pageNumber: Int, layout: PdfReportLayout) {
        val paint = smallPaint().apply { color = android.graphics.Color.rgb(92, 103, 115) }
        val pageLabel = context.getString(R.string.pdf_page, pageNumber)
        canvas.drawText(context.getString(R.string.pdf_footer), layout.marginPoints, layout.footerBaseline, paint)
        canvas.drawText(
            pageLabel,
            layout.widthPoints - layout.marginPoints - paint.measureText(pageLabel),
            layout.footerBaseline,
            paint
        )
    }

    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 29, 39)
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun sectionPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(25, 94, 145)
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(34, 44, 55)
        textSize = 11f
    }

    private fun smallPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(56, 68, 80)
        textSize = 9f
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private companion object {
        const val MIN_REPORT_IMAGE_POINTS = 72f
        const val MAX_OCR_APPENDIX_CHARS = 4_000
    }
}
