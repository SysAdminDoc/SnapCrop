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
        val preview = source("EditorPreview.kt")

        assertTrue(activity.contains("CropImageRenderer.render("))
        assertTrue(renderer.contains("fun render("))
        assertTrue(renderer.contains("private fun applyRedactions("))
        assertTrue(renderer.contains("private fun applyDraw("))
        assertTrue(renderer.contains("private fun applyAdjustments("))
        assertTrue(renderer.contains("private fun applyGradientBackground("))
        assertTrue(renderer.contains("val rendered = applyRedactions(drawn, redactions)"))
        assertTrue(preview.contains("fun renderEditorPreviewBitmap("))
        assertTrue(preview.contains("): Bitmap = CropImageRenderer.render("))
        assertFalse(preview.contains("Bitmap.createBitmap("))
        assertFalse(activity.contains("private fun createCroppedBitmap("))
        assertFalse(activity.contains("private fun applyAdjustments("))
    }

    @Test
    fun requestedSideEffectsUseFailClosedPublicationBoundaries() {
        val activity = source("CropActivity.kt")
        val cachePublisher = source("CropCacheArtifactPublisher.kt")
        val mediaStoreWriter = source("MediaStoreImageWriter.kt")

        assertTrue(activity.contains("CropCacheArtifactPublisher.publish("))
        assertTrue(activity.contains("MediaStoreImageWriter.write("))
        assertTrue(cachePublisher.contains("Stage.EMPTY_OUTPUT"))
        assertTrue(cachePublisher.contains("dispatcher(file)"))
        assertTrue(mediaStoreWriter.contains("Stage.INSERT"))
        assertTrue(mediaStoreWriter.contains("Stage.OPEN_STREAM"))
        assertTrue(mediaStoreWriter.contains("Stage.PUBLISH"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
