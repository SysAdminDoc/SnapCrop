package com.sysadmindoc.snapcrop

import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DeviceFrame(
    val key: String,
    @param:StringRes val nameRes: Int,
    val bezelColor: Int,       // bezel fill color
    val cornerRadius: Float,   // fraction of width
    val bezelWidth: Float,     // fraction of width
    val screenRadius: Float,   // fraction of width for inner screen corners
    val notchHeight: Float     // fraction of screen height (0 = no notch)
)

private val frames = listOf(
    DeviceFrame("pixel", R.string.device_frame_pixel, 0xFF1A1A1A.toInt(), 0.08f, 0.03f, 0.06f, 0.015f),
    DeviceFrame("iphone", R.string.device_frame_iphone, 0xFF2C2C2E.toInt(), 0.10f, 0.025f, 0.08f, 0.02f),
    DeviceFrame("samsung", R.string.device_frame_samsung, 0xFF000000.toInt(), 0.07f, 0.02f, 0.05f, 0.01f),
    DeviceFrame("flat", R.string.device_frame_flat, 0xFF333333.toInt(), 0.05f, 0.04f, 0.03f, 0f),
    DeviceFrame("white", R.string.device_frame_white, 0xFFE0E0E0.toInt(), 0.08f, 0.03f, 0.06f, 0.015f),
)

class DeviceFrameActivity : ComponentActivity() {

    private companion object {
        const val STATE_IMAGE_URI = "frame_image_uri"
        const val STATE_FRAME = "frame_key"
        const val STATE_BACKGROUND = "frame_background"
    }

    private val imageUri = mutableStateOf<Uri?>(null)
    private val selectedFrame = mutableStateOf(frames[0])
    private val isSaving = mutableStateOf(false)
    private val bgColor = mutableStateOf(0xFF000000.toInt())

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let {
        WorkflowStateRestoration.persistReadGrant(this, it)
        imageUri.value = it
    } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)

        val restored = WorkflowStateRestoration.restoreUris(savedInstanceState, STATE_IMAGE_URI)
        if (savedInstanceState != null) {
            imageUri.value = restored.firstOrNull()
            selectedFrame.value = frames.firstOrNull {
                it.key == savedInstanceState.getString(STATE_FRAME)
            } ?: frames[0]
            bgColor.value = savedInstanceState.getInt(STATE_BACKGROUND, 0xFF000000.toInt())
        } else {
            imageUri.value = intent?.data
        }

        setContent {
            SnapCropTheme {
                FrameScreen(
                    imageUri = imageUri.value,
                    frame = selectedFrame.value,
                    isSaving = isSaving.value,
                    onFrameChange = { selectedFrame.value = it },
                    onPickImage = { pickImageLauncher.launch(arrayOf("image/*")) },
                    onSave = { renderAndSave() },
                    onClose = { finish() }
                )
            }
        }

        if (savedInstanceState != null && restored.isNotEmpty()) {
            lifecycleScope.launch {
                val validated = withContext(Dispatchers.IO) {
                    WorkflowStateRestoration.validateReadableUris(this@DeviceFrameActivity, restored)
                }
                if (validated.unavailableCount > 0) {
                    imageUri.value = validated.uris.firstOrNull()
                    Toast.makeText(this@DeviceFrameActivity, R.string.workflow_restore_missing_media, Toast.LENGTH_LONG).show()
                    if (imageUri.value == null) pickImageLauncher.launch(arrayOf("image/*"))
                }
            }
        }
        if (imageUri.value == null) pickImageLauncher.launch(arrayOf("image/*"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        WorkflowStateRestoration.putUris(outState, STATE_IMAGE_URI, listOfNotNull(imageUri.value))
        outState.putString(STATE_FRAME, selectedFrame.value.key)
        outState.putInt(STATE_BACKGROUND, bgColor.value)
        super.onSaveInstanceState(outState)
    }

    private fun renderAndSave() {
        val uri = imageUri.value ?: return
        if (isSaving.value) return
        isSaving.value = true
        val frame = selectedFrame.value
        val backgroundColor = bgColor.value

        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = RasterCompositionPipeline.compose(
                resolver = contentResolver,
                uris = listOf(uri),
                minimumInputs = 1,
                allowedOmissions = emptySet(),
                planner = { bounds ->
                    bounds.singleOrNull()?.let {
                        RasterCompositionLayouts.deviceFrame(
                            bounds = it,
                            bezelWidthFraction = frame.bezelWidth,
                            backgroundColor = backgroundColor,
                        )
                    }
                },
                drawBeforeInputs = { canvas, layout ->
                    val geometry = layout.metadata
                    val cornerRadius = geometry.totalWidth * frame.cornerRadius
                    canvas.drawRoundRect(
                        RectF(geometry.deviceRect),
                        cornerRadius,
                        cornerRadius,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = frame.bezelColor
                            style = Paint.Style.FILL
                        },
                    )
                },
                drawInput = { canvas, layout, _, bitmap ->
                    val geometry = layout.metadata
                    val screenRect = RectF(geometry.screenRect)
                    val screenRadius = geometry.totalWidth * frame.screenRadius
                    val saveCount = canvas.save()
                    try {
                        canvas.clipPath(Path().apply {
                            addRoundRect(screenRect, screenRadius, screenRadius, Path.Direction.CW)
                        })
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            screenRect,
                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                        )
                    } finally {
                        canvas.restoreToCount(saveCount)
                    }
                },
                drawAfterInputs = { canvas, layout ->
                    val geometry = layout.metadata
                    val screenRect = RectF(geometry.screenRect)
                    val screenRadius = geometry.totalWidth * frame.screenRadius
                    canvas.drawRoundRect(
                        screenRect,
                        screenRadius,
                        screenRadius,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0x30FFFFFF
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        },
                    )
                    if (frame.notchHeight > 0f) {
                        val notchWidth = geometry.totalWidth * 0.25f
                        val notchHeight = geometry.screenHeight * frame.notchHeight
                        val notchRect = RectF(
                            layout.width / 2f - notchWidth / 2f,
                            screenRect.top,
                            layout.width / 2f + notchWidth / 2f,
                            screenRect.top + notchHeight,
                        )
                        canvas.drawRoundRect(
                            notchRect,
                            notchHeight / 2f,
                            notchHeight / 2f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = frame.bezelColor
                                style = Paint.Style.FILL
                            },
                        )
                    }
                },
            )) {
                is RasterCompositionResult.Success -> {
                    try {
                        saveToGallery(result.bitmap)
                    } catch (_: OutOfMemoryError) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@DeviceFrameActivity,
                                R.string.raster_composition_memory_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                            isSaving.value = false
                        }
                    } finally {
                        if (!result.bitmap.isRecycled) result.bitmap.recycle()
                    }
                }
                is RasterCompositionResult.ConfirmationRequired -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DeviceFrameActivity,
                            R.string.raster_composition_input_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                        isSaving.value = false
                    }
                }
                is RasterCompositionResult.Failure -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DeviceFrameActivity,
                            result.reason.messageRes,
                            Toast.LENGTH_LONG,
                        ).show()
                        isSaving.value = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    private fun getSaveFormat(): Triple<android.graphics.Bitmap.CompressFormat, Int, String> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
                Triple(fmt, quality, "webp")
            }
            prefs.getBoolean("use_jpeg", false) -> Triple(android.graphics.Bitmap.CompressFormat.JPEG, quality, "jpg")
            else -> Triple(android.graphics.Bitmap.CompressFormat.PNG, 100, "png")
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val (format, quality, ext) = getSaveFormat()
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val mimeType = when (ext) {
            "webp" -> "image/webp"
            "jpg" -> "image/jpeg"
            else -> "image/png"
        }
        val result = MediaStoreImageWriter.write(
            resolver = contentResolver,
            request = MediaStoreImageWriter.Request(
                displayName = "SnapCrop_Frame_${System.currentTimeMillis()}.$ext",
                mimeType = mimeType,
                relativePath = savePath,
            ),
        ) { output ->
            bitmap.compress(format, quality, output)
        }
        runOnUiThread {
            if (result is MediaStoreImageWriter.Result.Success) {
                Toast.makeText(this, getString(R.string.device_frame_saved), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                isSaving.value = false
            }
        }
    }
}

@Composable
private fun FrameScreen(
    imageUri: Uri?,
    frame: DeviceFrame,
    isSaving: Boolean,
    onFrameChange: (DeviceFrame) -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Black).safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = OnSurface) }
            Text(stringResource(R.string.device_frame_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f))
        }

        // Frame picker
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(frames, key = { it.key }, contentType = { "device-frame-chip" }) { f ->
                val frameName = stringResource(f.nameRes)
                val frameCd = stringResource(
                    R.string.device_frame_option_cd,
                    frameName,
                    if (frame == f) stringResource(R.string.selected_suffix) else ""
                )
                FilterChip(
                    selected = frame == f,
                    onClick = { onFrameChange(f) },
                    label = { Text(frameName, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = frameCd
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            if (imageUri != null) {
                // Simulated frame preview
                Card(
                    modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(9f / 19f),
                    colors = CardDefaults.cardColors(containerColor = Color(frame.bezelColor)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        AsyncImage(
                            model = imageUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.padding(14.dp).size(28.dp), tint = Primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.device_frame_empty_title), color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.device_frame_empty_subtitle),
                        color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onPickImage, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.device_frame_choose), color = OnSurface)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSaving) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.device_frame_rendering), color = OnSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val canSave = imageUri != null
                Button(
                    onClick = onSave, modifier = Modifier.fillMaxWidth(),
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
                    Text(if (canSave) stringResource(R.string.device_frame_save_button) else stringResource(R.string.device_frame_choose),
                        color = if (canSave) OnPrimary else OnSurfaceVariant,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
