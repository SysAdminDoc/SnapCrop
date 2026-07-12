package com.sysadmindoc.snapcrop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomRedactionRoutingTest {
    @Test
    fun everyProductionSensitiveScanLoadsCustomPatternsAndShareReviewsRegions() {
        val crop = source("CropActivity.kt")
        val editor = source("CropEditorScreen.kt")
        val service = source("ScreenshotService.kt")

        assertTrue(crop.contains("customPatterns = CustomRedactionPatternStore.load"))
        assertTrue(editor.contains("customPatterns = CustomRedactionPatternStore.load"))
        assertTrue(service.contains("CustomRedactionPatternStore.load(prefs)"))
        assertTrue(crop.contains("redact_share_candidate"))
        assertTrue(crop.contains("checks[index].isChecked"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
