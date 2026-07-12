package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareMetadataRoutingTest {
    @Test
    fun allAppOwnedImageShareEntryPointsUseThePreflight() {
        val main = source("MainActivity.kt")
        val crop = source("CropActivity.kt")
        val service = source("ScreenshotService.kt")

        assertTrue(main.contains("ExifTransfer.inspect(contentResolver, it)"))
        assertTrue(main.contains("chooseShareOptions(summary"))
        assertTrue(crop.contains("chooseShareOptions("))
        assertTrue(main.contains("sourceUrl?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }"))
        assertTrue(crop.contains("putExtra(Intent.EXTRA_TEXT, it)"))
        assertTrue(service.contains("PendingIntent.getActivity(this, 11"))
        assertTrue(service.contains("putStringArrayListExtra(EXTRA_PRIVACY_SHARE_URIS"))
        assertFalse(main.contains("fallback to original"))
        assertFalse(service.contains("Intent.createChooser"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
