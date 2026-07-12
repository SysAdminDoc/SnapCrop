package com.sysadmindoc.snapcrop

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCapabilitiesTest {
    @Test
    fun android32LegacyMediaGrantControlsImagesAndVideosOnly() {
        val denied = MediaCapabilityResolver.evaluate(32, emptySet())
        val granted = MediaCapabilityResolver.evaluate(32, setOf(Manifest.permission.READ_EXTERNAL_STORAGE))

        assertFalse(denied.canMonitorScreenshots)
        assertFalse(denied.canQueryVideos)
        assertTrue(denied.notificationAccess)
        assertTrue(granted.canMonitorScreenshots)
        assertTrue(granted.canQueryVideos)
    }

    @Test
    fun android33CapabilitiesAreIndependent() {
        val state = MediaCapabilityResolver.evaluate(
            33,
            setOf(Manifest.permission.READ_MEDIA_IMAGES)
        )

        assertTrue(state.canMonitorScreenshots)
        assertFalse(state.canQueryVideos)
        assertFalse(state.notificationAccess)
    }

    @Test
    fun android34SelectedPhotosCannotPowerMonitoring() {
        val state = MediaCapabilityResolver.evaluate(
            34,
            setOf(
                MediaCapabilityResolver.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        assertEquals(MediaAccess.SELECTED, state.imageAccess)
        assertTrue(state.canQueryImages)
        assertFalse(state.canMonitorScreenshots)
        assertEquals(MediaAccess.SELECTED, state.videoAccess)
        assertTrue(state.canQueryVideos)
        assertTrue(state.notificationAccess)
    }

    @Test
    fun android34FullImagesAndSelectedVideosRemainIndependent() {
        val state = MediaCapabilityResolver.evaluate(
            34,
            setOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                MediaCapabilityResolver.READ_MEDIA_VISUAL_USER_SELECTED
            )
        )

        assertEquals(MediaAccess.FULL, state.imageAccess)
        assertEquals(MediaAccess.SELECTED, state.videoAccess)
        assertTrue(state.canMonitorScreenshots)
        assertTrue(state.canQueryVideos)
        assertFalse(state.notificationAccess)
    }

    @Test
    fun requestsNeverCoupleUnrelatedAndroid13Permissions() {
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), MediaCapabilityResolver.imageRequest(33).toList())
        assertEquals(listOf(Manifest.permission.READ_MEDIA_VIDEO), MediaCapabilityResolver.videoRequest(33).toList())
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), MediaCapabilityResolver.notificationRequest(33).toList())
    }

    @Test
    fun android34RequestsIncludeReselectionPermission() {
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, MediaCapabilityResolver.READ_MEDIA_VISUAL_USER_SELECTED),
            MediaCapabilityResolver.imageRequest(34).toList()
        )
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_VIDEO, MediaCapabilityResolver.READ_MEDIA_VISUAL_USER_SELECTED),
            MediaCapabilityResolver.videoRequest(34).toList()
        )
    }
}
