package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Long edge of the copy the editor renders per-pixel adjustments on; the canvas scales it up. */
internal const val ADJUST_PREVIEW_MAX_EDGE = 1280

/** Wait after the last slider change before rendering, so a drag doesn't queue a render per frame. */
internal const val ADJUST_PREVIEW_DEBOUNCE_MS = 90L

/**
 * True when an adjustment only [CropImageRenderer.applyAdjustments] can show: the canvas draws
 * brightness, contrast, saturation, warmth, vignette and matrix filters itself, but sharpen,
 * highlights, shadows, tilt-shift, denoise, curves and the pixel filters (color pops, glitch)
 * need the real pipeline.
 */
internal fun needsPixelAdjustPreview(
    sharpen: Float,
    highlights: Float,
    shadows: Float,
    tiltShift: Float,
    denoise: Float,
    curveR: Float,
    curveG: Float,
    curveB: Float,
    filter: ImageFilter,
): Boolean = sharpen > 0.01f || highlights != 0f || shadows != 0f || tiltShift >= 0.01f ||
    denoise > 0.01f || curveR != 0f || curveG != 0f || curveB != 0f ||
    (filter != ImageFilter.NONE && getFilterMatrix(filter) == null)

/**
 * Private ARGB_8888 copy of [bitmap] for preview rendering, at most [maxEdge] on the long side.
 * Always a new bitmap: rotate recycles the editor bitmap on the main thread, so background work
 * must never hold the original.
 */
internal fun adjustPreviewSource(bitmap: Bitmap, maxEdge: Int = ADJUST_PREVIEW_MAX_EDGE): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    val copy = if (longEdge > maxEdge) {
        val factor = maxEdge.toFloat() / longEdge
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).roundToInt().coerceAtLeast(1),
            (bitmap.height * factor).roundToInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    if (copy.config == Bitmap.Config.ARGB_8888) return copy
    return copy.copy(Bitmap.Config.ARGB_8888, false).also { copy.recycle() }
}
