package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AutoCrop {

    private const val COLOR_TOLERANCE = 30
    private const val PADDING = 2

    data class CropResult(
        val rect: Rect,
        val method: String // "border", "statusbar", "full"
    )

    /**
     * Multi-strategy autocrop using exact system bar heights from the device.
     * @param statusBarPx exact status bar height in pixels (from resources)
     * @param navBarPx exact navigation bar height in pixels (from resources)
     */
    fun detect(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): Rect {
        return detectWithMethod(bitmap, statusBarPx, navBarPx).rect
    }

    fun detectWithMethod(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): CropResult {
        val w = bitmap.width
        val h = bitmap.height

        // Strategy 1: Uniform border detection (meme borders, solid margins)
        val borderRect = detectBorders(bitmap, w, h)
        val hasBorders = borderRect.left > 0 || borderRect.top > 0 ||
                borderRect.right < w || borderRect.bottom < h

        if (hasBorders) {
            // Also strip system bars within the border crop
            val combined = stripSystemBars(borderRect, w, h, statusBarPx, navBarPx)
            return CropResult(combined, "border")
        }

        // Strategy 2: System bar stripping using exact device dimensions
        // On modern Android (12+), status bar is transparent — no pixel analysis works.
        // We use the known system bar pixel heights from the device to always strip.
        if (statusBarPx > 0 || navBarPx > 0) {
            val stripped = stripSystemBars(Rect(0, 0, w, h), w, h, statusBarPx, navBarPx)
            val hasStrip = stripped.top > 0 || stripped.bottom < h
            if (hasStrip) {
                return CropResult(stripped, "statusbar")
            }
        }

        // Strategy 3: No crop detected
        return CropResult(Rect(0, 0, w, h), "full")
    }

    /**
     * Strips system bars using exact pixel heights from the device.
     * Always strips — no pixel analysis needed since screenshots always
     * capture the full framebuffer including bar regions.
     */
    private fun stripSystemBars(
        rect: Rect, imgW: Int, imgH: Int, statusBarPx: Int, navBarPx: Int
    ): Rect {
        var top = rect.top
        var bottom = rect.bottom

        // Strip status bar if the rect starts at the very top
        if (top == 0 && statusBarPx > 0 && statusBarPx < imgH / 4) {
            top = statusBarPx
        }

        // Strip nav bar if the rect extends to the very bottom
        if (bottom == imgH && navBarPx > 0 && navBarPx < imgH / 4) {
            bottom = imgH - navBarPx
        }

        return Rect(rect.left, top, rect.right, bottom)
    }

    private fun detectBorders(bitmap: Bitmap, w: Int, h: Int): Rect {
        val top = findTopEdge(bitmap, w, h)
        val bottom = findBottomEdge(bitmap, w, h)
        val left = findLeftEdge(bitmap, w, h, top, bottom)
        val right = findRightEdge(bitmap, w, h, top, bottom)

        val cropLeft = max(0, left - PADDING)
        val cropTop = max(0, top - PADDING)
        val cropRight = min(w, right + PADDING)
        val cropBottom = min(h, bottom + PADDING)

        val minW = (w * 0.1f).toInt()
        val minH = (h * 0.1f).toInt()

        return if (cropRight - cropLeft < minW || cropBottom - cropTop < minH) {
            Rect(0, 0, w, h)
        } else {
            Rect(cropLeft, cropTop, cropRight, cropBottom)
        }
    }

    private fun findTopEdge(bitmap: Bitmap, w: Int, h: Int): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }

        for (y in 0 until (h * 0.45f).toInt()) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0

            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) {
                    nonUniform++
                }
            }

            if (nonUniform >= 2 || !isRowUniform(bitmap, y, w)) {
                return max(0, y)
            }
        }
        return 0
    }

    private fun findBottomEdge(bitmap: Bitmap, w: Int, h: Int): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }

        for (y in h - 1 downTo (h * 0.55f).toInt()) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0

            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) {
                    nonUniform++
                }
            }

            if (nonUniform >= 2 || !isRowUniform(bitmap, y, w)) {
                return min(h, y + 1)
            }
        }
        return h
    }

    private fun findLeftEdge(bitmap: Bitmap, w: Int, h: Int, top: Int, bottom: Int): Int {
        val sampleY = IntArray(5) {
            (top + (bottom - top) * (it + 1) / 6f).toInt().coerceIn(top, max(top, bottom - 1))
        }

        for (x in 0 until (w * 0.45f).toInt()) {
            var nonUniform = 0
            val refColor = bitmap.getPixel(x, sampleY[0])

            for (sy in sampleY) {
                if (!colorsMatch(bitmap.getPixel(x, sy), refColor)) {
                    nonUniform++
                }
            }

            if (nonUniform >= 2 || !isColumnUniform(bitmap, x, top, bottom)) {
                return max(0, x)
            }
        }
        return 0
    }

    private fun findRightEdge(bitmap: Bitmap, w: Int, h: Int, top: Int, bottom: Int): Int {
        val sampleY = IntArray(5) {
            (top + (bottom - top) * (it + 1) / 6f).toInt().coerceIn(top, max(top, bottom - 1))
        }

        for (x in w - 1 downTo (w * 0.55f).toInt()) {
            var nonUniform = 0
            val refColor = bitmap.getPixel(x, sampleY[0])

            for (sy in sampleY) {
                if (!colorsMatch(bitmap.getPixel(x, sy), refColor)) {
                    nonUniform++
                }
            }

            if (nonUniform >= 2 || !isColumnUniform(bitmap, x, top, bottom)) {
                return min(w, x + 1)
            }
        }
        return w
    }

    private fun isRowUniform(bitmap: Bitmap, y: Int, w: Int): Boolean {
        val step = max(1, w / 20)
        val refColor = bitmap.getPixel(0, y)
        var matches = 0
        var total = 0

        var x = 0
        while (x < w) {
            total++
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) {
                matches++
            }
            x += step
        }

        return matches.toFloat() / total > 0.85f
    }

    private fun isColumnUniform(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        val h = bottom - top
        if (h <= 0) return true
        val step = max(1, h / 20)
        val refColor = bitmap.getPixel(x, top)
        var matches = 0
        var total = 0

        var y = top
        while (y < bottom) {
            total++
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) {
                matches++
            }
            y += step
        }

        return matches.toFloat() / total > 0.85f
    }

    private fun colorsMatch(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        return abs(r1 - r2) <= COLOR_TOLERANCE &&
                abs(g1 - g2) <= COLOR_TOLERANCE &&
                abs(b1 - b2) <= COLOR_TOLERANCE
    }
}
