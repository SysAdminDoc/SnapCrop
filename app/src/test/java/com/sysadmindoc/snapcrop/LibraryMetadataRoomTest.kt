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
class LibraryMetadataRoomTest {
    @Test
    fun applyThenReplanIsIdempotentAgainstTheRealDao() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ScreenshotIndexDatabase::class.java,
        ).build()
        try {
            val dao = database.dao()
            val media = media()
            val document = document(media)
            val empty = snapshot(media)
            val first = LibraryMetadataPlanner.plan(document, empty, NOW)

            dao.applyLibraryMetadataPlan(first, NOW)

            val after = snapshot(media).copy(
                collections = dao.getAllCollections(),
                collectionItems = dao.getAllCollectionItems(),
                notes = dao.getAllNoteReminders(),
                triage = dao.getMediaTriage(),
            )
            val second = LibraryMetadataPlanner.plan(document, after, NOW)
            assertEquals(0, second.report.changes)
            assertEquals(1, dao.getAllCollections().size)
            assertEquals(1, dao.getAllCollectionItems().size)
            assertEquals("Remember", dao.getAllNoteReminders().single().note)
            assertTrue(dao.getAllNoteReminders().single().reminderToken?.isNotBlank() == true)
            assertEquals(1, dao.getMediaTriage().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun invalidLaterChangeRollsBackTheWholeDaoTransaction() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ScreenshotIndexDatabase::class.java,
        ).build()
        try {
            val dao = database.dao()
            val plan = LibraryMetadataImportPlan(
                report = LibraryMetadataImportReport(0, 0, 0, 0, 2, 0, 0, 0, 0),
                collectionChanges = listOf(
                    LibraryCollectionChange("Research", 1, 1, emptyList()),
                    LibraryCollectionChange("Invalid\u0000name", 1, 1, emptyList()),
                ),
                noteChanges = emptyList(),
                triageChanges = emptyList(),
            )

            var rejected = false
            try {
                dao.applyLibraryMetadataPlan(plan, NOW)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }

            assertTrue(rejected)
            assertTrue(dao.getAllCollections().isEmpty())
        } finally {
            database.close()
        }
    }

    private fun media() = LibraryMediaIdentity(
        uri = "content://media/external/images/media/42",
        dateAdded = 42,
        name = "Screenshot_42.png",
        albumPath = "Pictures/Screenshots/",
        size = 1042,
        width = 1080,
        height = 2400,
        mimeType = "image/png",
    )

    private fun document(media: LibraryMediaIdentity) = LibraryMetadataDocument(
        exportedAt = NOW,
        selection = LibraryMetadataSelection(),
        media = listOf(
            LibraryMetadataMedia(
                "m1", media.uri, media.dateAdded, media.name, media.albumPath,
                media.size, media.width, media.height, media.mimeType, null,
            )
        ),
        collections = listOf(LibraryMetadataCollection("Research", 1, 2, listOf("m1"))),
        notes = listOf(LibraryMetadataNote("m1", "Remember", NOW + 60_000, 3, 4)),
        triage = listOf(LibraryMetadataTriage("m1", 5)),
    )

    private fun snapshot(media: LibraryMediaIdentity) = LibraryMetadataLocalSnapshot(
        media = listOf(media),
        collections = emptyList(),
        collectionItems = emptyList(),
        notes = emptyList(),
        triage = emptyList(),
        exactSha256ByMedia = emptyMap(),
    )

    companion object {
        private const val NOW = 1_700_000_000_000L
    }
}
