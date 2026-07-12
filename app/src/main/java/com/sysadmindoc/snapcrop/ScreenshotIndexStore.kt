package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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
    val mimeType: String = "",
    val ownerPackage: String = "",
    val orientation: String = mediaOrientation(width, height),
    val indexedAt: Long = System.currentTimeMillis()
)

internal fun mediaOrientation(width: Int, height: Int): String = when {
    width <= 0 || height <= 0 -> "unknown"
    width == height -> "square"
    width > height -> "landscape"
    else -> "portrait"
}

private fun normalizeIndexMime(value: String?): String = value.orEmpty()
    .substringBefore(';')
    .trim()
    .lowercase(Locale.ROOT)

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
        isFavorite: Boolean,
        mimeType: String = "",
        ownerPackage: String = ""
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
            append(normalizeIndexMime(mimeType)).append(' ')
            append(ownerPackage).append(' ')
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
            searchText = searchText,
            mimeType = normalizeIndexMime(mimeType),
            ownerPackage = ownerPackage.trim(),
            orientation = mediaOrientation(width, height)
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

@Entity(
    tableName = "screenshot_index",
    indices = [
        Index(value = ["media_id"]),
        Index(value = ["date_added", "uri"]),
        Index(value = ["is_video", "date_added"]),
        Index(value = ["album_path", "date_added"]),
        Index(value = ["owner_package", "date_added"]),
        Index(value = ["mime_type", "date_added"]),
        Index(value = ["orientation", "date_added"]),
        Index(value = ["is_favorite", "date_added"]),
        Index(value = ["width", "height"])
    ]
)
internal data class ScreenshotIndexRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @PrimaryKey @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "album_path") val albumPath: String,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    @ColumnInfo(name = "is_screenshot") val isScreenshot: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "base_categories") val baseCategories: String,
    @ColumnInfo(name = "recognized_categories") val recognizedCategories: String = "",
    @ColumnInfo(name = "base_search_text") val baseSearchText: String,
    @ColumnInfo(name = "recognized_text") val recognizedText: String = "",
    @ColumnInfo(name = "mime_type") val mimeType: String = "",
    @ColumnInfo(name = "owner_package") val ownerPackage: String = "",
    @ColumnInfo(name = "orientation") val orientation: String = "unknown",
    @ColumnInfo(name = "indexed_at") val indexedAt: Long = System.currentTimeMillis()
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

@Entity(
    tableName = "media_source_context",
    primaryKeys = ["media_uri", "media_date_added"],
    indices = [Index("media_uri")]
)
internal data class MediaSourceContextRow(
    @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "media_date_added") val mediaDateAdded: Long,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
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
    @Query("SELECT * FROM screenshot_index ORDER BY date_added DESC, uri DESC")
    suspend fun getAll(): List<ScreenshotIndexRow>

    @Query("SELECT * FROM screenshot_index ORDER BY date_added DESC, uri DESC")
    fun observeAll(): Flow<List<ScreenshotIndexRow>>

    @Query("SELECT * FROM screenshot_index WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): ScreenshotIndexRow?

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
        val recognizedByUri = getAll().associateBy { it.uri }
        deleteAll()
        upsertAll(rows.map { row ->
            recognizedByUri[row.uri]?.takeIf { it.dateAdded == row.dateAdded }?.let { previous ->
                row.copy(
                    recognizedCategories = previous.recognizedCategories,
                    recognizedText = previous.recognizedText
                )
            } ?: row
        })
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

    @Query("SELECT * FROM media_source_context ORDER BY updated_at DESC")
    fun observeSourceContexts(): Flow<List<MediaSourceContextRow>>

    @Query("SELECT * FROM media_source_context WHERE media_uri = :uri AND media_date_added = :dateAdded LIMIT 1")
    suspend fun getSourceContext(uri: String, dateAdded: Long): MediaSourceContextRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceContext(row: MediaSourceContextRow)

    @Query("DELETE FROM media_source_context WHERE media_uri = :uri AND media_date_added = :dateAdded")
    suspend fun deleteSourceContext(uri: String, dateAdded: Long): Int

    @Query("DELETE FROM media_source_context WHERE media_uri IN (:uris)")
    suspend fun deleteSourceContexts(uris: List<String>): Int
}

@Database(
    entities = [
        ScreenshotIndexRow::class,
        ManualCollectionRow::class,
        ManualCollectionItemRow::class,
        MediaSourceContextRow::class
    ],
    version = 4,
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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `screenshot_index_v3` (
                    `media_id` INTEGER NOT NULL,
                    `uri` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `album_path` TEXT NOT NULL,
                    `width` INTEGER NOT NULL,
                    `height` INTEGER NOT NULL,
                    `date_added` INTEGER NOT NULL,
                    `size` INTEGER NOT NULL,
                    `is_video` INTEGER NOT NULL,
                    `is_screenshot` INTEGER NOT NULL,
                    `is_favorite` INTEGER NOT NULL,
                    `base_categories` TEXT NOT NULL,
                    `recognized_categories` TEXT NOT NULL,
                    `base_search_text` TEXT NOT NULL,
                    `recognized_text` TEXT NOT NULL,
                    `mime_type` TEXT NOT NULL,
                    `owner_package` TEXT NOT NULL,
                    `orientation` TEXT NOT NULL,
                    `indexed_at` INTEGER NOT NULL,
                    PRIMARY KEY(`uri`))""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO `screenshot_index_v3` (
                    `media_id`, `uri`, `name`, `album_path`, `width`, `height`,
                    `date_added`, `size`, `is_video`, `is_screenshot`, `is_favorite`,
                    `base_categories`, `recognized_categories`, `base_search_text`,
                    `recognized_text`, `mime_type`, `owner_package`, `orientation`, `indexed_at`)
                    SELECT `media_id`, `uri`, `name`, `album_path`, `width`, `height`,
                    `date_added`, `size`, `is_video`, `is_screenshot`, `is_favorite`,
                    `categories`, `categories`, `search_text`, SUBSTR(`search_text`, 1, 8192),
                    '', '', CASE
                        WHEN `width` <= 0 OR `height` <= 0 THEN 'unknown'
                        WHEN `width` = `height` THEN 'square'
                        WHEN `width` > `height` THEN 'landscape'
                        ELSE 'portrait' END,
                    `indexed_at` FROM `screenshot_index`""".trimIndent()
            )
            db.execSQL("DROP TABLE `screenshot_index`")
            db.execSQL("ALTER TABLE `screenshot_index_v3` RENAME TO `screenshot_index`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_media_id` ON `screenshot_index` (`media_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_date_added_uri` ON `screenshot_index` (`date_added`, `uri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_is_video_date_added` ON `screenshot_index` (`is_video`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_album_path_date_added` ON `screenshot_index` (`album_path`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_owner_package_date_added` ON `screenshot_index` (`owner_package`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_mime_type_date_added` ON `screenshot_index` (`mime_type`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_orientation_date_added` ON `screenshot_index` (`orientation`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_is_favorite_date_added` ON `screenshot_index` (`is_favorite`, `date_added`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshot_index_width_height` ON `screenshot_index` (`width`, `height`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `media_source_context` (
                    `media_uri` TEXT NOT NULL,
                    `media_date_added` INTEGER NOT NULL,
                    `source_url` TEXT,
                    `source_label` TEXT,
                    `source_package` TEXT,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`media_uri`, `media_date_added`))""".trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_source_context_media_uri` ON `media_source_context` (`media_uri`)")
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
        favoriteKeys: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val entries = buildList {
            addAll(queryImages(resolver, screenW, screenH, favoriteKeys))
            addAll(queryVideos(resolver, favoriteKeys))
        }
        dao.replaceAll(entries.map { it.toRow() })
        entries.size
    }

    suspend fun loadEntryMap(): Map<String, ScreenshotIndexEntry> =
        dao.getAll().associate { it.uri to it.toEntry() }

    /** Emits the full index whenever it changes (rebuild, OCR token capture, purge). */
    fun observeEntries(): Flow<Map<String, ScreenshotIndexEntry>> =
        dao.observeAll().map { rows -> rows.associate { it.uri to it.toEntry() } }

    suspend fun purge() {
        dao.deleteAll()
    }

    suspend fun updateRecognizedText(uri: Uri, text: String, codes: List<String>) {
        val existing = dao.getByUri(uri.toString()) ?: return
        val recognizedText = (listOf(text) + codes).joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_RECOGNIZED_TEXT_CHARS)
        val categories = linkedSetOf<String>()
        if (codes.isNotEmpty()) categories.add("codes")
        if (SensitiveTextPatterns.containsSensitivePattern(recognizedText)) categories.add("sensitive")
        val updated = existing.copy(
            recognizedCategories = categories.joinToString(","),
            recognizedText = recognizedText.lowercase(Locale.ROOT),
            indexedAt = System.currentTimeMillis()
        )
        dao.upsert(updated)
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

    fun observeSourceContexts(): Flow<Map<Pair<String, Long>, ExplicitSourceContext>> =
        dao.observeSourceContexts().map { rows ->
            rows.mapNotNull { row ->
                ExplicitSourceContext(row.sourceUrl, row.sourceLabel, row.sourcePackage)
                    .normalizedOrNull()
                    ?.let { (row.mediaUri to row.mediaDateAdded) to it }
            }.toMap()
        }

    suspend fun sourceContext(uri: Uri, dateAdded: Long): ExplicitSourceContext? =
        dao.getSourceContext(uri.toString(), dateAdded)?.let { row ->
            ExplicitSourceContext(row.sourceUrl, row.sourceLabel, row.sourcePackage).normalizedOrNull()
        }

    suspend fun putSourceContext(uri: Uri, dateAdded: Long, context: ExplicitSourceContext?) {
        require(uri.scheme.equals("content", ignoreCase = true) && uri.toString().length <= 8_192)
        require(dateAdded >= 0)
        val normalized = context?.normalizedOrNull()
        if (normalized == null) {
            dao.deleteSourceContext(uri.toString(), dateAdded)
        } else {
            dao.upsertSourceContext(
                MediaSourceContextRow(
                    mediaUri = uri.toString(),
                    mediaDateAdded = dateAdded,
                    sourceUrl = normalized.url,
                    sourceLabel = normalized.label,
                    sourcePackage = normalized.packageName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSourceContexts(uris: Collection<Uri>): Int {
        val values = uris.map(Uri::toString).distinct()
        return if (values.isEmpty()) 0 else dao.deleteSourceContexts(values)
    }

    private fun queryImages(
        resolver: ContentResolver,
        screenW: Int,
        screenH: Int,
        favoriteKeys: Set<String>
    ): List<ScreenshotIndexEntry> {
        val entries = mutableListOf<ScreenshotIndexEntry>()
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
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
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
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
                        isFavorite = FavoritesStore.isFavoriteKey(favoriteKeys, uri, id, isVideo = false),
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                        ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else ""
                    )
                )
            }
        }
        return entries
    }

    private fun queryVideos(
        resolver: ContentResolver,
        favoriteKeys: Set<String>
    ): List<ScreenshotIndexEntry> {
        val entries = mutableListOf<ScreenshotIndexEntry>()
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
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
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
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
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateCol),
                        size = cursor.getLong(sizeCol),
                        isVideo = true,
                        isScreenshot = false,
                        isFavorite = FavoritesStore.isFavoriteKey(favoriteKeys, uri, id, isVideo = true),
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                        ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else ""
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
        baseCategories = categories.joinToString(","),
        baseSearchText = searchText,
        mimeType = mimeType,
        ownerPackage = ownerPackage,
        orientation = orientation,
        indexedAt = indexedAt
    )

    private fun ScreenshotIndexRow.toEntry(): ScreenshotIndexEntry {
        val categories = (baseCategories.split(',') + recognizedCategories.split(','))
            .filter { it.isNotBlank() }
            .toSet()
        return ScreenshotIndexEntry(
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
        categories = categories,
        searchText = "$baseSearchText $recognizedText ${categories.joinToString(" ")}".trim().lowercase(Locale.ROOT),
        mimeType = mimeType,
        ownerPackage = ownerPackage,
        orientation = orientation,
        indexedAt = indexedAt
    )
    }

    companion object {
        const val PREF_ENABLED = "screenshot_index_enabled"
        private const val MAX_RECOGNIZED_TEXT_CHARS = 8192
        private const val DB_NAME = "screenshot_index_room.db"
        private const val LEGACY_DB_NAME = "screenshot_index.db"

        @Volatile
        private var INSTANCE: ScreenshotIndexDatabase? = null

        private fun database(context: Context): ScreenshotIndexDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, ScreenshotIndexDatabase::class.java, DB_NAME)
                    .addMigrations(
                        ScreenshotIndexMigrations.MIGRATION_1_2,
                        ScreenshotIndexMigrations.MIGRATION_2_3,
                        ScreenshotIndexMigrations.MIGRATION_3_4
                    )
                    .build()
                    .also {
                        INSTANCE = it
                        // Remove the obsolete raw-SQLite database file from pre-Room versions.
                        runCatching { context.deleteDatabase(LEGACY_DB_NAME) }
                    }
            }
    }
}
