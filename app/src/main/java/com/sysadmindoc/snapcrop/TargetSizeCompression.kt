package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

internal sealed interface TargetDownscalePolicy {
    data object Never : TargetDownscalePolicy

    data class Allow(
        val minimumWidth: Int = 320,
        val minimumHeight: Int = 320,
        val scaleStep: Float = 0.85f,
    ) : TargetDownscalePolicy {
        init {
            require(minimumWidth > 0 && minimumHeight > 0)
            require(scaleStep > 0f && scaleStep < 1f)
        }
    }
}

internal fun interface TargetBitmapEncoder {
    fun encode(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        output: OutputStream,
    ): Boolean
}

internal sealed interface TargetCompressionResult {
    data class WithinBudget(
        val bytes: ByteArray,
        val quality: Int,
        val format: Bitmap.CompressFormat,
        val width: Int,
        val height: Int,
        val downscaled: Boolean,
    ) : TargetCompressionResult

    data class CannotMeetWithoutResize(
        val targetBytes: Long,
        val minimumEncodedBytes: Long,
        val width: Int,
        val height: Int,
        val minimumDimensionsReached: Boolean,
    ) : TargetCompressionResult

    data class EncoderFailure(val cause: Throwable? = null) : TargetCompressionResult
}

internal object TargetSizeCompression {
    private const val MIN_QUALITY = 10
    private const val MAX_QUALITY = 100

    fun compress(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        targetKb: Int,
        downscalePolicy: TargetDownscalePolicy = TargetDownscalePolicy.Never,
    ): TargetCompressionResult = compress(
        bitmap,
        format,
        targetKb,
        downscalePolicy,
        TargetBitmapEncoder { source, requestedFormat, quality, output ->
            source.compress(requestedFormat, quality, output)
        },
    )

    internal fun compress(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        targetKb: Int,
        downscalePolicy: TargetDownscalePolicy,
        encoder: TargetBitmapEncoder,
    ): TargetCompressionResult {
        val targetBytes = targetKb.coerceIn(50, 5000).toLong() * 1024L
        val allow = downscalePolicy as? TargetDownscalePolicy.Allow
        val minimumScale = allow?.let {
            max(
                it.minimumWidth.toFloat() / bitmap.width,
                it.minimumHeight.toFloat() / bitmap.height,
            ).coerceAtMost(1f)
        } ?: 1f
        var scale = 1f
        var current = bitmap

        try {
            while (true) {
                when (val attempt = compressAtCurrentSize(current, format, targetBytes, encoder)) {
                    is ScaleResult.Success -> return TargetCompressionResult.WithinBudget(
                        bytes = attempt.bytes,
                        quality = attempt.quality,
                        format = format,
                        width = current.width,
                        height = current.height,
                        downscaled = current !== bitmap,
                    )
                    is ScaleResult.Failure -> return TargetCompressionResult.EncoderFailure(attempt.cause)
                    is ScaleResult.TooLarge -> {
                        val nextScale = allow?.let { max(scale * it.scaleStep, minimumScale) }
                        if (nextScale == null || nextScale >= scale) {
                            return TargetCompressionResult.CannotMeetWithoutResize(
                                targetBytes = targetBytes,
                                minimumEncodedBytes = attempt.minimumEncodedBytes,
                                width = current.width,
                                height = current.height,
                                minimumDimensionsReached = allow != null && scale <= minimumScale,
                            )
                        }

                        val nextWidth = (bitmap.width * nextScale).roundToInt().coerceAtLeast(1)
                        val nextHeight = (bitmap.height * nextScale).roundToInt().coerceAtLeast(1)
                        if (nextWidth == current.width && nextHeight == current.height) {
                            return TargetCompressionResult.CannotMeetWithoutResize(
                                targetBytes = targetBytes,
                                minimumEncodedBytes = attempt.minimumEncodedBytes,
                                width = current.width,
                                height = current.height,
                                minimumDimensionsReached = true,
                            )
                        }

                        val scaled = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
                        scaled.setHasAlpha(bitmap.hasAlpha())
                        preserveUltraHdrGainmap(bitmap, scaled)
                        if (current !== bitmap) current.recycle()
                        current = scaled
                        scale = nextScale
                    }
                }
            }
        } finally {
            if (current !== bitmap && !current.isRecycled) current.recycle()
        }
    }

    private fun compressAtCurrentSize(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        targetBytes: Long,
        encoder: TargetBitmapEncoder,
    ): ScaleResult {
        if (!isQualityAdjustable(format)) {
            return when (val attempt = encode(bitmap, format, MAX_QUALITY, targetBytes, encoder)) {
                is EncodeResult.Success -> if (attempt.totalBytes <= targetBytes) {
                    ScaleResult.Success(attempt.bytes, MAX_QUALITY)
                } else {
                    ScaleResult.TooLarge(attempt.totalBytes)
                }
                is EncodeResult.Failure -> ScaleResult.Failure(attempt.cause)
            }
        }

        val minimum = when (val attempt = encode(bitmap, format, MIN_QUALITY, targetBytes, encoder)) {
            is EncodeResult.Success -> attempt
            is EncodeResult.Failure -> return ScaleResult.Failure(attempt.cause)
        }
        if (minimum.totalBytes > targetBytes) return ScaleResult.TooLarge(minimum.totalBytes)

        var best = minimum.bytes
        var bestQuality = MIN_QUALITY
        var low = MIN_QUALITY + 1
        var high = MAX_QUALITY
        while (low <= high) {
            val quality = (low + high) / 2
            when (val attempt = encode(bitmap, format, quality, targetBytes, encoder)) {
                is EncodeResult.Failure -> return ScaleResult.Failure(attempt.cause)
                is EncodeResult.Success -> if (attempt.totalBytes <= targetBytes) {
                    best = attempt.bytes
                    bestQuality = quality
                    low = quality + 1
                } else {
                    high = quality - 1
                }
            }
        }
        return ScaleResult.Success(best, bestQuality)
    }

    private fun encode(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        targetBytes: Long,
        encoder: TargetBitmapEncoder,
    ): EncodeResult {
        val output = CappedCountingOutputStream(targetBytes)
        return try {
            if (!encoder.encode(bitmap, format, quality, output)) EncodeResult.Failure()
            else EncodeResult.Success(output.toByteArray(), output.totalBytes)
        } catch (error: Throwable) {
            if (error is CancellationException || error is Error) throw error
            EncodeResult.Failure(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun isQualityAdjustable(format: Bitmap.CompressFormat): Boolean =
        format == Bitmap.CompressFormat.JPEG || format == Bitmap.CompressFormat.WEBP ||
            format.name == "WEBP_LOSSY"

    private sealed interface EncodeResult {
        data class Success(val bytes: ByteArray, val totalBytes: Long) : EncodeResult
        data class Failure(val cause: Throwable? = null) : EncodeResult
    }

    private sealed interface ScaleResult {
        data class Success(val bytes: ByteArray, val quality: Int) : ScaleResult
        data class TooLarge(val minimumEncodedBytes: Long) : ScaleResult
        data class Failure(val cause: Throwable? = null) : ScaleResult
    }

    private class CappedCountingOutputStream(private val byteLimit: Long) : OutputStream() {
        private val retained = ByteArrayOutputStream(minOf(byteLimit, 64 * 1024L).toInt())
        var totalBytes: Long = 0
            private set

        override fun write(value: Int) {
            if (totalBytes < byteLimit) retained.write(value)
            totalBytes++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
            val retainCount = minOf(length.toLong(), (byteLimit - totalBytes).coerceAtLeast(0)).toInt()
            if (retainCount > 0) retained.write(buffer, offset, retainCount)
            totalBytes += length
        }

        fun toByteArray(): ByteArray = retained.toByteArray()
    }
}

internal fun ExportSettings.targetDownscalePolicy(): TargetDownscalePolicy =
    if (targetSizeAllowResize) TargetDownscalePolicy.Allow() else TargetDownscalePolicy.Never
