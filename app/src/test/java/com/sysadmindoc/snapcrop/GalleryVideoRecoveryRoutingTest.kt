package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryVideoRecoveryRoutingTest {
    @Test
    fun gallerySeparatesQueryFailuresAndPreservesFilenameDateFallback() {
        val gallery = source("GalleryScreen.kt")
        val coordinator = source("GalleryLoadCoordinator.kt")
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(Regex("GalleryQueryUnavailableException").findAll(gallery).count() >= 7)
        assertTrue(coordinator.contains("GalleryFailureSource.IMAGE_QUERY"))
        assertTrue(coordinator.contains("GalleryFailureSource.VIDEO_QUERY"))
        assertTrue(coordinator.contains("GalleryFailureSource.INDEX_DATABASE"))
        assertTrue(strings.contains("filename and date browsing still work"))
        assertTrue(coordinator.contains("sortedByDescending(Photo::dateAdded)"))
    }

    @Test
    fun videoFailuresAreTypedRecoverableAndContentFree() {
        val activity = source("VideoClipActivity.kt")

        assertTrue(activity.contains("VideoClipLoader.metadata"))
        assertTrue(activity.contains("VideoClipLoader.frame"))
        assertTrue(activity.contains("onChooseAnother"))
        assertTrue(activity.contains("previewBitmap?.takeIf { !it.isRecycled }?.recycle()"))
        assertTrue(activity.contains("DiagnosticOperation.VIDEO"))
        assertTrue(activity.contains("DiagnosticCode.DECODE_FAILURE"))
    }

    private fun source(name: String): String = File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
