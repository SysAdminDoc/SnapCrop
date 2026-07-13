package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlin.math.cos
import kotlin.math.sqrt

internal enum class SimilarityMetric(val estimatedPairScratchBytes: Int) {
    PRODUCTION_DHASH(2 * 9 * 8 * Int.SIZE_BYTES * 3),
    RAW_DHASH(2 * 9 * 8 * Int.SIZE_BYTES * 3),
    PHASH(36_000),
    SSIM(270_000),
}

internal data class DHashFingerprint(
    val width: Int,
    val height: Int,
    val hash: Long,
    val averageLuma: Int,
)

internal object ScreenshotSimilarityMetrics {
    private const val PHASH_SIZE = 32
    private const val PHASH_LOW_FREQUENCY = 8
    private const val SSIM_SIZE = 128
    private const val SSIM_WINDOW = 8
    private val cosine = Array(PHASH_LOW_FREQUENCY) { frequency ->
        DoubleArray(PHASH_SIZE) { position ->
            cos((2 * position + 1) * frequency * Math.PI / (2 * PHASH_SIZE))
        }
    }

    fun dHash(bitmap: Bitmap): DHashFingerprint {
        val (hash, luma) = DuplicateHashing.perceptual(bitmap)
        return DHashFingerprint(bitmap.width, bitmap.height, hash, luma)
    }

    fun dHashDistance(first: DHashFingerprint, second: DHashFingerprint): Int =
        java.lang.Long.bitCount(first.hash xor second.hash)

    fun productionMatch(
        first: DHashFingerprint,
        second: DHashFingerprint,
        sensitivity: DuplicateSensitivity,
    ): Boolean {
        fun candidate(id: Long, value: DHashFingerprint) = DuplicateCandidate(
            uri = Uri.parse("content://benchmark/$id"),
            dateAdded = id,
            displayName = "fixture-$id.png",
            width = value.width,
            height = value.height,
            sizeBytes = value.width.toLong() * value.height * Int.SIZE_BYTES,
            exactSha256 = null,
            differenceHash = value.hash,
            averageLuma = value.averageLuma,
        )
        return DuplicateGrouping.group(
            listOf(candidate(1, first), candidate(2, second)),
            sensitivity,
            emptySet(),
        ).isNotEmpty()
    }

    fun pHash(bitmap: Bitmap): Long {
        val scaled = scaled(bitmap, PHASH_SIZE)
        return try {
            val pixels = IntArray(PHASH_SIZE * PHASH_SIZE)
            scaled.getPixels(pixels, 0, PHASH_SIZE, 0, 0, PHASH_SIZE, PHASH_SIZE)
            val luma = DoubleArray(pixels.size) { compositeLuma(pixels[it]).toDouble() }
            val coefficients = DoubleArray(PHASH_LOW_FREQUENCY * PHASH_LOW_FREQUENCY)
            for (v in 0 until PHASH_LOW_FREQUENCY) {
                for (u in 0 until PHASH_LOW_FREQUENCY) {
                    var sum = 0.0
                    for (y in 0 until PHASH_SIZE) for (x in 0 until PHASH_SIZE) {
                        sum += luma[y * PHASH_SIZE + x] * cosine[u][x] * cosine[v][y]
                    }
                    val normalization = 0.25 *
                        (if (u == 0) 1.0 / sqrt(2.0) else 1.0) *
                        (if (v == 0) 1.0 / sqrt(2.0) else 1.0)
                    coefficients[v * PHASH_LOW_FREQUENCY + u] = sum * normalization
                }
            }
            val medianValues = coefficients.copyOfRange(1, coefficients.size).sorted()
            val median = medianValues[medianValues.size / 2]
            var hash = 0L
            for (index in 1 until coefficients.size) {
                if (coefficients[index] > median) hash = hash or (1L shl index)
            }
            hash
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    fun pHashDistance(first: Long, second: Long): Int = java.lang.Long.bitCount(first xor second)

    fun ssim(first: Bitmap, second: Bitmap): Double {
        val firstLuma = lumaGrid(first, SSIM_SIZE)
        val secondLuma = lumaGrid(second, SSIM_SIZE)
        val c1 = (0.01 * 255).let { it * it }
        val c2 = (0.03 * 255).let { it * it }
        var score = 0.0
        var windows = 0
        for (top in 0 until SSIM_SIZE step SSIM_WINDOW) {
            for (left in 0 until SSIM_SIZE step SSIM_WINDOW) {
                var sumX = 0.0
                var sumY = 0.0
                var sumXX = 0.0
                var sumYY = 0.0
                var sumXY = 0.0
                for (y in top until top + SSIM_WINDOW) for (x in left until left + SSIM_WINDOW) {
                    val index = y * SSIM_SIZE + x
                    val a = firstLuma[index].toDouble()
                    val b = secondLuma[index].toDouble()
                    sumX += a
                    sumY += b
                    sumXX += a * a
                    sumYY += b * b
                    sumXY += a * b
                }
                val count = (SSIM_WINDOW * SSIM_WINDOW).toDouble()
                val meanX = sumX / count
                val meanY = sumY / count
                val varianceX = (sumXX - count * meanX * meanX) / (count - 1)
                val varianceY = (sumYY - count * meanY * meanY) / (count - 1)
                val covariance = (sumXY - count * meanX * meanY) / (count - 1)
                score += ((2 * meanX * meanY + c1) * (2 * covariance + c2)) /
                    ((meanX * meanX + meanY * meanY + c1) * (varianceX + varianceY + c2))
                windows++
            }
        }
        return (score / windows).coerceIn(-1.0, 1.0)
    }

    private fun lumaGrid(bitmap: Bitmap, size: Int): IntArray {
        val scaled = scaled(bitmap, size)
        return try {
            IntArray(size * size).also { pixels ->
                scaled.getPixels(pixels, 0, size, 0, 0, size, size)
                pixels.indices.forEach { pixels[it] = compositeLuma(pixels[it]) }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun scaled(bitmap: Bitmap, size: Int): Bitmap =
        if (bitmap.width == size && bitmap.height == size) bitmap
        else Bitmap.createScaledBitmap(bitmap, size, size, true)

    private fun compositeLuma(color: Int): Int {
        val alpha = Color.alpha(color)
        val red = (Color.red(color) * alpha + 128 * (255 - alpha)) / 255
        val green = (Color.green(color) * alpha + 128 * (255 - alpha)) / 255
        val blue = (Color.blue(color) * alpha + 128 * (255 - alpha)) / 255
        return (299 * red + 587 * green + 114 * blue) / 1000
    }
}
