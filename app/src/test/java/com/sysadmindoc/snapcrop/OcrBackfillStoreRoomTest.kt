package com.sysadmindoc.snapcrop

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrBackfillStoreRoomTest {
    @Test
    fun completedItemStaysCheckpointedWhenABatchStopsBeforeTheNextItem() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ScreenshotIndexDatabase::class.java,
        ).build()
        try {
            val dao = database.dao()
            val first = row(1)
            val second = row(2)
            dao.replaceAll(listOf(first, second))

            dao.updateOcrPayload(
                first.uri,
                first.dateAdded,
                ScreenshotIndexStore.OCR_CHECKPOINT_MARKER,
                "first result",
                100,
            )
            // Simulate cancellation before the second item is decoded.
            val resumed = dao.getOcrBackfillCandidates(ScreenshotIndexStore.OCR_CHECKPOINT_MARKER, 20)

            assertEquals(listOf(second.uri), resumed.map { it.uri })
            assertEquals("first result", dao.getByUri(first.uri)?.recognizedText)
        } finally {
            database.close()
        }
    }

    @Test
    fun candidatesCheckpointEmptyTextAndClearForARepeatableBackfill() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ScreenshotIndexDatabase::class.java,
        ).build()
        try {
            val dao = database.dao()
            val uncached = row(1)
            val manuallyCached = row(2).copy(recognizedText = "already indexed")
            val emptyCheckpoint = row(3).copy(recognizedCategories = ScreenshotIndexStore.OCR_CHECKPOINT_MARKER)
            val video = row(4).copy(isVideo = true, isScreenshot = false)
            dao.replaceAll(listOf(uncached, manuallyCached, emptyCheckpoint, video))

            assertEquals(
                listOf(uncached.uri),
                dao.getOcrBackfillCandidates(ScreenshotIndexStore.OCR_CHECKPOINT_MARKER, 20).map { it.uri },
            )
            assertEquals(1, dao.countOcrBackfillCandidates(ScreenshotIndexStore.OCR_CHECKPOINT_MARKER))

            assertEquals(
                1,
                dao.updateOcrPayload(
                    uncached.uri,
                    uncached.dateAdded,
                    ScreenshotIndexStore.OCR_CHECKPOINT_MARKER,
                    "",
                    100,
                ),
            )
            assertEquals(0, dao.countOcrBackfillCandidates(ScreenshotIndexStore.OCR_CHECKPOINT_MARKER))

            assertEquals(3, dao.clearOcrPayload(200))
            assertEquals(3, dao.countOcrBackfillCandidates(ScreenshotIndexStore.OCR_CHECKPOINT_MARKER))
            assertTrue(dao.getAll().all { it.recognizedText.isEmpty() && it.recognizedCategories.isEmpty() })
        } finally {
            database.close()
        }
    }

    private fun row(id: Long) = ScreenshotIndexRow(
        mediaId = id,
        uri = "content://media/external/images/media/$id",
        name = "Screenshot_$id.png",
        albumPath = "Pictures/Screenshots/",
        width = 1080,
        height = 2400,
        dateAdded = id,
        size = 1_000_000,
        isVideo = false,
        isScreenshot = true,
        isFavorite = false,
        baseCategories = "screenshots",
        baseSearchText = "screenshot $id",
    )
}
