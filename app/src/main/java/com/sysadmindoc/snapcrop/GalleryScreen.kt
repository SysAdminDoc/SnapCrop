package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
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
    val subtitle: String = ""
)
data class Photo(
    val id: Long, val uri: Uri, val dateAdded: Long,
    val name: String = "", val size: Long = 0,
    val isVideo: Boolean = false, val duration: Long = 0,
    val width: Int = 0, val height: Int = 0,
    val isScreenshot: Boolean = false,
    val albumPath: String = "",
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

    fun isFavorite(context: Context, id: Long): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).contains(id.toString())

    fun toggle(context: Context, id: Long): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isFav = prefs.contains(id.toString())
        if (isFav) prefs.edit().remove(id.toString()).apply()
        else prefs.edit().putBoolean(id.toString(), true).apply()
        return !isFav
    }

    fun getAllIds(context: Context): Set<Long> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all.keys.mapNotNull { it.toLongOrNull() }.toSet()
}

private fun Photo.withIndex(entry: ScreenshotIndexEntry?): Photo {
    if (entry == null) return this
    return copy(
        isScreenshot = entry.isScreenshot,
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
    refreshKey: Int = 0 // increment to force refresh (e.g., after returning from editor)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE) }
    val (screenW, screenH) = remember { getScreenSize(context) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var smartAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var indexEntries by remember { mutableStateOf<Map<Long, ScreenshotIndexEntry>>(emptyMap()) }
    var indexEnabled by remember { mutableStateOf(prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) }
    var isLoading by remember { mutableStateOf(true) }
    var viewerIndex by remember { mutableIntStateOf(-1) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var searchQuery by remember { mutableStateOf("") }
    var favIds by remember { mutableStateOf(FavoritesStore.getAllIds(context)) }
    var sortMode by remember { mutableStateOf(SortMode.DATE) }
    var gridColumns by remember { mutableIntStateOf(3) }
    val selectionMode = selectedIds.isNotEmpty()

    // Reload albums on initial load and when refreshKey changes (e.g., returning from editor)
    LaunchedEffect(refreshKey, indexEnabled, favIds) {
        val refreshedCollections = withContext(Dispatchers.IO) {
            val index = if (indexEnabled) {
                val store = ScreenshotIndexStore(context)
                store.rebuildFromMediaStore(context.contentResolver, screenW, screenH, FavoritesStore.getAllIds(context))
                store.loadEntryMap()
            } else {
                emptyMap()
            }
            Triple(
                loadAlbums(context.contentResolver),
                loadSmartAlbums(context.contentResolver, screenW, screenH, index),
                index
            )
        }
        albums = refreshedCollections.first
        smartAlbums = refreshedCollections.second
        indexEntries = refreshedCollections.third
        val currentIndex = refreshedCollections.third
        indexEnabled = prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
        // Also refresh current album photos if viewing one
        selectedAlbum?.let { path ->
            withContext(Dispatchers.IO) {
                photos = when {
                    path == ALL_PHOTOS_PATH -> loadAllPhotos(context.contentResolver, screenW, screenH, currentIndex)
                    path == FAVORITES_PATH -> loadFavoritePhotos(context.contentResolver, FavoritesStore.getAllIds(context), screenW, screenH, currentIndex)
                    path.startsWith(SMART_ALBUM_PREFIX) -> loadSmartAlbumPhotos(context.contentResolver, path, screenW, screenH, currentIndex)
                    else -> loadPhotos(context.contentResolver, path, screenW, screenH, currentIndex)
                }
            }
        }
        isLoading = false
    }

    // Reactively follow the Room-backed index: rebuilds, OCR token capture, and purges all emit
    // here, refreshing smart-album membership and search without a manual reload.
    LaunchedEffect(indexEnabled) {
        if (!indexEnabled) return@LaunchedEffect
        ScreenshotIndexStore(context).observeEntries().collect { latest ->
            indexEntries = latest
        }
    }

    LaunchedEffect(selectedAlbum, indexEntries) {
        selectedAlbum?.let { path ->
            isLoading = true
            selectedIds.clear()
            withContext(Dispatchers.IO) {
                photos = when (path) {
                    ALL_PHOTOS_PATH -> loadAllPhotos(context.contentResolver, screenW, screenH, indexEntries)
                    FAVORITES_PATH -> loadFavoritePhotos(context.contentResolver, FavoritesStore.getAllIds(context), screenW, screenH, indexEntries)
                    else -> if (path.startsWith(SMART_ALBUM_PREFIX)) {
                        loadSmartAlbumPhotos(context.contentResolver, path, screenW, screenH, indexEntries)
                    } else {
                        loadPhotos(context.contentResolver, path, screenW, screenH, indexEntries)
                    }
                }
            }
            isLoading = false
        }
    }

    // Compute sorted photos at the top level so viewer uses same order as grid
    val viewerPhotos = remember(photos, sortMode, searchQuery, selectedAlbum) {
        val sorted = when (sortMode) {
            SortMode.DATE -> photos
            SortMode.NAME -> photos.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> photos.sortedByDescending { it.size }
        }
        if (selectedAlbum != null && searchQuery.isNotBlank()) {
            sorted.filter { it.matchesGalleryQuery(searchQuery) }
        } else {
            sorted
        }
    }

    // Fullscreen viewer
    if (viewerIndex >= 0 && viewerPhotos.isNotEmpty()) {
        PhotoViewer(
            photos = viewerPhotos,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 },
            onEdit = { onOpenEditor(it.uri); viewerIndex = -1 },
            onShare = { onShareUris(listOf(it.uri)) },
            onDelete = { photo ->
                onDeleteUris(listOf(photo.uri))
                // Drop the favorite entry too — leaving it stranded keeps a dead ID in
                // SharedPreferences that the Favorites view would then fail to resolve.
                if (FavoritesStore.isFavorite(context, photo.id)) {
                    FavoritesStore.toggle(context, photo.id)
                    favIds = FavoritesStore.getAllIds(context)
                }
                photos = photos.filter { it.id != photo.id }
                if (photos.isEmpty()) viewerIndex = -1
                else viewerIndex = viewerIndex.coerceIn(0, photos.size - 1)
                scope.launch(Dispatchers.IO) {
                    val refreshed = loadAlbums(context.contentResolver)
                    val refreshedSmart = loadSmartAlbums(context.contentResolver, screenW, screenH)
                    withContext(Dispatchers.Main) {
                        albums = refreshed
                        smartAlbums = refreshedSmart
                    }
                }
            },
            onToggleFavorite = { photo ->
                val newState = FavoritesStore.toggle(context, photo.id)
                favIds = FavoritesStore.getAllIds(context)
                newState
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                // Selection mode bar
                IconButton(onClick = { selectedIds.clear() }) {
                    Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = OnSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.gallery_selected_count, selectedIds.size), color = OnSurface, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.gallery_selected_actions), color = OnSurfaceVariant, fontSize = 11.sp)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    IconButton(onClick = {
                        selectedIds.clear()
                        selectedIds.addAll(viewerPhotos.map { it.id })
                    }) { Icon(Icons.Default.SelectAll, stringResource(R.string.gallery_select_all), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.id in selectedIds }.map { it.uri }
                        onShareUris(uris)
                    }) { Icon(Icons.Default.Share, stringResource(R.string.gallery_share), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.id in selectedIds && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onExportPdf(uris); selectedIds.clear() }
                    }) { Icon(Icons.Default.PictureAsPdf, stringResource(R.string.gallery_export_pdf), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.id in selectedIds && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onBatchRename(uris); selectedIds.clear() }
                    }) { Icon(Icons.Default.SortByAlpha, stringResource(R.string.gallery_rename), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.id in selectedIds && !it.isVideo }.map { it.uri }
                        if (uris.isNotEmpty()) { onBatchResize(uris); selectedIds.clear() }
                    }) { Icon(Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.resize), tint = OnSurface) }
                    IconButton(onClick = {
                        val uris = photos.filter { it.id in selectedIds }.map { it.uri }
                        val deletedIds = selectedIds.toSet()
                        onDeleteUris(uris)
                        // Remove deleted photos from local list immediately
                        photos = photos.filter { it.id !in deletedIds }
                        selectedIds.clear()
                        // Refresh albums in background (counts changed)
                        scope.launch(Dispatchers.IO) {
                            val refreshed = loadAlbums(context.contentResolver)
                            val refreshedSmart = loadSmartAlbums(context.contentResolver, screenW, screenH)
                            withContext(Dispatchers.Main) {
                                albums = refreshed
                                smartAlbums = refreshedSmart
                            }
                        }
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
                        else -> smartAlbumRuleFor(selectedAlbum!!)?.title
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
                } else {
                    // Photo count
                    Text("${viewerPhotos.size}", color = OnSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp))
                    // One-tap "select all screenshots" — drops user into selection mode with
                    // every dimension-matching image pre-selected, ready for bulk delete.
                    val screenshotCount = viewerPhotos.count { it.isScreenshot && it.id !in favIds }
                    if (screenshotCount > 0) {
                        TextButton(
                            onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(viewerPhotos.filter { it.isScreenshot && it.id !in favIds }.map { it.id })
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

        val filteredAlbums = if (searchQuery.isBlank()) albums
            else albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val filteredSmartAlbums = if (searchQuery.isBlank()) smartAlbums
            else smartAlbums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.subtitle.contains(searchQuery, ignoreCase = true)
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
            AlbumGrid(albums = filteredAlbums, smartAlbums = filteredSmartAlbums,
                showLibraryCards = searchQuery.isBlank(),
                totalMediaCount = albums.sumOf { it.count },
                onAlbumClick = { searchQuery = ""; selectedAlbum = it.path },
                onAllPhotos = { searchQuery = ""; selectedAlbum = ALL_PHOTOS_PATH },
                onFavorites = { searchQuery = ""; selectedAlbum = FAVORITES_PATH },
                favCount = favIds.size,
                emptyTitle = if (searchQuery.isBlank()) stringResource(R.string.gallery_empty_title) else stringResource(R.string.gallery_search_empty_title),
                emptySubtitle = if (searchQuery.isBlank()) stringResource(R.string.gallery_empty_subtitle)
                    else stringResource(R.string.gallery_search_empty_subtitle))
        } else {
            PhotoGrid(
                photos = viewerPhotos,
                columns = gridColumns,
                showDateHeaders = sortMode == SortMode.DATE,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                onPhotoClick = { photo, index ->
                    if (selectionMode) toggleSelection(selectedIds, photo.id)
                    else if (photo.isVideo) onPlayVideo(photo.uri)
                    else viewerIndex = index
                },
                onPhotoLongClick = { photo -> toggleSelection(selectedIds, photo.id) },
                onPinchZoom = { zoom ->
                    if (zoom < 0.8f) gridColumns = (gridColumns + 1).coerceAtMost(6)
                    else if (zoom > 1.2f) gridColumns = (gridColumns - 1).coerceAtLeast(2)
                }
            )
        }
    }
}

private fun toggleSelection(selectedIds: MutableList<Long>, id: Long) {
    if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
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
    showLibraryCards: Boolean,
    totalMediaCount: Int,
    onAlbumClick: (Album) -> Unit,
    onAllPhotos: () -> Unit,
    onFavorites: () -> Unit,
    favCount: Int,
    emptyTitle: String,
    emptySubtitle: String
) {
    if (albums.isEmpty() && smartAlbums.isEmpty() && (!showLibraryCards || favCount == 0)) {
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
            item {
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
                item {
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

        if (smartAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.gallery_smart_albums),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(smartAlbums) { album ->
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.gallery_albums),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        items(albums) { album ->
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
                            Text(album.name, color = OnSurface, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.count}", color = OnSurfaceVariant, fontSize = 11.sp)
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
    selectedIds: List<Long>,
    selectionMode: Boolean,
    onPhotoClick: (Photo, Int) -> Unit,
    onPhotoLongClick: (Photo) -> Unit,
    onPinchZoom: (Float) -> Unit
) {
    if (photos.isEmpty()) {
        GalleryEmptyState(
            icon = Icons.Default.Photo,
            title = stringResource(R.string.gallery_no_media_title),
            subtitle = stringResource(R.string.gallery_no_media_subtitle)
        )
        return
    }

    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }

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
            val indexById = photos.withIndex().associate { (i, p) -> p.id to i }
            val grouped = photos.groupBy { dateFormat.format(java.util.Date(it.dateAdded * 1000)) }
            grouped.forEach { (date, datePhotos) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(date, color = OnSurfaceVariant, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
                items(datePhotos.size) { i ->
                    val photo = datePhotos[i]
                    val globalIdx = indexById[photo.id] ?: 0
                    val isSelected = photo.id in selectedIds
                    PhotoItem(photo, globalIdx, isSelected, selectionMode, onPhotoClick, onPhotoLongClick)
                }
            }
        } else {
        items(photos.size) { index ->
            val photo = photos[index]
            val isSelected = photo.id in selectedIds
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
                    // Play icon + duration
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
        FavoritesStore.isFavorite(context, photos.getOrNull(initialIndex)?.id ?: 0)
    ) }

    // Update fav state on page change
    LaunchedEffect(pagerState.currentPage) {
        val photo = photos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        isFav = FavoritesStore.isFavorite(context, photo.id)
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
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
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    tint = if (summaryLoading) OnSurfaceVariant else Color.White
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
                Text(photoInfo, color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
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
                                android.content.ClipData.newPlainText("Summary", summaryText)
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

private fun loadAlbums(resolver: ContentResolver): List<Album> {
    val albumMap = mutableMapOf<String, MutableList<Pair<Long, Boolean>>>() // id to isVideo
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // Images
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val path = cursor.getString(pathCol) ?: continue
            albumMap.getOrPut(path) { mutableListOf() }.add(id to false)
        }
    }
    // Videos
    resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
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
    indexEntries: Map<Long, ScreenshotIndexEntry> = emptyMap()
): List<Album> {
    val screenshots = loadAllPhotos(resolver, screenW, screenH, indexEntries)
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
    indexEntries: Map<Long, ScreenshotIndexEntry> = emptyMap()
): List<Photo> {
    val rule = smartAlbumRuleFor(smartAlbumPath) ?: return emptyList()
    return loadAllPhotos(resolver, screenW, screenH, indexEntries)
        .filter { rule.matches(it) }
        .sortedByDescending { it.dateAdded }
}

private fun loadPhotos(
    resolver: ContentResolver,
    albumPath: String,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<Long, ScreenshotIndexEntry> = emptyMap()
): List<Photo> {
    val photos = mutableListOf<Photo>()

    // Images
    val imgProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT)
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf(albumPath)
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProjection, selection, selectionArgs,
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = cursor.getString(nameCol) ?: ""
            val w = cursor.getInt(wCol); val h = cursor.getInt(hCol)
            val isShot = looksLikeScreenshot(w, h, name, screenW, screenH)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                width = w, height = h, isScreenshot = isShot, albumPath = albumPath)
                .withIndex(indexEntries[id]))
        }
    }

    // Videos (screenshot detection N/A)
    val vidProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION)
    resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProjection,
        "${MediaStore.Video.Media.RELATIVE_PATH} = ?", selectionArgs,
        "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "",
                cursor.getLong(sizeCol), isVideo = true, duration = cursor.getLong(durCol),
                albumPath = albumPath).withIndex(indexEntries[id]))
        }
    }

    return photos.sortedByDescending { it.dateAdded }
}

private fun loadAllPhotos(
    resolver: ContentResolver,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<Long, ScreenshotIndexEntry> = emptyMap()
): List<Photo> {
    val photos = mutableListOf<Photo>()

    // Images
    val imgProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.RELATIVE_PATH)
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProjection, null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = cursor.getString(nameCol) ?: ""
            val w = cursor.getInt(wCol); val h = cursor.getInt(hCol)
            val isShot = looksLikeScreenshot(w, h, name, screenW, screenH)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                width = w, height = h, isScreenshot = isShot,
                albumPath = cursor.getString(pathCol) ?: "").withIndex(indexEntries[id]))
        }
    }

    // Videos
    val vidProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RELATIVE_PATH)
    resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProjection, null, null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "",
                cursor.getLong(sizeCol), isVideo = true, duration = cursor.getLong(durCol),
                albumPath = cursor.getString(pathCol) ?: "").withIndex(indexEntries[id]))
        }
    }

    return photos.sortedByDescending { it.dateAdded }
}

private fun loadFavoritePhotos(
    resolver: ContentResolver,
    favIds: Set<Long>,
    screenW: Int,
    screenH: Int,
    indexEntries: Map<Long, ScreenshotIndexEntry> = emptyMap()
): List<Photo> {
    if (favIds.isEmpty()) return emptyList()
    val photos = mutableListOf<Photo>()

    // Batch queries in chunks of 500 to avoid SQLite bind variable limit (~999)
    fun queryFavs(contentUri: Uri, idsList: List<Long>, isVideo: Boolean) {
        val projection = if (isVideo) {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.Video.Media.DURATION, MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.RELATIVE_PATH)
        }
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
                val wCol = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH) else -1
                val hCol = if (!isVideo) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT) else -1
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    val name = cursor.getString(nameCol) ?: ""
                    val duration = if (isVideo) cursor.getLong(durCol) else 0L
                    val w = if (!isVideo) cursor.getInt(wCol) else 0
                    val h = if (!isVideo) cursor.getInt(hCol) else 0
                    val isShot = !isVideo && looksLikeScreenshot(w, h, name, screenW, screenH)
                    photos.add(Photo(id, uri, cursor.getLong(dateCol), name, cursor.getLong(sizeCol),
                        isVideo = isVideo, duration = duration, width = w, height = h,
                        isScreenshot = isShot, albumPath = cursor.getString(pathCol) ?: "")
                        .withIndex(indexEntries[id]))
                }
            }
        }
    }

    val idsList = favIds.toList()
    queryFavs(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, idsList, false)
    queryFavs(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idsList, true)
    return photos.sortedByDescending { it.dateAdded }
}
