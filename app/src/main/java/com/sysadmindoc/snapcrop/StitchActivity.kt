package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sysadmindoc.snapcrop.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class StitchActivity : ComponentActivity() {

    private val imageUris = mutableStateListOf<Uri>()
    private val isVertical = mutableStateOf(true)
    private val isSaving = mutableStateOf(false)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) imageUris.addAll(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SnapCropTheme {
                StitchScreen(
                    uris = imageUris,
                    isVertical = isVertical.value,
                    isSaving = isSaving.value,
                    onToggleDirection = { isVertical.value = !isVertical.value },
                    onAddImages = { pickImagesLauncher.launch(arrayOf("image/*")) },
                    onMoveUp = { i -> if (i > 0) { val u = imageUris.removeAt(i); imageUris.add(i - 1, u) } },
                    onMoveDown = { i -> if (i < imageUris.size - 1) { val u = imageUris.removeAt(i); imageUris.add(i + 1, u) } },
                    onRemoveImage = { imageUris.removeAt(it) },
                    onSave = { stitchAndSave() },
                    onClose = { finish() }
                )
            }
        }

        // Auto-open picker on launch
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun stitchAndSave() {
        if (imageUris.size < 2 || isSaving.value) return
        isSaving.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmaps = imageUris.mapNotNull { uri ->
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmaps.size < 2) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@StitchActivity, "Need at least 2 images", Toast.LENGTH_SHORT).show()
                        isSaving.value = false
                    }
                    return@launch
                }

                val result = if (isVertical.value) stitchVertical(bitmaps) else stitchHorizontal(bitmaps)
                bitmaps.forEach { it.recycle() }

                withContext(Dispatchers.Main) { saveToGallery(result) }
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StitchActivity, "Stitch failed", Toast.LENGTH_SHORT).show()
                    isSaving.value = false
                }
            }
        }
    }

    private fun stitchVertical(bitmaps: List<Bitmap>): Bitmap {
        val maxW = bitmaps.maxOf { it.width }
        val totalH = bitmaps.sumOf { (it.height.toFloat() * maxW / it.width).toInt() }
        val result = Bitmap.createBitmap(maxW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var y = 0f
        for (bmp in bitmaps) {
            val scale = maxW.toFloat() / bmp.width
            val scaledH = (bmp.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bmp, maxW, scaledH, true)
            canvas.drawBitmap(scaled, 0f, y, null)
            if (scaled !== bmp) scaled.recycle()
            y += scaledH
        }
        return result
    }

    private fun stitchHorizontal(bitmaps: List<Bitmap>): Bitmap {
        val maxH = bitmaps.maxOf { it.height }
        val totalW = bitmaps.sumOf { (it.width.toFloat() * maxH / it.height).toInt() }
        val result = Bitmap.createBitmap(totalW, maxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var x = 0f
        for (bmp in bitmaps) {
            val scale = maxH.toFloat() / bmp.height
            val scaledW = (bmp.width * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bmp, scaledW, maxH, true)
            canvas.drawBitmap(scaled, x, 0f, null)
            if (scaled !== bmp) scaled.recycle()
            x += scaledW
        }
        return result
    }

    private fun getSaveFormat(): Triple<Bitmap.CompressFormat, Int, String> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                          else Bitmap.CompressFormat.WEBP
                Triple(fmt, quality, "webp")
            }
            prefs.getBoolean("use_jpeg", false) -> Triple(Bitmap.CompressFormat.JPEG, quality, "jpg")
            else -> Triple(Bitmap.CompressFormat.PNG, 100, "png")
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val (fmt, quality, ext) = getSaveFormat()
        val mime = when (ext) { "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Stitch_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, getSharedPreferences("snapcrop", MODE_PRIVATE).getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            isSaving.value = false
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(fmt, quality, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Stitched image saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            isSaving.value = false
        }
    }
}

@Composable
private fun StitchScreen(
    uris: List<Uri>,
    isVertical: Boolean,
    isSaving: Boolean,
    onToggleDirection: () -> Unit,
    onAddImages: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = OnSurface)
            }
            Text("Stitch Images", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = OnSurface, modifier = Modifier.weight(1f))
            Text("${uris.size} images", color = OnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp))
        }

        // Direction toggle + add button
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isVertical,
                onClick = { if (!isVertical) onToggleDirection() },
                label = { Text("Vertical") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                    containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = !isVertical,
                onClick = { if (isVertical) onToggleDirection() },
                label = { Text("Horizontal") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                    containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onAddImages,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp)
            }
        }

        // Preview
        if (uris.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Select images to stitch together", color = OnSurfaceVariant, fontSize = 15.sp)
            }
        } else if (isVertical) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uris.size) { index ->
                    Box {
                        AsyncImage(
                            model = uris[index], contentDescription = null,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                            if (index > 0) {
                                IconButton(
                                    onClick = { onMoveUp(index) },
                                    modifier = Modifier.size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                ) { Icon(Icons.Default.ArrowUpward, "Move up", tint = OnSurface, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(4.dp))
                            }
                            if (index < uris.size - 1) {
                                IconButton(
                                    onClick = { onMoveDown(index) },
                                    modifier = Modifier.size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                ) { Icon(Icons.Default.ArrowDownward, "Move down", tint = OnSurface, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            ) { Icon(Icons.Default.Close, "Remove", tint = Tertiary, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        } else {
            LazyRow(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(uris.size) { index ->
                    Box {
                        AsyncImage(
                            model = uris[index], contentDescription = null,
                            modifier = Modifier.fillMaxHeight().width(200.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.FillHeight
                        )
                        Column(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            ) { Icon(Icons.Default.Close, "Remove", tint = Tertiary, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }

        // Output dimensions estimate
        if (uris.size >= 2) {
            val context = LocalContext.current
            val dims = remember(uris, isVertical) {
                try {
                    var totalW = 0; var totalH = 0; var maxW = 0; var maxH = 0
                    for (u in uris) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                        val w = opts.outWidth; val h = opts.outHeight
                        if (w > 0 && h > 0) {
                            if (isVertical) { maxW = maxOf(maxW, w); totalH += h } else { totalW += w; maxH = maxOf(maxH, h) }
                        }
                    }
                    if (isVertical) "${maxW}x${totalH}" else "${totalW}x${maxH}"
                } catch (_: Exception) { "" }
            }
            if (dims.isNotEmpty()) {
                Text("Output: $dims px", Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = OnSurfaceVariant, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        // Save button
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Primary)
            } else {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uris.size >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Stitch & Save", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
