package com.sysadmindoc.snapcrop

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sysadmindoc.snapcrop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoClipActivity : ComponentActivity() {
    private var videoUri: Uri? = null
    private val isWorking = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        videoUri = intent.data
        if (videoUri == null) {
            finish()
            return
        }

        setContent {
            SnapCropTheme {
                VideoClipScreen(
                    uri = videoUri!!,
                    isWorking = isWorking.value,
                    onClose = { finish() },
                    onOpenExternally = { openExternally(videoUri!!) },
                    onGrabFrame = { timeMs -> grabFrame(timeMs) },
                    onTrimClip = { startMs, endMs -> trimClip(startMs, endMs) }
                )
            }
        }
    }

    private fun getSaveFormat(): FrameSaveFormat {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                FrameSaveFormat(format, quality, "webp", "image/webp")
            }
            prefs.getBoolean("use_jpeg", false) ->
                FrameSaveFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
            else -> FrameSaveFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
        }
    }

    private fun grabFrame(timeMs: Long) {
        val uri = videoUri ?: return
        if (isWorking.value) return
        isWorking.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val frame = VideoClipExporter.frameAt(this@VideoClipActivity, uri, timeMs)
                if (frame == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoClipActivity, "Frame capture failed", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val saveFormat = getSaveFormat()
                val savePath = getSharedPreferences("snapcrop", MODE_PRIVATE)
                    .getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
                val savedUri = try {
                    VideoClipExporter.saveFrameToGallery(
                        contentResolver,
                        frame,
                        saveFormat.format,
                        saveFormat.quality,
                        saveFormat.ext,
                        saveFormat.mime,
                        savePath
                    )
                } finally {
                    frame.recycle()
                }

                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(this@VideoClipActivity, "Frame saved", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@VideoClipActivity, CropActivity::class.java).apply { data = savedUri })
                    } else {
                        Toast.makeText(this@VideoClipActivity, "Frame save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isWorking.value = false
                }
            }
        }
    }

    private fun trimClip(startMs: Long, endMs: Long) {
        val uri = videoUri ?: return
        if (endMs - startMs < 1000L) {
            Toast.makeText(this, "Choose at least one second", Toast.LENGTH_SHORT).show()
            return
        }
        if (isWorking.value) return
        isWorking.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedUri = VideoClipExporter.trimToGallery(this@VideoClipActivity, uri, startMs, endMs)
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(this@VideoClipActivity, "Clip saved to Movies/SnapCrop", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@VideoClipActivity, "Clip trim failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isWorking.value = false
                }
            }
        }
    }

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No video player available", Toast.LENGTH_SHORT).show()
        }
    }
}

private data class FrameSaveFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String
)

@Composable
private fun VideoClipScreen(
    uri: Uri,
    isWorking: Boolean,
    onClose: () -> Unit,
    onOpenExternally: () -> Unit,
    onGrabFrame: (Long) -> Unit,
    onTrimClip: (Long, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var durationMs by remember { mutableLongStateOf(0L) }
    var framePosition by remember { mutableFloatStateOf(0f) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        durationMs = withContext(Dispatchers.IO) { VideoClipExporter.durationMs(context, uri) }
        val end = durationMs.toFloat().coerceAtLeast(1f)
        framePosition = 0f
        trimRange = 0f..end
        isLoading = false
    }

    LaunchedEffect(uri, framePosition, durationMs) {
        if (durationMs <= 0L) return@LaunchedEffect
        delay(140)
        val bitmap = withContext(Dispatchers.IO) {
            VideoClipExporter.frameAt(context, uri, framePosition.toLong())
        }
        preview = bitmap?.asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Back", tint = OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Screen Recording", color = OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Trim a clip or grab a frame for editing", color = OnSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onOpenExternally) {
                Icon(Icons.Default.PlayCircle, "Play video", tint = OnSurfaceVariant)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val image = preview
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Selected video frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Move the scrubber to preview a frame", color = OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Frame", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMs(framePosition.toLong()), color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = framePosition.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { framePosition = it },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Primary,
                    activeTrackColor = Primary,
                    inactiveTrackColor = SurfaceVariant
                )
            )
            Text(formatMs(durationMs), color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        }

        Button(
            onClick = {
                onGrabFrame(framePosition.toLong())
            },
            enabled = !isWorking && durationMs > 0L,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Crop, null, modifier = Modifier.size(16.dp), tint = Color.Black)
            Spacer(Modifier.width(6.dp))
            Text("Grab frame and edit", color = Color.Black)
        }

        Spacer(Modifier.height(20.dp))

        Text("Trim clip", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "${formatMs(trimRange.start.toLong())} - ${formatMs(trimRange.endInclusive.toLong())}",
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
        RangeSlider(
            value = trimRange,
            onValueChange = {
                trimRange = it.start.coerceAtLeast(0f)..it.endInclusive.coerceAtMost(durationMs.toFloat())
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = SurfaceVariant
            )
        )

        OutlinedButton(
            onClick = {
                onTrimClip(trimRange.start.toLong(), trimRange.endInclusive.toLong())
            },
            enabled = !isWorking && trimRange.endInclusive - trimRange.start >= 1000f,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
        ) {
            Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save trimmed clip")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Frames save to your SnapCrop image folder. Trimmed clips save to Movies/SnapCrop.",
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(20.dp))
    }
}

private fun formatMs(value: Long): String {
    val totalSeconds = (value / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
