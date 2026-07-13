package com.sysadmindoc.snapcrop

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkflowStateRestorationTest {
    @Test
    fun uriStateRejectsOversizeAndStaysWithinCountAndCharacterBudgets() {
        val values = buildList {
            add("content://fixture/first")
            add("x".repeat(WorkflowStateRestoration.MAX_URI_CHARS + 1))
            repeat(100) { add("content://fixture/$it/" + "a".repeat(900)) }
        }

        val bounded = WorkflowStateRestoration.boundedUriStrings(values)

        assertFalse(bounded.any { it.length > WorkflowStateRestoration.MAX_URI_CHARS })
        assertFalse(bounded.any { it == "x".repeat(WorkflowStateRestoration.MAX_URI_CHARS) })
        assertEquals(bounded.distinct(), bounded)
        assert(bounded.size <= WorkflowStateRestoration.MAX_SAVED_URIS)
        assert(bounded.sumOf(String::length) <= WorkflowStateRestoration.MAX_URI_STATE_CHARS)
    }

    @Test
    fun deepestGalleryBackLayerIsDeterministic() {
        val fields = listOf<(GalleryBackState) -> GalleryBackState>(
            { it.copy(deleteDialog = true) },
            { it.copy(collectionEditor = true) },
            { it.copy(collectionPicker = true) },
            { it.copy(sourceEditor = true) },
            { it.copy(noteEditor = true) },
            { it.copy(duplicateReview = true) },
            { it.copy(duplicateScan = true) },
            { it.copy(viewer = true) },
            { it.copy(selection = true) },
            { it.copy(filters = true) },
            { it.copy(album = true) },
        )
        val targets = GalleryBackTarget.entries.filterNot { it == GalleryBackTarget.ROOT }

        fields.indices.forEach { index ->
            val state = fields.drop(index).fold(GalleryBackState()) { value, enable -> enable(value) }
            assertEquals(targets[index], WorkflowStateRestoration.deepestGalleryBackTarget(state))
        }
        assertEquals(
            GalleryBackTarget.ROOT,
            WorkflowStateRestoration.deepestGalleryBackTarget(GalleryBackState()),
        )
    }

    @Test
    fun videoTimelineClampsAndFallsBackToFullDuration() {
        assertEquals(
            RestoredVideoTimeline(10_000, 2_000, 9_000),
            WorkflowStateRestoration.restoreVideoTimeline(10_000, 20_000, 2_000, 9_000),
        )
        assertEquals(
            RestoredVideoTimeline(0, 0, 10_000),
            WorkflowStateRestoration.restoreVideoTimeline(10_000, -1, 8_000, 2_000),
        )
    }

    @Test
    fun galleryViewerIdentitySurvivesReorderAndFailsClosedWhenRemoved() {
        val first = photo(1, 100)
        val target = photo(2, 200)
        val identity = galleryViewerIdentity(target)

        assertEquals(0, resolveGalleryViewerIndex(listOf(target, first), identity))
        assertEquals(1, resolveGalleryViewerIndex(listOf(first, target), identity))
        assertEquals(-1, resolveGalleryViewerIndex(listOf(first), identity))
        assertEquals(-1, resolveGalleryViewerIndex(listOf(target.copy(dateAdded = 201)), identity))
    }

    private fun photo(id: Long, dateAdded: Long) = Photo(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        dateAdded = dateAdded,
    )
}
