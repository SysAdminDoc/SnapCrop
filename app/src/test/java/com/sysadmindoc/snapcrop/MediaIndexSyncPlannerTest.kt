package com.sysadmindoc.snapcrop

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexSyncPlannerTest {
    private val snapshot = MediaVolumeSnapshot("external_primary", "v1", 100)
    private val stored = MediaSyncStateRow("external_primary", "v1", 80, "scope-a", 1)

    @Test
    fun firstRunAndUnsafeScopeTransitionsForceAFullEquivalentScan() {
        assertTrue(plan(stored = emptyList()).fullScan)
        assertTrue(plan(current = listOf(snapshot.copy(version = "v2"))).fullScan)
        assertTrue(plan(current = listOf(snapshot.copy(generation = 79))).fullScan)
        assertTrue(plan(current = listOf(snapshot, MediaVolumeSnapshot("sdcard", "v1", 4))).fullScan)
        assertTrue(plan(scope = "scope-b").fullScan)
        assertTrue(plan(partial = true).fullScan)
        assertTrue(plan(generationSupported = false).fullScan)
        assertTrue(plan(forceFull = true).fullScan)
    }

    @Test
    fun stableVolumesVersionsAndScopeUsePerVolumeGenerationFloors() {
        val result = plan()

        assertFalse(result.fullScan)
        assertEquals(mapOf("external_primary" to 80L), result.generationFloorByVolume)
    }

    @Test
    fun deletedFileFeedKeepsOnlyAllowedVolumesTypesAndGenerationWindow() {
        val records = listOf(
            DeletedMediaRecord("external_primary", 1, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, 81),
            DeletedMediaRecord("external_primary", 2, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, 100),
            DeletedMediaRecord("external_primary", 3, MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, 90),
            DeletedMediaRecord("external_primary", 4, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, 80),
            DeletedMediaRecord("external_primary", 5, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, 101),
            DeletedMediaRecord("detached", 6, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, 90),
        )

        val eligible = DeletedMediaRecordFilter.eligible(
            records,
            snapshots = listOf(snapshot),
            generationFloorByVolume = mapOf("external_primary" to 80),
            allowedMediaTypes = setOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
        )

        assertEquals(listOf(1L), eligible.map(DeletedMediaRecord::mediaId))
    }

    @Test
    fun incrementalReconciliationMatchesFullResultOnLargeCorpusWithFarFewerDetailedRows() {
        val previous = (1L..10_000L).map(::row)
        val modified = listOf(
            row(10).copy(name = "Screenshot_10_modified.png"),
            row(5_000).copy(size = 9_999),
            row(9_999).copy(width = 1440),
        )
        val added = row(10_001)
        val deletedUris = setOf(row(20).uri, row(8_000).uri)
        val fullRows = previous
            .filterNot { it.uri in deletedUris }
            .associateBy(ScreenshotIndexRow::uri)
            .toMutableMap()
            .apply {
                (modified + added).forEach { put(it.uri, it) }
            }
            .values
            .toList()
        val visible = fullRows.mapTo(hashSetOf(), ScreenshotIndexRow::uri)

        val incremental = IndexRowReconciliation.plan(previous, modified + added, visible)
        val forcedFull = IndexRowReconciliation.plan(previous, fullRows, visible)

        val incrementalResult = apply(previous, incremental)
        assertEquals(apply(previous, forcedFull), incrementalResult)
        val replay = IndexRowReconciliation.plan(
            incrementalResult.values.toList(),
            modified + added,
            visible,
        )
        assertEquals(incrementalResult, apply(incrementalResult.values.toList(), replay))
        assertEquals(4, incremental.upserts.size)
        assertTrue(incremental.upserts.size * 1_000 < forcedFull.upserts.size)
        assertEquals(deletedUris, incremental.deletes.mapTo(hashSetOf(), ScreenshotIndexRow::uri))
    }

    @Test
    fun ocrIdentityAndDeletionRacesFailClosed() {
        val original = row(7).copy(recognizedCategories = "sensitive", recognizedText = "private text")
        val sameIdentity = IndexRowReconciliation.plan(
            listOf(original),
            listOf(row(7).copy(name = "renamed.png")),
            setOf(original.uri),
        ).upserts.single()
        assertEquals("private text", sameIdentity.recognizedText)

        val reusedIdentity = IndexRowReconciliation.plan(
            listOf(original),
            listOf(row(7).copy(dateAdded = 700)),
            setOf(original.uri),
        ).upserts.single()
        assertEquals("", reusedIdentity.recognizedText)

        val deletedByExtension = IndexRowReconciliation.plan(
            listOf(original),
            changedRows = emptyList(),
            visibleUris = null,
            explicitlyRemovedUris = setOf(original.uri),
        )
        assertEquals(listOf(original), deletedByExtension.deletes)

        val racedAddition = row(8)
        val fallback = IndexRowReconciliation.plan(
            previousRows = emptyList(),
            changedRows = listOf(racedAddition),
            visibleUris = emptySet(),
        )
        assertTrue(fallback.upserts.isEmpty())
    }

    private fun plan(
        current: List<MediaVolumeSnapshot> = listOf(snapshot),
        stored: List<MediaSyncStateRow> = listOf(this.stored),
        scope: String = "scope-a",
        generationSupported: Boolean = true,
        partial: Boolean = false,
        forceFull: Boolean = false,
    ) = MediaIndexSyncPlanner.plan(
        current,
        stored,
        scope,
        generationSupported,
        partial,
        forceFull,
    )

    private fun apply(
        previous: List<ScreenshotIndexRow>,
        changes: IndexRowChanges,
    ): Map<String, ScreenshotIndexRow> = previous.associateBy(ScreenshotIndexRow::uri)
        .toMutableMap()
        .apply {
            changes.deletes.forEach { remove(it.uri) }
            changes.upserts.forEach { put(it.uri, it) }
        }

    private fun row(id: Long) = ScreenshotIndexRow(
        mediaId = id,
        uri = "content://media/external/images/media/$id",
        name = "Screenshot_$id.png",
        albumPath = "Pictures/Screenshots/",
        width = 1080,
        height = 2400,
        dateAdded = id,
        size = 1_000 + id,
        isVideo = false,
        isScreenshot = true,
        isFavorite = false,
        baseCategories = "screenshots",
        baseSearchText = "screenshot $id",
        indexedAt = id,
    )
}
