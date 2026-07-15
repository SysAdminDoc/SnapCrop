package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowStateRoutingTest {
    @Test
    fun workflowsPersistOnlyBoundedIdentitiesAndOptions() {
        val files = listOf(
            source("StitchActivity.kt"),
            source("CollageActivity.kt"),
            source("DeviceFrameActivity.kt"),
            source("VideoClipActivity.kt"),
            source("WebCaptureActivity.kt"),
            source("CompareActivity.kt"),
        )

        files.forEach { code ->
            assertTrue(code.contains("override fun onSaveInstanceState"))
            assertFalse(code.contains("putParcelable(\"bitmap"))
            assertFalse(code.contains("putParcelableArrayList(\"bitmap"))
        }
        assertTrue(files[0].contains("WorkflowStateRestoration.putUris"))
        assertTrue(files[1].contains("WorkflowStateRestoration.putUris"))
        assertTrue(files[2].contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(files[3].contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(files[4].contains("inputUrl.take(WebCapturePolicy.MAX_URL_CHARS)"))
        assertTrue(files[5].contains("putString(EXTRA_BEFORE_URI"))
        assertTrue(files[5].contains("putString(EXTRA_AFTER_URI"))
    }

    @Test
    fun galleryToolbarSystemBackAndViewerSwipesShareIdentityReducer() {
        val gallery = source("GalleryScreen.kt")

        assertTrue(gallery.contains("PredictiveBackHandler { progress ->"))
        assertTrue(gallery.contains("progress.collect { }"))
        assertTrue(gallery.contains("IconButton(onClick = ::handleGalleryBack)"))
        assertTrue(gallery.contains("deepestGalleryBackTarget"))
        assertTrue(gallery.contains("onCurrentPhotoChanged(photo)"))
        assertTrue(gallery.contains("rememberSaveable(\n        saver = listSaver"))
        assertTrue(gallery.contains("if (isLoading) return@LaunchedEffect"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
