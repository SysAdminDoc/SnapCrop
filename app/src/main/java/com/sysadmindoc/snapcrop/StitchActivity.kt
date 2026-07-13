package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

private fun stitchItemKey(uris: List<Uri>, index: Int): String {
    val uri = uris[index]
    val duplicateOrdinal = uris.take(index + 1).count { it == uri }
    return "${uri}#$duplicateOrdinal"
}

class StitchActivity : ComponentActivity() {

    private companion object {
        const val STATE_URIS = "stitch_uris"
        const val STATE_VERTICAL = "stitch_vertical"
    }

    private val imageUris = mutableStateListOf<Uri>()
    private val isVertical = mutableStateOf(true)
    private val isSaving = mutableStateOf(false)

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { WorkflowStateRestoration.persistReadGrant(this, it) }
            val existing = imageUris.mapTo(mutableSetOf(), Uri::toString)
            imageUris.addAll(
                uris.filter { existing.add(it.toString()) }
                    .take(WorkflowStateRestoration.MAX_SAVED_URIS - imageUris.size),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        val restored = WorkflowStateRestoration.restoreUris(savedInstanceState, STATE_URIS)
        if (savedInstanceState != null) {
            imageUris.addAll(restored)
            isVertical.value = savedInstanceState.getBoolean(STATE_VERTICAL, true)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(InboundShareContract.EXTRA_URIS)
                ?.distinctBy(Uri::toString)
                ?.take(WorkflowStateRestoration.MAX_SAVED_URIS)
                ?.let(imageUris::addAll)
        }

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

        if (savedInstanceState != null && restored.isNotEmpty()) {
            lifecycleScope.launch {
                val validated = withContext(Dispatchers.IO) {
                    WorkflowStateRestoration.validateReadableUris(this@StitchActivity, restored)
                }
                if (validated.unavailableCount > 0) {
                    imageUris.clear()
                    imageUris.addAll(validated.uris)
                    Toast.makeText(this@StitchActivity, R.string.workflow_restore_missing_media, Toast.LENGTH_LONG).show()
                    if (imageUris.isEmpty()) pickImagesLauncher.launch(arrayOf("image/*"))
                }
            }
        }

        // Auto-open picker on launch
        if (imageUris.isEmpty()) pickImagesLauncher.launch(arrayOf("image/*"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        WorkflowStateRestoration.putUris(outState, STATE_URIS, imageUris)
        outState.putBoolean(STATE_VERTICAL, isVertical.value)
        super.onSaveInstanceState(outState)
    }

    private fun stitchAndSave() {
        if (imageUris.size < 2 || isSaving.value) return
        isSaving.value = true
        val urisSnapshot = imageUris.toList()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = stitchFromUris(urisSnapshot, isVertical.value)
                if (result == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@StitchActivity, getString(R.string.stitch_one_title), Toast.LENGTH_SHORT).show()
                        isSaving.value = false
                    }
                    return@launch
                }

                saveToGallery(result)
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StitchActivity, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                    isSaving.value = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    /**
     * Streams the stitch from source URIs: reads bounds first, then decodes/scales/draws one
     * source at a time so peak memory stays at the result plus a single frame, instead of holding
     * every full-resolution image at once (which OOMed on large multi-image stitches).
     */
    private fun stitchFromUris(uris: List<Uri>, vertical: Boolean): Bitmap? {
        val cap = 2048 // bound the normalized edge to keep memory sane on very large sources
        val dims = uris.mapNotNull { uri ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } } catch (_: Exception) {}
            if (o.outWidth > 0 && o.outHeight > 0) uri to (o.outWidth to o.outHeight) else null
        }
        if (dims.size < 2) return null

        return if (vertical) {
            val maxW = minOf(dims.maxOf { it.second.first }, cap)
            val totalH = dims.sumOf { (it.second.second.toFloat() * maxW / it.second.first).toInt().coerceAtLeast(1) }
            val result = Bitmap.createBitmap(maxW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var y = 0f
            for ((uri, d) in dims) {
                val targetH = (d.second.toFloat() * maxW / d.first).toInt().coerceAtLeast(1)
                drawScaledFrame(canvas, uri, maxW, targetH, 0f, y)
                y += targetH
            }
            result
        } else {
            val maxH = minOf(dims.maxOf { it.second.second }, cap)
            val totalW = dims.sumOf { (it.second.first.toFloat() * maxH / it.second.second).toInt().coerceAtLeast(1) }
            val result = Bitmap.createBitmap(totalW, maxH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var x = 0f
            for ((uri, d) in dims) {
                val targetW = (d.first.toFloat() * maxH / d.second).toInt().coerceAtLeast(1)
                drawScaledFrame(canvas, uri, targetW, maxH, x, 0f)
                x += targetW
            }
            result
        }
    }

    private fun drawScaledFrame(canvas: Canvas, uri: Uri, targetW: Int, targetH: Int, x: Float, y: Float) {
        val src = decodeSampled(uri, maxOf(targetW, targetH)) ?: return
        try {
            val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
            canvas.drawBitmap(scaled, x, y, null)
            if (scaled !== src) scaled.recycle()
        } finally {
            src.recycle()
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
        val savePath = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val result = MediaStoreImageWriter.write(
            resolver = contentResolver,
            request = MediaStoreImageWriter.Request(
                displayName = "SnapCrop_Stitch_${System.currentTimeMillis()}.$ext",
                mimeType = mime,
                relativePath = savePath,
            ),
        ) { output ->
            bitmap.compress(fmt, quality, output)
        }

        runOnUiThread {
            if (result is MediaStoreImageWriter.Result.Success) {
                Toast.makeText(this, getString(R.string.stitch_saved), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                isSaving.value = false
            }
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
        Modifier.fillMaxSize().background(Black).safeDrawingPadding().imePadding()
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = OnSurface)
            }
            Text(stringResource(R.string.stitch_title), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = OnSurface, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.stitch_count, uris.size), color = OnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp))
        }

        // Direction toggle + add button
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val verticalCd = stringResource(
                R.string.stitch_vertical_cd,
                ""
            )
            FilterChip(
                selected = isVertical,
                onClick = { if (!isVertical) onToggleDirection() },
                label = { Text(stringResource(R.string.stitch_vertical)) },
                modifier = Modifier.semantics {
                    contentDescription = verticalCd
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                    containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                shape = RoundedCornerShape(8.dp)
            )
            val horizontalCd = stringResource(
                R.string.stitch_horizontal_cd,
                ""
            )
            FilterChip(
                selected = !isVertical,
                onClick = { if (isVertical) onToggleDirection() },
                label = { Text(stringResource(R.string.stitch_horizontal)) },
                modifier = Modifier.semantics {
                    contentDescription = horizontalCd
                },
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
                Text(stringResource(R.string.stitch_add), fontSize = 13.sp)
            }
        }

        // Preview
        if (uris.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.padding(14.dp).size(28.dp), tint = Primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.stitch_empty_title), color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.stitch_empty_subtitle),
                        color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        } else if (isVertical) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    count = uris.size,
                    key = { index -> stitchItemKey(uris, index) },
                    contentType = { "stitch-image-vertical" }
                ) { index ->
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
                                    modifier = Modifier.size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                ) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.stitch_move_up_cd, index + 1), tint = OnMediaSurface, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(4.dp))
                            }
                            if (index < uris.size - 1) {
                                IconButton(
                                    onClick = { onMoveDown(index) },
                                    modifier = Modifier.size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                ) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.stitch_move_down_cd, index + 1), tint = OnMediaSurface, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) { Icon(Icons.Default.Close, stringResource(R.string.stitch_remove_cd, index + 1), tint = Danger, modifier = Modifier.size(18.dp)) }
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
                items(
                    count = uris.size,
                    key = { index -> stitchItemKey(uris, index) },
                    contentType = { "stitch-image-horizontal" }
                ) { index ->
                    Box {
                        AsyncImage(
                            model = uris[index], contentDescription = null,
                            modifier = Modifier.fillMaxHeight().width(200.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.FillHeight
                        )
                        Column(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) { Icon(Icons.Default.Close, stringResource(R.string.stitch_remove_cd, index + 1), tint = Danger, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }

        // Output dimensions estimate
        if (uris.size >= 2) {
            val context = LocalContext.current
            val uriSnapshot = uris.toList()
            val dims by produceState<Pair<Int, Int>?>(initialValue = null, uriSnapshot, isVertical) {
                value = withContext(Dispatchers.IO) {
                    try {
                        var totalW = 0; var totalH = 0; var maxW = 0; var maxH = 0
                        for (u in uriSnapshot) {
                            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            context.contentResolver.openInputStream(u)?.use {
                                android.graphics.BitmapFactory.decodeStream(it, null, opts)
                            }
                            val w = opts.outWidth; val h = opts.outHeight
                            if (w > 0 && h > 0) {
                                if (isVertical) { maxW = maxOf(maxW, w); totalH += h } else { totalW += w; maxH = maxOf(maxH, h) }
                            }
                        }
                        if (isVertical) Pair(maxW, totalH) else Pair(totalW, maxH)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val resolvedDims = dims
            if (resolvedDims != null && resolvedDims.first > 0 && resolvedDims.second > 0) {
                Text(stringResource(R.string.stitch_dimensions, resolvedDims.first, resolvedDims.second),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = OnSurfaceVariant, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        // Save button
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSaving) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stitch_rendering), color = OnSurfaceVariant, fontSize = 13.sp)
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
                    Text(if (canSave) stringResource(R.string.stitch_save_button) else stringResource(R.string.stitch_one_title),
                        color = if (canSave) OnPrimary else OnSurfaceVariant,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
