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

    fun detect(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): Rect {
        return detectWithMethod(bitmap, statusBarPx, navBarPx).rect
    }

    fun detectWithMethod(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): CropResult {
        val w = bitmap.width
        val h = bitmap.height

        // Step 1: ALWAYS strip system bars first.
        // This removes the status bar (with icons that confuse border detection)
        // and the nav bar, giving us the clean content area to analyze.
        var contentTop = 0
        var contentBottom = h

        if (statusBarPx > 0 && statusBarPx < h / 4) {
            contentTop = statusBarPx
        }
        if (navBarPx > 0 && navBarPx < h / 4) {
            contentBottom = h - navBarPx
        }

        val strippedBars = contentTop > 0 || contentBottom < h

        // Step 2: Run border detection WITHIN the content area (skipping system bars).
        // This way findTopEdge starts below the status bar, so it correctly
        // finds black borders between the status bar and the actual content.
        val borderRect = detectBorders(bitmap, w, contentTop, contentBottom)
        val hasBorders = borderRect.left > 0 || borderRect.top > contentTop ||
                borderRect.right < w || borderRect.bottom < contentBottom

        if (hasBorders) {
            return CropResult(borderRect, if (strippedBars) "border" else "border")
        }

        // Step 3: If no borders found but we stripped bars, return stripped result
        if (strippedBars) {
            return CropResult(Rect(0, contentTop, w, contentBottom), "statusbar")
        }

        // Step 4: No crop detected
        return CropResult(Rect(0, 0, w, h), "full")
    }

    /**
     * Detects uniform-color borders within a vertical range.
     * @param scanTop where to start scanning (below status bar)
     * @param scanBottom where to stop scanning (above nav bar)
     */
    private fun detectBorders(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int): Rect {
        if (scanTop >= scanBottom || w <= 0) return Rect(0, scanTop, w, scanBottom)

        // Sample corner colors to identify the actual border color.
        // For dark mode screenshots, both border and content may be dark — corners reliably represent borders.
        val corners = intArrayOf(
            bitmap.getPixel(0, scanTop.coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(w - 1, scanTop.coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(0, (scanBottom - 1).coerceIn(0, bitmap.height - 1)),
            bitmap.getPixel(w - 1, (scanBottom - 1).coerceIn(0, bitmap.height - 1))
        )
        // Use the most common corner color as reference border color
        val borderColor = corners.groupBy { it }.maxByOrNull { it.value.size }?.key ?: corners[0]

        val top = findTopEdge(bitmap, w, scanTop, scanBottom, borderColor)
        val bottom = findBottomEdge(bitmap, w, scanTop, scanBottom, borderColor)
        val left = findLeftEdge(bitmap, w, top, bottom)
        val right = findRightEdge(bitmap, w, top, bottom)

        val cropLeft = max(0, left - PADDING)
        val cropTop = max(scanTop, top - PADDING)
        val cropRight = min(w, right + PADDING)
        val cropBottom = min(scanBottom, bottom + PADDING)

        val minW = (w * 0.1f).toInt()
        val minH = ((scanBottom - scanTop) * 0.1f).toInt()

        return if (cropRight - cropLeft < minW || cropBottom - cropTop < minH) {
            Rect(0, scanTop, w, scanBottom)
        } else {
            Rect(cropLeft, cropTop, cropRight, cropBottom)
        }
    }

    /**
     * Scans downward from scanTop looking for the first non-uniform row.
     * Rows that are all one color (like black bars) are "uniform" — skip them.
     * The first row with actual content (non-uniform) is the top edge.
     */
    private fun findTopEdge(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int, borderColor: Int = 0): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }
        val limit = scanTop + ((scanBottom - scanTop) * 0.45f).toInt()

        for (y in scanTop until limit) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0

            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) {
                    nonUniform++
                }
            }

            // Row is non-uniform OR uniform but doesn't match border color = content found
            val isUniform = nonUniform < 2 && isRowUniform(bitmap, y, w)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) {
                return max(scanTop, y)
            }
        }
        return scanTop
    }

    /**
     * Scans upward from scanBottom looking for the first non-uniform row.
     */
    private fun findBottomEdge(bitmap: Bitmap, w: Int, scanTop: Int, scanBottom: Int, borderColor: Int = 0): Int {
        val sampleX = IntArray(5) { (w * (it + 1) / 6f).toInt().coerceIn(0, w - 1) }
        val limit = scanTop + ((scanBottom - scanTop) * 0.55f).toInt()

        for (y in scanBottom - 1 downTo limit) {
            val refColor = bitmap.getPixel(sampleX[0], y)
            var nonUniform = 0

            for (sx in sampleX) {
                if (!colorsMatch(bitmap.getPixel(sx, y), refColor)) {
                    nonUniform++
                }
            }

            val isUniform = nonUniform < 2 && isRowUniform(bitmap, y, w)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) {
                return min(scanBottom, y + 1)
            }
        }
        return scanBottom
    }

    private fun findLeftEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int): Int {
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

    private fun findRightEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int): Int {
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
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) matches++
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
            if (colorsMatch(bitmap.getPixel(x, y), refColor)) matches++
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
