package com.sysadmindoc.snapcrop

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CropSourceHintsTest {
    @Test
    fun webCaptureSourceUrlSurvivesCacheUriHandoff() {
        val source = "https://example.com/guide"
        val intent = Intent().putExtra(WebCaptureActivity.EXTRA_SOURCE_URL, source)

        val hints = CropSourceHints.fromIntent(
            intent,
            Uri.parse("content://com.sysadmindoc.snapcrop.fileprovider/web_capture/page.png")
        )

        assertTrue(source in hints)
    }
}
