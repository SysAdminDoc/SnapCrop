package com.sysadmindoc.snapcrop

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotIndexDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ScreenshotIndexDatabase::class.java
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun exportedVersionOneSchemaOpensAndPreservesRows() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """INSERT INTO screenshot_index (
                    media_id, uri, name, album_path, width, height, date_added, size,
                    is_video, is_screenshot, is_favorite, categories, search_text, indexed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf<Any>(
                    42L,
                    "content://media/external/images/media/42",
                    "Screenshot_42.png",
                    "Pictures/Screenshots/",
                    1080,
                    2400,
                    1_700_000_000L,
                    123_456L,
                    0,
                    1,
                    1,
                    "screenshots,favorites",
                    "screenshot 42 favorites",
                    1_700_000_001L
                )
            )
            close()
        }

        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_1_2,
                ScreenshotIndexMigrations.MIGRATION_2_3,
                ScreenshotIndexMigrations.MIGRATION_3_4,
                ScreenshotIndexMigrations.MIGRATION_4_5,
                ScreenshotIndexMigrations.MIGRATION_5_6,
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            database.openHelper.writableDatabase
            val rows = database.dao().getAll()
            assertEquals(1, rows.size)
            assertEquals(42L, rows.single().mediaId)
            assertEquals("Screenshot_42.png", rows.single().name)
        } finally {
            database.close()
        }
    }

    @Test
    fun versionTwoMigrationAddsStructuredFilterIndexesAndPreservesRows() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """INSERT INTO screenshot_index (
                    media_id, uri, name, album_path, width, height, date_added, size,
                    is_video, is_screenshot, is_favorite, categories, search_text, indexed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf<Any>(
                    7L,
                    "content://media/external/images/media/7",
                    "Screenshot_7.webp",
                    "Pictures/Screenshots/",
                    1440,
                    3120,
                    1_700_000_007L,
                    765_432L,
                    0,
                    1,
                    0,
                    "screenshots,documents",
                    "screenshot 7 documents",
                    1_700_000_008L
                )
            )
            close()
        }

        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_2_3,
                ScreenshotIndexMigrations.MIGRATION_3_4,
                ScreenshotIndexMigrations.MIGRATION_4_5,
                ScreenshotIndexMigrations.MIGRATION_5_6,
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            database.openHelper.writableDatabase
            assertEquals(7L, database.dao().getAll().single().mediaId)
            val indexNames = mutableSetOf<String>()
            database.openHelper.readableDatabase.query("PRAGMA index_list(`screenshot_index`)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) indexNames += cursor.getString(nameColumn)
            }
            assertTrue(
                indexNames.containsAll(
                    setOf(
                    "index_screenshot_index_media_id",
                    "index_screenshot_index_date_added_uri",
                    "index_screenshot_index_is_video_date_added",
                    "index_screenshot_index_album_path_date_added",
                    "index_screenshot_index_owner_package_date_added",
                    "index_screenshot_index_mime_type_date_added",
                    "index_screenshot_index_orientation_date_added",
                    "index_screenshot_index_is_favorite_date_added",
                    "index_screenshot_index_width_height"
                    )
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun versionThreeMigrationAddsUserOwnedSourceContextTable() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 3).close()
        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_3_4,
                ScreenshotIndexMigrations.MIGRATION_4_5,
                ScreenshotIndexMigrations.MIGRATION_5_6,
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            val dao = database.dao()
            val row = MediaSourceContextRow(
                mediaUri = "content://media/external/images/media/9",
                mediaDateAdded = 123,
                sourceUrl = "https://example.com/runbook",
                sourceLabel = "Runbook",
                sourcePackage = "com.example.browser",
                updatedAt = 456
            )
            dao.upsertSourceContext(row)
            assertEquals(row, dao.getSourceContext(row.mediaUri, row.mediaDateAdded))
            assertEquals(null, dao.getSourceContext(row.mediaUri, row.mediaDateAdded + 1))

            dao.replaceAll(listOf(sampleRow(9)))
            dao.deleteAll()
            assertEquals(row, dao.getSourceContext(row.mediaUri, row.mediaDateAdded))
        } finally {
            database.close()
        }
    }

    @Test
    fun versionFourMigrationAddsDurableNotesAndConditionalReminders() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 4).close()
        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_4_5,
                ScreenshotIndexMigrations.MIGRATION_5_6,
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            val dao = database.dao()
            val row = ScreenshotNoteReminderRow(
                mediaUri = "content://media/external/images/media/12",
                mediaDateAdded = 123L,
                note = "Call the clinic",
                reminderAt = 1_800_000_000_000L,
                reminderToken = "token-new",
                createdAt = 100L,
                updatedAt = 200L
            )
            dao.upsertNoteReminder(row)
            assertEquals(row, dao.getNoteReminder(row.mediaUri, row.mediaDateAdded))
            assertEquals(null, dao.getNoteReminder(row.mediaUri, row.mediaDateAdded + 1))

            dao.replaceAll(listOf(sampleRow(12)))
            dao.deleteAll()
            assertEquals(row, dao.getNoteReminder(row.mediaUri, row.mediaDateAdded))
            assertEquals(0, dao.clearReminder(row.mediaUri, row.mediaDateAdded, "token-old", 300L))
            assertEquals(1, dao.clearReminder(row.mediaUri, row.mediaDateAdded, "token-new", 300L))
            assertEquals(null, dao.getNoteReminder(row.mediaUri, row.mediaDateAdded)?.reminderAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun uriPrimaryKeyKeepsImageAndVideoWithSameNumericId() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val image = sampleRow(42)
            val video = sampleRow(42).copy(
                uri = "content://media/external/video/media/42",
                name = "Recording_42.mp4",
                isVideo = true,
                isScreenshot = false,
                mimeType = "video/mp4"
            )
            database.dao().replaceAll(listOf(image, video))

            assertEquals(2, database.dao().count())
            assertEquals(setOf(image.uri, video.uri), database.dao().getAll().map { it.uri }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun versionFiveMigrationAddsDerivedFingerprintsAndDurableDismissals() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 5).close()
        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_5_6,
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            val dao = database.dao()
            val fingerprint = DuplicateFingerprintRow(
                mediaUri = "content://media/external/images/media/15",
                mediaDateAdded = 15,
                displayName = "Screenshot_15.png",
                width = 1080,
                height = 2400,
                sizeBytes = 1234,
                exactSha256 = "a".repeat(64),
                differenceHash = 42,
                averageLuma = 127,
                hashVersion = DuplicateHashing.HASH_VERSION,
                updatedAt = 100
            )
            val dismissal = DuplicateDismissalRow(
                firstFingerprint = "sha256:${"a".repeat(64)}",
                secondFingerprint = "sha256:${"b".repeat(64)}",
                createdAt = 200
            )
            dao.upsertDuplicateFingerprints(listOf(fingerprint))
            dao.insertDuplicateDismissals(listOf(dismissal))

            assertEquals(fingerprint, dao.getDuplicateFingerprints().single())
            assertEquals(dismissal, dao.getDuplicateDismissals().single())
            dao.deleteDuplicateFingerprints()
            assertTrue(dao.getDuplicateFingerprints().isEmpty())
            assertEquals(dismissal, dao.getDuplicateDismissals().single())
        } finally {
            database.close()
        }
    }

    @Test
    fun versionSixMigrationAddsSyncWatermarksWithoutTouchingUserOwnedMetadata() = runBlocking<Unit> {
        val uri = "content://media/external/images/media/42"
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """INSERT INTO screenshot_index (
                    media_id, uri, name, album_path, width, height, date_added, size,
                    is_video, is_screenshot, is_favorite, base_categories,
                    recognized_categories, base_search_text, recognized_text,
                    mime_type, owner_package, orientation, indexed_at
                ) VALUES (42, '$uri', 'Screenshot_42.png', 'Pictures/Screenshots/',
                    1080, 2400, 42, 1042, 0, 1, 0, 'screenshots', 'codes',
                    'screenshot 42', 'reviewed text', 'image/png', '', 'portrait', 42)""".trimIndent()
            )
            execSQL("INSERT INTO manual_collection (collection_id, name, normalized_name, created_at, updated_at) VALUES (1, 'Work', 'work', 1, 1)")
            execSQL("INSERT INTO manual_collection_item (collection_id, media_uri, media_date_added, added_at) VALUES (1, '$uri', 42, 2)")
            execSQL("INSERT INTO media_source_context (media_uri, media_date_added, source_url, source_label, source_package, updated_at) VALUES ('$uri', 42, 'https://example.com', 'Example', 'com.example', 3)")
            execSQL("INSERT INTO media_note_reminder (media_uri, media_date_added, note, reminder_at, reminder_token, created_at, updated_at) VALUES ('$uri', 42, 'Review', 1800000000000, 'token', 4, 4)")
            execSQL("INSERT INTO duplicate_dismissal (first_fingerprint, second_fingerprint, created_at) VALUES ('first', 'second', 5)")
            close()
        }

        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(
                ScreenshotIndexMigrations.MIGRATION_6_7,
                ScreenshotIndexMigrations.MIGRATION_7_8,
            )
            .build()
        try {
            val dao = database.dao()
            assertEquals("reviewed text", dao.getByUri(uri)?.recognizedText)
            assertEquals(uri, dao.getCollectionItems(1).single().mediaUri)
            assertEquals("Example", dao.getSourceContext(uri, 42)?.sourceLabel)
            assertEquals("Review", dao.getNoteReminder(uri, 42)?.note)
            assertEquals("first", dao.getDuplicateDismissals().single().firstFingerprint)
            assertTrue(dao.getMediaSyncStates().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun versionSevenMigrationAddsDurableTriageWithoutTouchingUserOwnedMetadata() = runBlocking<Unit> {
        val uri = "content://media/external/images/media/77"
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                """INSERT INTO screenshot_index (
                    media_id, uri, name, album_path, width, height, date_added, size,
                    is_video, is_screenshot, is_favorite, base_categories,
                    recognized_categories, base_search_text, recognized_text,
                    mime_type, owner_package, orientation, indexed_at
                ) VALUES (77, '$uri', 'Screenshot_77.png', 'Pictures/Screenshots/',
                    1080, 2400, 77, 1077, 0, 1, 0, 'screenshots', 'receipts',
                    'screenshot 77', 'reviewed receipt', 'image/png', '', 'portrait', 77)""".trimIndent()
            )
            execSQL("INSERT INTO manual_collection (collection_id, name, normalized_name, created_at, updated_at) VALUES (7, 'Receipts', 'receipts', 1, 1)")
            execSQL("INSERT INTO manual_collection_item (collection_id, media_uri, media_date_added, added_at) VALUES (7, '$uri', 77, 2)")
            execSQL("INSERT INTO media_source_context (media_uri, media_date_added, source_url, source_label, source_package, updated_at) VALUES ('$uri', 77, 'https://example.com/77', 'Receipt', 'com.example', 3)")
            execSQL("INSERT INTO media_note_reminder (media_uri, media_date_added, note, reminder_at, reminder_token, created_at, updated_at) VALUES ('$uri', 77, 'Expense', NULL, NULL, 4, 4)")
            execSQL("INSERT INTO duplicate_dismissal (first_fingerprint, second_fingerprint, created_at) VALUES ('first-77', 'second-77', 5)")
            execSQL("INSERT INTO media_sync_state (volume_name, store_version, generation, scope_signature, completed_at) VALUES ('external_primary', 'v7', 7, 'scope', 6)")
            close()
        }

        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB)
            .addMigrations(ScreenshotIndexMigrations.MIGRATION_7_8)
            .build()
        try {
            val dao = database.dao()
            assertEquals("reviewed receipt", dao.getByUri(uri)?.recognizedText)
            assertEquals(77L, dao.getCollectionItems(7).single().mediaDateAdded)
            assertEquals("Receipt", dao.getSourceContext(uri, 77)?.sourceLabel)
            assertEquals("Expense", dao.getNoteReminder(uri, 77)?.note)
            assertEquals("first-77", dao.getDuplicateDismissals().single().firstFingerprint)
            assertEquals(7L, dao.getMediaSyncStates().single().generation)
            assertTrue(dao.getMediaTriage().isEmpty())

            val triage = MediaTriageRow(uri, 77, 100)
            dao.upsertMediaTriage(listOf(triage))
            val reusedIdentity = ManualCollectionItemRow(7, uri, 88, 101)
            val reusedTriage = MediaTriageRow(uri, 88, 101)
            assertEquals(
                1,
                dao.insertCollectionItemsAndTriage(listOf(reusedIdentity), listOf(reusedTriage)),
            )
            assertEquals(88L, dao.getCollectionItems(7).single().mediaDateAdded)
            dao.replaceAll(listOf(sampleRow(77)))
            dao.deleteAll()
            assertEquals(setOf(triage, reusedTriage), dao.getMediaTriage().toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun suspendDaoReplaceAndPurgeRemainAtomicForObservers() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val rows = listOf(sampleRow(1), sampleRow(2))

            dao.replaceAll(rows)

            assertEquals(2, dao.count())
            assertEquals(listOf(2L, 1L), dao.observeAll().first().map { it.mediaId })

            dao.deleteAll()
            assertEquals(0, dao.count())
        } finally {
            database.close()
        }
    }

    @Test
    fun rebuildPreservesReplaceableRecognizedPayloadByUri() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val original = sampleRow(9).copy(
                recognizedCategories = "codes,sensitive",
                recognizedText = "one time secret"
            )
            dao.replaceAll(listOf(original))

            dao.replaceAll(listOf(sampleRow(9).copy(baseSearchText = "refreshed media metadata")))

            val refreshed = dao.getByUri(original.uri)!!
            assertEquals("refreshed media metadata", refreshed.baseSearchText)
            assertEquals("one time secret", refreshed.recognizedText)
            assertEquals("codes,sensitive", refreshed.recognizedCategories)
        } finally {
            database.close()
        }
    }

    @Test
    fun rebuildDropsRecognizedPayloadWhenMediaStoreReusesUri() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val stale = sampleRow(11).copy(
                recognizedCategories = "sensitive",
                recognizedText = "old private text"
            )
            dao.replaceAll(listOf(stale))

            dao.replaceAll(listOf(sampleRow(11).copy(dateAdded = stale.dateAdded + 1)))

            val replacement = dao.getByUri(stale.uri)!!
            assertEquals("", replacement.recognizedText)
            assertEquals("", replacement.recognizedCategories)
        } finally {
            database.close()
        }
    }

    @Test
    fun manualCollectionMembershipSurvivesIndexRebuild() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val now = 1_700_000_000_000L
            val collectionId = dao.insertCollection(
                ManualCollectionRow(name = "Research", normalizedName = "research", createdAt = now, updatedAt = now)
            )
            val uri = "content://media/external/images/media/42"
            dao.insertCollectionItems(listOf(ManualCollectionItemRow(collectionId, uri, 42L, now)))

            dao.replaceAll(listOf(sampleRow(42)))
            dao.replaceAll(listOf(sampleRow(7)))

            assertEquals(listOf(uri), dao.getCollectionItems(collectionId).map { it.mediaUri })
            assertEquals(1, dao.observeCollections().first().single().itemCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedCollectionCreationDoesNotFileOrTriageMedia() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val collection = ManualCollectionRow(
                name = "Research",
                normalizedName = "research",
                createdAt = 100,
                updatedAt = 100,
            )
            assertTrue(dao.insertCollection(collection) > 0)
            val mediaUri = "content://media/external/images/media/42"

            var rejected = false
            try {
                dao.insertCollectionWithItemsAndTriage(
                    collection = collection,
                    items = listOf(ManualCollectionItemRow(0, mediaUri, 42, 101)),
                    triageRows = listOf(MediaTriageRow(mediaUri, 42, 101)),
                )
            } catch (_: SQLiteConstraintException) {
                rejected = true
            }

            assertTrue(rejected)
            assertTrue(dao.getAllCollectionItems().isEmpty())
            assertTrue(dao.getMediaTriage().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun libraryMetadataPlanAppliesAtomicallyAndReplansIdempotently() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
        try {
            val dao = database.dao()
            val media = LibraryMediaIdentity(
                uri = "content://media/external/images/media/42",
                dateAdded = 42,
                name = "Screenshot_42.png",
                albumPath = "Pictures/Screenshots/",
                size = 1042,
                width = 1080,
                height = 2400,
                mimeType = "image/png",
            )
            val document = LibraryMetadataDocument(
                exportedAt = 100,
                selection = LibraryMetadataSelection(),
                media = listOf(
                    LibraryMetadataMedia(
                        "m1", media.uri, media.dateAdded, media.name, media.albumPath,
                        media.size, media.width, media.height, media.mimeType, null,
                    )
                ),
                collections = listOf(LibraryMetadataCollection("Research", 1, 2, listOf("m1"))),
                notes = listOf(LibraryMetadataNote("m1", "Keep this", 10_000, 3, 4)),
                triage = listOf(LibraryMetadataTriage("m1", 5)),
            )
            val empty = LibraryMetadataLocalSnapshot(
                listOf(media), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(),
            )
            val first = LibraryMetadataPlanner.plan(document, empty, now = 1_000)

            dao.applyLibraryMetadataPlan(first, now = 1_000)

            val collection = dao.getAllCollections().single()
            val after = LibraryMetadataLocalSnapshot(
                media = listOf(media),
                collections = listOf(collection),
                collectionItems = dao.getAllCollectionItems(),
                notes = dao.getAllNoteReminders(),
                triage = dao.getMediaTriage(),
                exactSha256ByMedia = emptyMap(),
            )
            val second = LibraryMetadataPlanner.plan(document, after, now = 1_000)
            assertEquals(0, second.report.changes)
            assertEquals(1, dao.getAllCollectionItems().size)
            assertEquals("Keep this", dao.getAllNoteReminders().single().note)
            assertEquals(1, dao.getMediaTriage().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedLibraryMetadataPlanRollsBackEarlierRows() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, ScreenshotIndexDatabase::class.java).build()
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
                dao.applyLibraryMetadataPlan(plan, now = 2)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }

            assertTrue(rejected)
            assertTrue(dao.getAllCollections().isEmpty())
        } finally {
            database.close()
        }
    }

    private fun sampleRow(id: Long) = ScreenshotIndexRow(
        mediaId = id,
        uri = "content://media/external/images/media/$id",
        name = "Screenshot_$id.png",
        albumPath = "Pictures/Screenshots/",
        width = 1080,
        height = 2400,
        dateAdded = id,
        size = 1000L + id,
        isVideo = false,
        isScreenshot = true,
        isFavorite = false,
        baseCategories = "screenshots",
        baseSearchText = "screenshot $id",
        indexedAt = id
    )

    companion object {
        private const val TEST_DB = "screenshot-index-migration-test"
    }
}
