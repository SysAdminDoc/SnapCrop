package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePublicationRoutingTest {
    @Test
    fun everyImagePublisherUsesTransactionalWriter() {
        val publishers = listOf(
            "MainActivity.kt",
            "CropActivity.kt",
            "ScreenshotService.kt",
            "StepCaptureService.kt",
            "StitchActivity.kt",
            "CollageActivity.kt",
            "DeviceFrameActivity.kt",
            "LongScreenshotStore.kt",
            "VideoClipExporter.kt",
        )

        publishers.forEach { name ->
            val source = source(name)
            assertTrue("$name must use MediaStoreImageWriter", source.contains("MediaStoreImageWriter.write("))
            assertFalse(
                "$name must not insert image rows directly",
                source.contains("insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI"),
            )
        }
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
