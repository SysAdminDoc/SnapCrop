package com.sysadmindoc.snapcrop

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryLoadCoordinatorTest {
    @Test
    fun overviewKeepsAvailableSourcesWhenIndexFails() = runBlocking {
        val source = FakeSource().apply {
            indexError = IllegalStateException("index unavailable")
            imageAlbums = listOf(album("Camera", "Pictures/Camera", 2))
            videoAlbums = listOf(album("Camera", "Pictures/Camera", 1))
            smartAlbums = listOf(album("Receipts", "$SMART_ALBUM_PREFIX:receipts", 4))
        }

        val result = GalleryLoadCoordinator.overview(
            source = source,
            indexEnabled = true,
            canReadImages = true,
            canReadVideos = true,
        )

        assertEquals(1, result.albums.size)
        assertEquals(3, result.albums.single().count)
        assertEquals(listOf(source.unfiledAlbum) + source.smartAlbums, result.smartAlbums)
        assertTrue(result.index.isEmpty())
        assertEquals(listOf(GalleryFailureSource.INDEX_DATABASE), result.failures.map { it.source })
        assertEquals(emptyMap<String, ScreenshotIndexEntry>(), source.smartIndex)
    }

    @Test
    fun overviewDoesNotTouchSourcesWithoutPermission() = runBlocking {
        val source = FakeSource()

        val result = GalleryLoadCoordinator.overview(
            source = source,
            indexEnabled = false,
            canReadImages = false,
            canReadVideos = false,
        )

        assertTrue(result.albums.isEmpty())
        assertTrue(result.smartAlbums.isEmpty())
        assertTrue(result.failures.isEmpty())
        assertEquals(0, source.indexCalls)
        assertEquals(0, source.imageAlbumCalls)
        assertEquals(0, source.videoAlbumCalls)
        assertEquals(0, source.smartAlbumCalls)
        assertEquals(0, source.unfiledAlbumCalls)
    }

    @Test
    fun manualCollectionFailureFallsBackToReadableMediaSources() = runBlocking {
        val image = photo(1, 20)
        val video = photo(2, 10, isVideo = true)
        val source = FakeSource().apply {
            collectionError = IllegalStateException("collection unavailable")
            imagePhotos = listOf(image)
            videoPhotos = listOf(video)
        }

        val result = GalleryLoadCoordinator.album(
            source = source,
            path = "${MANUAL_COLLECTION_PREFIX}42",
            canReadImages = true,
            canReadVideos = true,
        )

        assertEquals(listOf(image, video), result.photos)
        assertEquals(listOf(GalleryFailureSource.COLLECTION_DATABASE), result.failures.map { it.source })
        assertEquals(listOf(emptySet<ManualCollectionMedia>(), emptySet()), source.photoMemberInputs)
    }

    @Test
    fun smartAlbumSkipsVideosAndDeduplicatesNewestImages() = runBlocking {
        val older = photo(1, 10)
        val newer = photo(2, 30)
        val duplicateNewer = newer.copy(name = "duplicate")
        val source = FakeSource().apply {
            imagePhotos = listOf(older, duplicateNewer, newer)
            videoPhotos = listOf(photo(3, 40, isVideo = true))
        }

        val result = GalleryLoadCoordinator.album(
            source = source,
            path = "${SMART_ALBUM_PREFIX}receipts",
            canReadImages = true,
            canReadVideos = true,
        )

        assertEquals(listOf(duplicateNewer, older), result.photos)
        assertEquals(listOf(true to false), source.photoKinds)
    }

    @Test
    fun unfiledFailureIsTypedAndDoesNotHideOtherAlbums() = runBlocking {
        val source = FakeSource().apply {
            unfiledError = IllegalStateException("triage unavailable")
            smartAlbums = listOf(album("Receipts", "${SMART_ALBUM_PREFIX}receipts", 2))
        }

        val result = GalleryLoadCoordinator.overview(source, false, true, false)

        assertEquals(source.smartAlbums, result.smartAlbums)
        assertEquals(listOf(GalleryFailureSource.TRIAGE_DATABASE), result.failures.map { it.source })
    }

    @Test
    fun unfiledAlbumUsesItsExactImageOnlySnapshot() = runBlocking {
        val older = photo(1, 10)
        val newer = photo(2, 20)
        val source = FakeSource().apply { imagePhotos = listOf(older, newer) }

        val result = GalleryLoadCoordinator.album(source, UNFILED_PATH, true, true)

        assertEquals(listOf(newer, older), result.photos)
        assertTrue(source.photoKinds.isEmpty())
    }

    @Test
    fun cancellationAlwaysEscapesInsteadOfBecomingFailureState() = runBlocking {
        val cancellation = CancellationException("screen left")
        val overviewSource = FakeSource().apply { indexError = cancellation }
        try {
            GalleryLoadCoordinator.overview(overviewSource, true, true, true)
            fail("Expected overview cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        val albumSource = FakeSource().apply { photoError = cancellation }
        try {
            GalleryLoadCoordinator.album(albumSource, "Pictures/Camera", true, true)
            fail("Expected album cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    private fun album(name: String, path: String, count: Int) = Album(
        name = name,
        path = path,
        coverUri = Uri.parse("content://media/$name"),
        count = count,
    )

    private fun photo(id: Long, dateAdded: Long, isVideo: Boolean = false) = Photo(
        id = id,
        uri = Uri.parse("content://media/$id"),
        dateAdded = dateAdded,
        isVideo = isVideo,
    )

    private class FakeSource : GalleryLoadCoordinator.Source {
        var indexError: Throwable? = null
        var collectionError: Throwable? = null
        var photoError: Throwable? = null
        var unfiledError: Throwable? = null
        var imageAlbums = emptyList<Album>()
        var videoAlbums = emptyList<Album>()
        var smartAlbums = emptyList<Album>()
        var unfiledAlbum = Album("Unfiled", UNFILED_PATH, Uri.EMPTY, 0, isSmart = true)
        var imagePhotos = emptyList<Photo>()
        var videoPhotos = emptyList<Photo>()
        var smartIndex: Map<String, ScreenshotIndexEntry>? = null
        var indexCalls = 0
        var imageAlbumCalls = 0
        var videoAlbumCalls = 0
        var smartAlbumCalls = 0
        var unfiledAlbumCalls = 0
        val photoKinds = mutableListOf<Pair<Boolean, Boolean>>()
        val photoMemberInputs = mutableListOf<Set<ManualCollectionMedia>>()

        override suspend fun loadIndex(): Map<String, ScreenshotIndexEntry> {
            indexCalls += 1
            indexError?.let { throw it }
            return emptyMap()
        }

        override fun loadImageAlbums(): List<Album> {
            imageAlbumCalls += 1
            return imageAlbums
        }

        override fun loadVideoAlbums(): List<Album> {
            videoAlbumCalls += 1
            return videoAlbums
        }

        override fun loadSmartAlbums(index: Map<String, ScreenshotIndexEntry>): List<Album> {
            smartAlbumCalls += 1
            smartIndex = index
            return smartAlbums
        }

        override suspend fun loadUnfiledAlbum(): Album {
            unfiledAlbumCalls += 1
            unfiledError?.let { throw it }
            return unfiledAlbum
        }

        override suspend fun loadUnfiledPhotos(): List<Photo> {
            unfiledError?.let { throw it }
            return imagePhotos
        }

        override suspend fun loadCollectionMembers(id: Long): Set<ManualCollectionMedia> {
            collectionError?.let { throw it }
            return emptySet()
        }

        override fun loadPhotos(
            path: String,
            members: Set<ManualCollectionMedia>,
            includeImages: Boolean,
            includeVideos: Boolean,
        ): List<Photo> {
            photoError?.let { throw it }
            photoKinds += includeImages to includeVideos
            photoMemberInputs += members
            return when {
                includeImages -> imagePhotos
                includeVideos -> videoPhotos
                else -> emptyList()
            }
        }
    }
}
