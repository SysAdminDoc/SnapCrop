package com.sysadmindoc.snapcrop

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LongScreenshotReviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REVIEW_URI = "review_uri"
        const val EXTRA_REVIEW_PATH = "review_path"
        const val EXTRA_FRAME_COUNT = "frame_count"
        const val EXTRA_STOP_REASON = "stop_reason"
    }

    private var reviewUri by mutableStateOf<Uri?>(null)
    private var reviewPath: String? = null
    private var frameCount by mutableStateOf(0)
    private var stopReason by mutableStateOf("")
    private var isSaving by mutableStateOf(false)
    private var keepReviewFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        reviewUri = intent.getStringExtra(EXTRA_REVIEW_URI)?.let(Uri::parse)
        reviewPath = intent.getStringExtra(EXTRA_REVIEW_PATH)
        frameCount = intent.getIntExtra(EXTRA_FRAME_COUNT, 0)
        stopReason = intent.getStringExtra(EXTRA_STOP_REASON).orEmpty()

        setContent {
            SnapCropTheme {
                LongScreenshotReviewScreen(
                    uri = reviewUri,
                    frameCount = frameCount,
                    stopReason = stopReason,
                    isSaving = isSaving,
                    onSave = { saveAndOpenEditor() },
                    onRetry = { retryCapture() },
                    onDiscard = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        if (!keepReviewFile) LongScreenshotStore.deleteReviewFile(reviewPath)
        super.onDestroy()
    }

    private fun saveAndOpenEditor() {
        val uri = reviewUri ?: return
        if (isSaving) return
        isSaving = true

        lifecycleScope.launch(Dispatchers.IO) {
            val savedUri = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
                    try {
                        LongScreenshotStore.saveToGallery(this@LongScreenshotReviewActivity, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                isSaving = false
                if (savedUri == null) {
                    Toast.makeText(
                        this@LongScreenshotReviewActivity,
                        "Long screenshot save failed",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }

                Toast.makeText(
                    this@LongScreenshotReviewActivity,
                    "Long screenshot saved",
                    Toast.LENGTH_SHORT
                ).show()
                LongScreenshotStore.deleteReviewFile(reviewPath)
                keepReviewFile = true
                startActivity(
                    Intent(this@LongScreenshotReviewActivity, CropActivity::class.java).apply {
                        data = savedUri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
                finish()
            }
        }
    }

    private fun retryCapture() {
        LongScreenshotStore.deleteReviewFile(reviewPath)
        keepReviewFile = true
        val started = ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 900L)
        if (!started) {
            Toast.makeText(
                this,
                "Enable SnapCrop Long screenshot in Accessibility",
                Toast.LENGTH_LONG
            ).show()
        }
        finish()
    }
}

@Composable
private fun LongScreenshotReviewScreen(
    uri: Uri?,
    frameCount: Int,
    stopReason: String,
    isSaving: Boolean,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = com.sysadmindoc.snapcrop.ui.theme.Surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Review long screenshot", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            "$frameCount frames",
                            stopReason.ifBlank { null }
                        ).joinToString(" - "),
                        color = OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDiscard, enabled = !isSaving) {
                    Icon(Icons.Default.Close, "Discard", tint = OnSurface)
                }
            }

            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (uri == null) {
                    Text("Preview unavailable", color = OnSurfaceVariant)
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Long screenshot preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
                ) {
                    Icon(Icons.Default.Refresh, "Retry", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
                ) {
                    Icon(Icons.Default.Close, "Discard", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Discard")
                }
                Button(
                    onClick = onSave,
                    enabled = uri != null && !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Save, "Save and edit", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Edit")
                }
            }
        }
    }
}
