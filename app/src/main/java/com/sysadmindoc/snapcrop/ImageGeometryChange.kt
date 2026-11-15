package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A whole-image change of geometry (quarter turn, mirror or downscale) and the matching change
 * for editor state that lives in source-pixel coordinates.
 *
 * The activity swaps the bitmap. Everything the editor stores in bitmap space (crop, redactions,
 * draw layers, perspective quad, cut bands, undo history) has to go through the same change, or a
 * redaction keeps covering the spot where the private detail used to be.
 */
internal sealed class ImageGeometryChange(val sourceWidth: Int, val sourceHeight: Int) {
    abstract val targetWidth: Int
    abstract val targetHeight: Int

    /** Quarter turn clockwise, the same mapping as `Matrix.postRotate(90f)` in `Bitmap.createBitmap`. */
    class RotateClockwise(sourceWidth: Int, sourceHeight: Int) : ImageGeometryChange(sourceWidth, sourceHeight) {
        override val targetWidth: Int get() = sourceHeight
        override val targetHeight: Int get() = sourceWidth
    }

    class Flip(sourceWidth: Int, sourceHeight: Int, val horizontal: Boolean) : ImageGeometryChange(sourceWidth, sourceHeight) {
        override val targetWidth: Int get() = sourceWidth
        override val targetHeight: Int get() = sourceHeight
    }

    class Scale(
        sourceWidth: Int,
        sourceHeight: Int,
        override val targetWidth: Int,
        override val targetHeight: Int,
    ) : ImageGeometryChange(sourceWidth, sourceHeight) {
        val scaleX: Float get() = targetWidth.toFloat() / sourceWidth
        val scaleY: Float get() = targetHeight.toFloat() / sourceHeight
        val strokeScale: Float get() = (scaleX + scaleY) / 2f
    }

    /** How a project stores this change. */
    val step: GeometryStep
        get() = when (this) {
            is RotateClockwise -> GeometryStep.RotateClockwise
            is Flip -> GeometryStep.Flip(horizontal)
            is Scale -> GeometryStep.Resize(targetWidth, targetHeight)
        }

    /** The pixel side of the change. Live edits and draft replay both go through here so they match. */
    fun apply(source: Bitmap): Bitmap = when (this) {
        is RotateClockwise ->
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
        is Flip -> Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) },
            true,
        )
        is Scale -> Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun mapPoint(x: Float, y: Float): PointF = when (this) {
        is RotateClockwise -> PointF(sourceHeight - y, x)
        is Flip -> if (horizontal) PointF(sourceWidth - x, y) else PointF(x, sourceHeight - y)
        is Scale -> PointF(x * scaleX, y * scaleY)
    }

    /** Maps a displacement, which has no origin, so only the linear part applies. */
    fun mapVector(dx: Float, dy: Float): PointF = when (this) {
        is RotateClockwise -> PointF(-dy, dx)
        is Flip -> if (horizontal) PointF(-dx, dy) else PointF(dx, -dy)
        is Scale -> PointF(dx * scaleX, dy * scaleY)
    }

    /**
     * Turns and mirrors are exact on whole pixels. A downscale rounds every edge outward so a
     * redaction never ends up smaller than what it covered. The result stays inside the new image
     * with at least one pixel on each side.
     */
    fun mapRect(rect: Rect): Rect {
        val mapped = when (this) {
            is RotateClockwise -> Rect(sourceHeight - rect.bottom, rect.left, sourceHeight - rect.top, rect.right)
            is Flip -> if (horizontal) {
                Rect(sourceWidth - rect.right, rect.top, sourceWidth - rect.left, rect.bottom)
            } else {
                Rect(rect.left, sourceHeight - rect.bottom, rect.right, sourceHeight - rect.top)
            }
            is Scale -> Rect(
                floor(rect.left * scaleX).toInt(),
                floor(rect.top * scaleY).toInt(),
                ceil(rect.right * scaleX).toInt(),
                ceil(rect.bottom * scaleY).toInt(),
            )
        }
        val left = mapped.left.coerceIn(0, (targetWidth - 1).coerceAtLeast(0))
        val top = mapped.top.coerceIn(0, (targetHeight - 1).coerceAtLeast(0))
        return Rect(
            left,
            top,
            mapped.right.coerceIn(left + 1, targetWidth.coerceAtLeast(left + 1)),
            mapped.bottom.coerceIn(top + 1, targetHeight.coerceAtLeast(top + 1)),
        )
    }

    fun mapRedaction(region: RedactionRegion): RedactionRegion = region.copy(bounds = mapRect(region.bounds))

    /**
     * Strokes, shapes, arrows and brush layers turn with the picture. Text, emoji, numbered
     * callouts and the magnifier stay upright so they can still be read; they move so their center
     * stays on the spot they were placed on. A downscale shrinks every layer, labels included.
     */
    fun mapDrawPath(layer: DrawPath): DrawPath {
        if (layer.points.isEmpty()) return layer
        if (this !is Scale && layer.staysUpright()) {
            val bounds = with(DrawLayerArrangement) { layer.visualBounds() }
            val center = mapPoint(bounds.centerX(), bounds.centerY())
            val dx = center.x - bounds.centerX()
            val dy = center.y - bounds.centerY()
            return layer.copy(
                points = layer.points.map { PointF(it.x + dx, it.y + dy) },
                controlPoint = layer.controlPoint?.let { PointF(it.x + dx, it.y + dy) },
            )
        }
        // The layer transform pivots on the points' bounding-box center, which these changes map
        // onto the new bounding-box center, so only the offset and the mirrored angle need fixing.
        val offset = mapVector(layer.transOffsetX, layer.transOffsetY)
        return layer.copy(
            points = layer.points.map { mapPoint(it.x, it.y) },
            controlPoint = layer.controlPoint?.let { mapPoint(it.x, it.y) },
            strokeWidth = if (this is Scale) layer.strokeWidth * strokeScale else layer.strokeWidth,
            transOffsetX = offset.x,
            transOffsetY = offset.y,
            transRotation = if (this is Flip) -layer.transRotation else layer.transRotation,
        )
    }

    /** Corners in top-left, top-right, bottom-right, bottom-left order, relabeled for the new orientation. */
    fun mapQuad(quad: List<PointF>): List<PointF> {
        require(quad.size == 4) { "A perspective quad has four corners" }
        val (tl, tr, br, bl) = quad.map { mapPoint(it.x, it.y) }
        return when (this) {
            is RotateClockwise -> listOf(bl, tl, tr, br)
            is Flip -> if (horizontal) listOf(tr, tl, bl, br) else listOf(bl, br, tr, tl)
            is Scale -> listOf(tl, tr, br, bl)
        }
    }

    fun mapCutBands(bands: List<CutBand>): List<CutBand> = bands.map { band ->
        when (this) {
            is RotateClockwise -> if (band.axis == CutAxis.HORIZONTAL) {
                CutBand(CutAxis.VERTICAL, sourceHeight - band.endExclusive, sourceHeight - band.start)
            } else {
                CutBand(CutAxis.HORIZONTAL, band.start, band.endExclusive)
            }
            is Flip -> when {
                horizontal && band.axis == CutAxis.VERTICAL ->
                    CutBand(CutAxis.VERTICAL, sourceWidth - band.endExclusive, sourceWidth - band.start)
                !horizontal && band.axis == CutAxis.HORIZONTAL ->
                    CutBand(CutAxis.HORIZONTAL, sourceHeight - band.endExclusive, sourceHeight - band.start)
                else -> band
            }
            is Scale -> {
                val scale = if (band.axis == CutAxis.HORIZONTAL) scaleY else scaleX
                val limit = if (band.axis == CutAxis.HORIZONTAL) targetHeight else targetWidth
                val start = floor(band.start * scale).toInt().coerceIn(0, (limit - 1).coerceAtLeast(0))
                CutBand(band.axis, start, ceil(band.endExclusive * scale).toInt().coerceIn(start + 1, limit.coerceAtLeast(start + 1)))
            }
        }
    }.sortedWith(compareBy<CutBand> { it.axis }.thenBy { it.start })

    /** Straightening is a rotation, so a mirror reverses its direction. */
    fun mapStraightenAngle(degrees: Float): Float = if (this is Flip) -degrees else degrees

    /** OCR boxes belong to the old pixels; the activity throws the scan away with them. */
    fun mapSnapshot(snapshot: EditorSnapshot): EditorSnapshot = snapshot.copy(
        crop = mapRect(snapshot.crop),
        rotAngle = mapStraightenAngle(snapshot.rotAngle),
        redactions = snapshot.redactions.map(::mapRedaction),
        draws = snapshot.draws.map(::mapDrawPath),
        ocrBlocks = emptyList(),
        ocrReviewed = false,
        perspectiveQuad = snapshot.perspectiveQuad?.takeIf { it.size == 4 }?.let(::mapQuad),
        cutout = snapshot.cutout.copy(bands = mapCutBands(snapshot.cutout.bands)),
    )

    companion object {
        /** The downscale the Resize dialog asks for, or null when the image already fits [maxDimension]. */
        fun resizeFor(sourceWidth: Int, sourceHeight: Int, maxDimension: Int): Scale? {
            if (sourceWidth <= maxDimension && sourceHeight <= maxDimension) return null
            val scale = maxDimension.toFloat() / maxOf(sourceWidth, sourceHeight)
            return Scale(
                sourceWidth,
                sourceHeight,
                (sourceWidth * scale).toInt().coerceAtLeast(1),
                (sourceHeight * scale).toInt().coerceAtLeast(1),
            )
        }
    }
}

/**
 * A whole-image change as a project or draft stores it. Projects keep the decoded source's size
 * and fingerprint and replay these in order, because the editor state is in the changed image's pixels.
 */
sealed interface GeometryStep {
    data object RotateClockwise : GeometryStep
    data class Flip(val horizontal: Boolean) : GeometryStep
    data class Resize(val width: Int, val height: Int) : GeometryStep
}

/** The change this step makes to a [width] x [height] image. */
internal fun GeometryStep.changeFor(width: Int, height: Int): ImageGeometryChange = when (this) {
    GeometryStep.RotateClockwise -> ImageGeometryChange.RotateClockwise(width, height)
    is GeometryStep.Flip -> ImageGeometryChange.Flip(width, height, horizontal)
    is GeometryStep.Resize -> ImageGeometryChange.Scale(width, height, this.width, this.height)
}

internal object GeometrySteps {
    /** Resizes only shrink and turns fold up, so a real edit session stays far below this. */
    const val MAX_STEPS = 32

    private val ORIENTATIONS = listOf(GeometryStep.RotateClockwise, GeometryStep.Flip(true), GeometryStep.Flip(false))

    /** Size after replaying [steps] on a [width] x [height] image, or null if a resize would enlarge it. */
    fun finalSize(width: Int, height: Int, steps: List<GeometryStep>): Pair<Int, Int>? {
        var w = width
        var h = height
        for (step in steps) {
            when (step) {
                GeometryStep.RotateClockwise -> { val t = w; w = h; h = t }
                is GeometryStep.Flip -> Unit
                is GeometryStep.Resize -> {
                    if (step.width !in 1..w || step.height !in 1..h) return null
                    w = step.width; h = step.height
                }
            }
        }
        return w to h
    }

    /**
     * Appends [step]. A run of turns and mirrors between resizes folds into the shortest run with
     * the same result (four turns vanish, two flips become a half turn), so the list stays short.
     */
    fun append(steps: List<GeometryStep>, step: GeometryStep): List<GeometryStep> {
        if (step is GeometryStep.Resize) return steps + step
        val runStart = steps.indexOfLast { it is GeometryStep.Resize } + 1
        val target = linear(steps.subList(runStart, steps.size) + step)
        return steps.subList(0, runStart) + shortestRunFor(target)
    }

    private fun shortestRunFor(target: List<Int>): List<GeometryStep> {
        var candidates = listOf(emptyList<GeometryStep>())
        repeat(4) {
            candidates.firstOrNull { linear(it) == target }?.let { return it }
            candidates = candidates.flatMap { run -> ORIENTATIONS.map { run + it } }
        }
        error("Every turn and mirror combination is reachable in three steps")
    }

    /** The 2x2 matrix (row-major) a run of turns and mirrors applies to a displacement. */
    private fun linear(run: List<GeometryStep>): List<Int> {
        var m = listOf(1, 0, 0, 1)
        for (step in run) {
            val s = when (step) {
                GeometryStep.RotateClockwise -> listOf(0, -1, 1, 0)
                is GeometryStep.Flip -> if (step.horizontal) listOf(-1, 0, 0, 1) else listOf(1, 0, 0, -1)
                is GeometryStep.Resize -> error("Resizes end a run")
            }
            m = listOf(
                s[0] * m[0] + s[1] * m[2], s[0] * m[1] + s[1] * m[3],
                s[2] * m[0] + s[3] * m[2], s[2] * m[1] + s[3] * m[3],
            )
        }
        return m
    }
}

internal fun DrawPath.staysUpright(): Boolean = shapeType in setOf("text", "emoji", "callout", "magnifier")

/** A locked crop ratio swaps orientation with the picture; a ratio with no portrait twin unlocks. */
internal fun AspectRatio.quarterTurned(): AspectRatio = when (this) {
    AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_3_4
    AspectRatio.RATIO_3_4 -> AspectRatio.RATIO_4_3
    AspectRatio.RATIO_4_5 -> AspectRatio.RATIO_5_4
    AspectRatio.RATIO_5_4 -> AspectRatio.RATIO_4_5
    AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_9_16
    AspectRatio.RATIO_9_16 -> AspectRatio.RATIO_16_9
    AspectRatio.RATIO_2_1, AspectRatio.RATIO_3_1, AspectRatio.RATIO_21_9 -> AspectRatio.FREE
    else -> this
}
