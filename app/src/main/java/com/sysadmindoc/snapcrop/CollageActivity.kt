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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.lifecycle.lifecycleScope
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
    // When true, the next picker result replaces all existing selections (initial pick / explicit "Pick All").
    // When false, it appends to fill empty cells (tapping the '+' tile).
    private var nextPickReplaces = true

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val slots = selectedLayout.value.slots
            if (nextPickReplaces) {
                imageUris.clear()
                imageUris.addAll(uris.take(slots))
            } else {
                val capacity = (slots - imageUris.size).coerceAtLeast(0)
                if (capacity > 0) imageUris.addAll(uris.take(capacity))
            }
            nextPickReplaces = true // default back so the toolbar "Pick" button always replaces.
        }
    }

    private fun launchReplacePicker() {
        nextPickReplaces = true
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun launchAppendPicker() {
        if (imageUris.size >= selectedLayout.value.slots) return
        nextPickReplaces = false
        pickImagesLauncher.launch(arrayOf("image/*"))
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
                    onLayoutChange = {
                        selectedLayout.value = it
                        // Drop overflow selections silently when shrinking layouts.
                        while (imageUris.size > it.slots) imageUris.removeAt(imageUris.size - 1)
                    },
                    onSpacingChange = { spacing.intValue = it },
                    onBgColorChange = { bgColorIdx.intValue = it },
                    cellAspect = cellAspect.floatValue,
                    onCellAspectChange = { cellAspect.floatValue = it },
                    onPickImages = { launchReplacePicker() },
                    onAddImage = { launchAppendPicker() },
                    onRemoveImage = { idx -> if (idx in imageUris.indices) imageUris.removeAt(idx) },
                    onSave = { buildAndSave() },
                    onClose = { finish() }
                )
            }
        }

        // First launch: pick replaces (list is empty anyway).
        nextPickReplaces = true
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun buildAndSave() {
        if (imageUris.size < 2 || isSaving.value) return
        isSaving.value = true
        val layout = selectedLayout.value
        val gap = spacing.intValue

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Target: 1080px wide collage
                val cellW = ((1080 - gap * (layout.cols + 1)) / layout.cols).coerceAtLeast(1)
                val cellH = (cellW / cellAspect.floatValue).toInt().coerceAtLeast(1)
                val totalW = cellW * layout.cols + gap * (layout.cols + 1)
                val totalH = cellH * layout.rows + gap * (layout.rows + 1)

                val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                // Background
                canvas.drawColor(collageBgColors[bgColorIdx.intValue.coerceIn(0, collageBgColors.size - 1)].first)

                // Decode only the cells we actually use, downsampled to roughly the cell size, one at
                // a time — full-resolution sources are never all held in memory at once.
                var drawn = 0
                for (i in 0 until layout.slots) {
                    val uri = imageUris.getOrNull(i) ?: break
                    val src = decodeSampled(uri, maxOf(cellW, cellH)) ?: continue
                    val col = i % layout.cols
                    val row = i / layout.cols
                    val x = gap + col * (cellW + gap)
                    val y = gap + row * (cellH + gap)
                    val scaled = centerCropBitmap(src, cellW, cellH)
                    canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
                    if (scaled !== src) scaled.recycle()
                    src.recycle()
                    drawn++
                }

                if (drawn == 0) {
                    result.recycle()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CollageActivity, getString(R.string.collage_failed), Toast.LENGTH_SHORT).show()
                        isSaving.value = false
                    }
                    return@launch
                }

                saveToGallery(result)
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CollageActivity, getString(R.string.collage_failed), Toast.LENGTH_SHORT).show()
                    isSaving.value = false
                }
            }
        }
    }

    private fun decodeSampled(uri: Uri, targetEdge: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest > 0 && longest / sample > targetEdge * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Exception) {
        null
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
            runOnUiThread { Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show() }
            isSaving.value = false
            return
        }
        try {
            (contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Failed to open output stream")).use { bitmap.compress(fmt, quality, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.collage_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: IOException) {
            runOnUiThread { Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show() }
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
    onAddImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().imePadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = OnSurface) }
            Text(stringResource(R.string.collage_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f))
            Text("${uris.size}/${layout.slots}", color = OnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp))
            TextButton(
                onClick = onPickImages,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(if (uris.isEmpty()) stringResource(R.string.collage_add_images) else stringResource(R.string.collage_replace), color = Primary, fontSize = 12.sp)
            }
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
                    modifier = Modifier.semantics {
                        contentDescription = "${l.name} layout, ${l.slots} cells${if (layout == l) ", selected" else ""}"
                    },
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
            Text(stringResource(R.string.collage_gap_px, spacing), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = spacing.toFloat(), onValueChange = { onSpacingChange(it.toInt()) },
                valueRange = 0f..20f,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Gap spacing, ${spacing} pixels"
                },
                colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant)
            )
        }

        // Cell aspect ratio
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.collage_cells), color = OnSurfaceVariant, fontSize = 11.sp)
            listOf("4:3" to 4f/3f, "1:1" to 1f, "16:9" to 16f/9f, "3:4" to 3f/4f).forEach { (label, ratio) ->
                val isSelected = kotlin.math.abs(cellAspect - ratio) < 0.01f
                FilterChip(
                    selected = isSelected,
                    onClick = { onCellAspectChange(ratio) },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = "$label cell aspect ratio${if (isSelected) ", selected" else ""}"
                    },
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
            Text(stringResource(R.string.collage_bg_label), color = OnSurfaceVariant, fontSize = 11.sp)
            collageBgColors.forEachIndexed { i, (color, name) ->
                Box(
                    Modifier.size(36.dp)
                        .semantics {
                            contentDescription = "$name background color${if (i == bgColorIdx) ", selected" else ""}"
                        }
                        .clickable { onBgColorChange(i) }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .background(Color(color), RoundedCornerShape(4.dp))
                            .then(if (i == bgColorIdx) Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp)) else Modifier)
                    )
                }
            }
        }

        // Collage preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            if (uris.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.padding(14.dp).size(28.dp), tint = Primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.collage_empty_title), color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.collage_empty_subtitle),
                        color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
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
                                val occupied = idx < uris.size
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .semantics {
                                            contentDescription = if (occupied) {
                                                "Collage cell ${idx + 1}, image loaded"
                                            } else {
                                                "Empty collage cell ${idx + 1}, tap to add image"
                                            }
                                        }
                                        .clickable {
                                            // Occupied cell: long-press would remove, single-tap is no-op
                                            // to prevent accidental edits. Empty cell appends new images.
                                            if (!occupied) onAddImage()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (occupied) {
                                        AsyncImage(
                                            model = uris[idx], contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { onRemoveImage(idx) },
                                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(Icons.Default.Close, "Remove image from cell ${idx + 1}",
                                                tint = OnSurface, modifier = Modifier.size(16.dp))
                                        }
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
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collage_rendering), color = OnSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val canSave = uris.size >= 2
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp),
                        tint = if (canSave) OnPrimary else OnSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(if (canSave) stringResource(R.string.collage_save_button) else stringResource(R.string.collage_save_needs_two),
                        color = if (canSave) OnPrimary else OnSurfaceVariant,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
