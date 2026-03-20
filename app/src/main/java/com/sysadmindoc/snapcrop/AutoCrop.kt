package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AutoCrop {

    private const val COLOR_TOLERANCE = 30
    private const val EDGE_SAMPLE_RATIO = 0.15f
    private const val MIN_UNIFORM_STRIP = 4
    private const val PADDING = 2

    /**
     * Detects uniform-color borders and returns a crop rect for the content area.
     * Handles white borders, black borders, solid color margins, status bars, etc.
     */
    fun detect(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height

        val top = findTopEdge(bitmap, w, h)
        val bottom = findBottomEdge(bitmap, w, h)
        val left = findLeftEdge(bitmap, w, h, top, bottom)
        val right = findRightEdge(bitmap, w, h, top, bottom)

        // Clamp and ensure minimum size
        val cropLeft = max(0, left - PADDING)
        val cropTop = max(0, top - PADDING)
        val cropRight = min(w, right + PADDING)
        val cropBottom = min(h, bottom + PADDING)

        // Ensure at least 10% of original dimensions
        val minW = (w * 0.1f).toInt()
        val minH = (h * 0.1f).toInt()

        return if (cropRight - cropLeft < minW || cropBottom - cropTop < minH) {
            Rect(0, 0, w, h) // Autocrop failed, return full image
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

            // Also check a horizontal sweep for this row
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
