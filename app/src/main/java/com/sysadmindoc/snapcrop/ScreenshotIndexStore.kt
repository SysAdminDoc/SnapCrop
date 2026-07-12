package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenshotIndexEntry(
    val mediaId: Long,
    val uri: Uri,
    val name: String,
    val albumPath: String,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val size: Long,
    val isVideo: Boolean,
    val isScreenshot: Boolean,
    val isFavorite: Boolean,
    val categories: Set<String>,
    val searchText: String,
    val indexedAt: Long = System.currentTimeMillis()
)

internal object ScreenshotIndexClassifier {
    fun buildEntry(
        mediaId: Long,
        uri: Uri,
        name: String,
        albumPath: String,
        width: Int,
        height: Int,
        dateAdded: Long,
        size: Long,
        isVideo: Boolean,
        isScreenshot: Boolean,
        isFavorite: Boolean
    ): ScreenshotIndexEntry {
        val source = "$name $albumPath".lowercase()
        val categories = linkedSetOf<String>()
        if (isScreenshot && !isVideo) categories.add("screenshots")
        addIfAny(categories, "chats", source, chatKeywords)
        addIfAny(categories, "games", source, gameKeywords)
        addIfAny(categories, "sites", source, siteKeywords)
        addIfAny(categories, "documents", source, documentKeywords)
        addIfAny(categories, "codes", source, codeKeywords)
        addIfAny(categories, "payments", source, paymentKeywords)
        if (SensitiveTextPatterns.containsSensitivePattern(source) || "payments" in categories) {
            categories.add("sensitive")
        }
        if (isFavorite) categories.add("favorites")

        val searchText = buildString {
            append(name).append(' ')
            append(albumPath).append(' ')
            append(width).append('x').append(height).append(' ')
            categories.forEach { append(it).append(' ') }
            if (isScreenshot) append("screenshot capture screen ")
            if (isVideo) append("video recording clip ")
            if (isFavorite) append("favorite starred ")
        }.lowercase()

        return ScreenshotIndexEntry(
            mediaId = mediaId,
            uri = uri,
            name = name,
            albumPath = albumPath,
            width = width,
            height = height,
            dateAdded = dateAdded,
            size = size,
            isVideo = isVideo,
            isScreenshot = isScreenshot,
            isFavorite = isFavorite,
            categories = categories,
            searchText = searchText
        )
    }

    private fun addIfAny(categories: MutableSet<String>, category: String, source: String, keywords: List<String>) {
        if (keywords.any { source.contains(it) }) categories.add(category)
    }

    private val chatKeywords = listOf(
        "message", "messages", "messenger", "whatsapp", "telegram", "signal",
        "discord", "slack", "teams", "sms", "chat", "conversation"
    )
    private val gameKeywords = listOf(
        "game", "gaming", "steam", "xbox", "playstation", "nintendo",
        "minecraft", "roblox", "genshin", "pubg", "codm", "fortnite", "pokemon"
    )
    private val siteKeywords = listOf(
        "chrome", "browser", "firefox", "edge", "brave", "safari", "web", "site",
        "url", "reddit", "twitter", "x-twitter", "x.com", "youtube", "instagram",
        "tiktok", "facebook", "linkedin"
    )
    private val documentKeywords = listOf(
        "document", "doc", "scan", "receipt", "invoice", "pdf", "note", "notes",
        "form", "contract", "statement", "ticket", "boarding", "label"
    )
    private val codeKeywords = listOf(
        "qr", "barcode", "code", "wifi", "totp", "2fa", "authenticator"
    )
    private val paymentKeywords = listOf(
        "payment", "pay", "paypal", "venmo", "cashapp", "zelle", "bank",
        "card", "credit", "debit", "invoice", "receipt", "order"
    )
}

@Entity(tableName = "screenshot_index")
internal data class ScreenshotIndexRow(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "album_path") val albumPath: String,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    @ColumnInfo(name = "is_screenshot") val isScreenshot: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "categories") val categories: String,
    @ColumnInfo(name = "search_text") val searchText: String,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long
)

@Dao
internal interface ScreenshotIndexDao {
    @Query("SELECT * FROM screenshot_index ORDER BY date_added DESC")
    suspend fun getAll(): List<ScreenshotIndexRow>

    @Query("SELECT * FROM screenshot_index ORDER BY date_added DESC")
    fun observeAll(): Flow<List<ScreenshotIndexRow>>

    @Query("SELECT * FROM screenshot_index WHERE media_id = :id LIMIT 1")
    suspend fun getById(id: Long): ScreenshotIndexRow?

    @Query("SELECT COUNT(*) FROM screenshot_index")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ScreenshotIndexRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ScreenshotIndexRow>)

    @Query("DELETE FROM screenshot_index")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(rows: List<ScreenshotIndexRow>) {
        deleteAll()
        upsertAll(rows)
    }
}

@Database(entities = [ScreenshotIndexRow::class], version = 1, exportSchema = true)
internal abstract class ScreenshotIndexDatabase : RoomDatabase() {
    abstract fun dao(): ScreenshotIndexDao
}

/**
 * Opt-in local screenshot intelligence index. Migrated from a raw [android.database.sqlite.SQLiteOpenHelper]
 * to Room: queries are compile-time verified, the gallery can observe a reactive [Flow], and schema
 * changes go through Room's migration tooling. Missing migrations fail closed so future user-owned
 * metadata cannot be silently erased if it is added alongside the derived MediaStore cache.
 */
class ScreenshotIndexStore(context: Context) {
    private val dao = database(context.applicationContext).dao()

    suspend fun rebuildFromMediaStore(
        resolver: ContentResolver,
        screenW: Int,
        screenH: Int,
        favoriteIds: Set<Long>
    ): Int = withContext(Dispatchers.IO) {
        val entries = buildList {
            addAll(queryImages(resolver, screenW, screenH, favoriteIds))
            addAll(queryVideos(resolver, favoriteIds))
        }
        dao.replaceAll(entries.map { it.toRow() })
        entries.size
    }

    suspend fun loadEntryMap(): Map<Long, ScreenshotIndexEntry> =
        dao.getAll().associate { it.mediaId to it.toEntry() }

    /** Emits the full index whenever it changes (rebuild, OCR token capture, purge). */
    fun observeEntries(): Flow<Map<Long, ScreenshotIndexEntry>> =
        dao.observeAll().map { rows -> rows.associate { it.mediaId to it.toEntry() } }

    suspend fun purge() {
        dao.deleteAll()
    }

    suspend fun updateRecognizedText(uri: Uri, text: String, codes: List<String>) {
        val mediaId = try {
            ContentUris.parseId(uri)
        } catch (_: Exception) {
            return
        }
        val existing = dao.getById(mediaId)?.toEntry() ?: return
        val recognizedText = (listOf(text) + codes).joinToString(" ").trim()
        if (recognizedText.isBlank()) return

        val categories = existing.categories.toMutableSet()
        if (codes.isNotEmpty()) categories.add("codes")
        if (SensitiveTextPatterns.containsSensitivePattern(recognizedText)) categories.add("sensitive")
        val updated = existing.copy(
            categories = categories,
            searchText = "${existing.searchText} $recognizedText ${categories.joinToString(" ")}".lowercase(),
            indexedAt = System.currentTimeMillis()
        )
        dao.upsert(updated.toRow())
    }

    suspend fun count(): Int = dao.count()

    private fun queryImages(
        resolver: ContentResolver,
        screenW: Int,
        screenH: Int,
        favoriteIds: Set<Long>
    ): List<ScreenshotIndexEntry> {
        val entries = mutableListOf<ScreenshotIndexEntry>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val width = cursor.getInt(wCol)
                val height = cursor.getInt(hCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                entries.add(
                    ScreenshotIndexClassifier.buildEntry(
                        mediaId = id,
                        uri = uri,
                        name = name,
                        albumPath = cursor.getString(pathCol) ?: "",
                        width = width,
                        height = height,
                        dateAdded = cursor.getLong(dateCol),
                        size = cursor.getLong(sizeCol),
                        isVideo = false,
                        isScreenshot = looksLikeScreenshot(width, height, name, screenW, screenH),
                        isFavorite = id in favoriteIds
                    )
                )
            }
        }
        return entries
    }

    private fun queryVideos(
        resolver: ContentResolver,
        favoriteIds: Set<Long>
    ): List<ScreenshotIndexEntry> {
        val entries = mutableListOf<ScreenshotIndexEntry>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                entries.add(
                    ScreenshotIndexClassifier.buildEntry(
                        mediaId = id,
                        uri = uri,
                        name = name,
                        albumPath = cursor.getString(pathCol) ?: "",
                        width = 0,
                        height = 0,
                        dateAdded = cursor.getLong(dateCol),
                        size = cursor.getLong(sizeCol),
                        isVideo = true,
                        isScreenshot = false,
                        isFavorite = id in favoriteIds
                    )
                )
            }
        }
        return entries
    }

    private fun ScreenshotIndexEntry.toRow(): ScreenshotIndexRow = ScreenshotIndexRow(
        mediaId = mediaId,
        uri = uri.toString(),
        name = name,
        albumPath = albumPath,
        width = width,
        height = height,
        dateAdded = dateAdded,
        size = size,
        isVideo = isVideo,
        isScreenshot = isScreenshot,
        isFavorite = isFavorite,
        categories = categories.joinToString(","),
        searchText = searchText,
        indexedAt = indexedAt
    )

    private fun ScreenshotIndexRow.toEntry(): ScreenshotIndexEntry = ScreenshotIndexEntry(
        mediaId = mediaId,
        uri = Uri.parse(uri),
        name = name,
        albumPath = albumPath,
        width = width,
        height = height,
        dateAdded = dateAdded,
        size = size,
        isVideo = isVideo,
        isScreenshot = isScreenshot,
        isFavorite = isFavorite,
        categories = categories.split(',').filter { it.isNotBlank() }.toSet(),
        searchText = searchText,
        indexedAt = indexedAt
    )

    companion object {
        const val PREF_ENABLED = "screenshot_index_enabled"
        private const val DB_NAME = "screenshot_index_room.db"
        private const val LEGACY_DB_NAME = "screenshot_index.db"

        @Volatile
        private var INSTANCE: ScreenshotIndexDatabase? = null

        private fun database(context: Context): ScreenshotIndexDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, DB_NAME)
                    .build()
                    .also {
                        INSTANCE = it
                        // Remove the obsolete raw-SQLite database file from pre-Room versions.
                        runCatching { context.deleteDatabase(LEGACY_DB_NAME) }
                    }
            }
    }
}
