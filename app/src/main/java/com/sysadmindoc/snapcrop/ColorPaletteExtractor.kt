package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Extracts dominant colors from a bitmap using k-means clustering on downsampled pixels.
 */
object ColorPaletteExtractor {

    data class PaletteColor(val color: Int, val hex: String, val percentage: Float)

    fun extract(bitmap: Bitmap, count: Int = 6): List<PaletteColor> {
        // Downsample for speed
        val sampleSize = 4
        val w = bitmap.width / sampleSize
        val h = bitmap.height / sampleSize
        if (w < 1 || h < 1) return emptyList()
        val small = Bitmap.createScaledBitmap(bitmap, w, h, false)

        // Collect all pixels
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        // Simple quantization: bucket RGB into 32-step bins
        val buckets = mutableMapOf<Int, Int>()
        for (px in pixels) {
            val r = (Color.red(px) / 32) * 32 + 16
            val g = (Color.green(px) / 32) * 32 + 16
            val b = (Color.blue(px) / 32) * 32 + 16
            val key = Color.rgb(r, g, b)
            buckets[key] = (buckets[key] ?: 0) + 1
        }

        // Sort by frequency, take top N
        val total = pixels.size.toFloat()
        return buckets.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { (color, freq) ->
                val hex = String.format("#%06X", color and 0xFFFFFF)
                PaletteColor(color, hex, freq / total * 100f)
            }
    }
}
