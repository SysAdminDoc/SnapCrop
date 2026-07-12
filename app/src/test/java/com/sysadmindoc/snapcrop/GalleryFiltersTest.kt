package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryFiltersTest {
    @Test
    fun filtersCombineAcrossEveryDimension() {
        val matching = photo(
            id = 1,
            uri = "content://media/external/images/1",
            dateAdded = NOW - 60,
            name = "capture.bin",
            width = 1440,
            height = 2560,
            isScreenshot = true,
            albumPath = "Pictures/Screenshots/",
            categories = setOf("Chats", "Sensitive"),
            mimeType = "image/png",
            ownerPackage = "Com.Example.Chat",
        )
        val wrongSource = matching.copy(id = 2, uri = Uri.parse("content://media/external/images/2"), ownerPackage = "com.example.browser")
        val wrongFormat = matching.copy(id = 3, uri = Uri.parse("content://media/external/images/3"), mimeType = "image/jpeg")
        val state = GalleryFilterState(
            mediaType = GalleryMediaType.SCREENSHOTS,
            sourceOrAlbum = "com.example.chat",
            categories = setOf("chats"),
            dateRange = GalleryDateRange.LAST_7_DAYS,
            orientation = GalleryOrientation.PORTRAIT,
            minWidth = 1080,
            minHeight = 1920,
            favoriteMode = GalleryFavoriteMode.FAVORITES,
            formats = setOf(GalleryFormat.PNG),
        )

        val result = applyGalleryFilters(
            listOf(wrongSource, matching, wrongFormat),
            state,
            favoriteUris = setOf(matching.uri.toString()),
            nowEpochSeconds = NOW,
        )

        assertEquals(listOf(matching), result)
        assertEquals(9, state.activeCount)
    }

    @Test
    fun mediaTypeAndOrientationTreatUnknownDimensionsConservatively() {
        val image = photo(1, "content://image/1", NOW, width = 300, height = 200)
        val video = photo(2, "content://video/2", NOW, isVideo = true, mimeType = "video/mp4")
        val screenshot = image.copy(id = 3, uri = Uri.parse("content://image/3"), isScreenshot = true)
        val unknown = image.copy(id = 4, uri = Uri.parse("content://image/4"), width = 0, height = 0)

        assertEquals(listOf(image, screenshot, unknown), apply(GalleryFilterState(mediaType = GalleryMediaType.IMAGES), listOf(image, video, screenshot, unknown)))
        assertEquals(listOf(video), apply(GalleryFilterState(mediaType = GalleryMediaType.VIDEOS), listOf(image, video, screenshot, unknown)))
        assertEquals(listOf(screenshot), apply(GalleryFilterState(mediaType = GalleryMediaType.SCREENSHOTS), listOf(image, video, screenshot, unknown)))
        assertEquals(listOf(image, screenshot), apply(GalleryFilterState(orientation = GalleryOrientation.LANDSCAPE), listOf(image, screenshot, unknown)))
        assertTrue(apply(GalleryFilterState(orientation = GalleryOrientation.PORTRAIT), listOf(unknown)).isEmpty())
    }

    @Test
    fun dateRangesUseExplicitNowAndInclusiveBoundaries() {
        val window = GalleryDateRange.LAST_7_DAYS.windowSeconds!!
        val atCutoff = photo(1, "content://image/1", NOW - window)
        val justOld = photo(2, "content://image/2", NOW - window - 1)
        val atNow = photo(3, "content://image/3", NOW)
        val future = photo(4, "content://image/4", NOW + 1)

        val result = apply(GalleryFilterState(dateRange = GalleryDateRange.LAST_7_DAYS), listOf(justOld, atCutoff, atNow, future))

        assertEquals(listOf(atCutoff, atNow), result)
    }

    @Test
    fun categoriesAndFormatsAreOrWithinDimension() {
        val pngChat = photo(1, "content://image/1", NOW, categories = setOf("chats"), mimeType = "image/png")
        val webpCode = photo(2, "content://image/2", NOW, categories = setOf("codes"), mimeType = "image/webp")
        val jpegDocument = photo(3, "content://image/3", NOW, categories = setOf("documents"), mimeType = "image/jpeg")
        val state = GalleryFilterState(
            categories = setOf("CHATS", "codes"),
            formats = setOf(GalleryFormat.PNG, GalleryFormat.WEBP),
        )

        assertEquals(listOf(pngChat, webpCode), apply(state, listOf(pngChat, webpCode, jpegDocument)))
    }

    @Test
    fun sourceKeyPrefersOwnerAndNormalizesAlbumFallback() {
        val owned = photo(
            1,
            "content://image/1",
            NOW,
            albumPath = "Pictures/Screenshots/",
            ownerPackage = " COM.Example.App ",
        )
        val albumOnly = photo(
            2,
            "content://image/2",
            NOW,
            albumPath = " Pictures\\Screenshots// ",
        )

        assertEquals("com.example.app", owned.sourceKey)
        assertEquals("pictures/screenshots", albumOnly.sourceKey)
        assertTrue(GalleryFilterState(sourceOrAlbum = "com.example.app").matches(owned, emptySet(), NOW))
        assertTrue(GalleryFilterState(sourceOrAlbum = "PICTURES/SCREENSHOTS/").matches(albumOnly, emptySet(), NOW))
        assertFalse(GalleryFilterState(sourceOrAlbum = "pictures").matches(albumOnly, emptySet(), NOW))
    }

    @Test
    fun favoriteFilteringUsesStableUriIdentity() {
        val first = photo(42, "content://image/42", NOW)
        val reusedId = photo(42, "content://other-provider/42", NOW)
        val favorites = setOf(first.uri.toString())

        assertEquals(listOf(first), apply(GalleryFilterState(favoriteMode = GalleryFavoriteMode.FAVORITES), listOf(first, reusedId), favorites))
        assertEquals(listOf(reusedId), apply(GalleryFilterState(favoriteMode = GalleryFavoriteMode.NOT_FAVORITES), listOf(first, reusedId), favorites))
    }

    @Test
    fun galleryFormatUsesNormalizedMimeWithoutFilenameGuessing() {
        assertEquals(GalleryFormat.JPEG, photo(1, "content://image/1", NOW, name = "wrong.png", mimeType = " IMAGE/JPEG; charset=binary ").galleryFormat)
        assertEquals(GalleryFormat.HEIC, photo(2, "content://image/2", NOW, mimeType = "image/heif-sequence").galleryFormat)
        assertEquals(GalleryFormat.THREE_GPP, photo(3, "content://video/3", NOW, mimeType = "video/3gpp2").galleryFormat)
        assertEquals(GalleryFormat.OTHER, photo(4, "content://image/4", NOW, name = "looks.webp", mimeType = "application/octet-stream").galleryFormat)
    }

    @Test
    fun encodeIsCanonicalAndRoundTripsEveryField() {
        val first = GalleryFilterState(
            mediaType = GalleryMediaType.IMAGES,
            sourceOrAlbum = " Pictures\\Screenshots// ",
            categories = linkedSetOf("Sensitive", "chats"),
            dateRange = GalleryDateRange.LAST_30_DAYS,
            orientation = GalleryOrientation.SQUARE,
            minWidth = 800,
            minHeight = 600,
            favoriteMode = GalleryFavoriteMode.NOT_FAVORITES,
            formats = linkedSetOf(GalleryFormat.WEBP, GalleryFormat.PNG),
        )
        val equivalent = first.copy(
            sourceOrAlbum = "pictures/screenshots",
            categories = linkedSetOf("chats", "sensitive"),
            formats = linkedSetOf(GalleryFormat.PNG, GalleryFormat.WEBP),
        )

        assertEquals(first.encode(), equivalent.encode())
        assertEquals(equivalent, GalleryFilterState.decode(first.encode()))
    }

    @Test
    fun decodeFailsSafeAndBoundsRestoredNumbers() {
        assertEquals(GalleryFilterState(), GalleryFilterState.decode(null))
        assertEquals(GalleryFilterState(), GalleryFilterState.decode("not json"))
        assertEquals(GalleryFilterState(), GalleryFilterState.decode("{\"schema\":\"foreign\",\"version\":1}"))

        val encoded = GalleryFilterState(minWidth = -10, minHeight = Int.MAX_VALUE).encode()
        val restored = GalleryFilterState.decode(encoded)
        assertEquals(0, restored.minWidth)
        assertEquals(1_000_000, restored.minHeight)
    }

    @Test
    fun defaultStateIsInactiveAndPreservesInputOrder() {
        val photos = listOf(photo(2, "content://image/2", NOW), photo(1, "content://image/1", NOW))

        assertEquals(0, GalleryFilterState().activeCount)
        assertEquals(photos, apply(GalleryFilterState(), photos))
    }

    private fun apply(
        state: GalleryFilterState,
        photos: List<Photo>,
        favoriteUris: Set<String> = emptySet(),
    ): List<Photo> = applyGalleryFilters(photos, state, favoriteUris, NOW)

    private fun photo(
        id: Long,
        uri: String,
        dateAdded: Long,
        name: String = "image-$id",
        isVideo: Boolean = false,
        width: Int = 100,
        height: Int = 100,
        isScreenshot: Boolean = false,
        albumPath: String = "Pictures/",
        categories: Set<String> = emptySet(),
        mimeType: String = if (isVideo) "video/mp4" else "image/png",
        ownerPackage: String = "",
    ) = Photo(
        id = id,
        uri = Uri.parse(uri),
        dateAdded = dateAdded,
        name = name,
        isVideo = isVideo,
        width = width,
        height = height,
        isScreenshot = isScreenshot,
        albumPath = albumPath,
        indexCategories = categories,
        mimeType = mimeType,
        ownerPackage = ownerPackage,
    )

    companion object {
        private const val NOW = 2_000_000_000L
    }
}
