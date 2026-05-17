package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenshotIndexClassifierTest {
    @Test
    fun classifierAddsSourceCategoriesAndSearchTokens() {
        val entry = ScreenshotIndexClassifier.buildEntry(
            mediaId = 42,
            uri = Uri.parse("content://media/external/images/media/42"),
            name = "Signal_receipt_qr_payment.png",
            albumPath = "Pictures/Screenshots/Signal",
            width = 1080,
            height = 2400,
            dateAdded = 123,
            size = 456,
            isVideo = false,
            isScreenshot = true,
            isFavorite = true
        )

        assertTrue("screenshots" in entry.categories)
        assertTrue("chats" in entry.categories)
        assertTrue("documents" in entry.categories)
        assertTrue("codes" in entry.categories)
        assertTrue("payments" in entry.categories)
        assertTrue("sensitive" in entry.categories)
        assertTrue("favorites" in entry.categories)
        assertTrue(entry.searchText.contains("signal"))
        assertTrue(entry.searchText.contains("1080x2400"))
    }
}
