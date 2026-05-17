package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore

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

class ScreenshotIndexStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE screenshot_index (
                media_id INTEGER PRIMARY KEY,
                uri TEXT NOT NULL,
                name TEXT NOT NULL,
                album_path TEXT NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                date_added INTEGER NOT NULL,
                size INTEGER NOT NULL,
                is_video INTEGER NOT NULL,
                is_screenshot INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL,
                categories TEXT NOT NULL,
                search_text TEXT NOT NULL,
                indexed_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_screenshot_index_search ON screenshot_index(search_text)")
        db.execSQL("CREATE INDEX idx_screenshot_index_categories ON screenshot_index(categories)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS screenshot_index")
        onCreate(db)
    }

    fun rebuildFromMediaStore(
        resolver: ContentResolver,
        screenW: Int,
        screenH: Int,
        favoriteIds: Set<Long>
    ): Int {
        val entries = buildList {
            addAll(queryImages(resolver, screenW, screenH, favoriteIds))
            addAll(queryVideos(resolver, favoriteIds))
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, null, null)
            entries.forEach { entry -> db.insertWithOnConflict(TABLE, null, entry.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return entries.size
    }

    fun loadEntryMap(): Map<Long, ScreenshotIndexEntry> {
        val db = readableDatabase
        val entries = mutableMapOf<Long, ScreenshotIndexEntry>()
        db.query(TABLE, null, null, null, null, null, "date_added DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val entry = ScreenshotIndexEntry(
                    mediaId = cursor.getLong(cursor.getColumnIndexOrThrow("media_id")),
                    uri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow("uri"))),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    albumPath = cursor.getString(cursor.getColumnIndexOrThrow("album_path")),
                    width = cursor.getInt(cursor.getColumnIndexOrThrow("width")),
                    height = cursor.getInt(cursor.getColumnIndexOrThrow("height")),
                    dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow("date_added")),
                    size = cursor.getLong(cursor.getColumnIndexOrThrow("size")),
                    isVideo = cursor.getInt(cursor.getColumnIndexOrThrow("is_video")) == 1,
                    isScreenshot = cursor.getInt(cursor.getColumnIndexOrThrow("is_screenshot")) == 1,
                    isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1,
                    categories = cursor.getString(cursor.getColumnIndexOrThrow("categories"))
                        .split(',')
                        .filter { it.isNotBlank() }
                        .toSet(),
                    searchText = cursor.getString(cursor.getColumnIndexOrThrow("search_text")),
                    indexedAt = cursor.getLong(cursor.getColumnIndexOrThrow("indexed_at"))
                )
                entries[entry.mediaId] = entry
            }
        }
        return entries
    }

    fun purge() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun updateRecognizedText(uri: Uri, text: String, codes: List<String>) {
        val mediaId = try {
            ContentUris.parseId(uri)
        } catch (_: Exception) {
            return
        }
        val existing = loadEntryMap()[mediaId] ?: return
        val recognizedText = (listOf(text) + codes).joinToString(" ").trim()
        if (recognizedText.isBlank()) return

        val categories = existing.categories.toMutableSet()
        if (codes.isNotEmpty()) categories.add("codes")
        if (SensitiveTextPatterns.containsSensitivePattern(recognizedText)) categories.add("sensitive")
        val values = ContentValues().apply {
            put("categories", categories.joinToString(","))
            put("search_text", "${existing.searchText} $recognizedText ${categories.joinToString(" ")}".lowercase())
            put("indexed_at", System.currentTimeMillis())
        }
        writableDatabase.update(TABLE, values, "media_id = ?", arrayOf(mediaId.toString()))
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
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

    private fun ScreenshotIndexEntry.toValues(): ContentValues = ContentValues().apply {
        put("media_id", mediaId)
        put("uri", uri.toString())
        put("name", name)
        put("album_path", albumPath)
        put("width", width)
        put("height", height)
        put("date_added", dateAdded)
        put("size", size)
        put("is_video", if (isVideo) 1 else 0)
        put("is_screenshot", if (isScreenshot) 1 else 0)
        put("is_favorite", if (isFavorite) 1 else 0)
        put("categories", categories.joinToString(","))
        put("search_text", searchText)
        put("indexed_at", indexedAt)
    }

    companion object {
        const val PREF_ENABLED = "screenshot_index_enabled"
        private const val DB_NAME = "screenshot_index.db"
        private const val DB_VERSION = 1
        private const val TABLE = "screenshot_index"
    }
}
