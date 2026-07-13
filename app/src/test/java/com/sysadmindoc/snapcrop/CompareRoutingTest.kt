package com.sysadmindoc.snapcrop

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompareRoutingTest {
    private val first = Photo(1, Uri.parse("content://media/images/1"), 1, isVideo = false)
    private val second = Photo(2, Uri.parse("content://media/images/2"), 2, isVideo = false)
    private val video = Photo(3, Uri.parse("content://media/video/3"), 3, isVideo = true)

    @Test
    fun galleryRequiresTwoDistinctImagesAndPreservesSelectionOrder() {
        val photos = listOf(first, second, video)
        assertNull(compareSelection(photos, listOf(first.uri.toString())))
        assertNull(compareSelection(photos, listOf(first.uri.toString(), video.uri.toString())))
        assertNull(compareSelection(photos, listOf(first.uri.toString(), second.uri.toString(), video.uri.toString())))
        assertEquals(
            listOf(second.uri, first.uri),
            compareSelection(photos, listOf(second.uri.toString(), first.uri.toString())),
        )
    }

    @Test
    fun compareIsPrivateBoundedAndHasNoPublicationOrNetworkPath() {
        val activity = source("CompareActivity.kt")
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(activity.contains("BatchImageIntake.decodeForAnalysis"))
        listOf("MediaStoreImageWriter", "openOutputStream", "shareImages(", "NetworkExport", "WebDav").forEach {
            assertFalse("Compare must remain transient and local: $it", activity.contains(it))
        }
        val declaration = Regex(
            "<activity\\s+android:name=\\\"\\.CompareActivity\\\"[\\s\\S]*?android:exported=\\\"false\\\"[\\s\\S]*?/>",
        )
        assertTrue(declaration.containsMatchIn(manifest))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
