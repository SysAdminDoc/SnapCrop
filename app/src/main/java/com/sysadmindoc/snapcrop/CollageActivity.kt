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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private data class CollageLayout(
    val name: String,
    val cols: Int,
    val rows: Int,
    val slots: Int = cols * rows
)

private val collageBgColors = listOf(
    0xFF1A1A1A.toInt() to "Dark",
    0xFF000000.toInt() to "Black",
    0xFFFFFFFF.toInt() to "White",
    0xFF89B4FA.toInt() to "Blue",
    0xFFA6E3A1.toInt() to "Green",
    0xFFF38BA8.toInt() to "Pink",
)

private val layouts = listOf(
    CollageLayout("2x1", 2, 1),
    CollageLayout("1x2", 1, 2),
    CollageLayout("2x2", 2, 2),
    CollageLayout("3x1", 3, 1),
    CollageLayout("1x3", 1, 3),
    CollageLayout("3x2", 3, 2),
    CollageLayout("2x3", 2, 3),
    CollageLayout("3x3", 3, 3),
    CollageLayout("4x1", 4, 1),
    CollageLayout("1x4", 1, 4),
    CollageLayout("4x2", 4, 2),
    CollageLayout("2x4", 2, 4),
    CollageLayout("4x3", 4, 3),
    CollageLayout("3x4", 3, 4),
    CollageLayout("4x4", 4, 4),
    CollageLayout("5x1", 5, 1),
    CollageLayout("1x5", 1, 5),
    CollageLayout("5x2", 5, 2),
    CollageLayout("2x5", 2, 5),
    CollageLayout("5x3", 5, 3),
    CollageLayout("3x5", 3, 5),
    CollageLayout("5x5", 5, 5),
    CollageLayout("6x1", 6, 1),
    CollageLayout("6x2", 6, 2),
    CollageLayout("6x3", 6, 3),
)

class CollageActivity : ComponentActivity() {

    private val imageUris = mutableStateListOf<Uri>()
    private val selectedLayout = mutableStateOf(layouts[2]) // default 2x2
    private val isSaving = mutableStateOf(false)
    private val spacing = mutableIntStateOf(4) // pixels gap
    private val bgColorIdx = mutableIntStateOf(0)
    private val cellAspect = mutableFloatStateOf(4f / 3f) // cell aspect ratio (w/h)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageUris.clear()
            imageUris.addAll(uris.take(selectedLayout.value.slots))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SnapCropTheme {
                CollageScreen(
                    uris = imageUris,
                    layout = selectedLayout.value,
                    isSaving = isSaving.value,
                    spacing = spacing.intValue,
                    bgColorIdx = bgColorIdx.intValue,
                    onLayoutChange = { selectedLayout.value = it },
                    onSpacingChange = { spacing.intValue = it },
                    onBgColorChange = { bgColorIdx.intValue = it },
                    cellAspect = cellAspect.floatValue,
                    onCellAspectChange = { cellAspect.floatValue = it },
                    onPickImages = { pickImagesLauncher.launch(arrayOf("image/*")) },
                    onSave = { buildAndSave() },
                    onClose = { finish() }
                )
            }
        }

        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun buildAndSave() {
        if (imageUris.isEmpty() || isSaving.value) return
        isSaving.value = true
        val layout = selectedLayout.value
        val gap = spacing.intValue

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmaps = imageUris.mapNotNull { uri ->
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                if (bitmaps.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CollageActivity, "No images loaded", Toast.LENGTH_SHORT).show()
                        isSaving.value = false
                    }
                    return@launch
                }

                // Target: 1080px wide collage
                val cellW = (1080 - gap * (layout.cols + 1)) / layout.cols
                val cellH = (cellW / cellAspect.floatValue).toInt()
                val totalW = cellW * layout.cols + gap * (layout.cols + 1)
                val totalH = cellH * layout.rows + gap * (layout.rows + 1)

                val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                // Background
                canvas.drawColor(collageBgColors[bgColorIdx.intValue.coerceIn(0, collageBgColors.size - 1)].first)

                for (i in 0 until layout.slots) {
                    if (i >= bitmaps.size) break
                    val col = i % layout.cols
                    val row = i / layout.cols
                    val x = gap + col * (cellW + gap)
                    val y = gap + row * (cellH + gap)

                    // Center-crop the bitmap into the cell
                    val src = bitmaps[i]
                    val scaled = centerCropBitmap(src, cellW, cellH)
                    canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
                    if (scaled !== src) scaled.recycle()
                }

                bitmaps.forEach { it.recycle() }

                withContext(Dispatchers.Main) { saveToGallery(result) }
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CollageActivity, "Collage failed", Toast.LENGTH_SHORT).show()
                    isSaving.value = false
                }
            }
        }
    }

    private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val targetRatio = targetW.toFloat() / targetH
        val (cropW, cropH) = if (srcRatio > targetRatio) {
            (src.height * targetRatio).toInt() to src.height
        } else {
            src.width to (src.width / targetRatio).toInt()
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x.coerceAtLeast(0), y.coerceAtLeast(0),
            cropW.coerceAtMost(src.width), cropH.coerceAtMost(src.height))
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped !== src) cropped.recycle()
        return scaled
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
            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Collage_${System.currentTimeMillis()}.$ext")
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
            Toast.makeText(this, "Collage saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            isSaving.value = false
        }
    }
}

@Composable
private fun CollageScreen(
    uris: List<Uri>,
    layout: CollageLayout,
    isSaving: Boolean,
    spacing: Int,
    bgColorIdx: Int,
    onLayoutChange: (CollageLayout) -> Unit,
    onSpacingChange: (Int) -> Unit,
    onBgColorChange: (Int) -> Unit,
    cellAspect: Float,
    onCellAspectChange: (Float) -> Unit,
    onPickImages: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = OnSurface) }
            Text("Collage", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f))
            Text("${uris.size}/${layout.slots}", color = OnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp))
        }

        // Layout picker
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(layouts) { l ->
                FilterChip(
                    selected = layout == l,
                    onClick = { onLayoutChange(l) },
                    label = { Text(l.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Spacing slider
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Gap: ${spacing}px", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = spacing.toFloat(), onValueChange = { onSpacingChange(it.toInt()) },
                valueRange = 0f..20f, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant)
            )
        }

        // Cell aspect ratio
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Cells:", color = OnSurfaceVariant, fontSize = 11.sp)
            listOf("4:3" to 4f/3f, "1:1" to 1f, "16:9" to 16f/9f, "3:4" to 3f/4f).forEach { (label, ratio) ->
                FilterChip(
                    selected = kotlin.math.abs(cellAspect - ratio) < 0.01f,
                    onClick = { onCellAspectChange(ratio) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Background color picker
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BG:", color = OnSurfaceVariant, fontSize = 11.sp)
            collageBgColors.forEachIndexed { i, (color, name) ->
                Box(
                    Modifier.size(24.dp)
                        .background(Color(color), RoundedCornerShape(4.dp))
                        .then(if (i == bgColorIdx) Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp)) else Modifier)
                        .clickable { onBgColorChange(i) }
                )
            }
        }

        // Collage preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            if (uris.isEmpty()) {
                Text("Select images to create a collage", color = OnSurfaceVariant, fontSize = 15.sp)
            } else {
                // Grid preview
                Column(
                    Modifier.fillMaxWidth().aspectRatio(layout.cols.toFloat() / layout.rows * cellAspect)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariant),
                    verticalArrangement = Arrangement.spacedBy(spacing.dp)
                ) {
                    for (row in 0 until layout.rows) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth().padding(
                                start = spacing.dp, end = spacing.dp,
                                top = if (row == 0) spacing.dp else 0.dp,
                                bottom = if (row == layout.rows - 1) spacing.dp else 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing.dp)
                        ) {
                            for (col in 0 until layout.cols) {
                                val idx = row * layout.cols + col
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .clickable { onPickImages() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (idx < uris.size) {
                                        AsyncImage(
                                            model = uris[idx], contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text("+", color = OnSurfaceVariant, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
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
                    enabled = uris.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Collage", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
