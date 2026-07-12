package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManualCollectionsTest {
    @Test
    fun collectionNamesNormalizeUnicodeWhitespaceAndCase() {
        val normalized = ManualCollectionNames.normalize("  Ｒesearch\t Set  ")

        assertEquals("Research Set", normalized.display)
        assertEquals("research set", normalized.key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun collectionNamesRejectBlankValues() {
        ManualCollectionNames.normalize(" \n\t ")
    }

    @Test
    fun collectionFilterRequiresUriAndOriginalDate() {
        val old = photo("content://media/external/images/media/42", 100)
        val reused = old.copy(dateAdded = 200)
        val other = photo("content://media/external/images/media/7", 100)
        val members = setOf(ManualCollectionMedia(old.uri, old.dateAdded))

        val filtered = filterCollectionPhotos(listOf(reused, other, old), members)

        assertEquals(listOf(old), filtered)
    }

    @Test
    fun collectionSelectionUsesUriIdentityAndReportsUnsupportedItems() {
        val image = photo("content://media/external/images/media/42", 100, isScreenshot = true)
        val video = photo("content://media/external/video/media/42", 100, isVideo = true)
        val ordinary = photo("content://media/external/images/media/7", 100)

        val result = collectionSelection(
            listOf(image, video, ordinary),
            setOf(image.uri.toString(), video.uri.toString(), ordinary.uri.toString())
        )

        assertEquals(listOf(ManualCollectionMedia(image.uri, image.dateAdded)), result.media)
        assertEquals(2, result.skipped)
        assertTrue(result.media.none { it.uri == video.uri })
    }

    private fun photo(
        uri: String,
        dateAdded: Long,
        isVideo: Boolean = false,
        isScreenshot: Boolean = false
    ) = Photo(
        id = uri.substringAfterLast('/').toLong(),
        uri = Uri.parse(uri),
        dateAdded = dateAdded,
        isVideo = isVideo,
        isScreenshot = isScreenshot
    )
}
