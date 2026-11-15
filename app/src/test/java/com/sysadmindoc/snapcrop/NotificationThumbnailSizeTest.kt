package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationThumbnailSizeTest {
    @Test
    fun aStitchedLongScreenshotDecodesInsideTheCap() {
        val sample = notificationThumbnailSampleSize(1440, 44_000)

        val width = 1440 / sample
        val height = 44_000 / sample
        assertTrue("width $width exceeds the cap", width <= NOTIFICATION_THUMBNAIL_MAX_PX)
        assertTrue("height $height exceeds the cap", height <= NOTIFICATION_THUMBNAIL_MAX_PX)
        // Still large enough to read as a preview rather than a smear.
        assertTrue("height $height is uselessly small", height >= 256)
    }

    @Test
    fun aSmallCaptureIsNotShrunkAtAll() {
        val sample = notificationThumbnailSampleSize(320, 640)

        assertEquals(1, sample)
    }

    @Test
    fun aTypicalPhoneScreenshotStaysWithinTheCap() {
        val sample = notificationThumbnailSampleSize(1440, 3120)

        val width = 1440 / sample
        val height = 3120 / sample
        assertTrue(width <= NOTIFICATION_THUMBNAIL_MAX_PX)
        assertTrue(height <= NOTIFICATION_THUMBNAIL_MAX_PX)
        assertTrue("height $height is uselessly small", height >= 512)
    }

    @Test
    fun sampleSizeIsAlwaysAPowerOfTwo() {
        listOf(1 to 1, 999 to 1, 4096 to 4096, 1440 to 44_000, 8000 to 100).forEach { (w, h) ->
            val sample = notificationThumbnailSampleSize(w, h)
            assertTrue("$w x $h gave $sample", sample > 0 && sample and (sample - 1) == 0)
        }
    }

    @Test
    fun unreadableBoundsFallBackToNoSubsampling() {
        assertEquals(1, notificationThumbnailSampleSize(-1, -1))
        assertEquals(1, notificationThumbnailSampleSize(0, 0))
    }
}
