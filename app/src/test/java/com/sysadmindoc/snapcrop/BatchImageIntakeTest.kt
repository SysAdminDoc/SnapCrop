package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchImageIntakeTest {
    @Test
    fun boundsInspectionUsesOneLimitedPassWithoutAllocatingPixels() {
        val bytes = png(320, 180)
        val source = ByteSource(bytes, declaredLength = bytes.size.toLong())

        assertEquals(BatchImageBoundsResult.Ready(320, 180), BatchImageIntake.inspectBounds(source))
        assertEquals(1, source.openCount)
    }

    @Test
    fun boundsInspectionRejectsDeclaredOversizeBeforeOpeningProvider() {
        val source = CountingSource(BatchImageIntake.MAX_ENCODED_BYTES + 1)

        assertEquals(
            BatchImageBoundsResult.Failure(BatchImageBoundsFailureKind.ENCODED_TOO_LARGE),
            BatchImageIntake.inspectBounds(source),
        )
        assertEquals(0, source.openCount)
    }

    @Test
    fun validImageBoundsDecodeBeforeAReadableWorkingBitmap() {
        val bytes = png(320, 180)
        val source = ByteSource(bytes, declaredLength = bytes.size.toLong())

        val result = BatchImageIntake.decode(source)

        assertTrue(result is BatchImageIntakeResult.Ready)
        result as BatchImageIntakeResult.Ready
        assertEquals(320, result.sourceWidth)
        assertEquals(180, result.sourceHeight)
        assertEquals(3, source.openCount)
        assertFalse(result.bitmap.isRecycled)
        result.bitmap.recycle()
    }

    @Test
    fun alreadySmallResizeStopsAfterBoundsWithoutAllocatingPixels() {
        val bytes = png(120, 80)
        val source = ByteSource(bytes, declaredLength = bytes.size.toLong())

        assertEquals(
            BatchImageIntakeResult.Skipped(BatchSkipReason.ALREADY_WITHIN_TARGET),
            BatchImageIntake.decode(source, targetMaxDimension = 720),
        )
        assertEquals(2, source.openCount)
    }

    @Test
    fun analysisDecodeReturnsPixelsForAnImageAlreadyWithinTarget() {
        val bytes = png(120, 80)
        val source = ByteSource(bytes, declaredLength = bytes.size.toLong())

        val result = BatchImageIntake.decode(
            source,
            targetMaxDimension = 720,
            skipAlreadyWithinTarget = false,
        )

        assertTrue(result is BatchImageIntakeResult.Ready)
        result as BatchImageIntakeResult.Ready
        assertEquals(120, result.bitmap.width)
        assertEquals(80, result.bitmap.height)
        result.bitmap.recycle()
    }

    @Test
    fun declaredAndStreamingByteLimitsRejectBeforeDecodeAllocation() {
        val declared = CountingSource(BatchImageIntake.MAX_ENCODED_BYTES + 1)
        assertTrue(BatchImageIntake.decode(declared) is BatchImageIntakeResult.Oversized)
        assertEquals(0, declared.openCount)

        val streaming = GeneratedSource(BatchImageIntake.MAX_ENCODED_BYTES + 1)
        assertTrue(BatchImageIntake.decode(streaming) is BatchImageIntakeResult.Oversized)
        assertEquals(1, streaming.openCount)
    }

    @Test
    fun hugeSyntheticDimensionsFailPlanningWithoutBitmapAllocation() {
        assertEquals(BatchDecodePlan.SourceTooLarge, BatchImageIntake.plan(100_000, 100_000))
        assertEquals(BatchDecodePlan.InvalidDimensions, BatchImageIntake.plan(Int.MAX_VALUE, 0))
        assertEquals(BatchDecodePlan.Decode(2), BatchImageIntake.plan(8_000, 6_000))
        assertEquals(BatchDecodePlan.Decode(8), BatchImageIntake.plan(8_000, 6_000, 720))
    }

    @Test
    fun shortAndThrowingStreamsAreTypedAndNeverReachPublication() {
        val short = ByteSource(byteArrayOf(1, 2, 3), declaredLength = 3)
        val shortResult = BatchImageIntake.decode(short)
        assertTrue(shortResult.toString(), shortResult is BatchImageIntakeResult.Unreadable)

        val throwing = object : BatchImageIntake.Source {
            override val declaredLength: Long = 12
            override fun openStream(): InputStream = object : InputStream() {
                override fun read(): Int = throw IOException("fixture")
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                    throw IOException("fixture")
            }
        }
        assertEquals(
            BatchImageIntakeResult.Failed(BatchStreamFailureKind.READ),
            BatchImageIntake.decode(throwing),
        )
    }

    @Test
    fun cancellationBeforeIntakeNeverOpensTheProvider() {
        val source = CountingSource(10)
        assertEquals(BatchImageIntakeResult.Cancelled, BatchImageIntake.decode(source) { true })
        assertEquals(0, source.openCount)
    }

    @Test
    fun providerThatNeverMakesReadProgressFailsInsteadOfHanging() {
        val stalled = object : BatchImageIntake.Source {
            override val declaredLength: Long = 1
            override fun openStream(): InputStream = object : InputStream() {
                override fun read(): Int = 0
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
            }
        }

        assertEquals(
            BatchImageIntakeResult.Failed(BatchStreamFailureKind.READ),
            BatchImageIntake.decode(stalled),
        )
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private class ByteSource(
        private val bytes: ByteArray,
        override val declaredLength: Long?,
    ) : BatchImageIntake.Source {
        var openCount = 0
        override fun openStream(): InputStream {
            openCount++
            return ByteArrayInputStream(bytes)
        }
    }

    private class CountingSource(override val declaredLength: Long?) : BatchImageIntake.Source {
        var openCount = 0
        override fun openStream(): InputStream {
            openCount++
            return ByteArrayInputStream(byteArrayOf())
        }
    }

    private class GeneratedSource(private val bytes: Long) : BatchImageIntake.Source {
        override val declaredLength: Long? = null
        var openCount = 0
        override fun openStream(): InputStream {
            openCount++
            return object : InputStream() {
                private var remaining = bytes
                override fun read(): Int {
                    if (remaining <= 0) return -1
                    remaining--
                    return 0
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (remaining <= 0) return -1
                    val count = minOf(length.toLong(), remaining).toInt()
                    remaining -= count
                    return count
                }
            }
        }
    }
}
