package com.sysadmindoc.snapcrop

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs

internal enum class LayerAlignment { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }
internal enum class LayerDistribution { HORIZONTAL, VERTICAL }

internal data class DuplicatedLayers(
    val layers: List<DrawPath>,
    val selectedIndices: Set<Int>,
)

internal object DrawLayerArrangement {
    fun align(
        layers: List<DrawPath>,
        selectedIndices: Set<Int>,
        crop: Rect,
        alignment: LayerAlignment,
    ): List<DrawPath> = layers.mapIndexed { index, layer ->
        if (index !in selectedIndices || layer.points.isEmpty()) return@mapIndexed layer
        val bounds = layer.visualBounds()
        val dx = when (alignment) {
            LayerAlignment.LEFT -> crop.left - bounds.left
            LayerAlignment.CENTER -> crop.exactCenterX() - bounds.centerX()
            LayerAlignment.RIGHT -> crop.right - bounds.right
            else -> 0f
        }
        val dy = when (alignment) {
            LayerAlignment.TOP -> crop.top - bounds.top
            LayerAlignment.MIDDLE -> crop.exactCenterY() - bounds.centerY()
            LayerAlignment.BOTTOM -> crop.bottom - bounds.bottom
            else -> 0f
        }
        layer.copy(
            transOffsetX = layer.transOffsetX + dx,
            transOffsetY = layer.transOffsetY + dy,
        )
    }

    fun distribute(
        layers: List<DrawPath>,
        selectedIndices: Set<Int>,
        crop: Rect,
        distribution: LayerDistribution,
    ): List<DrawPath> {
        val selected = selectedIndices
            .filter { it in layers.indices && layers[it].points.isNotEmpty() }
            .map { index -> index to layers[index].visualBounds() }
            .sortedWith(compareBy<Pair<Int, RectF>> {
                if (distribution == LayerDistribution.HORIZONTAL) it.second.left else it.second.top
            }.thenBy { it.first })
        if (selected.size < 3) return layers
        val firstStart = selected.first().second.axisStart(distribution)
        val lastStart = selected.last().second.axisStart(distribution)
        val lastEnd = selected.last().second.axisEnd(distribution)
        val (start, end) = if (abs(lastStart - firstStart) >= 1f) {
            firstStart to lastEnd
        } else if (distribution == LayerDistribution.HORIZONTAL) {
            crop.left.toFloat() to crop.right.toFloat()
        } else {
            crop.top.toFloat() to crop.bottom.toFloat()
        }
        val totalSize = selected.sumOf { (_, bounds) -> bounds.axisSize(distribution).toDouble() }.toFloat()
        val gap = (end - start - totalSize) / (selected.size - 1)
        var cursor = start
        val deltas = selected.associate { (index, bounds) ->
            val delta = cursor - bounds.axisStart(distribution)
            cursor += bounds.axisSize(distribution) + gap
            index to delta
        }
        return layers.mapIndexed { index, layer ->
            val delta = deltas[index] ?: return@mapIndexed layer
            if (distribution == LayerDistribution.HORIZONTAL) {
                layer.copy(transOffsetX = layer.transOffsetX + delta)
            } else {
                layer.copy(transOffsetY = layer.transOffsetY + delta)
            }
        }
    }

    fun duplicate(
        layers: List<DrawPath>,
        selectedIndices: Set<Int>,
        crop: Rect,
    ): DuplicatedLayers {
        val selected = selectedIndices.filter { it in layers.indices }.sorted()
        if (selected.isEmpty()) return DuplicatedLayers(layers, emptySet())
        val limits = SnapCropProjectSidecar.DEFAULT_LIMITS
        val addedPoints = selected.sumOf { layers[it].points.size }
        if (layers.size + selected.size > limits.maxDrawLayers ||
            layers.sumOf { it.points.size } + addedPoints > limits.maxTotalPoints
        ) return DuplicatedLayers(layers, emptySet())
        val offset = (crop.width().coerceAtMost(crop.height()) * 0.025f).coerceIn(8f, 32f)
        val newSelection = linkedSetOf<Int>()
        val arranged = buildList {
            layers.forEachIndexed { index, layer ->
                add(layer)
                if (index in selectedIndices) {
                    add(
                        layer.copy(
                            points = layer.points.map { android.graphics.PointF(it.x, it.y) },
                            controlPoint = layer.controlPoint?.let { android.graphics.PointF(it.x, it.y) },
                            transOffsetX = layer.transOffsetX + offset,
                            transOffsetY = layer.transOffsetY + offset,
                        )
                    )
                    newSelection.add(lastIndex)
                }
            }
        }
        return DuplicatedLayers(
            layers = arranged,
            selectedIndices = newSelection,
        )
    }

    internal fun DrawPath.visualBounds(): RectF {
        val raw = when {
            points.isEmpty() -> RectF(0f, 0f, 0f, 0f)
            shapeType == "text" && !text.isNullOrEmpty() -> textBounds(strokeWidth * 3f)
            shapeType == "emoji" && !text.isNullOrEmpty() -> emojiBounds(strokeWidth * 5f)
            shapeType == "callout" -> {
                val point = points.first()
                val radius = strokeWidth * 2f
                RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius)
            }
            shapeType == "magnifier" -> {
                val point = points.first()
                val radius = 124f
                val centerY = point.y - 140f
                RectF(point.x - radius, centerY - radius, point.x + radius, centerY + radius)
            }
            shapeType == "measure" && points.size >= 2 -> measureBounds()
            else -> {
                val allPoints = points + listOfNotNull(controlPoint) + arrowHeadPoints()
                val halfStroke = when (shapeType) {
                    "neon" -> strokeWidth * 3.5f
                    "blur" -> strokeWidth * 2f
                    else -> strokeWidth / 2f
                }.coerceAtLeast(1f)
                RectF(
                    allPoints.minOf { it.x } - halfStroke,
                    allPoints.minOf { it.y } - halfStroke,
                    allPoints.maxOf { it.x } + halfStroke,
                    allPoints.maxOf { it.y } + halfStroke,
                )
            }
        }
        return RectF(raw).also { bounds -> transformMatrix()?.mapRect(bounds) }
    }

    private fun DrawPath.textBounds(textSize: Float): RectF {
        val point = points.first()
        val measured = Rect()
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = textSize }
            .getTextBounds(text.orEmpty(), 0, text.orEmpty().length, measured)
        val pad = if (filled && shapeType == "text") textSize * 0.3f else 0f
        return RectF(
            point.x - pad,
            point.y + measured.top - pad,
            point.x + measured.width() + pad,
            point.y + measured.bottom + pad,
        )
    }

    private fun DrawPath.emojiBounds(textSize: Float): RectF {
        val point = points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = textSize }
        val measured = Rect()
        paint.getTextBounds(text.orEmpty(), 0, text.orEmpty().length, measured)
        val baseline = point.y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        return RectF(
            point.x + measured.left,
            baseline + measured.top,
            point.x + measured.right,
            baseline + measured.bottom,
        )
    }

    private fun DrawPath.measureBounds(): RectF {
        val first = points.first()
        val last = points.last()
        val tick = strokeWidth * 2.5f
        val lineBounds = RectF(
            minOf(first.x, last.x) - tick,
            minOf(first.y, last.y) - tick,
            maxOf(first.x, last.x) + tick,
            maxOf(first.y, last.y) + tick,
        )
        val distance = kotlin.math.hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble())
        val label = "${distance.toInt()} px"
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (strokeWidth * 5f).coerceAtLeast(20f)
            textAlign = Paint.Align.CENTER
        }
        val measured = Rect()
        labelPaint.getTextBounds(label, 0, label.length, measured)
        val pad = labelPaint.textSize * 0.3f
        val middleX = (first.x + last.x) / 2f
        val baselineY = (first.y + last.y) / 2f - tick - strokeWidth
        lineBounds.union(
            middleX - measured.width() / 2f - pad,
            baselineY + measured.top - pad,
            middleX + measured.width() / 2f + pad,
            baselineY + measured.bottom + pad,
        )
        return lineBounds
    }

    private fun DrawPath.arrowHeadPoints(): List<android.graphics.PointF> {
        if (!isArrow || points.size < 2) return emptyList()
        val last = points.last()
        val previous = if (controlPoint != null) {
            val t = 0.95f
            android.graphics.PointF(
                (1 - t) * (1 - t) * points[0].x + 2 * (1 - t) * t * controlPoint.x + t * t * last.x,
                (1 - t) * (1 - t) * points[0].y + 2 * (1 - t) * t * controlPoint.y + t * t * last.y,
            )
        } else points[points.lastIndex - 1]
        val dx = last.x - previous.x
        val dy = last.y - previous.y
        val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length <= 0f) return emptyList()
        val ux = dx / length
        val uy = dy / length
        val headLength = strokeWidth * 4f
        val headWidth = strokeWidth * 2.5f
        return listOf(
            android.graphics.PointF(last.x - ux * headLength + uy * headWidth, last.y - uy * headLength - ux * headWidth),
            android.graphics.PointF(last.x - ux * headLength - uy * headWidth, last.y - uy * headLength + ux * headWidth),
        )
    }

    private fun RectF.axisStart(distribution: LayerDistribution): Float =
        if (distribution == LayerDistribution.HORIZONTAL) left else top

    private fun RectF.axisEnd(distribution: LayerDistribution): Float =
        if (distribution == LayerDistribution.HORIZONTAL) right else bottom

    private fun RectF.axisSize(distribution: LayerDistribution): Float =
        if (distribution == LayerDistribution.HORIZONTAL) width() else height()
}
