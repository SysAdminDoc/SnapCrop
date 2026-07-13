package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.IOException
import java.io.OutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TargetSizeCompressionTest {
    @Test
    fun noisyJpegAndWebpNeverReportAnOversizedSuccess() {
        val source = noisyBitmap(768, 768)

        listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP_LOSSY).forEach { format ->
            when (val result = TargetSizeCompression.compress(source, format, 50)) {
                is TargetCompressionResult.WithinBudget -> {
                    assertTrue(result.bytes.size <= 50 * 1024)
                    assertTrue(result.quality in 10..100)
                    assertEquals(format, result.format)
                }
                is TargetCompressionResult.CannotMeetWithoutResize -> {
                    assertTrue(result.minimumEncodedBytes > result.targetBytes)
                }
                is TargetCompressionResult.EncoderFailure -> throw AssertionError(result.cause)
            }
        }

        source.recycle()
    }

    @Test
    fun pngKeepsAlphaAndUsesItsRequestedLosslessFormat() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(64, 20, 40, 60))
        }

        val result = TargetSizeCompression.compress(source, Bitmap.CompressFormat.PNG, 50)
        assertTrue(result is TargetCompressionResult.WithinBudget)
        result as TargetCompressionResult.WithinBudget
        assertEquals(Bitmap.CompressFormat.PNG, result.format)
        assertEquals(100, result.quality)
        val decoded = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        assertNotNull(decoded)
        assertTrue(decoded.hasAlpha())
        assertTrue(Color.alpha(decoded.getPixel(0, 0)) < 255)

        decoded.recycle()
        source.recycle()
    }

    @Test
    fun impossibleBudgetReturnsNoPayloadUntilResizeIsExplicitlyAllowed() {
        val source = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888).apply { setHasAlpha(true) }
        val observations = mutableListOf<Observation>()
        val encoder = TargetBitmapEncoder { bitmap, format, quality, output ->
            observations += Observation(bitmap.width, bitmap.height, bitmap.hasAlpha(), format, quality)
            output.write(ByteArray(if (bitmap.width > 640) 80 * 1024 else 40 * 1024))
            true
        }

        val withoutResize = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.WEBP_LOSSY,
            50,
            TargetDownscalePolicy.Never,
            encoder,
        )
        assertTrue(withoutResize is TargetCompressionResult.CannotMeetWithoutResize)
        assertFalse(source.isRecycled)

        observations.clear()
        val resized = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.WEBP_LOSSY,
            50,
            TargetDownscalePolicy.Allow(minimumWidth = 320, minimumHeight = 320, scaleStep = 0.5f),
            encoder,
        )
        assertTrue(resized is TargetCompressionResult.WithinBudget)
        resized as TargetCompressionResult.WithinBudget
        assertEquals(640, resized.width)
        assertEquals(320, resized.height)
        assertTrue(resized.downscaled)
        assertTrue(observations.all { it.hasAlpha })
        assertTrue(observations.all { it.format == Bitmap.CompressFormat.WEBP_LOSSY })
        assertEquals(2f, resized.width.toFloat() / resized.height, 0.01f)
        assertFalse(source.isRecycled)
        source.recycle()
    }

    @Test
    fun minimumDimensionsStillFailClosedWhenTheyCannotMeetBudget() {
        val source = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val encoder = TargetBitmapEncoder { _, _, _, output ->
            output.write(ByteArray(75 * 1024))
            true
        }

        val result = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.JPEG,
            50,
            TargetDownscalePolicy.Allow(minimumWidth = 320, minimumHeight = 320, scaleStep = 0.5f),
            encoder,
        )
        assertTrue(result is TargetCompressionResult.CannotMeetWithoutResize)
        result as TargetCompressionResult.CannotMeetWithoutResize
        assertTrue(result.minimumDimensionsReached)
        assertEquals(320, result.width)
        assertEquals(320, result.height)
        assertTrue(result.minimumEncodedBytes > result.targetBytes)
        source.recycle()
    }

    @Test
    fun falseAndThrowingEncodersReturnTypedFailure() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val returnedFalse = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.JPEG,
            50,
            TargetDownscalePolicy.Never,
            TargetBitmapEncoder { _, _, _, _ -> false },
        )
        val thrown = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.JPEG,
            50,
            TargetDownscalePolicy.Never,
            TargetBitmapEncoder { _, _, _, _ -> throw IOException("encoder unavailable") },
        )

        assertTrue(returnedFalse is TargetCompressionResult.EncoderFailure)
        assertTrue(thrown is TargetCompressionResult.EncoderFailure)
        assertEquals("encoder unavailable", (thrown as TargetCompressionResult.EncoderFailure).cause?.message)
        source.recycle()
    }

    @Test
    fun losslessFormatsEncodeOnceAtFullQualityAndOversizedOutputIsNotRetained() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val qualities = mutableListOf<Int>()
        val encoder = TargetBitmapEncoder { _, _, quality, output ->
            qualities += quality
            output.write(ByteArray(100 * 1024))
            true
        }

        val result = TargetSizeCompression.compress(
            source,
            Bitmap.CompressFormat.PNG,
            50,
            TargetDownscalePolicy.Never,
            encoder,
        )
        assertTrue(result is TargetCompressionResult.CannotMeetWithoutResize)
        assertEquals(listOf(100), qualities)
        assertEquals(100 * 1024L, (result as TargetCompressionResult.CannotMeetWithoutResize).minimumEncodedBytes)
        source.recycle()
    }

    private fun noisyBitmap(width: Int, height: Int): Bitmap {
        val random = Random(42)
        val pixels = IntArray(width * height) {
            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private data class Observation(
        val width: Int,
        val height: Int,
        val hasAlpha: Boolean,
        val format: Bitmap.CompressFormat,
        val quality: Int,
    )
}
