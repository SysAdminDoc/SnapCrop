package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRecoveryRoutingTest {
    @Test
    fun mainUsesOneTypedPendingActionAndExactSettingsReturnPath() {
        val main = source("MainActivity.kt")

        assertTrue(main.contains("private var pendingPermissionAction: PendingPermissionAction?"))
        assertTrue(main.contains("PermissionSettingsRouteFactory.forCapability(capability)"))
        assertTrue(main.contains("resumePendingPermissionAction(capability)"))
        assertTrue(main.contains("PendingPermissionAction.RunLastAction"))
        assertTrue(main.contains("PendingPermissionAction.DelayedCapture(seconds)"))
        assertFalse(main.contains("pendingMonitorStart"))
        assertFalse(main.contains("pendingDelayedCaptureSeconds"))
        assertFalse(main.contains("pendingWidgetOpenLatest"))
    }

    @Test
    fun galleryPinAndLanUploadResumeOnlyAfterTheirSettingsGrant() {
        val main = source("MainActivity.kt")
        val gallery = source("GalleryScreen.kt")

        assertTrue(gallery.contains("onRequestOverlayForPin(photo.uri)"))
        assertTrue(main.contains("PendingPermissionAction.Pin(uri)"))
        assertTrue(main.contains("openLocalNetworkPermissionSettings(): Boolean"))
        assertTrue(main.contains("if (openLocalNetworkPermissionSettings())"))
        assertTrue(main.contains("localNetworkAccess = AndroidLocalNetworkAccess.assess(this, settings)"))
    }

    private fun source(name: String): String = File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
