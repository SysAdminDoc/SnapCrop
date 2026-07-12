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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

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

@Entity(
    tableName = "manual_collection",
    indices = [Index(value = ["normalized_name"], unique = true)]
)
internal data class ManualCollectionRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "collection_id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "manual_collection_item",
    primaryKeys = ["collection_id", "media_uri"],
    foreignKeys = [
        ForeignKey(
            entity = ManualCollectionRow::class,
            parentColumns = ["collection_id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collection_id"), Index(value = ["media_uri", "media_date_added"])]
)
internal data class ManualCollectionItemRow(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "media_date_added") val mediaDateAdded: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

data class ManualCollectionMedia(val uri: Uri, val dateAdded: Long)

data class ManualCollectionSummary(
    @ColumnInfo(name = "collection_id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "cover_uri") val coverUri: String?
)

internal data class NormalizedCollectionName(val display: String, val key: String)

internal object ManualCollectionNames {
    fun normalize(name: String): NormalizedCollectionName {
        val display = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .trim().replace(Regex("\\s+"), " ")
        require(display.length in 1..80) { "Collection names must be 1 to 80 characters" }
        require(display.none(Char::isISOControl)) { "Collection names cannot contain control characters" }
        return NormalizedCollectionName(display, display.lowercase(Locale.ROOT))
    }
}

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

    @Query("""SELECT c.collection_id, c.name, COUNT(i.media_uri) AS item_count,
        MIN(i.media_uri) AS cover_uri FROM manual_collection c
        LEFT JOIN manual_collection_item i ON i.collection_id = c.collection_id
        GROUP BY c.collection_id ORDER BY c.normalized_name""")
    fun observeCollections(): Flow<List<ManualCollectionSummary>>

    @Query("SELECT * FROM manual_collection WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getCollectionByName(normalizedName: String): ManualCollectionRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(row: ManualCollectionRow): Long

    @Query("UPDATE manual_collection SET name = :name, normalized_name = :normalizedName, updated_at = :updatedAt WHERE collection_id = :id")
    suspend fun renameCollection(id: Long, name: String, normalizedName: String, updatedAt: Long): Int

    @Query("DELETE FROM manual_collection WHERE collection_id = :id")
    suspend fun deleteCollection(id: Long): Int

    @Query("SELECT * FROM manual_collection_item WHERE collection_id = :collectionId ORDER BY added_at DESC")
    suspend fun getCollectionItems(collectionId: Long): List<ManualCollectionItemRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollectionItems(rows: List<ManualCollectionItemRow>): List<Long>

    @Transaction
    suspend fun insertCollectionWithItems(
        collection: ManualCollectionRow,
        items: List<ManualCollectionItemRow>
    ): Pair<Long, Int> {
        val id = insertCollection(collection)
        val inserted = if (items.isEmpty()) 0 else insertCollectionItems(items.map { it.copy(collectionId = id) }).count { it != -1L }
        return id to inserted
    }

    @Query("DELETE FROM manual_collection_item WHERE collection_id = :collectionId AND media_uri IN (:mediaUris)")
    suspend fun deleteCollectionItems(collectionId: Long, mediaUris: List<String>): Int
}

@Database(
    entities = [ScreenshotIndexRow::class, ManualCollectionRow::class, ManualCollectionItemRow::class],
    version = 2,
    exportSchema = true
)
internal abstract class ScreenshotIndexDatabase : RoomDatabase() {
    abstract fun dao(): ScreenshotIndexDao
}

internal object ScreenshotIndexMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `manual_collection` (
                    `collection_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `normalized_name` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL)""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_manual_collection_normalized_name` ON `manual_collection` (`normalized_name`)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `manual_collection_item` (
                    `collection_id` INTEGER NOT NULL,
                    `media_uri` TEXT NOT NULL,
                    `media_date_added` INTEGER NOT NULL,
                    `added_at` INTEGER NOT NULL,
                    PRIMARY KEY(`collection_id`, `media_uri`),
                    FOREIGN KEY(`collection_id`) REFERENCES `manual_collection`(`collection_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_collection_item_collection_id` ON `manual_collection_item` (`collection_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_collection_item_media_uri_media_date_added` ON `manual_collection_item` (`media_uri`, `media_date_added`)")
        }
    }
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

    fun observeCollections(): Flow<List<ManualCollectionSummary>> = dao.observeCollections()

    suspend fun createCollection(name: String): Long {
        val normalized = ManualCollectionNames.normalize(name)
        require(dao.getCollectionByName(normalized.key) == null) { "A collection with that name already exists" }
        val now = System.currentTimeMillis()
        return dao.insertCollection(ManualCollectionRow(name = normalized.display, normalizedName = normalized.key, createdAt = now, updatedAt = now))
    }

    suspend fun createCollection(name: String, media: Collection<ManualCollectionMedia>): Pair<Long, Int> {
        val normalized = ManualCollectionNames.normalize(name)
        require(dao.getCollectionByName(normalized.key) == null) { "A collection with that name already exists" }
        val now = System.currentTimeMillis()
        val collection = ManualCollectionRow(name = normalized.display, normalizedName = normalized.key, createdAt = now, updatedAt = now)
        val items = media.asSequence().distinctBy { it.uri.toString() }
            .map { ManualCollectionItemRow(0, it.uri.toString(), it.dateAdded, now) }.toList()
        return dao.insertCollectionWithItems(collection, items)
    }

    suspend fun renameCollection(id: Long, name: String) {
        val normalized = ManualCollectionNames.normalize(name)
        val existing = dao.getCollectionByName(normalized.key)
        require(existing == null || existing.id == id) { "A collection with that name already exists" }
        require(dao.renameCollection(id, normalized.display, normalized.key, System.currentTimeMillis()) == 1) { "Collection no longer exists" }
    }

    suspend fun deleteCollection(id: Long) {
        dao.deleteCollection(id)
    }

    suspend fun collectionItems(id: Long): Set<ManualCollectionMedia> = dao.getCollectionItems(id)
        .mapTo(linkedSetOf()) { ManualCollectionMedia(Uri.parse(it.mediaUri), it.mediaDateAdded) }

    suspend fun addToCollection(id: Long, media: Collection<ManualCollectionMedia>): Int {
        val now = System.currentTimeMillis()
        val rows = media.asSequence().distinctBy { it.uri.toString() }
            .map { ManualCollectionItemRow(id, it.uri.toString(), it.dateAdded, now) }.toList()
        if (rows.isEmpty()) return 0
        return dao.insertCollectionItems(rows).count { it != -1L }
    }

    suspend fun removeFromCollection(id: Long, uris: Collection<Uri>): Int {
        val values = uris.asSequence().map(Uri::toString).distinct().toList()
        return if (values.isEmpty()) 0 else dao.deleteCollectionItems(id, values)
    }

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
                    .addMigrations(ScreenshotIndexMigrations.MIGRATION_1_2)
                    .build()
                    .also {
                        INSTANCE = it
                        // Remove the obsolete raw-SQLite database file from pre-Room versions.
                        runCatching { context.deleteDatabase(LEGACY_DB_NAME) }
                    }
            }
    }
}
