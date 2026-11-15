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

    fun detect(
        bitmap: Bitmap,
        statusBarPx: Int = 0,
        navBarPx: Int = 0,
        sourceHints: List<String> = emptyList(),
        profileTextHints: List<String> = emptyList(),
        userProfiles: List<UserAppCropProfile> = emptyList(),
        appProfilesEnabled: Boolean = true
    ): Rect {
        return detectWithMethod(
            bitmap = bitmap,
            statusBarPx = statusBarPx,
            navBarPx = navBarPx,
            sourceHints = sourceHints,
            profileTextHints = profileTextHints,
            userProfiles = userProfiles,
            appProfilesEnabled = appProfilesEnabled
        ).rect
    }

    fun detectWithMethod(
        bitmap: Bitmap,
        statusBarPx: Int = 0,
        navBarPx: Int = 0,
        sourceHints: List<String> = emptyList(),
        profileTextHints: List<String> = emptyList(),
        userProfiles: List<UserAppCropProfile> = emptyList(),
        appProfilesEnabled: Boolean = true
    ): CropResult {
        val baseResult = detectBase(bitmap, statusBarPx, navBarPx)
        return AppCropProfiles.apply(
            bitmap = bitmap,
            baseResult = baseResult,
            statusBarPx = statusBarPx,
            navBarPx = navBarPx,
            sourceHints = sourceHints,
            profileTextHints = profileTextHints,
            userProfiles = userProfiles,
            enabled = appProfilesEnabled
        )
    }

    fun explainProfileMatch(
        bitmap: Bitmap,
        statusBarPx: Int = 0,
        navBarPx: Int = 0,
        sourceHints: List<String> = emptyList(),
        profileTextHints: List<String> = emptyList(),
        userProfiles: List<UserAppCropProfile> = emptyList(),
        appProfilesEnabled: Boolean = true
    ): AppCropProfilePreview? {
        val baseResult = detectBase(bitmap, statusBarPx, navBarPx)
        return AppCropProfiles.evaluate(
            bitmap = bitmap,
            baseResult = baseResult,
            statusBarPx = statusBarPx,
            navBarPx = navBarPx,
            sourceHints = sourceHints,
            profileTextHints = profileTextHints,
            userProfiles = userProfiles,
            enabled = appProfilesEnabled
        )
    }

    private fun detectBase(bitmap: Bitmap, statusBarPx: Int = 0, navBarPx: Int = 0): CropResult {
        val w = bitmap.width
        val h = bitmap.height

        // Images too small for reliable border detection
        if (w < 100 || h < 100) return CropResult(Rect(0, 0, w, h), "too_small")

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
            return CropResult(borderRect, if (strippedBars) "border+bars" else "border")
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

        // The scans call a line border when 85% of its samples match, so the sparse first and last
        // lines of real content (the tops of letters, a thin rule) pass as border and get cut off.
        // Each edge walks back out past any line that still holds a non-border pixel.
        val rowLimit = max(8, (scanBottom - scanTop) / 25)
        val top = growEdge(findTopEdge(bitmap, w, scanTop, scanBottom, borderColor), -1, scanTop, rowLimit) {
            rowHasInk(bitmap, it, w, borderColor)
        }
        val bottom = growEdge(findBottomEdge(bitmap, w, scanTop, scanBottom, borderColor), 1, scanBottom, rowLimit) {
            rowHasInk(bitmap, it, w, borderColor)
        }
        val columnLimit = max(8, w / 25)
        val left = growEdge(findLeftEdge(bitmap, w, top, bottom, borderColor), -1, 0, columnLimit) {
            columnHasInk(bitmap, it, top, bottom, borderColor)
        }
        val right = growEdge(findRightEdge(bitmap, w, top, bottom, borderColor), 1, w, columnLimit) {
            columnHasInk(bitmap, it, top, bottom, borderColor)
        }

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

    private fun findLeftEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int, borderColor: Int = 0): Int {
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

            val isUniform = nonUniform < 2 && isColumnUniform(bitmap, x, top, bottom)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) {
                return max(0, x)
            }
        }
        return 0
    }

    private fun findRightEdge(bitmap: Bitmap, w: Int, top: Int, bottom: Int, borderColor: Int = 0): Int {
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

            val isUniform = nonUniform < 2 && isColumnUniform(bitmap, x, top, bottom)
            val matchesBorder = borderColor == 0 || colorsMatch(refColor, borderColor)
            if (!isUniform || !matchesBorder) {
                return min(w, x + 1)
            }
        }
        return w
    }

    /**
     * Moves [edge] outward by [direction] (-1 toward 0, +1 toward [bound]) to take in content lines
     * just outside it, stepping over clean gaps shorter than a quarter of [limit] (the dot over an
     * "i", an underline). It stops at the first clean run that long. A margin that never shows one
     * within [limit] lines is grain or noise rather than the edge of the content, so the edge stays.
     * Top and left edges are inclusive and bottom and right exclusive, so the next line out is
     * edge - 1 going toward 0 and edge itself going toward [bound].
     */
    private inline fun growEdge(edge: Int, direction: Int, bound: Int, limit: Int, hasInk: (Int) -> Boolean): Int {
        val gap = max(4, limit / 4)
        var at = edge
        var grown = edge
        var clean = 0
        repeat(limit) {
            val line = if (direction < 0) at - 1 else at
            if (if (direction < 0) line < bound else line >= bound) return grown
            at += direction
            if (hasInk(line)) {
                grown = at
                clean = 0
            } else if (++clean >= gap) {
                return grown
            }
        }
        return edge
    }

    private fun rowHasInk(bitmap: Bitmap, y: Int, w: Int, borderColor: Int): Boolean {
        val row = IntArray(w)
        bitmap.getPixels(row, 0, w, 0, y, w, 1)
        return row.any { !colorsMatch(it, borderColor) }
    }

    private fun columnHasInk(bitmap: Bitmap, x: Int, top: Int, bottom: Int, borderColor: Int): Boolean {
        if (bottom <= top) return false
        val column = IntArray(bottom - top)
        bitmap.getPixels(column, 0, 1, x, top, 1, bottom - top)
        return column.any { !colorsMatch(it, borderColor) }
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
