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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        applySecureWindow(this)
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

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
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
                        Toast.makeText(this@VideoClipActivity, getString(R.string.video_frame_failed), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@VideoClipActivity, getString(R.string.video_frame_saved), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@VideoClipActivity, CropActivity::class.java).apply { data = savedUri })
                    } else {
                        Toast.makeText(this@VideoClipActivity, getString(R.string.video_frame_save_failed), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.video_min_duration), Toast.LENGTH_SHORT).show()
            return
        }
        if (isWorking.value) return
        isWorking.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedUri = VideoClipExporter.trimToGallery(this@VideoClipActivity, uri, startMs, endMs)
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(this@VideoClipActivity, getString(R.string.video_trim_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@VideoClipActivity, getString(R.string.video_trim_failed), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.video_no_player), Toast.LENGTH_SHORT).show()
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
            .background(Black)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.video_screen_recording), color = OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.video_empty_subtitle), color = OnSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onOpenExternally) {
                Icon(Icons.Default.PlayCircle, stringResource(R.string.video_play), tint = OnSurfaceVariant)
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
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(MediaSurface),
                contentAlignment = Alignment.Center
            ) {
                val image = preview
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.video_frame_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(stringResource(R.string.video_scrubber_hint), color = OnMediaSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.video_frame_label), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMs(framePosition.toLong()), color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            val framePositionCd = stringResource(
                R.string.video_frame_slider_cd,
                formatMs(framePosition.toLong()),
                formatMs(durationMs)
            )
            Slider(
                value = framePosition.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { framePosition = it },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = framePositionCd
                },
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
            Icon(Icons.Default.Crop, null, modifier = Modifier.size(16.dp), tint = OnPrimary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.video_grab_edit), color = OnPrimary)
        }

        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.video_trim_clip), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "${formatMs(trimRange.start.toLong())} - ${formatMs(trimRange.endInclusive.toLong())}",
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
        val trimRangeCd = stringResource(
            R.string.video_trim_slider_cd,
            formatMs(trimRange.start.toLong()),
            formatMs(trimRange.endInclusive.toLong())
        )
        RangeSlider(
            value = trimRange,
            onValueChange = {
                trimRange = it.start.coerceAtLeast(0f)..it.endInclusive.coerceAtMost(durationMs.toFloat())
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.semantics {
                contentDescription = trimRangeCd
            },
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
            Text(stringResource(R.string.video_save_trim))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.video_footer),
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
