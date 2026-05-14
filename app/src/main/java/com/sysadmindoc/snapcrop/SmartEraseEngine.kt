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

        val originalMask = mask.copyOf()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        fillMask(pixels, mask, width, height, expanded, radius)
        featherEdges(pixels, originalMask, width, height, expanded, radius)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
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

    private fun fillMask(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        bounds: Rect,
        radius: Int
    ) {
        var remaining = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                if (mask[y * width + x]) remaining++
            }
        }
        if (remaining == 0) return

        val capacity = (bounds.width() * bounds.height()).coerceAtLeast(1)
        val updateIndices = IntArray(capacity)
        val updateColors = IntArray(capacity)
        val sampleRadius = (radius * 2.4f).roundToInt().coerceIn(8, 96)
        val step = if (sampleRadius > 28) 3 else 2
        val maxPasses = (radius * 3 + 32).coerceAtMost(220)

        repeat(maxPasses) {
            if (remaining == 0) return
            var updateCount = 0
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    val idx = y * width + x
                    if (!mask[idx]) continue
                    val color = estimateColor(pixels, mask, width, height, x, y, sampleRadius, step)
                    if (color != null) {
                        updateIndices[updateCount] = idx
                        updateColors[updateCount] = color
                        updateCount++
                    }
                }
            }
            if (updateCount == 0) return@repeat
            for (i in 0 until updateCount) {
                val idx = updateIndices[i]
                pixels[idx] = updateColors[i]
                mask[idx] = false
                remaining--
            }
        }

        if (remaining > 0) {
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    val idx = y * width + x
                    if (mask[idx]) {
                        pixels[idx] = estimateColor(pixels, mask, width, height, x, y, sampleRadius * 2, step)
                            ?: nearestSafeColor(pixels, mask, width, height, x, y)
                        mask[idx] = false
                    }
                }
            }
        }
    }

    private fun estimateColor(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int,
        step: Int
    ): Int? {
        val centerIdx = y * width + x
        val center = pixels[centerIdx]
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
                if (sx !in 0 until width || sy !in 0 until height) continue
                val sampleIdx = sy * width + sx
                if (mask[sampleIdx]) continue
                val sample = pixels[sampleIdx]
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
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Int {
        for (radius in 1..96) {
            val left = (x - radius).coerceAtLeast(0)
            val top = (y - radius).coerceAtLeast(0)
            val right = (x + radius).coerceAtMost(width - 1)
            val bottom = (y + radius).coerceAtMost(height - 1)
            for (xx in left..right) {
                val topIdx = top * width + xx
                if (!mask[topIdx]) return pixels[topIdx]
                val bottomIdx = bottom * width + xx
                if (!mask[bottomIdx]) return pixels[bottomIdx]
            }
            for (yy in top..bottom) {
                val leftIdx = yy * width + left
                if (!mask[leftIdx]) return pixels[leftIdx]
                val rightIdx = yy * width + right
                if (!mask[rightIdx]) return pixels[rightIdx]
            }
        }
        return pixels[y * width + x]
    }

    private fun featherEdges(
        pixels: IntArray,
        originalMask: BooleanArray,
        width: Int,
        height: Int,
        bounds: Rect,
        radius: Int
    ) {
        val copy = pixels.copyOf()
        val featherRadius = (radius / 4).coerceIn(1, 6)
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                val idx = y * width + x
                if (!originalMask[idx] || !touchesSafePixel(originalMask, width, height, x, y)) continue
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -featherRadius..featherRadius) {
                    for (dx in -featherRadius..featherRadius) {
                        val sx = x + dx
                        val sy = y + dy
                        if (sx !in 0 until width || sy !in 0 until height) continue
                        val sample = copy[sy * width + sx]
                        a += (sample ushr 24) and 0xFF
                        r += (sample ushr 16) and 0xFF
                        g += (sample ushr 8) and 0xFF
                        b += sample and 0xFF
                        count++
                    }
                }
                if (count > 0) {
                    pixels[idx] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
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
