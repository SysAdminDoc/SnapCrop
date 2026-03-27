package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
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

private data class DeviceFrame(
    val name: String,
    val bezelColor: Int,       // bezel fill color
    val cornerRadius: Float,   // fraction of width
    val bezelWidth: Float,     // fraction of width
    val screenRadius: Float,   // fraction of width for inner screen corners
    val notchHeight: Float     // fraction of screen height (0 = no notch)
)

private val frames = listOf(
    DeviceFrame("Pixel", 0xFF1A1A1A.toInt(), 0.08f, 0.03f, 0.06f, 0.015f),
    DeviceFrame("iPhone", 0xFF2C2C2E.toInt(), 0.10f, 0.025f, 0.08f, 0.02f),
    DeviceFrame("Samsung", 0xFF000000.toInt(), 0.07f, 0.02f, 0.05f, 0.01f),
    DeviceFrame("Flat", 0xFF333333.toInt(), 0.05f, 0.04f, 0.03f, 0f),
    DeviceFrame("White", 0xFFE0E0E0.toInt(), 0.08f, 0.03f, 0.06f, 0.015f),
)

class DeviceFrameActivity : ComponentActivity() {

    private val imageUri = mutableStateOf<Uri?>(null)
    private val selectedFrame = mutableStateOf(frames[0])
    private val isSaving = mutableStateOf(false)
    private val bgColor = mutableStateOf(0xFF000000.toInt())

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { imageUri.value = it } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Accept incoming image
        imageUri.value = intent?.data

        setContent {
            SnapCropTheme {
                FrameScreen(
                    imageUri = imageUri.value,
                    frame = selectedFrame.value,
                    isSaving = isSaving.value,
                    onFrameChange = { selectedFrame.value = it },
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    onSave = { renderAndSave() },
                    onClose = { finish() }
                )
            }
        }

        if (imageUri.value == null) pickImageLauncher.launch("image/*")
    }

    private fun renderAndSave() {
        val uri = imageUri.value ?: return
        if (isSaving.value) return
        isSaving.value = true
        val frame = selectedFrame.value

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (src == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceFrameActivity, "Failed to load", Toast.LENGTH_SHORT).show()
                        isSaving.value = false
                    }
                    return@launch
                }

                val screenW = src.width
                val screenH = src.height
                val bezel = (screenW * frame.bezelWidth).toInt()
                val totalW = screenW + bezel * 2
                val totalH = screenH + bezel * 2
                val padding = (totalW * 0.06f).toInt() // outer padding
                val canvasW = totalW + padding * 2
                val canvasH = totalH + padding * 2

                val result = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawColor(bgColor.value)

                val deviceRect = RectF(padding.toFloat(), padding.toFloat(),
                    (padding + totalW).toFloat(), (padding + totalH).toFloat())
                val cornerR = totalW * frame.cornerRadius

                // Device body
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = frame.bezelColor; style = Paint.Style.FILL
                }
                canvas.drawRoundRect(deviceRect, cornerR, cornerR, bodyPaint)

                // Screen area
                val screenRect = RectF(
                    deviceRect.left + bezel, deviceRect.top + bezel,
                    deviceRect.right - bezel, deviceRect.bottom - bezel
                )
                val screenR = totalW * frame.screenRadius

                // Clip to screen shape and draw screenshot
                canvas.save()
                val clipPath = Path()
                clipPath.addRoundRect(screenRect, screenR, screenR, Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.drawBitmap(src, null, screenRect, null)
                canvas.restore()

                // Screen border (subtle)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x30FFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f
                }
                canvas.drawRoundRect(screenRect, screenR, screenR, borderPaint)

                // Notch indicator (top center)
                if (frame.notchHeight > 0) {
                    val notchW = totalW * 0.25f
                    val notchH = screenH * frame.notchHeight
                    val notchRect = RectF(
                        canvasW / 2f - notchW / 2, screenRect.top,
                        canvasW / 2f + notchW / 2, screenRect.top + notchH
                    )
                    val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = frame.bezelColor; style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(notchRect, notchH / 2, notchH / 2, notchPaint)
                }

                src.recycle()
                withContext(Dispatchers.Main) { saveToGallery(result) }
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceFrameActivity, "Failed", Toast.LENGTH_SHORT).show()
                    isSaving.value = false
                }
            }
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Frame_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, getSharedPreferences("snapcrop", MODE_PRIVATE).getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show(); isSaving.value = false; return }
        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Mockup saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            contentResolver.delete(uri, null, null)
            isSaving.value = false
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
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = OnSurface) }
            Text("Device Mockup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f))
        }

        // Frame picker
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(frames) { f ->
                FilterChip(
                    selected = frame == f,
                    onClick = { onFrameChange(f) },
                    label = { Text(f.name, fontSize = 12.sp) },
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
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        AsyncImage(
                            model = imageUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                OutlinedButton(onClick = onPickImage, shape = RoundedCornerShape(12.dp)) {
                    Text("Pick Screenshot", color = OnSurface)
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Primary)
            } else {
                Button(
                    onClick = onSave, modifier = Modifier.fillMaxWidth(),
                    enabled = imageUri != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Mockup", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
