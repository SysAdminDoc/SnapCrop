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
        val coordinator = String(Files.readAllBytes(
            Paths.get("src/main/java/com/sysadmindoc/snapcrop/GalleryLoadCoordinator.kt")
        ))

        assertTrue(source.contains("GalleryLoadCoordinator.album("))
        val albumLoad = coordinator.substringAfter("suspend fun album(").substringBefore("internal interface Source")
        assertFalse(albumLoad.contains("loadIndex()"))
        assertTrue(source.contains("photo.withIndex(indexEntries[photo.uri.toString()])"))
        assertTrue(source.contains("selectedUris.retainAll(visibleUris)"))
    }
}
