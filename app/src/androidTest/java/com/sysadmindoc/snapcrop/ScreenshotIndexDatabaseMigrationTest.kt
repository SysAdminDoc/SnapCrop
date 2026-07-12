package com.sysadmindoc.snapcrop

import android.content.Context
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
                ScreenshotIndexMigrations.MIGRATION_2_3
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
            .addMigrations(ScreenshotIndexMigrations.MIGRATION_2_3)
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
