package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GalleryReliabilityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        IndexHealthStore.clear(context)
    }

    @Test
    fun galleryStatesKeepLoadingPartialFailureEmptyAndReadyDistinct() {
        assertEquals(GalleryContentStatus.LOADING, state(loading = true).status)
        assertEquals(GalleryContentStatus.FAILED, state(failed = true).status)
        assertEquals(
            GalleryContentStatus.PARTIAL_PERMISSION,
            state(imageAccess = MediaAccess.SELECTED, itemCount = 3).status,
        )
        assertEquals(GalleryContentStatus.EMPTY, state().status)
        assertEquals(GalleryContentStatus.READY, state(itemCount = 3).status)
        assertTrue(state(failed = true).retryAvailable)
    }

    @Test
    fun indexHealthTracksPendingSuccessFailureAndObservedCountsWithoutContent() {
        IndexHealthStore.markStarted(context)
        assertEquals(1, IndexHealthStore.load(context).pendingCount)

        IndexHealthStore.markFailure(context)
        assertEquals(1, IndexHealthStore.load(context).failedCount)
        assertEquals(0, IndexHealthStore.load(context).pendingCount)

        IndexHealthStore.markSuccess(context, indexedCount = 12, eligibleCount = 15)
        val success = IndexHealthStore.load(context)
        assertEquals(12, success.indexedCount)
        assertEquals(15, success.eligibleCount)
        assertEquals(0, success.failedCount)
        assertTrue(success.lastSuccessfulScanMs > 0L)

        IndexHealthStore.updateObservedCounts(context, indexedCount = 9, eligibleCount = 11)
        assertEquals(9, IndexHealthStore.load(context).indexedCount)
        assertEquals(11, IndexHealthStore.load(context).eligibleCount)
    }

    private fun state(
        loading: Boolean = false,
        failed: Boolean = false,
        itemCount: Int = 0,
        imageAccess: MediaAccess = MediaAccess.FULL,
        videoAccess: MediaAccess = MediaAccess.FULL,
    ): GalleryContentState = GalleryContentStateResolver.resolve(
        loading,
        failed,
        itemCount,
        imageAccess,
        videoAccess,
    )
}
