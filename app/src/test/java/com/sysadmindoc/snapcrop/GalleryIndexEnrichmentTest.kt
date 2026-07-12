package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryIndexEnrichmentTest {
    @Test
    fun reusedUriWithDifferentDateDoesNotInheritOldRecognizedMetadata() {
        val uri = Uri.parse("content://media/external/images/media/7")
        val replacement = Photo(id = 7, uri = uri, dateAdded = 200, name = "new.png")
        val stale = ScreenshotIndexEntry(
            mediaId = 7,
            uri = uri,
            name = "old.png",
            albumPath = "Pictures/Screenshots/",
            width = 1080,
            height = 2400,
            dateAdded = 100,
            size = 10,
            isVideo = false,
            isScreenshot = true,
            isFavorite = false,
            categories = setOf("sensitive"),
            searchText = "old private text"
        )

        assertEquals(replacement, replacement.withIndex(stale))
    }

    @Test
    fun exactUriAndDateReceivesCurrentIndexFacets() {
        val uri = Uri.parse("content://media/external/images/media/8")
        val photo = Photo(id = 8, uri = uri, dateAdded = 300, name = "capture.bin")
        val entry = ScreenshotIndexEntry(
            mediaId = 8,
            uri = uri,
            name = "capture.bin",
            albumPath = "Pictures/Screenshots/",
            width = 1440,
            height = 3120,
            dateAdded = 300,
            size = 20,
            isVideo = false,
            isScreenshot = true,
            isFavorite = false,
            categories = setOf("codes"),
            searchText = "qr token",
            mimeType = "image/png",
            ownerPackage = "com.example.capture"
        )

        val enriched = photo.withIndex(entry)

        assertTrue(enriched.isScreenshot)
        assertEquals(setOf("codes"), enriched.indexCategories)
        assertEquals("image/png", enriched.mimeType)
        assertEquals("com.example.capture", enriched.ownerPackage)
    }
}
