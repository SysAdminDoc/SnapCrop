package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

enum class RedactionStyle(val preferenceValue: String) {
    SOLID("solid"),
    PIXELATE("pixelate");

    companion object {
        fun fromPreference(value: String?): RedactionStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: SOLID
    }
}

object ImageRedactor {
    const val PREF_REDACTION_STYLE = "redaction_style"

    fun redact(bitmap: Bitmap, rects: List<Rect>, style: RedactionStyle = RedactionStyle.SOLID): Bitmap =
        when (style) {
            RedactionStyle.SOLID -> opaque(bitmap, rects)
            RedactionStyle.PIXELATE -> pixelate(bitmap, rects)
        }

    fun opaque(bitmap: Bitmap, rects: List<Rect>, color: Int = android.graphics.Color.BLACK): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        rects.forEach { rect ->
            val left = rect.left.coerceIn(0, result.width)
            val top = rect.top.coerceIn(0, result.height)
            val right = rect.right.coerceIn(0, result.width)
            val bottom = rect.bottom.coerceIn(0, result.height)
            if (left < right && top < bottom) {
                val row = IntArray(right - left) { color or (0xFF shl 24) }
                for (y in top until bottom) {
                    result.setPixels(row, 0, row.size, left, y, row.size, 1)
                }
            }
        }
        return result
    }

    fun pixelate(bitmap: Bitmap, rects: List<Rect>): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val blockSize = 12

        for (rect in rects) {
            val left = rect.left.coerceIn(0, result.width)
            val top = rect.top.coerceIn(0, result.height)
            val right = rect.right.coerceIn(0, result.width)
            val bottom = rect.bottom.coerceIn(0, result.height)
            if (right - left < 2 || bottom - top < 2) continue

            val w = right - left
            val h = bottom - top
            // Regions smaller than two blocks can't carry a meaningful mosaic — a 1-block grid can
            // still leak high-contrast glyphs. Solid-fill those so small codes/text never survive.
            if (w < blockSize * 2 || h < blockSize * 2) {
                solidFill(result, canvas, left, top, w, h)
                continue
            }

            var region: Bitmap? = null
            var tiny: Bitmap? = null
            var mosaic: Bitmap? = null
            try {
                region = Bitmap.createBitmap(result, left, top, w, h)
                // Average each block on downscale (filter=true) so a single sampled pixel can't
                // reproduce text; keep the upscale blocky (filter=false) for the mosaic look.
                tiny = Bitmap.createScaledBitmap(
                    region,
                    (w / blockSize).coerceAtLeast(1),
                    (h / blockSize).coerceAtLeast(1),
                    true
                )
                mosaic = Bitmap.createScaledBitmap(tiny, w, h, false)
                canvas.drawBitmap(mosaic, left.toFloat(), top.toFloat(), null)
            } finally {
                region?.recycle()
                tiny?.recycle()
                mosaic?.recycle()
            }
        }

        return result
    }

    /** Fills a region with the average of its pixels so small areas are fully obscured. */
    private fun solidFill(source: Bitmap, canvas: Canvas, left: Int, top: Int, w: Int, h: Int) {
        var region: Bitmap? = null
        var one: Bitmap? = null
        try {
            region = Bitmap.createBitmap(source, left, top, w, h)
            one = Bitmap.createScaledBitmap(region, 1, 1, true) // average colour of the region
            val paint = Paint().apply { color = one.getPixel(0, 0); style = Paint.Style.FILL }
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + w).toFloat(), (top + h).toFloat(), paint)
        } finally {
            region?.recycle()
            one?.recycle()
        }
    }
}
