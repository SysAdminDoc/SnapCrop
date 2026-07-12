package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CropImageRendererRoutingTest {
    @Test
    fun activityCoordinatesExportsAndRendererOwnsRasterOperations() {
        val activity = source("CropActivity.kt")
        val renderer = source("CropImageRenderer.kt")

        assertTrue(activity.contains("CropImageRenderer.render("))
        assertTrue(renderer.contains("fun render("))
        assertTrue(renderer.contains("private fun applyRedactions("))
        assertTrue(renderer.contains("private fun applyDraw("))
        assertTrue(renderer.contains("private fun applyAdjustments("))
        assertTrue(renderer.contains("private fun applyGradientBackground("))
        assertFalse(activity.contains("private fun createCroppedBitmap("))
        assertFalse(activity.contains("private fun applyAdjustments("))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
