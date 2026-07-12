package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect

/** Renders the same retained source tiles for editor preview and final export. */
internal object CutoutBitmapRenderer {
    fun render(
        source: Bitmap,
        plan: CutoutSqueeze,
        drawSeparators: Boolean = true,
        preserveGainmap: Boolean = true,
    ): Bitmap {
        require(source.width == plan.sourceWidth && source.height == plan.sourceHeight) {
            "Cut plan dimensions do not match the bitmap"
        }
        if (plan.bands.isEmpty()) return source

        val output = Bitmap.createBitmap(plan.outputWidth, plan.outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val horizontal = retainedIntervals(source.height, plan.horizontalBands)
        val vertical = retainedIntervals(source.width, plan.verticalBands)
        var outputTop = 0
        for (row in horizontal) {
            var outputLeft = 0
            for (column in vertical) {
                canvas.drawBitmap(
                    source,
                    Rect(column.first, row.first, column.last + 1, row.last + 1),
                    Rect(
                        outputLeft,
                        outputTop,
                        outputLeft + column.count(),
                        outputTop + row.count(),
                    ),
                    bitmapPaint,
                )
                outputLeft += column.count()
            }
            outputTop += row.count()
        }
        if (drawSeparators) drawSeparators(canvas, plan)
        if (preserveGainmap) {
            // A very small gain map can round a near-full cut to zero retained pixels. In that
            // case keep the correctly rendered SDR base instead of attaching misaligned HDR data.
            runCatching {
                preserveUltraHdrGainmap(source, output) { contents ->
                    val scaledPlan = scaledPlan(plan, contents.width, contents.height)
                    render(contents, scaledPlan, drawSeparators = false, preserveGainmap = false)
                }
            }
        }
        return output
    }

    private fun retainedIntervals(limit: Int, bands: List<CutBand>): List<IntRange> = buildList {
        var cursor = 0
        bands.forEach { band ->
            if (cursor < band.start) add(cursor until band.start)
            cursor = band.endExclusive
        }
        if (cursor < limit) add(cursor until limit)
    }

    private fun drawSeparators(canvas: Canvas, plan: CutoutSqueeze) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99000000.toInt()
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        plan.separators.forEach { separator ->
            when (separator.style) {
                CutSeparatorStyle.STRAIGHT -> drawStraight(canvas, plan, separator, shadow, line)
                CutSeparatorStyle.DASHED -> {
                    shadow.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    line.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    drawStraight(canvas, plan, separator, shadow, line)
                    shadow.pathEffect = null
                    line.pathEffect = null
                }
                CutSeparatorStyle.TORN -> {
                    val path = tornPath(plan, separator)
                    canvas.drawPath(path, shadow)
                    canvas.drawPath(path, line)
                }
            }
        }
    }

    private fun drawStraight(
        canvas: Canvas,
        plan: CutoutSqueeze,
        separator: CutSeparator,
        shadow: Paint,
        line: Paint,
    ) {
        val position = separator.outputPosition.toFloat().coerceAtLeast(0.5f)
        if (separator.axis == CutAxis.HORIZONTAL) {
            canvas.drawLine(0f, position, plan.outputWidth.toFloat(), position, shadow)
            canvas.drawLine(0f, position, plan.outputWidth.toFloat(), position, line)
        } else {
            canvas.drawLine(position, 0f, position, plan.outputHeight.toFloat(), shadow)
            canvas.drawLine(position, 0f, position, plan.outputHeight.toFloat(), line)
        }
    }

    private fun tornPath(plan: CutoutSqueeze, separator: CutSeparator): Path {
        val path = Path()
        val seam = separator.outputPosition.toFloat()
        val length = if (separator.axis == CutAxis.HORIZONTAL) plan.outputWidth else plan.outputHeight
        val step = 18f
        val amplitude = 4f
        var cursor = 0f
        if (separator.axis == CutAxis.HORIZONTAL) path.moveTo(0f, seam) else path.moveTo(seam, 0f)
        var direction = 1f
        while (cursor < length) {
            cursor = (cursor + step).coerceAtMost(length.toFloat())
            if (separator.axis == CutAxis.HORIZONTAL) {
                path.lineTo(cursor, seam + amplitude * direction)
            } else {
                path.lineTo(seam + amplitude * direction, cursor)
            }
            direction = -direction
        }
        return path
    }

    private fun scaledPlan(plan: CutoutSqueeze, width: Int, height: Int): CutoutSqueeze {
        val xScale = width.toDouble() / plan.sourceWidth
        val yScale = height.toDouble() / plan.sourceHeight
        val bands = plan.bands.mapNotNull { band ->
            val scale = if (band.axis == CutAxis.VERTICAL) xScale else yScale
            val start = kotlin.math.floor(band.start * scale).toInt()
            val end = kotlin.math.ceil(band.endExclusive * scale).toInt()
            if (end > start) CutBand(band.axis, start, end) else null
        }
        return CutoutSqueeze.create(width, height, bands, plan.separatorStyle)
    }
}
