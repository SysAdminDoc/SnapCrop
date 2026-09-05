package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Gainmap
import android.graphics.Matrix
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraHdrUtilsTest {
    @Test
    fun identityEditReattachesGainmapAndMetadata() {
        val source = hdrBitmap(8, 4, 2, 1)
        source.gainmap!!.setRatioMax(4f, 4f, 4f)
        val target = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)

        preserveUltraHdrGainmap(source, target)

        assertTrue(target.hasUltraHdrGainmap())
        assertEquals(4f, target.gainmap!!.ratioMax[0], 0.001f)
    }

    @Test
    fun transformedEditScalesAndTranslatesGainmapGeometry() {
        val source = hdrBitmap(8, 4, 2, 1)
        val target = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        val transform = Matrix().apply { postTranslate(2f, 2f) }

        preserveUltraHdrGainmap(source, target, transform)

        assertTrue(target.hasUltraHdrGainmap())
        assertEquals(3, target.gainmap!!.gainmapContents.width)
        assertEquals(2, target.gainmap!!.gainmapContents.height)
    }

    @Test
    fun straightenReattachesGainmapSoTheRendererDoesNotCopyANull() {
        val source = hdrBitmap(16, 16, 4, 4)

        val straightened = createEditorSpaceStraightenedBitmap(source, 12f)

        assertTrue(straightened.hasUltraHdrGainmap())
    }

    @Test
    fun renderKeepsGainmapThroughAFreeRotation() {
        val source = hdrBitmap(16, 16, 4, 4)
        val adj = FloatArray(25).apply {
            this[1] = 1f
            this[2] = 1f
            this[8] = 12f
        }

        val output = CropImageRenderer.render(
            source,
            Rect(0, 0, 16, 16),
            emptyList(),
            emptyList(),
            adj,
        )

        assertTrue(output.hasUltraHdrGainmap())
    }

    @Test
    @Config(sdk = [33])
    fun preAndroid14ReportsNoGainmapSupport() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        assertFalse(bitmap.hasUltraHdrGainmap())
    }

    private fun hdrBitmap(width: Int, height: Int, gainWidth: Int, gainHeight: Int): Bitmap {
        val base = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
        }
        val contents = Bitmap.createBitmap(gainWidth, gainHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        base.gainmap = Gainmap(contents)
        return base
    }
}
