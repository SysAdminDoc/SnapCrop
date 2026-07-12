package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageFormatResolverTest {
    @Test
    fun hdrKeepsPngOnAndroid16WhenPngSelected() {
        val r = resolveExportFormat(
            ExportImageFormat.PNG, quality = 80, forcePng = false, ultraHdr = true,
            pngGainmapSupported = true
        )
        assertEquals(Bitmap.CompressFormat.PNG, r.format)
        assertEquals("png", r.ext)
        assertEquals(100, r.quality)
    }

    @Test
    fun hdrFallsBackToJpegBeforeAndroid16EvenWhenPngSelected() {
        val r = resolveExportFormat(
            ExportImageFormat.PNG, quality = 70, forcePng = false, ultraHdr = true,
            pngGainmapSupported = false
        )
        assertEquals(Bitmap.CompressFormat.JPEG, r.format)
        assertEquals("jpg", r.ext)
        assertEquals(90, r.quality) // coerced to at least 90 to protect the gain map base
    }

    @Test
    fun hdrForcedPngShapeCropKeepsPngOnAndroid16ButJpegBefore() {
        val on16 = resolveExportFormat(
            ExportImageFormat.JPEG, quality = 90, forcePng = true, ultraHdr = true,
            pngGainmapSupported = true
        )
        assertEquals(Bitmap.CompressFormat.PNG, on16.format)
        val before16 = resolveExportFormat(
            ExportImageFormat.JPEG, quality = 90, forcePng = true, ultraHdr = true,
            pngGainmapSupported = false
        )
        assertEquals(Bitmap.CompressFormat.JPEG, before16.format)
    }

    @Test
    fun hdrWithNonPngPreferenceStaysJpegRegardlessOfApi() {
        // WEBP cannot carry a gain map; honoring a JPEG preference is already correct.
        val webp = resolveExportFormat(
            ExportImageFormat.WEBP, quality = 88, forcePng = false, ultraHdr = true,
            pngGainmapSupported = true
        )
        assertEquals(Bitmap.CompressFormat.JPEG, webp.format)
        val jpeg = resolveExportFormat(
            ExportImageFormat.JPEG, quality = 88, forcePng = false, ultraHdr = true,
            pngGainmapSupported = true
        )
        assertEquals(Bitmap.CompressFormat.JPEG, jpeg.format)
    }

    @Test
    fun nonHdrHonorsUserFormatAndForcedPng() {
        assertEquals(
            Bitmap.CompressFormat.JPEG,
            resolveExportFormat(ExportImageFormat.JPEG, 75, forcePng = false, ultraHdr = false).format
        )
        assertEquals(
            Bitmap.CompressFormat.PNG,
            resolveExportFormat(ExportImageFormat.JPEG, 75, forcePng = true, ultraHdr = false).format
        )
        // @Config(sdk = 34) ⇒ SDK_INT >= R ⇒ WEBP_LOSSY.
        val webpLossy = resolveExportFormat(ExportImageFormat.WEBP, 60, forcePng = false, ultraHdr = false)
        assertEquals(Bitmap.CompressFormat.WEBP_LOSSY, webpLossy.format)
        assertEquals(60, webpLossy.quality)
    }
}
