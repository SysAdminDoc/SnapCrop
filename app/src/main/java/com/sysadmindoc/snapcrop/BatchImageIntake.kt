package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.IOException
import java.io.InputStream

internal enum class BatchStreamFailureKind {
    OPEN,
    READ,
    DECODE,
}

internal enum class BatchSkipReason {
    ALREADY_WITHIN_TARGET,
}

internal sealed interface BatchImageIntakeResult {
    data class Ready(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sampleSize: Int,
    ) : BatchImageIntakeResult

    data class Skipped(val reason: BatchSkipReason) : BatchImageIntakeResult
    data class Oversized(val reason: String) : BatchImageIntakeResult
    data class Unreadable(val reason: String) : BatchImageIntakeResult
    data class Failed(val kind: BatchStreamFailureKind) : BatchImageIntakeResult
    data object Cancelled : BatchImageIntakeResult
}

internal sealed interface BatchDecodePlan {
    data class Decode(val sampleSize: Int) : BatchDecodePlan
    data object SourceTooLarge : BatchDecodePlan
    data object WorkingBitmapTooLarge : BatchDecodePlan
    data object InvalidDimensions : BatchDecodePlan
}

/** Bounded, allocation-first intake shared by Home/Gallery batch bitmap workflows. */
internal object BatchImageIntake {
    const val MAX_ITEMS = 50
    const val MAX_ENCODED_BYTES = 64L * 1024L * 1024L
    const val MAX_SOURCE_PIXELS = 48_000_000L
    const val MAX_WORKING_PIXELS = 12_000_000L
    const val MAX_WORKING_BYTES = 48L * 1024L * 1024L

    internal interface Source {
        val declaredLength: Long?
        fun openStream(): InputStream?
    }

    fun decode(
        resolver: ContentResolver,
        uri: Uri,
        targetMaxDimension: Int? = null,
        cancelled: () -> Boolean = { false },
    ): BatchImageIntakeResult = decode(
        source = object : Source {
            override val declaredLength: Long? = InboundShareContract.declaredSize(resolver, uri)
            override fun openStream(): InputStream? = resolver.openInputStream(uri)
        },
        targetMaxDimension = targetMaxDimension,
        skipAlreadyWithinTarget = true,
        cancelled = cancelled,
    )

    /** Shared bounded decode for workflows that need pixels even when a source is already small. */
    fun decodeForAnalysis(
        resolver: ContentResolver,
        uri: Uri,
        targetMaxDimension: Int = 1_536,
        cancelled: () -> Boolean = { false },
    ): BatchImageIntakeResult = decode(
        source = object : Source {
            override val declaredLength: Long? = InboundShareContract.declaredSize(resolver, uri)
            override fun openStream(): InputStream? = resolver.openInputStream(uri)
        },
        targetMaxDimension = targetMaxDimension,
        skipAlreadyWithinTarget = false,
        cancelled = cancelled,
    )

    internal fun decode(
        source: Source,
        targetMaxDimension: Int? = null,
        skipAlreadyWithinTarget: Boolean = true,
        cancelled: () -> Boolean = { false },
    ): BatchImageIntakeResult {
        if (cancelled()) return BatchImageIntakeResult.Cancelled
        val declaredLength = source.declaredLength
        if (declaredLength != null && declaredLength > MAX_ENCODED_BYTES) {
            return BatchImageIntakeResult.Oversized("encoded source exceeds 64 MiB")
        }
        when (preflightStream(source, cancelled)) {
            StreamPreflight.WITHIN_LIMIT -> Unit
            StreamPreflight.TOO_LARGE ->
                return BatchImageIntakeResult.Oversized("encoded source exceeds 64 MiB")
            StreamPreflight.UNREADABLE ->
                return BatchImageIntakeResult.Unreadable("source stream could not be opened")
            StreamPreflight.FAILED ->
                return BatchImageIntakeResult.Failed(BatchStreamFailureKind.READ)
            StreamPreflight.CANCELLED -> return BatchImageIntakeResult.Cancelled
        }

        if (cancelled()) return BatchImageIntakeResult.Cancelled
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            val stream = source.openStream()
                ?: return BatchImageIntakeResult.Unreadable("source stream could not be opened")
            stream.use { BitmapFactory.decodeStream(LimitedInputStream(it), null, bounds) }
        } catch (_: IOException) {
            return BatchImageIntakeResult.Failed(BatchStreamFailureKind.READ)
        } catch (_: SecurityException) {
            return BatchImageIntakeResult.Failed(BatchStreamFailureKind.OPEN)
        } catch (_: RuntimeException) {
            return BatchImageIntakeResult.Unreadable("image bounds are corrupt")
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return BatchImageIntakeResult.Unreadable("image bounds could not be decoded")
        }
        if (skipAlreadyWithinTarget && targetMaxDimension != null &&
            bounds.outWidth <= targetMaxDimension && bounds.outHeight <= targetMaxDimension
        ) {
            return BatchImageIntakeResult.Skipped(BatchSkipReason.ALREADY_WITHIN_TARGET)
        }
        val plan = plan(bounds.outWidth, bounds.outHeight, targetMaxDimension)
        if (plan == BatchDecodePlan.SourceTooLarge) {
            return BatchImageIntakeResult.Oversized("source exceeds 48 megapixels")
        }
        if (plan == BatchDecodePlan.WorkingBitmapTooLarge) {
            return BatchImageIntakeResult.Oversized("working bitmap exceeds the memory budget")
        }
        if (plan == BatchDecodePlan.InvalidDimensions) {
            return BatchImageIntakeResult.Unreadable("image dimensions are invalid")
        }

        if (cancelled()) return BatchImageIntakeResult.Cancelled
        val sampleSize = (plan as BatchDecodePlan.Decode).sampleSize
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = try {
            val stream = source.openStream()
                ?: return BatchImageIntakeResult.Unreadable("source stream could not be reopened")
            stream.use { BitmapFactory.decodeStream(LimitedInputStream(it), null, options) }
        } catch (_: IOException) {
            return BatchImageIntakeResult.Failed(BatchStreamFailureKind.READ)
        } catch (_: SecurityException) {
            return BatchImageIntakeResult.Failed(BatchStreamFailureKind.OPEN)
        } catch (_: RuntimeException) {
            return BatchImageIntakeResult.Unreadable("image pixels are corrupt")
        } catch (_: OutOfMemoryError) {
            return BatchImageIntakeResult.Failed(BatchStreamFailureKind.DECODE)
        } ?: return BatchImageIntakeResult.Unreadable("image pixels could not be decoded")

        if (cancelled()) {
            bitmap.recycle()
            return BatchImageIntakeResult.Cancelled
        }
        val actualPixels = bitmap.width.toLong() * bitmap.height.toLong()
        if (actualPixels > MAX_WORKING_PIXELS || bitmap.allocationByteCount.toLong() > MAX_WORKING_BYTES) {
            bitmap.recycle()
            return BatchImageIntakeResult.Oversized("decoded bitmap exceeds the memory budget")
        }
        return BatchImageIntakeResult.Ready(
            bitmap = bitmap,
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            sampleSize = sampleSize,
        )
    }

    internal fun plan(width: Int, height: Int, targetMaxDimension: Int? = null): BatchDecodePlan {
        if (width <= 0 || height <= 0) return BatchDecodePlan.InvalidDimensions
        val sourcePixels = width.toLong() * height.toLong()
        if (sourcePixels > MAX_SOURCE_PIXELS) return BatchDecodePlan.SourceTooLarge

        var sample = 1
        while (true) {
            val decodedWidth = ceilDiv(width, sample)
            val decodedHeight = ceilDiv(height, sample)
            val decodedPixels = decodedWidth.toLong() * decodedHeight.toLong()
            val decodedBytes = decodedPixels * 4L
            val withinBudget = decodedPixels <= MAX_WORKING_PIXELS && decodedBytes <= MAX_WORKING_BYTES
            val canSampleForTarget = targetMaxDimension?.let { target ->
                ceilDiv(maxOf(width, height), sample * 2) >= target
            } ?: false
            if (withinBudget && !canSampleForTarget) return BatchDecodePlan.Decode(sample)
            if (sample >= 128 || sample > Int.MAX_VALUE / 2) return BatchDecodePlan.WorkingBitmapTooLarge
            sample *= 2
        }
    }

    private enum class StreamPreflight { WITHIN_LIMIT, TOO_LARGE, UNREADABLE, FAILED, CANCELLED }

    private fun preflightStream(source: Source, cancelled: () -> Boolean): StreamPreflight {
        val stream = try {
            source.openStream() ?: return StreamPreflight.UNREADABLE
        } catch (_: Exception) {
            return StreamPreflight.FAILED
        }
        return try {
            var result = StreamPreflight.WITHIN_LIMIT
            stream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var emptyReads = 0
                while (true) {
                    if (cancelled()) {
                        result = StreamPreflight.CANCELLED
                        break
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) {
                        emptyReads++
                        if (emptyReads > 16) {
                            result = StreamPreflight.FAILED
                            break
                        }
                        continue
                    }
                    emptyReads = 0
                    total += read
                    if (total > MAX_ENCODED_BYTES) {
                        result = StreamPreflight.TOO_LARGE
                        break
                    }
                }
            }
            result
        } catch (_: Exception) {
            StreamPreflight.FAILED
        }
    }

    private fun ceilDiv(value: Int, divisor: Int): Int =
        ((value.toLong() + divisor - 1L) / divisor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Prevents a provider that changes after preflight from feeding an unbounded decoder pass. */
    private class LimitedInputStream(input: InputStream) : java.io.FilterInputStream(input) {
        private var consumed = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) record(count.toLong())
            return count
        }

        override fun skip(byteCount: Long): Long {
            val count = super.skip(byteCount.coerceAtMost(MAX_ENCODED_BYTES - consumed + 1L))
            if (count > 0) record(count)
            return count
        }

        private fun record(count: Long) {
            consumed += count
            if (consumed > MAX_ENCODED_BYTES) throw IOException("encoded source exceeds 64 MiB")
        }
    }
}
