package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Album(val name: String, val path: String, val coverUri: Uri, val count: Int)
data class Photo(val id: Long, val uri: Uri, val dateAdded: Long)

@Composable
fun GalleryScreen(
    onOpenEditor: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { albums = loadAlbums(context.contentResolver) }
        isLoading = false
    }

    LaunchedEffect(selectedAlbum) {
        selectedAlbum?.let { path ->
            isLoading = true
            withContext(Dispatchers.IO) { photos = loadPhotos(context.contentResolver, path) }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedAlbum != null) { selectedAlbum = null; photos = emptyList() }
                else onBack()
            }) {
                @Suppress("DEPRECATION")
                Icon(Icons.Default.ArrowBack, "Back", tint = OnSurface)
            }
            Text(
                text = if (selectedAlbum != null) {
                    selectedAlbum!!.trimEnd('/').substringAfterLast("/")
                } else "Gallery",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (selectedAlbum == null) {
            AlbumGrid(albums = albums, onAlbumClick = { selectedAlbum = it.path })
        } else {
            PhotoGrid(photos = photos, onPhotoClick = { onOpenEditor(it.uri) })
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
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
        items(albums) { album ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onAlbumClick(album) },
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverUri)
                            .crossfade(true)
                            .size(300)
                            .build(),
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f)).padding(8.dp)
                    ) {
                        Column {
                            Text(album.name, color = OnSurface, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${album.count}", color = OnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<Photo>, onPhotoClick: (Photo) -> Unit) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos", color = OnSurfaceVariant, fontSize = 15.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(photos) { photo ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.uri)
                    .crossfade(true)
                    .size(250)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onPhotoClick(photo) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun loadAlbums(resolver: ContentResolver): List<Album> {
    val albumMap = mutableMapOf<String, MutableList<Long>>()

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.RELATIVE_PATH
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection, null, null, sortOrder
    )?.use { cursor ->
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

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf(albumPath)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection, selection, selectionArgs, sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val date = cursor.getLong(dateCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            photos.add(Photo(id, uri, date))
        }
    }

    return photos
}
