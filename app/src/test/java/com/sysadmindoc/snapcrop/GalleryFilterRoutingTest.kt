package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GalleryFilterRoutingTest {
    @Test
    fun galleryRestoresBoundedFilterStateAndReusesCollectionSelection() {
        val source = String(Files.readAllBytes(
            Paths.get("src/main/java/com/sysadmindoc/snapcrop/GalleryScreen.kt")
        ))

        assertTrue(source.contains("rememberSaveable { mutableStateOf(GalleryFilterState().encode()) }"))
        assertTrue(source.contains("GalleryFilterState.decode(encodedFilters)"))
        assertTrue(source.contains("applyGalleryFilters("))
        assertTrue(source.contains("pendingCollectionSelection = collectionSeed"))
        assertTrue(source.contains("pendingCollectionSelection\n                                        ?: collectionSelection"))
        assertFalse(source.contains("indexEntries[id]"))
    }

    @Test
    fun indexUpdatesEnrichInMemoryWithoutReloadingOrClearingSelection() {
        val source = String(Files.readAllBytes(
            Paths.get("src/main/java/com/sysadmindoc/snapcrop/GalleryScreen.kt")
        ))
        val loadEffect = source.substringAfter(
            "LaunchedEffect(selectedAlbum, manualCollections, refreshKey, canReadImages, canReadVideos)"
        ).substringBefore("val enrichedPhotos")

        assertFalse(loadEffect.contains("indexEntries,"))
        assertTrue(source.contains("photo.withIndex(indexEntries[photo.uri.toString()])"))
        assertTrue(source.contains("selectedUris.retainAll(visibleUris)"))
    }
}
