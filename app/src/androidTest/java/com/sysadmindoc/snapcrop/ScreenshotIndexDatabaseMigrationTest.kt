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

        val database = Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, TEST_DB).build()
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
        categories = "screenshots",
        searchText = "screenshot $id",
        indexedAt = id
    )

    companion object {
        private const val TEST_DB = "screenshot-index-migration-test"
    }
}
