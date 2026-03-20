package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sysadmindoc.snapcrop.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Album(val name: String, val path: String, val coverUri: Uri, val count: Int)
data class Photo(val id: Long, val uri: Uri, val dateAdded: Long, val name: String = "", val size: Long = 0, val isVideo: Boolean = false, val duration: Long = 0)

private enum class SortMode(val label: String) { DATE("Date"), NAME("Name"), SIZE("Size") }

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

@Composable
fun GalleryScreen(
    onOpenEditor: (Uri) -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onShareUris: (List<Uri>) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    refreshKey: Int = 0 // increment to force refresh (e.g., after returning from editor)
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var viewerIndex by remember { mutableIntStateOf(-1) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var searchQuery by remember { mutableStateOf("") }
    var favIds by remember { mutableStateOf(FavoritesStore.getAllIds(context)) }
    var sortMode by remember { mutableStateOf(SortMode.DATE) }
    var gridColumns by remember { mutableIntStateOf(3) }
    val selectionMode = selectedIds.isNotEmpty()

    // Reload albums on initial load and when refreshKey changes (e.g., returning from editor)
    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) { albums = loadAlbums(context.contentResolver) }
        // Also refresh current album photos if viewing one
        selectedAlbum?.let { path ->
            withContext(Dispatchers.IO) {
                photos = if (path == "__ALL__") loadAllPhotos(context.contentResolver)
                else loadPhotos(context.contentResolver, path)
            }
        }
        isLoading = false
    }

    LaunchedEffect(selectedAlbum) {
        selectedAlbum?.let { path ->
            isLoading = true
            selectedIds.clear()
            withContext(Dispatchers.IO) {
                photos = when (path) {
                    "__ALL__" -> loadAllPhotos(context.contentResolver)
                    "__FAVS__" -> loadFavoritePhotos(context.contentResolver, FavoritesStore.getAllIds(context))
                    else -> loadPhotos(context.contentResolver, path)
                }
            }
            isLoading = false
        }
    }

    // Fullscreen viewer
    if (viewerIndex >= 0 && photos.isNotEmpty()) {
        PhotoViewer(
            photos = photos,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 },
            onEdit = { onOpenEditor(it.uri); viewerIndex = -1 },
            onShare = { onShareUris(listOf(it.uri)) },
            onDelete = { photo ->
                onDeleteUris(listOf(photo.uri))
                photos = photos.filter { it.id != photo.id }
                if (photos.isEmpty()) viewerIndex = -1
                else viewerIndex = viewerIndex.coerceAtMost(photos.size - 1)
                CoroutineScope(Dispatchers.IO).launch {
                    val refreshed = loadAlbums(context.contentResolver)
                    withContext(Dispatchers.Main) { albums = refreshed }
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
                    Icon(Icons.Default.Close, "Cancel", tint = OnSurface)
                }
                Text("${selectedIds.size} selected", color = OnSurface, fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    selectedIds.clear()
                    selectedIds.addAll(photos.map { it.id })
                }) { Icon(Icons.Default.SelectAll, "Select all", tint = OnSurface) }
                IconButton(onClick = {
                    val uris = photos.filter { it.id in selectedIds }.map { it.uri }
                    onShareUris(uris)
                }) { Icon(Icons.Default.Share, "Share", tint = OnSurface) }
                IconButton(onClick = {
                    val uris = photos.filter { it.id in selectedIds }.map { it.uri }
                    val deletedIds = selectedIds.toSet()
                    onDeleteUris(uris)
                    // Remove deleted photos from local list immediately
                    photos = photos.filter { it.id !in deletedIds }
                    selectedIds.clear()
                    // Refresh albums in background (counts changed)
                    CoroutineScope(Dispatchers.IO).launch {
                        val refreshed = loadAlbums(context.contentResolver)
                        withContext(Dispatchers.Main) { albums = refreshed }
                    }
                }) { Icon(Icons.Default.Delete, "Delete", tint = Tertiary) }
            } else {
                IconButton(onClick = {
                    if (selectedAlbum != null) { selectedAlbum = null; photos = emptyList() }
                    else onBack()
                }) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.ArrowBack, "Back", tint = OnSurface)
                }
                Text(
                    text = when (selectedAlbum) {
                        "__ALL__" -> "All Photos"
                        "__FAVS__" -> "Favorites"
                        null -> "Gallery"
                        else -> selectedAlbum!!.trimEnd('/').substringAfterLast("/")
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
                if (selectedAlbum == null) {
                    Text("${albums.sumOf { it.count }} photos", color = OnSurfaceVariant,
                        fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                } else {
                    // Sort button when viewing photos
                    IconButton(onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DATE -> SortMode.NAME
                            SortMode.NAME -> SortMode.SIZE
                            SortMode.SIZE -> SortMode.DATE
                        }
                    }) {
                        Icon(Icons.Default.SortByAlpha, "Sort: ${sortMode.label}", tint = OnSurfaceVariant)
                    }
                }
            }
        }

        // Search bar (album view only)
        if (selectedAlbum == null && !selectionMode && albums.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search albums...", color = OnSurfaceVariant, fontSize = 14.sp) },
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

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (selectedAlbum == null) {
            AlbumGrid(albums = filteredAlbums, onAlbumClick = { selectedAlbum = it.path },
                onAllPhotos = { selectedAlbum = "__ALL__" },
                onFavorites = { selectedAlbum = "__FAVS__" },
                favCount = favIds.size)
        } else {
            val sortedPhotos = remember(photos, sortMode) {
                when (sortMode) {
                    SortMode.DATE -> photos
                    SortMode.NAME -> photos.sortedBy { it.name.lowercase() }
                    SortMode.SIZE -> photos.sortedByDescending { it.size }
                }
            }
            PhotoGrid(
                photos = sortedPhotos,
                columns = gridColumns,
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
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit, onAllPhotos: () -> Unit,
                      onFavorites: () -> Unit, favCount: Int) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", color = OnSurfaceVariant, fontSize = 15.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All Photos" card first
        item {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onAllPhotos() },
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Photo, null, Modifier.size(40.dp), tint = Primary)
                        Spacer(Modifier.height(8.dp))
                        Text("All Photos", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${albums.sumOf { it.count }}", color = OnSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }

        // Favorites card
        if (favCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onFavorites() },
                    colors = CardDefaults.cardColors(containerColor = Tertiary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, null, Modifier.size(40.dp), tint = Tertiary)
                            Spacer(Modifier.height(8.dp))
                            Text("Favorites", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("$favCount", color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(albums) { album ->
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onAlbumClick(album) },
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverUri).crossfade(true).size(300).build(),
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
    selectedIds: List<Long>,
    selectionMode: Boolean,
    onPhotoClick: (Photo, Int) -> Unit,
    onPhotoLongClick: (Photo) -> Unit,
    onPinchZoom: (Float) -> Unit
) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos", color = OnSurfaceVariant, fontSize = 15.sp)
        }
        return
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
        items(photos.size) { index ->
            val photo = photos[index]
            val isSelected = photo.id in selectedIds
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.uri).crossfade(true).size(250).build(),
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
                        val durText = "${secs / 60}:${String.format("%02d", secs % 60)}"
                        Text(durText, color = Color.White, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                        tint = Primary)
                }
            }
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
    var showInfo by remember { mutableStateOf(false) }
    var photoInfo by remember { mutableStateOf("") }
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
                        size > 1_000_000 -> "${size / 1_000_000}MB"
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photos[page].uri).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clickable { showInfo = !showInfo },
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
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Text("${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White, fontSize = 14.sp)
            Row {
                IconButton(onClick = { showInfo = !showInfo }) {
                    Icon(Icons.Default.Info, "Info", tint = if (showInfo) Primary else Color.White)
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
                isFav = onToggleFavorite(photos[pagerState.currentPage])
            }) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite", tint = if (isFav) Tertiary else Color.White)
            }
            IconButton(onClick = { onShare(photos[pagerState.currentPage]) }) {
                Icon(Icons.Default.Share, "Share", tint = Color.White)
            }
            IconButton(onClick = { onEdit(photos[pagerState.currentPage]) }) {
                Icon(Icons.Default.Crop, "Edit", tint = Primary)
            }
            IconButton(onClick = {
                val photo = photos[pagerState.currentPage]
                onDelete(photo)
            }) {
                Icon(Icons.Default.Delete, "Delete", tint = Tertiary)
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
    }
}

private fun loadAlbums(resolver: ContentResolver): List<Album> {
    val albumMap = mutableMapOf<String, MutableList<Long>>()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val path = cursor.getString(pathCol) ?: continue
            albumMap.getOrPut(path) { mutableListOf() }.add(id)
        }
    }

    return albumMap.map { (path, ids) ->
        val name = path.trimEnd('/').substringAfterLast("/").ifEmpty { path }
        val coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ids.first())
        Album(name, path, coverUri, ids.size)
    }.sortedByDescending { it.count }
}

private fun loadPhotos(resolver: ContentResolver, albumPath: String): List<Photo> {
    val photos = mutableListOf<Photo>()

    // Images
    val imgProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf(albumPath)
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProjection, selection, selectionArgs,
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "", cursor.getLong(sizeCol)))
        }
    }

    // Videos
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
                cursor.getLong(sizeCol), isVideo = true, duration = cursor.getLong(durCol)))
        }
    }

    return photos.sortedByDescending { it.dateAdded }
}

private fun loadAllPhotos(resolver: ContentResolver): List<Photo> {
    val photos = mutableListOf<Photo>()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "", cursor.getLong(sizeCol)))
        }
    }
    return photos
}

private fun loadFavoritePhotos(resolver: ContentResolver, favIds: Set<Long>): List<Photo> {
    if (favIds.isEmpty()) return emptyList()
    val photos = mutableListOf<Photo>()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
    val placeholders = favIds.joinToString(",") { "?" }
    val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
    val selectionArgs = favIds.map { it.toString() }.toTypedArray()
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, cursor.getLong(dateCol), cursor.getString(nameCol) ?: "", cursor.getLong(sizeCol)))
        }
    }
    return photos
}
