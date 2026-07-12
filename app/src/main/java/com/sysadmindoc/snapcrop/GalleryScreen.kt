package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import android.content.ClipData
import android.graphics.Point
import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sysadmindoc.snapcrop.ui.theme.*
import android.content.ClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Album(
    val name: String,
    val path: String,
    val coverUri: Uri,
    val count: Int,
    val isSmart: Boolean = false,
    val subtitle: String = "",
    val isManual: Boolean = false
)
data class Photo(
    val id: Long, val uri: Uri, val dateAdded: Long,
    val name: String = "", val size: Long = 0,
    val isVideo: Boolean = false, val duration: Long = 0,
    val width: Int = 0, val height: Int = 0,
    val isScreenshot: Boolean = false,
    val albumPath: String = "",
    val mimeType: String = "",
    val ownerPackage: String = "",
    val sourceContext: ExplicitSourceContext? = null,
    val indexCategories: Set<String> = emptySet(),
    val indexText: String = ""
)

/** Full physical display size, ignoring system bars / cutouts — screenshots are
 *  captured at this resolution on stock Android. */
internal fun getScreenSize(context: Context): Pair<Int, Int> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(android.view.WindowManager::class.java)
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    } catch (_: Exception) { 0 to 0 }
}

/** Heuristic screenshot detection: filename hint OR dimensions matching the device
 *  screen (either orientation) within a small tolerance. */
internal fun looksLikeScreenshot(width: Int, height: Int, name: String, screenW: Int, screenH: Int): Boolean {
    if (name.contains("screenshot", ignoreCase = true)) return true
    if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) return false
    val maxImg = maxOf(width, height); val minImg = minOf(width, height)
    val maxScr = maxOf(screenW, screenH); val minScr = minOf(screenW, screenH)
    // ~2% tolerance covers status-bar trims and minor device-cutout differences.
    val tol = (maxScr * 0.02f).toInt().coerceAtLeast(4)
    return kotlin.math.abs(maxImg - maxScr) <= tol && kotlin.math.abs(minImg - minScr) <= tol
}

private enum class SortMode { DATE, NAME, SIZE }

private const val ALL_PHOTOS_PATH = "__ALL__"
private const val FAVORITES_PATH = "__FAVS__"
private const val SMART_ALBUM_PREFIX = "__SMART__:"
private const val MANUAL_COLLECTION_PREFIX = "__COLLECTION__:"

private fun manualCollectionPath(id: Long) = "$MANUAL_COLLECTION_PREFIX$id"
private fun manualCollectionId(path: String?): Long? =
    path?.takeIf { it.startsWith(MANUAL_COLLECTION_PREFIX) }
        ?.removePrefix(MANUAL_COLLECTION_PREFIX)?.toLongOrNull()

private data class SmartAlbumRule(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String> = emptyList(),
    val categories: Set<String> = emptySet()
) {
    val path: String = "$SMART_ALBUM_PREFIX$id"

    fun matches(photo: Photo): Boolean {
        if (photo.isVideo || !photo.isScreenshot) return false
        if (id == "screenshots") return true
        if (categories.any { it in photo.indexCategories }) return true
        val sourceText = "${photo.albumPath} ${photo.name}".lowercase()
        return keywords.any { sourceText.contains(it) }
    }
}

private val smartAlbumRules = listOf(
    SmartAlbumRule(
        id = "screenshots",
        title = "Screenshots",
        subtitle = "Detected by filename and screen-size signals"
    ),
    SmartAlbumRule(
        id = "chats",
        title = "Chats",
        subtitle = "Messages, WhatsApp, Telegram, Discord, Slack",
        categories = setOf("chats"),
        keywords = listOf(
            "message", "messages", "messenger", "whatsapp", "telegram", "signal",
            "discord", "slack", "teams", "sms", "chat", "conversation"
        )
    ),
    SmartAlbumRule(
        id = "games",
        title = "Games",
        subtitle = "Game and launcher screenshots",
        categories = setOf("games"),
        keywords = listOf(
            "game", "gaming", "steam", "xbox", "playstation", "nintendo",
            "minecraft", "roblox", "genshin", "pubg", "codm", "fortnite", "pokemon"
        )
    ),
    SmartAlbumRule(
        id = "sites",
        title = "Sites",
        subtitle = "Browser, Reddit, X/Twitter, YouTube, social pages",
        categories = setOf("sites"),
        keywords = listOf(
            "chrome", "browser", "firefox", "edge", "brave", "safari", "web", "site",
            "url", "reddit", "twitter", "x-twitter", "x.com", "youtube", "instagram",
            "tiktok", "facebook", "linkedin"
        )
    ),
    SmartAlbumRule(
        id = "documents",
        title = "Documents",
        subtitle = "Receipts, invoices, tickets, scans, notes",
        categories = setOf("documents")
    ),
    SmartAlbumRule(
        id = "codes",
        title = "Codes",
        subtitle = "QR, barcode, Wi-Fi, and authenticator screenshots",
        categories = setOf("codes")
    ),
    SmartAlbumRule(
        id = "payments",
        title = "Payments",
        subtitle = "Banking, receipts, orders, cards, invoices",
        categories = setOf("payments", "sensitive")
    )
)

private fun smartAlbumRuleFor(path: String): SmartAlbumRule? =
    smartAlbumRules.firstOrNull { it.path == path }

object FavoritesStore {
    private const val PREF_NAME = "snapcrop_favorites"
    private const val URI_PREFIX = "uri:"

    private fun uriKey(uri: Uri): String = "$URI_PREFIX$uri"

    internal fun isFavoriteKey(keys: Set<String>, uri: Uri, id: Long, isVideo: Boolean): Boolean =
        uriKey(uri) in keys || (!isVideo && id.toString() in keys)

    fun isFavorite(context: Context, photo: Photo): Boolean = isFavoriteKey(
        getAllKeys(context), photo.uri, photo.id, photo.isVideo
    )

    fun toggle(context: Context, photo: Photo): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys
        val isFav = isFavoriteKey(keys, photo.uri, photo.id, photo.isVideo)
        prefs.edit().apply {
            if (isFav) {
                remove(uriKey(photo.uri))
                if (!photo.isVideo) remove(photo.id.toString())
            } else {
                putBoolean(uriKey(photo.uri), true)
                if (!photo.isVideo) remove(photo.id.toString())
            }
        }.apply()
        return !isFav
    }

    fun getAllKeys(context: Context): Set<String> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all.keys

    fun favoriteUris(keys: Set<String>, photos: Collection<Photo>): Set<String> =
        photos.asSequence()
            .filter { isFavoriteKey(keys, it.uri, it.id, it.isVideo) }
            .map { it.uri.toString() }
            .toSet()

    fun removeAll(context: Context, uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            uris.forEach { uri ->
                remove(uriKey(uri))
                if (!uri.toString().contains("/video/")) {
                    runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { remove(it.toString()) }
                }
            }
        }.apply()
    }
}

internal fun Photo.withIndex(entry: ScreenshotIndexEntry?): Photo {
    if (entry == null || entry.uri != uri || entry.dateAdded != dateAdded) return this
    return copy(
        isScreenshot = entry.isScreenshot,
        mimeType = entry.mimeType.ifBlank { mimeType },
        ownerPackage = entry.ownerPackage.ifBlank { ownerPackage },
        indexCategories = entry.categories,
        indexText = entry.searchText
    )
}

private fun Photo.matchesGalleryQuery(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    return name.lowercase().contains(normalized) ||
            albumPath.lowercase().contains(normalized) ||
            indexText.contains(normalized) ||
            indexCategories.any { it.contains(normalized) }
}

internal fun filterCollectionPhotos(
    photos: List<Photo>,
    members: Set<ManualCollectionMedia>
): List<Photo> {
    val keys = members.mapTo(hashSetOf()) { it.uri.toString() to it.dateAdded }
    return photos.filter { (it.uri.toString() to it.dateAdded) in keys }
}

internal data class CollectionSelection(val media: List<ManualCollectionMedia>, val skipped: Int)

internal fun collectionSelection(photos: List<Photo>, selectedUris: Set<String>): CollectionSelection {
    val selected = photos.filter { it.uri.toString() in selectedUris }
    val supported = selected.filter { it.isScreenshot && !it.isVideo }
    return CollectionSelection(
        media = supported.map { ManualCollectionMedia(it.uri, it.dateAdded) },
        skipped = selected.size - supported.size
    )
}

@Composable
fun GalleryScreen(
    onOpenEditor: (Uri) -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onShareUris: (List<Uri>) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
    onExportPdf: (List<Uri>) -> Unit,
    onBatchResize: (List<Uri>) -> Unit,
    onBatchRename: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    imageAccess: MediaAccess = MediaAccess.FULL,
    videoAccess: MediaAccess = MediaAccess.FULL,
    onRequestImageAccess: () -> Unit = {},
    onRequestVideoAccess: () -> Unit = {},
    refreshKey: Int = 0 // increment to force refresh (e.g., after returning from editor)
) {
    val canReadImages = imageAccess != MediaAccess.NONE
    val canReadVideos = videoAccess != MediaAccess.NONE
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE) }
    val collectionStore = remember { ScreenshotIndexStore(context) }
    val (screenW, screenH) = remember { getScreenSize(context) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var smartAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var manualCollections by remember { mutableStateOf<List<ManualCollectionSummary>>(emptyList()) }
    var sourceContexts by remember { mutableStateOf<Map<Pair<String, Long>, ExplicitSourceContext>>(emptyMap()) }
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var indexEntries by remember { mutableStateOf<Map<String, ScreenshotIndexEntry>>(emptyMap()) }
    var indexEnabled by remember { mutableStateOf(prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) }
    var isLoading by remember { mutableStateOf(true) }
    var viewerIndex by remember { mutableIntStateOf(-1) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var favoriteKeys by remember { mutableStateOf(FavoritesStore.getAllKeys(context)) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DATE) }
    var gridColumns by rememberSaveable { mutableIntStateOf(3) }
    var encodedFilters by rememberSaveable { mutableStateOf(GalleryFilterState().encode()) }
    val galleryFilters = remember(encodedFilters) { GalleryFilterState.decode(encodedFilters) }
    var showFilters by remember { mutableStateOf(false) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>?>(null) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var collectionEditorId by remember { mutableStateOf<Long?>(null) }
    var collectionName by remember { mutableStateOf("") }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var collectionMutating by remember { mutableStateOf(false) }
    var addSelectionAfterCreate by remember { mutableStateOf(false) }
    var pendingCollectionSelection by remember { mutableStateOf<CollectionSelection?>(null) }
    var sourceEditorPhoto by remember { mutableStateOf<Photo?>(null) }
    val selectionMode = selectedUris.isNotEmpty()

    LaunchedEffect(Unit) {
        collectionStore.observeCollections().collect { manualCollections = it }
    }

    LaunchedEffect(Unit) {
        collectionStore.observeSourceContexts().collect { sourceContexts = it }
    }

    // Reload albums on initial load and when refreshKey changes (e.g., returning from editor)
    LaunchedEffect(refreshKey, indexEnabled, favoriteKeys, canReadImages, canReadVideos) {
        val refreshedCollections = withContext(Dispatchers.IO) {
            val index = if (indexEnabled) {
                val store = ScreenshotIndexStore(context)
                store.loadEntryMap()
            } else {
                emptyMap()
            }
            Triple(
                loadAlbums(context.contentResolver, canReadImages, canReadVideos),
                loadSmartAlbums(context.contentResolver, screenW, screenH, index, canReadImages),
                index
            )
        }
        albums = refreshedCollections.first
        smartAlbums = refreshedCollections.second
        indexEntries = refreshedCollections.third
        indexEnabled = prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
        // Photos for the open album are loaded by the single effect below (keyed on refreshKey),
        // so they are never written from two effects at once.
        if (selectedAlbum == null) isLoading = false
    }

    // Reactively follow the Room-backed index: rebuilds, OCR token capture, and purges all emit
    // here, refreshing smart-album membership and search without a manual reload.
    LaunchedEffect(indexEnabled) {
        if (!indexEnabled) return@LaunchedEffect
        ScreenshotIndexStore(context).observeEntries().collect { latest ->
            indexEntries = latest
        }
    }

    LaunchedEffect(selectedAlbum, manualCollections, refreshKey, canReadImages, canReadVideos) {
        selectedAlbum?.let { path ->
            isLoading = true
            selectedUris.clear()
            val loadedPhotos = withContext(Dispatchers.IO) {
                when (path) {
                    ALL_PHOTOS_PATH -> loadAllPhotos(context.contentResolver, screenW, screenH, emptyMap(), canReadImages, canReadVideos)
                    FAVORITES_PATH -> loadFavoritePhotos(context.contentResolver, FavoritesStore.getAllKeys(context), screenW, screenH, emptyMap(), canReadImages, canReadVideos)
                    else -> if (path.startsWith(MANUAL_COLLECTION_PREFIX)) {
                        val collectionId = manualCollectionId(path) ?: return@withContext emptyList()
                        val members = collectionStore.collectionItems(collectionId)
                        filterCollectionPhotos(
                            loadAllPhotos(context.contentResolver, screenW, screenH, emptyMap(), canReadImages, canReadVideos),
                            members
                        )
                    } else if (path.startsWith(SMART_ALBUM_PREFIX)) {
                        loadAllPhotos(context.contentResolver, screenW, screenH, emptyMap(), canReadImages, includeVideos = false)
                    } else {
                        loadPhotos(context.contentResolver, path, screenW, screenH, emptyMap(), canReadImages, canReadVideos)
                    }
                }
            }
            photos = loadedPhotos
            isLoading = false
        }
    }

    val enrichedPhotos = remember(photos, indexEntries, selectedAlbum, favoriteKeys, sourceContexts) {
        val enriched = photos.map { photo ->
            photo.withIndex(indexEntries[photo.uri.toString()]).copy(
                sourceContext = sourceContexts[photo.uri.toString() to photo.dateAdded]
            )
        }
        when (selectedAlbum) {
            FAVORITES_PATH -> enriched.filter { FavoritesStore.isFavoriteKey(favoriteKeys, it.uri, it.id, it.isVideo) }
            else -> smartAlbumRuleFor(selectedAlbum.orEmpty())?.let { rule -> enriched.filter(rule::matches) } ?: enriched
        }
    }
    val favoriteUris = remember(favoriteKeys, enrichedPhotos) {
        FavoritesStore.favoriteUris(favoriteKeys, enrichedPhotos)
    }
    val structuredPhotos = remember(enrichedPhotos, galleryFilters, favoriteUris) {
        applyGalleryFilters(
            enrichedPhotos,
            galleryFilters,
            favoriteUris,
            System.currentTimeMillis() / 1000L
        )
    }
    val sourceOptions = remember(enrichedPhotos) { buildGallerySourceOptions(context, enrichedPhotos) }
    val categoryOptions = remember(enrichedPhotos) {
        enrichedPhotos.flatMap(Photo::indexCategories).map { it.trim().lowercase() }
            .filter(String::isNotBlank).distinct().sorted()
    }
    val formatOptions = remember(enrichedPhotos) {
        enrichedPhotos.map(Photo::galleryFormat).distinct().sortedBy(GalleryFormat::name)
    }

    // Compute sorted photos at the top level so viewer uses same order as grid.
    val viewerPhotos = remember(structuredPhotos, sortMode, searchQuery, selectedAlbum) {
        val sorted = when (sortMode) {
            SortMode.DATE -> structuredPhotos.sortedWith(compareByDescending<Photo> { it.dateAdded }.thenByDescending { it.uri.toString() })
            SortMode.NAME -> structuredPhotos.sortedWith(compareBy<Photo> { it.name.lowercase() }.thenBy { it.uri.toString() })
            SortMode.SIZE -> structuredPhotos.sortedWith(compareByDescending<Photo> { it.size }.thenByDescending { it.uri.toString() })
        }
        if (selectedAlbum != null && searchQuery.isNotBlank()) {
            sorted.filter { it.matchesGalleryQuery(searchQuery) }
        } else {
            sorted
        }
    }

    LaunchedEffect(viewerPhotos.size) {
        if (viewerIndex >= viewerPhotos.size) {
            viewerIndex = if (viewerPhotos.isEmpty()) -1 else viewerPhotos.lastIndex
        }
    }

    LaunchedEffect(viewerPhotos) {
        val visibleUris = viewerPhotos.mapTo(hashSetOf()) { it.uri.toString() }
        selectedUris.retainAll(visibleUris)
    }

    if (showFilters) {
        val collectionSeed = collectionSelection(enrichedPhotos, viewerPhotos.mapTo(hashSetOf()) { it.uri.toString() })
        GalleryFilterDialog(
            state = galleryFilters,
            sourceOptions = sourceOptions,
            categoryOptions = categoryOptions,
            formatOptions = formatOptions,
            indexEnabled = indexEnabled,
            resultCount = viewerPhotos.size,
            eligibleCollectionCount = collectionSeed.media.size,
            skippedCollectionCount = collectionSeed.skipped,
            onChange = { encodedFilters = it.encode() },
            onSeedCollection = {
                pendingCollectionSelection = collectionSeed
                showFilters = false
                showCollectionPicker = true
            },
            onDismiss = { showFilters = false }
        )
    }

    sourceEditorPhoto?.let { photo ->
        SourceContextEditorDialog(
            initial = photo.sourceContext,
            onSave = { contextValue ->
                sourceEditorPhoto = null
                scope.launch {
                    runCatching { collectionStore.putSourceContext(photo.uri, photo.dateAdded, contextValue) }
                        .onSuccess {
                            Toast.makeText(context, R.string.source_context_saved, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, R.string.source_context_save_failed, Toast.LENGTH_LONG).show()
                        }
                }
            },
            onDismiss = { sourceEditorPhoto = null }
        )
    }

    pendingDeleteUris?.let { uris ->
        AlertDialog(
            onDismissRequest = { pendingDeleteUris = null },
            title = { Text(stringResource(R.string.gallery_trash_title), color = OnSurface) },
            text = {
                Text(
                    stringResource(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.gallery_trash_body
                        else R.string.gallery_trash_body_legacy,
                        uris.size
                    ),
                    color = OnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteUris = null
                        onDeleteUris(uris)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Tertiary)
                ) { Text(stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.move_to_trash
                    else R.string.delete_permanently
                )) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteUris = null }) {
                    Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceVariant
        )
    }

    collectionEditorId?.let { editorId ->
        val existingNames = manualCollections.filter { it.id != editorId }.map { it.name.trim().lowercase() }.toSet()
        val cleanName = collectionName.trim().replace(Regex("\\s+"), " ")
        val nameValid = cleanName.isNotEmpty() && cleanName.length <= 80 && cleanName.lowercase() !in existingNames
        AlertDialog(
            onDismissRequest = { if (!collectionMutating) { collectionEditorId = null; collectionError = null; addSelectionAfterCreate = false; pendingCollectionSelection = null } },
            title = { Text(stringResource(if (editorId == -1L) R.string.gallery_collection_create else R.string.gallery_collection_rename), color = OnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it; collectionError = null },
                        label = { Text(stringResource(R.string.gallery_collection_name)) },
                        singleLine = true,
                        isError = collectionError != null || (cleanName.isNotEmpty() && cleanName.lowercase() in existingNames),
                        supportingText = {
                            val message = collectionError ?: when {
                                cleanName.lowercase() in existingNames -> stringResource(R.string.gallery_collection_duplicate)
                                collectionName.length > 80 -> stringResource(R.string.gallery_collection_name_too_long)
                                else -> null
                            }
                            if (message != null) Text(message)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameValid && !collectionMutating,
                    onClick = {
                        collectionMutating = true
                        scope.launch {
                            try {
                                var resultMessage = context.getString(R.string.gallery_collection_saved)
                                if (editorId == -1L && addSelectionAfterCreate) {
                                    val selected = pendingCollectionSelection
                                        ?: collectionSelection(enrichedPhotos, selectedUris.toSet())
                                    val (_, added) = collectionStore.createCollection(cleanName, selected.media)
                                    resultMessage = context.getString(R.string.gallery_collection_added_result, added, selected.skipped)
                                    selectedUris.clear()
                                    pendingCollectionSelection = null
                                } else if (editorId == -1L) {
                                    collectionStore.createCollection(cleanName)
                                } else {
                                    collectionStore.renameCollection(editorId, cleanName)
                                }
                                Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
                                collectionEditorId = null
                                collectionError = null
                                addSelectionAfterCreate = false
                                pendingCollectionSelection = null
                            } catch (error: Exception) {
                                collectionError = error.message ?: context.getString(R.string.gallery_collection_failed)
                            } finally {
                                collectionMutating = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { collectionEditorId = null; collectionError = null; addSelectionAfterCreate = false; pendingCollectionSelection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = SurfaceVariant
        )
    }

    if (showCollectionPicker) {
        AlertDialog(
            onDismissRequest = { showCollectionPicker = false; pendingCollectionSelection = null },
            title = { Text(stringResource(R.string.gallery_add_to_collection), color = OnSurface) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    manualCollections.forEach { collection ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showCollectionPicker = false
                                scope.launch {
                                    try {
                                        val selected = pendingCollectionSelection
                                            ?: collectionSelection(enrichedPhotos, selectedUris.toSet())
                                        val added = collectionStore.addToCollection(collection.id, selected.media)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.gallery_collection_added_result, added, selected.skipped),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedUris.clear()
                                        pendingCollectionSelection = null
                                    } catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.gallery_collection_failed), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.gallery_collection_target, collection.name, collection.itemCount)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCollectionPicker = false
                    collectionName = ""
                    collectionError = null
                    addSelectionAfterCreate = true
                    collectionEditorId = -1L
                }) { Text(stringResource(R.string.gallery_collection_new)) }
            },
            dismissButton = {
                TextButton(onClick = { showCollectionPicker = false; pendingCollectionSelection = null }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = SurfaceVariant
        )
    }

    // Fullscreen viewer
    if (viewerIndex >= 0 && viewerPhotos.isNotEmpty()) {
        PhotoViewer(
            photos = viewerPhotos,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 },
            onEdit = { onOpenEditor(it.uri); viewerIndex = -1 },
            onShare = { onShareUris(listOf(it.uri)) },
            onEditSource = { sourceEditorPhoto = it },
            onOpenSource = { photo -> openExplicitSource(context, photo.sourceContext) },
            onDelete = { photo ->
                pendingDeleteUris = listOf(photo.uri)
            },
            onToggleFavorite = { photo ->
                val newState = FavoritesStore.toggle(context, photo)
                favoriteKeys = FavoritesStore.getAllKeys(context)
                newState
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                // Selection mode bar
                IconButton(onClick = { selectedUris.clear() }) {
                    Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = OnSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.gallery_selected_count, selectedUris.size), color = OnSurface, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.gallery_selected_actions), color = OnSurfaceVariant, fontSize = 11.sp)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    IconButton(onClick = {
                        selectedUris.clear()
                        selectedUris.addAll(viewerPhotos.map { it.uri.toString() })
                    }) { Icon(Icons.Default.SelectAll, stringResource(R.string.gallery_select_all), tint = OnSurface) }
                    IconButton(onClick = { pendingCollectionSelection = null; showCollectionPicker = true }) {
                        Icon(Icons.Default.PushPin, stringResource(R.string.gallery_add_to_collection), tint = Primary)
                    }
                    manualCollectionId(selectedAlbum)?.let { collectionId ->
                        IconButton(onClick = {
                            val selected = photos.filter { it.uri.toString() in selectedUris }.map { it.uri }
                            scope.launch {
                                try {
                                    val removed = collectionStore.removeFromCollection(collectionId, selected)
                                    selectedUris.clear()
                                    Toast.makeText(context, context.getString(R.string.gallery_collection_removed, removed), Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.gallery_collection_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.gallery_remove_from_collection), tint = Tertiary) }
                    }
                    IconButton(onClick = {
                        val uris = photos.filter { it.uri.toString() in selectedUris }.map { it.uri }
                        onShareUris(uris)
                    }) { Icon(Icons.Default.Share, stringResource(R.string.gallery_share), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.uri.toString() in selectedUris && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onExportPdf(uris); selectedUris.clear() }
                    }) { Icon(Icons.Default.PictureAsPdf, stringResource(R.string.gallery_export_pdf), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.uri.toString() in selectedUris && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onBatchRename(uris); selectedUris.clear() }
                    }) { Icon(Icons.Default.SortByAlpha, stringResource(R.string.gallery_rename), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.uri.toString() in selectedUris && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onBatchResize(uris); selectedUris.clear() }
                    }) { Icon(Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.resize), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.uri.toString() in selectedUris }.map { it.uri }
                        if (uris.isNotEmpty()) pendingDeleteUris = uris
                    }) { Icon(Icons.Default.Delete, stringResource(R.string.gallery_delete_selected), tint = Tertiary) }
                }
            } else {
                IconButton(onClick = {
                    if (selectedAlbum != null) { selectedAlbum = null; photos = emptyList(); searchQuery = "" }
                    else onBack()
                }) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = OnSurface)
                }
                Text(
                    text = when (selectedAlbum) {
                        ALL_PHOTOS_PATH -> stringResource(R.string.gallery_all_photos)
                        FAVORITES_PATH -> stringResource(R.string.gallery_favorites)
                        null -> stringResource(R.string.gallery_title)
                        else -> manualCollectionId(selectedAlbum)?.let { id ->
                            manualCollections.firstOrNull { it.id == id }?.name
                                ?: stringResource(R.string.gallery_collection)
                        } ?: smartAlbumRuleFor(selectedAlbum!!)?.title
                            ?: selectedAlbum!!.trimEnd('/').substringAfterLast("/")
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedAlbum == null) {
                    Text(stringResource(R.string.gallery_photo_count, albums.sumOf { it.count }), color = OnSurfaceVariant,
                        fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    IconButton(onClick = {
                        selectedAlbum = ALL_PHOTOS_PATH
                        showFilters = true
                    }) {
                        Icon(Icons.Default.FilterList, stringResource(R.string.gallery_filters_inactive), tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = {
                        collectionName = ""
                        collectionError = null
                        addSelectionAfterCreate = false
                        collectionEditorId = -1L
                    }) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.gallery_collection_new), tint = Primary)
                    }
                } else {
                    manualCollectionId(selectedAlbum)?.let { collectionId ->
                        val collection = manualCollections.firstOrNull { it.id == collectionId }
                        IconButton(onClick = {
                            collectionName = collection?.name.orEmpty()
                            collectionError = null
                            addSelectionAfterCreate = false
                            collectionEditorId = collectionId
                        }) { Icon(Icons.Default.Edit, stringResource(R.string.gallery_collection_rename), tint = OnSurfaceVariant) }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    collectionStore.deleteCollection(collectionId)
                                    selectedAlbum = null
                                    photos = emptyList()
                                    selectedUris.clear()
                                    Toast.makeText(context, context.getString(R.string.gallery_collection_deleted), Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.gallery_collection_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.gallery_collection_delete), tint = Tertiary) }
                    }
                    // Photo count
                    Text("${viewerPhotos.size}", color = OnSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp))
                    val filterDescription = if (galleryFilters.activeCount > 0) {
                        stringResource(R.string.gallery_filters_active, galleryFilters.activeCount)
                    } else {
                        stringResource(R.string.gallery_filters_inactive)
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(
                            badge = {
                                if (galleryFilters.activeCount > 0) {
                                    Badge { Text(galleryFilters.activeCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, filterDescription, tint = OnSurfaceVariant)
                        }
                    }
                    // One-tap "select all screenshots" — drops user into selection mode with
                    // every dimension-matching image pre-selected, ready for bulk delete.
                    val screenshotCount = viewerPhotos.count { it.isScreenshot && !FavoritesStore.isFavoriteKey(favoriteKeys, it.uri, it.id, it.isVideo) }
                    if (screenshotCount > 0) {
                        TextButton(
                            onClick = {
                            selectedUris.clear()
                            selectedUris.addAll(viewerPhotos.filter { it.isScreenshot && !FavoritesStore.isFavoriteKey(favoriteKeys, it.uri, it.id, it.isVideo) }.map { it.uri.toString() })
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid,
                                null, tint = Tertiary,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.gallery_cleanup, screenshotCount), color = Tertiary, fontSize = 11.sp,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    // Sort button when viewing photos
                    val sortLabel = when (sortMode) {
                        SortMode.DATE -> stringResource(R.string.gallery_sort_date)
                        SortMode.NAME -> stringResource(R.string.gallery_sort_name)
                        SortMode.SIZE -> stringResource(R.string.gallery_sort_size)
                    }
                    IconButton(onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DATE -> SortMode.NAME
                            SortMode.NAME -> SortMode.SIZE
                            SortMode.SIZE -> SortMode.DATE
                        }
                    }) {
                        Icon(Icons.Default.SortByAlpha, stringResource(R.string.gallery_sort, sortLabel), tint = OnSurfaceVariant)
                    }
                }
            }
        }

        if (!selectionMode && (imageAccess != MediaAccess.FULL || videoAccess != MediaAccess.FULL)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (imageAccess != MediaAccess.FULL) {
                    GalleryCapabilityBanner(
                        title = stringResource(if (imageAccess == MediaAccess.SELECTED) R.string.gallery_images_partial_title else R.string.gallery_images_denied_title),
                        body = stringResource(if (imageAccess == MediaAccess.SELECTED) R.string.gallery_images_partial_body else R.string.gallery_images_denied_body),
                        action = stringResource(if (imageAccess == MediaAccess.SELECTED) R.string.gallery_manage_selection else R.string.gallery_allow_images),
                        onClick = onRequestImageAccess
                    )
                }
                if (videoAccess != MediaAccess.FULL) {
                    GalleryCapabilityBanner(
                        title = stringResource(if (videoAccess == MediaAccess.SELECTED) R.string.gallery_videos_partial_title else R.string.gallery_videos_denied_title),
                        body = stringResource(if (videoAccess == MediaAccess.SELECTED) R.string.gallery_videos_partial_body else R.string.gallery_videos_denied_body),
                        action = stringResource(if (videoAccess == MediaAccess.SELECTED) R.string.gallery_manage_selection else R.string.gallery_allow_videos),
                        onClick = onRequestVideoAccess
                    )
                }
            }
        }

        // Search bar
        if (!selectionMode && ((selectedAlbum == null && (albums.isNotEmpty() || smartAlbums.isNotEmpty())) || selectedAlbum != null)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedAlbum == null) stringResource(R.string.gallery_search_albums)
                        else stringResource(R.string.gallery_search_photos),
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                    focusedTextColor = OnSurface, unfocusedTextColor = OnSurface,
                    cursorColor = Primary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (!selectionMode && selectedAlbum != null && galleryFilters.activeCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.gallery_filters_active, galleryFilters.activeCount) +
                        " · " + stringResource(R.string.gallery_filter_result_count, viewerPhotos.size),
                    modifier = Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
                TextButton(onClick = { encodedFilters = GalleryFilterState().encode() }) {
                    Text(stringResource(R.string.gallery_filter_clear_all))
                }
            }
        }

        val filteredAlbums = if (searchQuery.isBlank()) albums
            else albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val filteredSmartAlbums = if (searchQuery.isBlank()) smartAlbums
            else smartAlbums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.subtitle.contains(searchQuery, ignoreCase = true)
            }
        val manualCollectionSubtitle = stringResource(R.string.gallery_collection_subtitle)
        val collectionAlbums = manualCollections.map { collection ->
            Album(
                name = collection.name,
                path = manualCollectionPath(collection.id),
                coverUri = collection.coverUri?.let(Uri::parse) ?: Uri.EMPTY,
                count = collection.itemCount,
                subtitle = manualCollectionSubtitle,
                isManual = true
            )
        }.let { values ->
            if (searchQuery.isBlank()) values else values.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.gallery_loading), color = OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else if (selectedAlbum == null) {
            AlbumGrid(albums = filteredAlbums, smartAlbums = filteredSmartAlbums, manualAlbums = collectionAlbums,
                showLibraryCards = searchQuery.isBlank(),
                totalMediaCount = albums.sumOf { it.count },
                onAlbumClick = { searchQuery = ""; selectedAlbum = it.path },
                onAllPhotos = { searchQuery = ""; selectedAlbum = ALL_PHOTOS_PATH },
                onFavorites = { searchQuery = ""; selectedAlbum = FAVORITES_PATH },
                favCount = favoriteKeys.size,
                emptyTitle = if (searchQuery.isBlank()) stringResource(R.string.gallery_empty_title) else stringResource(R.string.gallery_search_empty_title),
                emptySubtitle = if (searchQuery.isBlank()) stringResource(R.string.gallery_empty_subtitle)
                    else stringResource(R.string.gallery_search_empty_subtitle))
        } else {
            PhotoGrid(
                photos = viewerPhotos,
                columns = gridColumns,
                showDateHeaders = sortMode == SortMode.DATE,
                emptyTitle = if (manualCollectionId(selectedAlbum) != null) stringResource(R.string.gallery_collection_empty_title) else stringResource(R.string.gallery_no_media_title),
                emptySubtitle = if (manualCollectionId(selectedAlbum) != null) stringResource(R.string.gallery_collection_empty_subtitle) else stringResource(R.string.gallery_no_media_subtitle),
                selectedUris = selectedUris,
                selectionMode = selectionMode,
                onPhotoClick = { photo, index ->
                    if (selectionMode) toggleSelection(selectedUris, photo.uri.toString())
                    else if (photo.isVideo) onPlayVideo(photo.uri)
                    else viewerIndex = index
                },
                onPhotoLongClick = { photo -> toggleSelection(selectedUris, photo.uri.toString()) },
                onPinchZoom = { zoom ->
                    if (zoom < 0.8f) gridColumns = (gridColumns + 1).coerceAtMost(6)
                    else if (zoom > 1.2f) gridColumns = (gridColumns - 1).coerceAtLeast(2)
                }
            )
        }
    }
}

@Composable
private fun GalleryCapabilityBanner(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(body, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
            }
            TextButton(onClick = onClick) { Text(action, color = Primary, fontSize = 12.sp) }
        }
    }
}

private fun toggleSelection(selectedUris: MutableList<String>, uri: String) {
    if (uri in selectedUris) selectedUris.remove(uri) else selectedUris.add(uri)
}

private fun buildGallerySourceOptions(context: Context, photos: List<Photo>): List<GallerySourceOption> =
    photos.asSequence()
        .filter { it.sourceKey.isNotBlank() }
        .distinctBy(Photo::sourceKey)
        .map { photo ->
            val label = if (photo.ownerPackage.isNotBlank()) {
                @Suppress("DEPRECATION")
                runCatching {
                    val info = context.packageManager.getApplicationInfo(photo.ownerPackage, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(photo.ownerPackage)
            } else {
                photo.albumPath.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
                    .ifBlank { photo.sourceKey }
            }
            GallerySourceOption(photo.sourceKey, label)
        }
        .sortedBy { it.label.lowercase() }
        .toList()

private fun openExplicitSource(context: Context, sourceContext: ExplicitSourceContext?) {
    val uri = sourceContext?.openUri ?: return
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
    try {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else Toast.makeText(context, R.string.source_context_open_failed, Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, R.string.source_context_open_failed, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun GalleryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = SurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                    tint = Primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = OnSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    smartAlbums: List<Album>,
    manualAlbums: List<Album>,
    showLibraryCards: Boolean,
    totalMediaCount: Int,
    onAlbumClick: (Album) -> Unit,
    onAllPhotos: () -> Unit,
    onFavorites: () -> Unit,
    favCount: Int,
    emptyTitle: String,
    emptySubtitle: String
) {
    if (albums.isEmpty() && smartAlbums.isEmpty() && manualAlbums.isEmpty() && (!showLibraryCards || favCount == 0)) {
        GalleryEmptyState(
            icon = Icons.Default.Photo,
            title = emptyTitle,
            subtitle = emptySubtitle
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLibraryCards) {
            // "All Photos" card first
            item(key = "library-all-photos", contentType = "library-card") {
                val allPhotosLabel = stringResource(R.string.gallery_all_photos)
                val allPhotosCd = stringResource(R.string.gallery_album_cd, allPhotosLabel, totalMediaCount)
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .semantics { contentDescription = allPhotosCd }
                        .clickable { onAllPhotos() },
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Photo, null, Modifier.size(40.dp), tint = Primary)
                            Spacer(Modifier.height(8.dp))
                            Text(allPhotosLabel, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("$totalMediaCount", color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Favorites card
            if (favCount > 0) {
                item(key = "library-favorites", contentType = "library-card") {
                    val favoritesLabel = stringResource(R.string.gallery_favorites)
                    val favoritesCd = stringResource(R.string.gallery_album_cd, favoritesLabel, favCount)
                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .semantics { contentDescription = favoritesCd }
                            .clickable { onFavorites() },
                        colors = CardDefaults.cardColors(containerColor = Tertiary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Favorite, null, Modifier.size(40.dp), tint = Tertiary)
                                Spacer(Modifier.height(8.dp))
                                Text(favoritesLabel, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("$favCount", color = OnSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (manualAlbums.isNotEmpty()) {
            item(key = "manual-collections-header", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                Text(
                    stringResource(R.string.gallery_collections),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(manualAlbums, key = { it.path }, contentType = { "manual-collection" }) { album ->
                val albumCd = stringResource(R.string.gallery_collection_cd, album.name, album.count)
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .semantics { contentDescription = albumCd }
                        .clickable { onAlbumClick(album) },
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (album.coverUri != Uri.EMPTY) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(album.coverUri).crossfade(true).size(300, 300).build(),
                                contentDescription = album.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Folder, null, tint = Primary, modifier = Modifier.size(42.dp).align(Alignment.Center))
                        }
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.68f)).padding(8.dp)) {
                            Column {
                                Text(album.name, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.gallery_collection_items, album.count), color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (smartAlbums.isNotEmpty()) {
            item(key = "smart-albums-header", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                Text(
                    stringResource(R.string.gallery_smart_albums),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(smartAlbums, key = { it.path }, contentType = { "smart-album" }) { album ->
                val smartAlbumCd = stringResource(R.string.gallery_smart_album_cd, album.name, album.count, album.subtitle)
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .semantics { contentDescription = smartAlbumCd }
                        .clickable { onAlbumClick(album) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.coverUri).crossfade(true).size(300, 300).build(),
                            contentDescription = album.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = Tertiary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.gallery_auto_badge), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.68f)).padding(8.dp)) {
                            Column {
                                Text(album.name, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(album.subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                                Text(stringResource(R.string.gallery_matched_count, album.count), color = Tertiary, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty() && smartAlbums.isNotEmpty()) {
            item(key = "albums-header", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                Text(
                    stringResource(R.string.gallery_albums),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        items(albums, key = { it.path }, contentType = { "album" }) { album ->
            val albumCd = stringResource(R.string.gallery_album_cd, album.name, album.count)
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .semantics { contentDescription = albumCd }
                    .clickable { onAlbumClick(album) },
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverUri).crossfade(true).size(300, 300).build(),
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(8.dp)) {
                        Column {
                            Text(album.name, color = OnMediaSurface, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.count}", color = OnMediaSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    columns: Int,
    showDateHeaders: Boolean = false,
    emptyTitle: String,
    emptySubtitle: String,
    selectedUris: List<String>,
    selectionMode: Boolean,
    onPhotoClick: (Photo, Int) -> Unit,
    onPhotoLongClick: (Photo) -> Unit,
    onPinchZoom: (Float) -> Unit
) {
    if (photos.isEmpty()) {
        GalleryEmptyState(
            icon = Icons.Default.Photo,
            title = emptyTitle,
            subtitle = emptySubtitle
        )
        return
    }

    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
    val selectedUriSet = selectedUris.toSet()
    val indexByUri = remember(photos) { photos.withIndex().associate { (i, p) -> p.uri.toString() to i } }
    val groupedByDate = remember(photos, showDateHeaders) {
        if (showDateHeaders) {
            photos.groupBy { dateFormat.format(java.util.Date(it.dateAdded * 1000)) }
        } else {
            emptyMap()
        }
    }

    var lastPinchZoom by remember { mutableFloatStateOf(1f) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { _, _, zoom, _ ->
                lastPinchZoom *= zoom
                if (lastPinchZoom < 0.7f || lastPinchZoom > 1.4f) {
                    onPinchZoom(lastPinchZoom)
                    lastPinchZoom = 1f
                }
            }
        }
    ) {
        // Date section headers (full-span)
        if (showDateHeaders) {
            groupedByDate.forEach { (date, datePhotos) ->
                item(key = "date-$date", span = { GridItemSpan(maxLineSpan) }, contentType = "date-header") {
                    Text(date, color = OnSurfaceVariant, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
                items(
                    count = datePhotos.size,
                    key = { i -> datePhotos[i].uri },
                    contentType = { "photo" }
                ) { i ->
                    val photo = datePhotos[i]
                    val globalIdx = indexByUri[photo.uri.toString()] ?: 0
                    val isSelected = photo.uri.toString() in selectedUriSet
                    PhotoItem(photo, globalIdx, isSelected, selectionMode, onPhotoClick, onPhotoLongClick)
                }
            }
        } else {
            items(
                count = photos.size,
                key = { index -> photos[index].uri },
                contentType = { "photo" }
            ) { index ->
                val photo = photos[index]
                val isSelected = photo.uri.toString() in selectedUriSet
                PhotoItem(photo, index, isSelected, selectionMode, onPhotoClick, onPhotoLongClick)
            }
        } // else no date headers
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoItem(
    photo: Photo, index: Int, isSelected: Boolean, selectionMode: Boolean,
    onPhotoClick: (Photo, Int) -> Unit, onPhotoLongClick: (Photo) -> Unit
) {
    val mediaItemLabel = stringResource(R.string.gallery_media_item)
    val photoLabel = buildString {
        append(photo.name.ifBlank { mediaItemLabel })
        if (photo.isVideo) append(", video")
        if (photo.isScreenshot && !photo.isVideo) append(", screenshot")
        if (isSelected) append(", selected")
    }
    Box(Modifier.semantics { contentDescription = photoLabel }) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri).crossfade(true).size(250, 250).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(2.dp))
                .then(if (isSelected) Modifier.border(3.dp, Primary, RoundedCornerShape(2.dp)) else Modifier)
                .combinedClickable(
                    onClick = { onPhotoClick(photo, index) },
                    onLongClick = { onPhotoLongClick(photo) }
                ),
            contentScale = ContentScale.Crop
        )
        if (photo.isVideo) {
            Icon(Icons.Default.PlayCircle, null,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                tint = Color.White.copy(alpha = 0.8f))
            if (photo.duration > 0) {
                val secs = (photo.duration / 1000).toInt()
                val durText = if (secs >= 3600) String.format("%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60) else "${secs / 60}:${String.format("%02d", secs % 60)}"
                Text(durText, color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        if (photo.isScreenshot && !photo.isVideo) {
            Icon(Icons.Default.PhoneAndroid, null,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                tint = Tertiary)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, null,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                tint = Primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewer(
    photos: List<Photo>,
    initialIndex: Int,
    onClose: () -> Unit,
    onEdit: (Photo) -> Unit,
    onShare: (Photo) -> Unit,
    onEditSource: (Photo) -> Unit,
    onOpenSource: (Photo) -> Unit,
    onDelete: (Photo) -> Unit,
    onToggleFavorite: (Photo) -> Boolean // returns new state
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    var photoInfo by remember { mutableStateOf("") }
    var summaryText by remember { mutableStateOf<String?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var isFav by remember { mutableStateOf(
        photos.getOrNull(initialIndex)?.let { FavoritesStore.isFavorite(context, it) } ?: false
    ) }

    // Update fav state on page change
    LaunchedEffect(pagerState.currentPage) {
        val photo = photos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        isFav = FavoritesStore.isFavorite(context, photo)
    }

    // Load info for current photo
    LaunchedEffect(pagerState.currentPage) {
        val photo = photos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            context.contentResolver.query(photo.uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: "Unknown"
                    val size = cursor.getLong(1)
                    val w = cursor.getInt(2)
                    val h = cursor.getInt(3)
                    val date = cursor.getLong(4)
                    val path = cursor.getString(5) ?: ""
                    val sizeStr = when {
                        size > 1_000_000 -> String.format("%.1fMB", size / 1_000_000.0)
                        size > 1_000 -> "${size / 1_000}KB"
                        else -> "${size}B"
                    }
                    val dateStr = java.text.SimpleDateFormat("MMM d, yyyy  HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date * 1000))
                    photoInfo = "$name\n${w}x${h}  $sizeStr\n$path\n$dateStr"
                }
            }
        }
    }

    // Zoom state tracked per page so adjacent pages aren't affected. Must be a
    // SnapshotStateMap so writes from the page composables propagate to the parent's
    // userScrollEnabled read — a plain mutableMap would silently leave swipes enabled
    // while the user is zoomed in.
    val zoomStates = remember { mutableStateMapOf<Int, Triple<Float, Float, Float>>() }
    val currentZoom = zoomStates[pagerState.currentPage]?.first ?: 1f

    Box(Modifier.fillMaxSize().background(MediaSurface)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = currentZoom <= 1.05f
        ) { page ->
            var viewerZoom by remember { mutableFloatStateOf(zoomStates[page]?.first ?: 1f) }
            var viewerPanX by remember { mutableFloatStateOf(zoomStates[page]?.second ?: 0f) }
            var viewerPanY by remember { mutableFloatStateOf(zoomStates[page]?.third ?: 0f) }

            // Sync back so pager knows about zoom for userScrollEnabled
            LaunchedEffect(viewerZoom, viewerPanX, viewerPanY) {
                zoomStates[page] = Triple(viewerZoom, viewerPanX, viewerPanY)
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photos[page].uri).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = viewerZoom, scaleY = viewerZoom,
                        translationX = viewerPanX, translationY = viewerPanY
                    )
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            viewerZoom = (viewerZoom * zoom).coerceIn(1f, 5f)
                            if (viewerZoom > 1.05f) {
                                viewerPanX += pan.x; viewerPanY += pan.y
                            } else {
                                viewerPanX = 0f; viewerPanY = 0f
                            }
                        }
                    }
                    .pointerInput(page, viewerZoom) {
                        detectTapGestures(
                            onTap = {
                                if (viewerZoom > 1.05f) {
                                    viewerZoom = 1f; viewerPanX = 0f; viewerPanY = 0f
                                } else showInfo = !showInfo
                            },
                            onLongPress = {
                                val photo = photos[page]
                                val clipData = ClipData.newUri(
                                    context.contentResolver, "Photo", photo.uri
                                )
                                val density = context.resources.displayMetrics.density
                                val shadowPx = (120 * density).toInt()
                                val shadowBuilder = object : View.DragShadowBuilder() {
                                    override fun onProvideShadowMetrics(
                                        outShadowSize: Point,
                                        outShadowTouchPoint: Point
                                    ) {
                                        outShadowSize.set(shadowPx, shadowPx)
                                        outShadowTouchPoint.set(shadowPx / 2, shadowPx / 2)
                                    }
                                    override fun onDrawShadow(canvas: android.graphics.Canvas) {
                                        val paint = android.graphics.Paint().apply {
                                            color = 0xCC89B4FA.toInt()
                                        }
                                        val r = 8 * density
                                        canvas.drawRoundRect(
                                            0f, 0f,
                                            shadowPx.toFloat(), shadowPx.toFloat(),
                                            r, r, paint
                                        )
                                        val icon = android.graphics.Paint().apply {
                                            color = 0xFFFFFFFF.toInt()
                                            textSize = 14 * density
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                        canvas.drawText(
                                            "🖼",
                                            shadowPx / 2f,
                                            shadowPx / 2f + 5 * density,
                                            icon
                                        )
                                    }
                                }
                                view.startDragAndDrop(
                                    clipData, shadowBuilder, null,
                                    View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                                )
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
        }

        // Top bar overlay
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
            }
            Text("${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White, fontSize = 14.sp)
            Row {
                IconButton(onClick = { showInfo = !showInfo }) {
                    Icon(Icons.Default.Info, stringResource(R.string.gallery_info), tint = if (showInfo) Primary else Color.White)
                }
            }
        }

        // Bottom action bar
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = {
                photos.getOrNull(pagerState.currentPage)?.let { isFav = onToggleFavorite(it) }
            }) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(R.string.gallery_favorite), tint = if (isFav) Tertiary else Color.White)
            }
            IconButton(onClick = { photos.getOrNull(pagerState.currentPage)?.let { onShare(it) } }) {
                Icon(Icons.Default.Share, stringResource(R.string.gallery_share), tint = Color.White)
            }
            IconButton(onClick = {
                val photo = photos.getOrNull(pagerState.currentPage)
                when {
                    photo == null -> {}
                    Settings.canDrawOverlays(context) -> FloatingScreenshotService.pin(context, photo.uri)
                    else -> Toast.makeText(context, context.getString(R.string.gallery_pin_no_permission), Toast.LENGTH_LONG).show()
                }
            }) {
                Icon(Icons.Default.PushPin, stringResource(R.string.gallery_pin), tint = Color.White)
            }
            IconButton(onClick = {
                val photo = photos.getOrNull(pagerState.currentPage)
                if (!summaryLoading && photo != null) {
                    summaryLoading = true
                    scope.launch {
                        val result = ScreenshotSummarizer.summarize(context, photo.uri)
                        summaryText = result.description
                        summaryLoading = false
                        showSummary = true
                    }
                }
            }) {
                Icon(
                    Icons.Default.Accessibility,
                    stringResource(R.string.gallery_describe),
                    tint = if (summaryLoading) OnMediaSurfaceVariant else OnMediaSurface
                )
            }
            IconButton(onClick = { photos.getOrNull(pagerState.currentPage)?.let { onEdit(it) } }) {
                Icon(Icons.Default.Crop, stringResource(R.string.gallery_edit), tint = Primary)
            }
            IconButton(onClick = {
                photos.getOrNull(pagerState.currentPage)?.let { onDelete(it) }
            }) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Tertiary)
            }
        }

        // Info panel (above bottom bar)
        if (showInfo && photoInfo.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(12.dp)
            ) {
                val currentPhoto = photos.getOrNull(pagerState.currentPage)
                val source = currentPhoto?.sourceContext
                val sourceLabel = source?.label ?: source?.openUri?.host ?: source?.packageName
                Column {
                    Text(photoInfo, color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    sourceLabel?.let {
                        Text(it, color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    currentPhoto?.let { photo ->
                        Row {
                            TextButton(onClick = { onEditSource(photo) }) {
                                Text(stringResource(if (source == null) R.string.source_context_action_add else R.string.source_context_action_edit))
                            }
                            source?.openUri?.host?.let { host ->
                                TextButton(onClick = { onOpenSource(photo) }) {
                                    Text(stringResource(R.string.source_context_action_open, host))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSummary && summaryText != null) {
            AlertDialog(
                onDismissRequest = { showSummary = false },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(context.getString(R.string.gallery_summary_clip_label), summaryText)
                            )
                            Toast.makeText(context, context.getString(R.string.gallery_summary_copied), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.gallery_summary_copy))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showSummary = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                },
                title = { Text(stringResource(R.string.gallery_describe), color = OnSurface) },
                text = {
                    Text(
                        summaryText ?: "",
                        color = OnSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                },
                containerColor = SurfaceContainer
            )
        }
    }
}

private fun loadAlbums(resolver: ContentResolver, includeImages: Boolean, includeVideos: Boolean): List<Album> {
    val albumMap = mutableMapOf<String, MutableList<Pair<Long, Boolean>>>() // id to isVideo
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // Images
    (if (includeImages) resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) else null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val path = cursor.getString(pathCol) ?: continue
            albumMap.getOrPut(path) { mutableListOf() }.add(id to false)
        }
    }
    // Videos
    (if (includeVideos) resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) else null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val path = cursor.getString(pathCol) ?: continue
            albumMap.getOrPut(path) { mutableListOf() }.add(id to true)
        }
    }

    return albumMap.map { (path, items) ->
        val name = path.trimEnd('/').substringAfterLast("/").ifEmpty { path }
        val (firstId, isVideo) = items.first()
        val coverUri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            firstId)
        Album(name, path, coverUri, items.size)
    }.sortedByDescending { it.count }
}

private fun loadSmartAlbums(
    resolver: ContentResolver,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<String, ScreenshotIndexEntry> = emptyMap(),
    includeImages: Boolean = true
): List<Album> {
    val screenshots = loadAllPhotos(resolver, screenW, screenH, indexEntries, includeImages, includeVideos = false)
        .filter { !it.isVideo && it.isScreenshot }
    if (screenshots.isEmpty()) return emptyList()

    return smartAlbumRules.mapNotNull { rule ->
        val matches = screenshots.filter { rule.matches(it) }
        val cover = matches.firstOrNull() ?: return@mapNotNull null
        Album(
            name = rule.title,
            path = rule.path,
            coverUri = cover.uri,
            count = matches.size,
            isSmart = true,
            subtitle = rule.subtitle
        )
    }
}

private fun loadSmartAlbumPhotos(
    resolver: ContentResolver,
    smartAlbumPath: String,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<String, ScreenshotIndexEntry> = emptyMap(),
    includeImages: Boolean = true
): List<Photo> {
    val rule = smartAlbumRuleFor(smartAlbumPath) ?: return emptyList()
    return loadAllPhotos(resolver, screenW, screenH, indexEntries, includeImages, includeVideos = false)
        .filter { rule.matches(it) }
        .sortedByDescending { it.dateAdded }
}

private fun loadPhotos(
    resolver: ContentResolver,
    albumPath: String,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<String, ScreenshotIndexEntry> = emptyMap(),
    includeImages: Boolean = true,
    includeVideos: Boolean = true
): List<Photo> {
    val photos = mutableListOf<Photo>()

    // Images
    val imgProjection = mutableListOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.MIME_TYPE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf(albumPath)
    (if (includeImages) resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProjection, selection, selectionArgs,
        "${MediaStore.Images.Media.DATE_ADDED} DESC") else null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = cursor.getString(nameCol) ?: ""
            val w = cursor.getInt(wCol); val h = cursor.getInt(hCol)
            val isShot = looksLikeScreenshot(w, h, name, screenW, screenH)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                width = w, height = h, isScreenshot = isShot, albumPath = albumPath,
                mimeType = cursor.getString(mimeCol).orEmpty(),
                ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else "")
                .withIndex(indexEntries[uri.toString()]))
        }
    }

    // Videos (screenshot detection N/A)
    val vidProjection = mutableListOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.MIME_TYPE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
    (if (includeVideos) resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProjection,
        "${MediaStore.Video.Media.RELATIVE_PATH} = ?", selectionArgs,
        "${MediaStore.Video.Media.DATE_ADDED} DESC") else null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "",
                cursor.getLong(sizeCol), isVideo = true, duration = cursor.getLong(durCol),
                width = cursor.getInt(wCol), height = cursor.getInt(hCol), albumPath = albumPath,
                mimeType = cursor.getString(mimeCol).orEmpty(),
                ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else "")
                .withIndex(indexEntries[uri.toString()]))
        }
    }

    return photos.sortedByDescending { it.dateAdded }
}

private fun loadAllPhotos(
    resolver: ContentResolver,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<String, ScreenshotIndexEntry> = emptyMap(),
    includeImages: Boolean = true,
    includeVideos: Boolean = true
): List<Photo> {
    val photos = mutableListOf<Photo>()

    // Images
    val imgProjection = mutableListOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.MIME_TYPE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
    (if (includeImages) resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProjection, null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC") else null)?.use { cursor ->
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
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = cursor.getString(nameCol) ?: ""
            val w = cursor.getInt(wCol); val h = cursor.getInt(hCol)
            val isShot = looksLikeScreenshot(w, h, name, screenW, screenH)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                width = w, height = h, isScreenshot = isShot,
                albumPath = cursor.getString(pathCol) ?: "",
                mimeType = cursor.getString(mimeCol).orEmpty(),
                ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else "")
                .withIndex(indexEntries[uri.toString()]))
        }
    }

    // Videos
    val vidProjection = mutableListOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.MIME_TYPE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
    (if (includeVideos) resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProjection, null, null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC") else null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "",
                cursor.getLong(sizeCol), isVideo = true, duration = cursor.getLong(durCol),
                width = cursor.getInt(wCol), height = cursor.getInt(hCol),
                albumPath = cursor.getString(pathCol) ?: "",
                mimeType = cursor.getString(mimeCol).orEmpty(),
                ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else "")
                .withIndex(indexEntries[uri.toString()]))
        }
    }

    return photos.sortedByDescending { it.dateAdded }
}

private fun loadFavoritePhotos(
    resolver: ContentResolver,
    favoriteKeys: Set<String>,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<String, ScreenshotIndexEntry> = emptyMap(),
    includeImages: Boolean = true,
    includeVideos: Boolean = true
): List<Photo> {
    if (favoriteKeys.isEmpty()) return emptyList()
    val photos = mutableListOf<Photo>()

    // Batch queries in chunks of 500 to avoid SQLite bind variable limit (~999)
    fun queryFavs(contentUri: Uri, idsList: List<Long>, isVideo: Boolean) {
        val projection = (if (isVideo) {
            mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.Video.Media.DURATION, MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.MIME_TYPE)
        } else {
            mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.MIME_TYPE)
        }).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
        for (chunk in idsList.chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.MediaColumns._ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()
            resolver.query(contentUri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durCol = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    val name = cursor.getString(nameCol) ?: ""
                    val duration = if (isVideo) cursor.getLong(durCol) else 0L
                    val w = cursor.getInt(wCol)
                    val h = cursor.getInt(hCol)
                    val isShot = !isVideo && looksLikeScreenshot(w, h, name, screenW, screenH)
                    photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                        isVideo = isVideo, duration = duration, width = w, height = h,
                        isScreenshot = isShot, albumPath = cursor.getString(pathCol) ?: "",
                        mimeType = cursor.getString(mimeCol).orEmpty(),
                        ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol).orEmpty() else "")
                        .withIndex(indexEntries[uri.toString()]))
                }
            }
        }
    }

    val legacyImageIds = favoriteKeys.mapNotNull { it.toLongOrNull() }
    val uriIds = favoriteKeys.asSequence().filter { it.startsWith("uri:") }
        .mapNotNull { key -> runCatching { ContentUris.parseId(Uri.parse(key.removePrefix("uri:"))) }.getOrNull() }
        .toList()
    if (includeImages) queryFavs(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, (legacyImageIds + uriIds).distinct(), false)
    if (includeVideos) queryFavs(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, uriIds.distinct(), true)
    photos.removeAll { !FavoritesStore.isFavoriteKey(favoriteKeys, it.uri, it.id, it.isVideo) }
    return photos.sortedByDescending { it.dateAdded }
}
