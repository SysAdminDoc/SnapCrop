package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

enum class RedactionStyle(val preferenceValue: String) {
    SOLID("solid"),
    PIXELATE("pixelate"),
    BLUR("blur");

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
            RedactionStyle.BLUR -> blur(bitmap, rects)
        }

    /** Renders editable regions once, with opaque bars last so cosmetic overlaps cannot weaken them. */
    fun render(bitmap: Bitmap, regions: List<RedactionRegion>): Bitmap {
        val enabled = regions.filter(RedactionRegion::enabled)
        if (enabled.isEmpty()) return bitmap
        enabled.forEach { region ->
            require(region.bounds.left >= 0 && region.bounds.top >= 0 &&
                region.bounds.right <= bitmap.width && region.bounds.bottom <= bitmap.height &&
                region.bounds.width() > 0 && region.bounds.height() > 0) {
                "Invalid enabled redaction region ${region.id}"
            }
        }
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        enabled.sortedBy { if (it.style == RedactionStyle.SOLID) 1 else 0 }.forEach { region ->
            when (region.style) {
                RedactionStyle.SOLID -> opaqueInPlace(result, region.bounds)
                RedactionStyle.PIXELATE -> pixelateInPlace(result, canvas, region.bounds)
                RedactionStyle.BLUR -> blurInPlace(result, canvas, region.bounds)
            }
        }
        preserveUltraHdrGainmap(bitmap, result)
        return result
    }

    fun opaque(bitmap: Bitmap, rects: List<Rect>, color: Int = android.graphics.Color.BLACK): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        rects.forEach { rect -> opaqueInPlace(result, rect, color) }
        preserveUltraHdrGainmap(bitmap, result)
        return result
    }

    fun pixelate(bitmap: Bitmap, rects: List<Rect>): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        rects.forEach { rect -> pixelateInPlace(result, canvas, rect) }
        preserveUltraHdrGainmap(bitmap, result)
        return result
    }

    fun blur(bitmap: Bitmap, rects: List<Rect>): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        rects.forEach { rect -> blurInPlace(result, canvas, rect) }
        preserveUltraHdrGainmap(bitmap, result)
        return result
    }

    private fun opaqueInPlace(
        bitmap: Bitmap,
        rect: Rect,
        color: Int = android.graphics.Color.BLACK
    ) {
        val safe = rect.clippedTo(bitmap) ?: return
        val row = IntArray(safe.width()) { color or (0xFF shl 24) }
        for (y in safe.top until safe.bottom) {
            bitmap.setPixels(row, 0, row.size, safe.left, y, row.size, 1)
        }
    }

    private fun pixelateInPlace(result: Bitmap, canvas: Canvas, rect: Rect) {
        val safe = rect.clippedTo(result) ?: return
        val blockSize = 12
        val w = safe.width()
        val h = safe.height()
        if (w < 2 || h < 2) return
        if (w < blockSize * 2 || h < blockSize * 2) {
            solidFill(result, canvas, safe.left, safe.top, w, h)
            return
        }
        scaleRegion(result, canvas, safe, blockSize, filterUpscale = false)
    }

    private fun blurInPlace(result: Bitmap, canvas: Canvas, rect: Rect) {
        val safe = rect.clippedTo(result) ?: return
        if (safe.width() < 2 || safe.height() < 2) return
        scaleRegion(result, canvas, safe, divisor = 24, filterUpscale = true)
    }

    private fun scaleRegion(
        result: Bitmap,
        canvas: Canvas,
        rect: Rect,
        divisor: Int,
        filterUpscale: Boolean
    ) {
        var region: Bitmap? = null
        var tiny: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            region = Bitmap.createBitmap(result, rect.left, rect.top, rect.width(), rect.height())
            tiny = Bitmap.createScaledBitmap(
                region,
                (rect.width() / divisor).coerceAtLeast(1),
                (rect.height() / divisor).coerceAtLeast(1),
                true
            )
            scaled = Bitmap.createScaledBitmap(tiny, rect.width(), rect.height(), filterUpscale)
            canvas.drawBitmap(scaled, rect.left.toFloat(), rect.top.toFloat(), null)
        } finally {
            region?.recycle()
            tiny?.recycle()
            scaled?.recycle()
        }
    }

    private fun Rect.clippedTo(bitmap: Bitmap): Rect? {
        val clipped = Rect(
            left.coerceIn(0, bitmap.width),
            top.coerceIn(0, bitmap.height),
            right.coerceIn(0, bitmap.width),
            bottom.coerceIn(0, bitmap.height)
        )
        return clipped.takeIf { it.width() > 0 && it.height() > 0 }
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
