package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SmartEraseEngine {
    fun eraseInPlace(bitmap: Bitmap, stroke: DrawPath) {
        if (bitmap.isRecycled || stroke.points.size < 2) return
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return

        val radius = (stroke.strokeWidth * 1.8f).roundToInt().coerceIn(4, 72)
        val mask = BooleanArray(width * height)
        val bounds = rasterizeStroke(mask, width, height, stroke.points, radius) ?: return
        val expanded = Rect(
            (bounds.left - radius * 3).coerceAtLeast(0),
            (bounds.top - radius * 3).coerceAtLeast(0),
            (bounds.right + radius * 3).coerceAtMost(width),
            (bounds.bottom + radius * 3).coerceAtMost(height)
        )
        dilate(mask, width, height, expanded, iterations = (radius / 18).coerceIn(1, 3))

        val rw = expanded.width()
        val rh = expanded.height()
        if (rw < 1 || rh < 1) return

        val originalMask = BooleanArray(rw * rh)
        for (y in expanded.top until expanded.bottom) {
            for (x in expanded.left until expanded.right) {
                if (mask[y * width + x]) originalMask[(y - expanded.top) * rw + (x - expanded.left)] = true
            }
        }

        val pixels = IntArray(rw * rh)
        bitmap.getPixels(pixels, 0, rw, expanded.left, expanded.top, rw, rh)

        fillMask(pixels, mask, width, height, expanded, radius)
        featherEdges(pixels, originalMask, rw, rh, mask, width, height, expanded, radius)
        bitmap.setPixels(pixels, 0, rw, expanded.left, expanded.top, rw, rh)
    }

    private fun rasterizeStroke(
        mask: BooleanArray,
        width: Int,
        height: Int,
        points: List<PointF>,
        radius: Int
    ): Rect? {
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        fun include(x: Int, y: Int) {
            left = minOf(left, x - radius)
            top = minOf(top, y - radius)
            right = maxOf(right, x + radius + 1)
            bottom = maxOf(bottom, y + radius + 1)
        }

        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val steps = ceil(distance / (radius * 0.45f).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            for (step in 0..steps) {
                val t = step.toFloat() / steps
                val cx = (a.x + dx * t).roundToInt()
                val cy = (a.y + dy * t).roundToInt()
                markCircle(mask, width, height, cx, cy, radius)
                include(cx, cy)
            }
        }

        if (right <= left || bottom <= top) return null
        return Rect(
            left.coerceAtLeast(0),
            top.coerceAtLeast(0),
            right.coerceAtMost(width),
            bottom.coerceAtMost(height)
        )
    }

    private fun markCircle(mask: BooleanArray, width: Int, height: Int, cx: Int, cy: Int, radius: Int) {
        val r2 = radius * radius
        val left = (cx - radius).coerceAtLeast(0)
        val top = (cy - radius).coerceAtLeast(0)
        val right = (cx + radius).coerceAtMost(width - 1)
        val bottom = (cy + radius).coerceAtMost(height - 1)
        for (y in top..bottom) {
            val dy = y - cy
            for (x in left..right) {
                val dx = x - cx
                if (dx * dx + dy * dy <= r2) mask[y * width + x] = true
            }
        }
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, bounds: Rect, iterations: Int) {
        repeat(iterations) {
            val next = mask.copyOf()
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    val idx = y * width + x
                    if (mask[idx]) continue
                    var nearMask = false
                    for (ny in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(height - 1)) {
                        for (nx in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1)) {
                            if (mask[ny * width + nx]) {
                                nearMask = true
                                break
                            }
                        }
                        if (nearMask) break
                    }
                    if (nearMask) next[idx] = true
                }
            }
            for (y in bounds.top until bounds.bottom) {
                val start = y * width + bounds.left
                val end = y * width + bounds.right
                for (idx in start until end) mask[idx] = next[idx]
            }
        }
    }

    /** Pixel array is region-scoped to [bounds]; mask is full-image. */
    private fun fillMask(
        pixels: IntArray,
        mask: BooleanArray,
        imgW: Int,
        imgH: Int,
        bounds: Rect,
        radius: Int
    ) {
        val rw = bounds.width()
        var remaining = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                if (mask[y * imgW + x]) remaining++
            }
        }
        if (remaining == 0) return

        val capacity = (rw * bounds.height()).coerceAtLeast(1)
        val updateIndices = IntArray(capacity)
        val updateMaskIndices = IntArray(capacity)
        val updateColors = IntArray(capacity)
        val sampleRadius = (radius * 2.4f).roundToInt().coerceIn(8, 96)
        val step = if (sampleRadius > 28) 3 else 2
        val maxPasses = (radius * 3 + 32).coerceAtMost(220)

        repeat(maxPasses) {
            if (remaining == 0) return
            var updateCount = 0
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    if (!mask[y * imgW + x]) continue
                    val color = estimateColor(pixels, mask, imgW, imgH, bounds, x, y, sampleRadius, step)
                    if (color != null) {
                        val ri = (y - bounds.top) * rw + (x - bounds.left)
                        updateIndices[updateCount] = ri
                        updateMaskIndices[updateCount] = y * imgW + x
                        updateColors[updateCount] = color
                        updateCount++
                    }
                }
            }
            if (updateCount == 0) return@repeat
            for (i in 0 until updateCount) {
                pixels[updateIndices[i]] = updateColors[i]
                mask[updateMaskIndices[i]] = false
                remaining--
            }
        }

        if (remaining > 0) {
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    val mi = y * imgW + x
                    if (mask[mi]) {
                        val ri = (y - bounds.top) * rw + (x - bounds.left)
                        pixels[ri] = estimateColor(pixels, mask, imgW, imgH, bounds, x, y, sampleRadius * 2, step)
                            ?: nearestSafeColor(pixels, mask, imgW, imgH, bounds, x, y)
                        mask[mi] = false
                    }
                }
            }
        }
    }

    private fun estimateColor(
        pixels: IntArray,
        mask: BooleanArray,
        imgW: Int,
        imgH: Int,
        bounds: Rect,
        x: Int,
        y: Int,
        radius: Int,
        step: Int
    ): Int? {
        val rw = bounds.width()
        val center = pixels[(y - bounds.top) * rw + (x - bounds.left)]
        var total = 0f
        var a = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        val r2 = radius * radius
        var samples = 0

        for (dy in -radius..radius step step) {
            for (dx in -radius..radius step step) {
                val d2 = dx * dx + dy * dy
                if (d2 == 0 || d2 > r2) continue
                val sx = x + dx
                val sy = y + dy
                if (sx !in 0 until imgW || sy !in 0 until imgH) continue
                if (mask[sy * imgW + sx]) continue
                if (sx !in bounds.left until bounds.right || sy !in bounds.top until bounds.bottom) continue
                val sample = pixels[(sy - bounds.top) * rw + (sx - bounds.left)]
                val distanceWeight = 1f / (sqrt(d2.toDouble()).toFloat() + 1f)
                val colorWeight = 1f / (1f + colorDistance(center, sample) / 96f)
                val weight = distanceWeight * (0.65f + colorWeight * 0.35f)
                a += ((sample ushr 24) and 0xFF) * weight
                r += ((sample ushr 16) and 0xFF) * weight
                g += ((sample ushr 8) and 0xFF) * weight
                b += (sample and 0xFF) * weight
                total += weight
                samples++
            }
        }

        if (samples < 4 || total <= 0f) return null
        return ((a / total).roundToInt().coerceIn(0, 255) shl 24) or
            ((r / total).roundToInt().coerceIn(0, 255) shl 16) or
            ((g / total).roundToInt().coerceIn(0, 255) shl 8) or
            (b / total).roundToInt().coerceIn(0, 255)
    }

    private fun nearestSafeColor(
        pixels: IntArray,
        mask: BooleanArray,
        imgW: Int,
        imgH: Int,
        bounds: Rect,
        x: Int,
        y: Int
    ): Int {
        val rw = bounds.width()
        for (radius in 1..96) {
            val left = (x - radius).coerceAtLeast(bounds.left)
            val top = (y - radius).coerceAtLeast(bounds.top)
            val right = (x + radius).coerceAtMost(bounds.right - 1)
            val bottom = (y + radius).coerceAtMost(bounds.bottom - 1)
            for (xx in left..right) {
                if (!mask[top * imgW + xx]) return pixels[(top - bounds.top) * rw + (xx - bounds.left)]
                if (!mask[bottom * imgW + xx]) return pixels[(bottom - bounds.top) * rw + (xx - bounds.left)]
            }
            for (yy in top..bottom) {
                if (!mask[yy * imgW + left]) return pixels[(yy - bounds.top) * rw + (left - bounds.left)]
                if (!mask[yy * imgW + right]) return pixels[(yy - bounds.top) * rw + (right - bounds.left)]
            }
        }
        return pixels[(y - bounds.top) * rw + (x - bounds.left)]
    }

    /** Pixel arrays are region-scoped; mask is full-image. [originalMask] is region-scoped. */
    private fun featherEdges(
        pixels: IntArray,
        originalMask: BooleanArray,
        rw: Int,
        rh: Int,
        fullMask: BooleanArray,
        imgW: Int,
        imgH: Int,
        bounds: Rect,
        radius: Int
    ) {
        val copy = pixels.copyOf()
        val featherRadius = (radius / 4).coerceIn(1, 6)
        for (ry in 0 until rh) {
            for (rx in 0 until rw) {
                val ri = ry * rw + rx
                if (!originalMask[ri]) continue
                val imgX = rx + bounds.left
                val imgY = ry + bounds.top
                if (!touchesSafePixel(fullMask, imgW, imgH, imgX, imgY)) continue
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -featherRadius..featherRadius) {
                    for (dx in -featherRadius..featherRadius) {
                        val sx = rx + dx
                        val sy = ry + dy
                        if (sx !in 0 until rw || sy !in 0 until rh) continue
                        val sample = copy[sy * rw + sx]
                        a += (sample ushr 24) and 0xFF
                        r += (sample ushr 16) and 0xFF
                        g += (sample ushr 8) and 0xFF
                        b += sample and 0xFF
                        count++
                    }
                }
                if (count > 0) {
                    pixels[ri] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
    }

    private fun touchesSafePixel(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Boolean {
        for (ny in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(height - 1)) {
            for (nx in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1)) {
                if (!mask[ny * width + nx]) return true
            }
        }
        return false
    }

    private fun colorDistance(a: Int, b: Int): Float {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
    }
}
