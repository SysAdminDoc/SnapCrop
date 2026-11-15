package com.sysadmindoc.snapcrop

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            IllegalStateException("Google Play services unavailable"),
            ConnectionResult.SUCCESS
        )

        assertEquals("Update Google Play services and retry background removal.", message)
    }

    @Test
    fun modelErrorsProduceDownloadGuidance() {
        val message = MlKitStatus.userMessage(
            context,
            MlKitFeature.TRANSLATION,
            IllegalStateException("model download failed"),
            ConnectionResult.SUCCESS
        )

        assertTrue(message.contains("model is not ready"))
        assertTrue(message.contains("retry"))
    }

    @Test
    fun workingPlayServicesHasNoIssue() {
        assertNull(MlKitStatus.playServicesMessage(context, "Background removal", ConnectionResult.SUCCESS))
    }

    @Test
    fun missingOrInvalidPlayServicesSaysThePhoneLacksItAndNeverSaysUpdate() {
        for (code in listOf(ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_INVALID)) {
            val message = requireNotNull(MlKitStatus.playServicesMessage(context, "Background removal", code))

            assertEquals(
                "Background removal needs Google Play services, which this phone doesn't have.",
                message
            )
            assertFalse(message, message.contains("update", ignoreCase = true))
        }
    }

    @Test
    fun disabledPlayServicesAsksToTurnItOnNotToUpdate() {
        val message = requireNotNull(
            MlKitStatus.playServicesMessage(context, "Chinese OCR", ConnectionResult.SERVICE_DISABLED)
        )

        assertTrue(message, message.startsWith("Chinese OCR needs Google Play services"))
        assertTrue(message, message.contains("Turn it on"))
        assertFalse(message, message.contains("update", ignoreCase = true))
    }

    @Test
    fun outdatedOrUpdatingPlayServicesKeepsTheUpdateAdvice() {
        for (code in listOf(ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, ConnectionResult.SERVICE_UPDATING)) {
            val message = requireNotNull(MlKitStatus.playServicesMessage(context, "Translation", code))

            assertTrue(message, message.contains("Update Play services"))
        }
    }

    @Test
    fun featuresThatNeedPlayServicesReportItsAbsenceWhateverTheError() {
        val message = MlKitStatus.userMessage(
            context,
            MlKitFeature.SUBJECT_SEGMENTATION,
            IllegalStateException("Waiting for the module to be downloaded"),
            ConnectionResult.SERVICE_MISSING
        )

        assertEquals("Background removal needs Google Play services, which this phone doesn't have.", message)
    }

    @Test
    fun translationWithoutPlayServicesKeepsItsOwnErrorAdvice() {
        val message = MlKitStatus.userMessage(
            context,
            MlKitFeature.TRANSLATION,
            IllegalStateException("model download failed"),
            ConnectionResult.SERVICE_MISSING
        )

        assertTrue(message, message.contains("model is not ready"))
        assertFalse(message, message.contains("Play services"))
    }

    @Test
    fun bundledFeaturesWithoutPlayServicesNeverAskForAnUpdate() {
        val code = ConnectionResult.SERVICE_MISSING
        val blank = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException(""), code)
        val other = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException("Internal error."), code)
        val service = MlKitStatus.userMessage(context, MlKitFeature.BARCODE_SCANNING, IllegalStateException("service failed"), code)

        assertEquals("OCR unavailable.", blank)
        assertEquals("OCR unavailable. Internal error.", other)
        // Barcode scanning is bundled, so Play services being absent isn't the reason it failed.
        assertEquals("Barcode scanning unavailable. service failed", service)
        for (message in listOf(blank, other, service)) {
            assertFalse(message, message.contains("update", ignoreCase = true))
            assertFalse(message, message.contains("needs Google Play services"))
        }
    }

    @Test
    fun disabledPlayServicesNeverAsksBundledFeaturesForAnUpdate() {
        val code = ConnectionResult.SERVICE_DISABLED
        val blank = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException(""), code)
        val other = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException("Internal error."), code)
        val needsIt = MlKitStatus.userMessage(context, MlKitFeature.SUBJECT_SEGMENTATION, IllegalStateException(""), code)

        assertEquals("OCR unavailable.", blank)
        assertEquals("OCR unavailable. Internal error.", other)
        assertTrue(needsIt, needsIt.contains("Turn it on"))
        for (message in listOf(blank, other, needsIt)) {
            assertFalse(message, message.contains("update", ignoreCase = true))
        }
    }

    @Test
    fun savedNonLatinScriptFallsBackToLatinWithoutPlayServices() {
        assertEquals(OcrScript.LATIN, OcrScript.usable(OcrScript.JAPANESE, playServicesMissing = true))
        assertEquals(OcrScript.JAPANESE, OcrScript.usable(OcrScript.JAPANESE, playServicesMissing = false))
        assertEquals(OcrScript.LATIN, OcrScript.usable(OcrScript.LATIN, playServicesMissing = true))
    }

    @Test
    fun bundledFeaturesWithPlayServicesKeepTheirRetryAdvice() {
        val code = ConnectionResult.SUCCESS
        val blank = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException(""), code)
        val other = MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, IllegalStateException("Internal error."), code)

        assertEquals("OCR unavailable. Retry after updating Google Play services.", blank)
        assertEquals(
            "OCR unavailable. Internal error. Retry after updating Google Play services or reconnecting to Wi-Fi.",
            other
        )
    }
}
