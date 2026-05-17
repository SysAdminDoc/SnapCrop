package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitStatusTest {
    @Test
    fun translationDownloadMessageNamesTargetAndWifi() {
        val message = MlKitStatus.translationDownloadMessage("Spanish")

        assertTrue(message.contains("Spanish"))
        assertTrue(message.contains("Wi-Fi"))
    }

    @Test
    fun userMessageAddsPlayServicesRetryGuidance() {
        val message = MlKitStatus.userMessage(
            MlKitFeature.SUBJECT_SEGMENTATION,
            IllegalStateException("Google Play services unavailable")
        )

        assertEquals("Update Google Play services and retry background removal.", message)
    }

    @Test
    fun modelErrorsProduceDownloadGuidance() {
        val message = MlKitStatus.userMessage(
            MlKitFeature.TRANSLATION,
            IllegalStateException("model download failed")
        )

        assertTrue(message.contains("model is not ready"))
        assertTrue(message.contains("retry"))
    }
}
