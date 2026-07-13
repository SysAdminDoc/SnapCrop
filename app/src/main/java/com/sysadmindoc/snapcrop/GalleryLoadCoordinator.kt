package com.sysadmindoc.snapcrop

import android.content.Context
import kotlinx.coroutines.CancellationException

/** Failure-isolating Gallery load boundary; Compose only publishes complete snapshots. */
internal object GalleryLoadCoordinator {
    suspend fun overview(
        source: Source,
        indexEnabled: Boolean,
        canReadImages: Boolean,
        canReadVideos: Boolean,
    ): GalleryOverviewLoad {
        val failures = mutableListOf<GalleryLoadFailure>()
        suspend fun <T> load(
            failureSource: GalleryFailureSource,
            fallback: T,
            block: suspend () -> T,
        ): T = try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            failures += GalleryLoadFailure(failureSource, error)
            fallback
        }

        val index = if (indexEnabled) {
            load(GalleryFailureSource.INDEX_DATABASE, emptyMap()) { source.loadIndex() }
        } else {
            emptyMap()
        }
        val imageAlbums = if (canReadImages) {
            load(GalleryFailureSource.IMAGE_QUERY, emptyList()) { source.loadImageAlbums() }
        } else {
            emptyList()
        }
        val videoAlbums = if (canReadVideos) {
            load(GalleryFailureSource.VIDEO_QUERY, emptyList()) { source.loadVideoAlbums() }
        } else {
            emptyList()
        }
        val smartAlbums = if (canReadImages) {
            load(GalleryFailureSource.IMAGE_QUERY, emptyList()) { source.loadSmartAlbums(index) }
        } else {
            emptyList()
        }
        return GalleryOverviewLoad(
            albums = mergeAlbumSources(imageAlbums, videoAlbums),
            smartAlbums = smartAlbums,
            index = index,
            failures = failures,
        )
    }

    suspend fun album(
        source: Source,
        path: String,
        canReadImages: Boolean,
        canReadVideos: Boolean,
    ): GalleryPhotoLoad {
        val failures = mutableListOf<GalleryLoadFailure>()
        val members = if (path.startsWith(MANUAL_COLLECTION_PREFIX)) {
            val collectionId = manualCollectionId(path)
                ?: return GalleryPhotoLoad(emptyList(), failures)
            try {
                source.loadCollectionMembers(collectionId)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failures += GalleryLoadFailure(GalleryFailureSource.COLLECTION_DATABASE, error)
                emptySet()
            }
        } else {
            emptySet()
        }

        fun load(failureSource: GalleryFailureSource, images: Boolean, videos: Boolean): List<Photo> =
            try {
                source.loadPhotos(path, members, images, videos)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failures += GalleryLoadFailure(failureSource, error)
                emptyList()
            }

        val images = if (canReadImages) {
            load(GalleryFailureSource.IMAGE_QUERY, images = true, videos = false)
        } else {
            emptyList()
        }
        val videos = if (canReadVideos && !path.startsWith(SMART_ALBUM_PREFIX)) {
            load(GalleryFailureSource.VIDEO_QUERY, images = false, videos = true)
        } else {
            emptyList()
        }
        return GalleryPhotoLoad(
            photos = (images + videos).distinctBy(Photo::uri).sortedByDescending(Photo::dateAdded),
            failures = failures,
        )
    }

    internal interface Source {
        suspend fun loadIndex(): Map<String, ScreenshotIndexEntry>
        fun loadImageAlbums(): List<Album>
        fun loadVideoAlbums(): List<Album>
        fun loadSmartAlbums(index: Map<String, ScreenshotIndexEntry>): List<Album>
        suspend fun loadCollectionMembers(id: Long): Set<ManualCollectionMedia>
        fun loadPhotos(
            path: String,
            members: Set<ManualCollectionMedia>,
            includeImages: Boolean,
            includeVideos: Boolean,
        ): List<Photo>
    }
}

internal class AndroidGalleryLoadSource(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val store: ScreenshotIndexStore,
) : GalleryLoadCoordinator.Source {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun loadIndex(): Map<String, ScreenshotIndexEntry> = store.loadEntryMap()

    override fun loadImageAlbums(): List<Album> =
        loadAlbums(resolver, includeImages = true, includeVideos = false)

    override fun loadVideoAlbums(): List<Album> =
        loadAlbums(resolver, includeImages = false, includeVideos = true)

    override fun loadSmartAlbums(index: Map<String, ScreenshotIndexEntry>): List<Album> =
        loadSmartAlbums(resolver, screenWidth, screenHeight, index, includeImages = true)

    override suspend fun loadCollectionMembers(id: Long): Set<ManualCollectionMedia> =
        store.collectionItems(id)

    override fun loadPhotos(
        path: String,
        members: Set<ManualCollectionMedia>,
        includeImages: Boolean,
        includeVideos: Boolean,
    ): List<Photo> = loadPhotoSource(
        resolver = resolver,
        path = path,
        screenW = screenWidth,
        screenH = screenHeight,
        favoriteKeys = FavoritesStore.getAllKeys(appContext),
        members = members,
        includeImages = includeImages,
        includeVideos = includeVideos,
    )
}
