package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MlKitStatusTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun translationDownloadMessageNamesTargetAndWifi() {
        val message = MlKitStatus.translationDownloadMessage(context, "Spanish")

        assertTrue(message.contains("Spanish"))
        assertTrue(message.contains("Wi-Fi"))
    }

    @Test
    fun userMessageAddsPlayServicesRetryGuidance() {
        val message = MlKitStatus.userMessage(
            context,
            MlKitFeature.SUBJECT_SEGMENTATION,
            IllegalStateException("Google Play services unavailable")
        )

        assertEquals("Update Google Play services and retry background removal.", message)
    }

    @Test
    fun modelErrorsProduceDownloadGuidance() {
        val message = MlKitStatus.userMessage(
            context,
            MlKitFeature.TRANSLATION,
            IllegalStateException("model download failed")
        )

        assertTrue(message.contains("model is not ready"))
        assertTrue(message.contains("retry"))
    }
}
