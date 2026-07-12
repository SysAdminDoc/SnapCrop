package com.sysadmindoc.snapcrop

import android.app.RecoverableSecurityException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sysadmindoc.snapcrop.ui.theme.OnPrimary
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import com.sysadmindoc.snapcrop.ui.theme.MediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnMediaSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class LongScreenshotReviewActivity : ComponentActivity() {
    private var pendingSavedUri: Uri? = null

    private val seedMutationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        openSavedResult()
    }
    companion object {
        const val EXTRA_REVIEW_URI = "review_uri"
        const val EXTRA_REVIEW_PATH = "review_path"
        const val EXTRA_BUNDLE_PATH = "review_bundle_path"
        const val EXTRA_FRAME_COUNT = "frame_count"
        const val EXTRA_STOP_REASON = "stop_reason"
        private const val KEY_PENDING_SAVED_URI = "pending_saved_uri"
    }

    private var reviewUri by mutableStateOf<Uri?>(null)
    private var reviewPath: String? = null
    private var bundlePath: String? = null
    private var frameCount by mutableStateOf(0)
    private var stopReason by mutableStateOf("")
    private var isSaving by mutableStateOf(false)
    private var isRendering by mutableStateOf(false)
    private var stitchPlan by mutableStateOf<ScrollStitchPlan?>(null)
    private var previewRevision by mutableStateOf(0)
    @Volatile private var renderGeneration = 0
    private val renderPublishLock = Any()
    private var keepReviewFile = false

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        pendingSavedUri = savedInstanceState?.getString(KEY_PENDING_SAVED_URI)?.let(Uri::parse)

        reviewUri = intent.getStringExtra(EXTRA_REVIEW_URI)?.let(Uri::parse)
        reviewPath = intent.getStringExtra(EXTRA_REVIEW_PATH)
        bundlePath = intent.getStringExtra(EXTRA_BUNDLE_PATH)
        frameCount = intent.getIntExtra(EXTRA_FRAME_COUNT, 0)
        stopReason = intent.getStringExtra(EXTRA_STOP_REASON).orEmpty()
        stitchPlan = LongScreenshotStore.loadPlan(this, bundlePath)

        setContent {
            SnapCropTheme {
                LongScreenshotReviewScreen(
                    uri = reviewUri,
                    frameCount = frameCount,
                    stopReason = stopReason,
                    isBusy = isSaving || isRendering,
                    stitchPlan = stitchPlan,
                    previewRevision = previewRevision,
                    onPlanChanged = { plan, render -> updateStitchPlan(plan, render) },
                    onRenderPlan = { stitchPlan?.let { updateStitchPlan(it, true) } },
                    onSave = { saveAndOpenEditor() },
                    onRetry = { retryCapture() },
                    onDiscard = { finish() }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingSavedUri?.let { outState.putString(KEY_PENDING_SAVED_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (!keepReviewFile) {
            LongScreenshotStore.deleteReviewFile(reviewPath)
            LongScreenshotStore.deleteReviewBundle(this, bundlePath)
        }
        super.onDestroy()
    }

    private fun updateStitchPlan(plan: ScrollStitchPlan, render: Boolean) {
        stitchPlan = plan
        if (!render || isSaving) return
        val generation = ++renderGeneration
        isRendering = true
        lifecycleScope.launch(Dispatchers.IO) {
            val rendered = LongScreenshotStore.renderBundle(this@LongScreenshotReviewActivity, bundlePath, plan)
            val published = rendered?.let { bitmap ->
                try {
                    synchronized(renderPublishLock) {
                        generation == renderGeneration &&
                                LongScreenshotStore.writePreview(reviewPath, bitmap) &&
                                LongScreenshotStore.savePlan(this@LongScreenshotReviewActivity, bundlePath, plan)
                    }
                } finally {
                    bitmap.recycle()
                }
            } == true
            withContext(Dispatchers.Main) {
                if (generation != renderGeneration) return@withContext
                isRendering = false
                if (published) {
                    previewRevision++
                } else {
                    Toast.makeText(
                        this@LongScreenshotReviewActivity,
                        getString(R.string.long_screenshot_seam_render_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveAndOpenEditor() {
        val uri = reviewUri ?: return
        if (isSaving) return
        isSaving = true

        lifecycleScope.launch(Dispatchers.IO) {
            val savedUri = try {
                val bitmap = stitchPlan?.let { plan ->
                    LongScreenshotStore.renderBundle(this@LongScreenshotReviewActivity, bundlePath, plan)
                } ?: contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                if (bitmap == null) {
                    null
                } else {
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
                        getString(R.string.long_screenshot_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }

                Toast.makeText(
                    this@LongScreenshotReviewActivity,
                    getString(R.string.long_screenshot_saved),
                    Toast.LENGTH_SHORT
                ).show()
                LongScreenshotStore.deleteReviewFile(reviewPath)
                LongScreenshotStore.deleteReviewBundle(this@LongScreenshotReviewActivity, bundlePath)
                keepReviewFile = true
                pendingSavedUri = savedUri
                if (!requestSeedTrash()) openSavedResult()
            }
        }
    }

    private fun requestSeedTrash(): Boolean {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val seedTime = prefs.getLong(ScreenshotService.PREF_LAST_SEED_TIME, 0L)
        if (System.currentTimeMillis() - seedTime > 120_000) return false
        val seedUriStr = prefs.getString(ScreenshotService.PREF_LAST_SEED_URI, null) ?: return false
        val seedUri = Uri.parse(seedUriStr)
        prefs.edit().remove(ScreenshotService.PREF_LAST_SEED_URI).remove(ScreenshotService.PREF_LAST_SEED_TIME).apply()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createTrashRequest(contentResolver, listOf(seedUri), true)
                seedMutationLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                return true
            } else {
                try {
                    contentResolver.delete(seedUri, null, null)
                } catch (recoverable: RecoverableSecurityException) {
                    seedMutationLauncher.launch(
                        IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build()
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SnapCrop", "Unable to request seed screenshot trash", e)
        }
        return false
    }

    private fun openSavedResult() {
        val savedUri = pendingSavedUri ?: return
        pendingSavedUri = null
        startActivity(
            Intent(this, CropActivity::class.java).apply {
                data = savedUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        finish()
    }

    private fun retryCapture() {
        LongScreenshotStore.deleteReviewFile(reviewPath)
        LongScreenshotStore.deleteReviewBundle(this, bundlePath)
        keepReviewFile = true
        val started = ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 900L)
        if (!started) {
            Toast.makeText(
                this,
                getString(R.string.toast_enable_accessibility),
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
    isBusy: Boolean,
    stitchPlan: ScrollStitchPlan?,
    previewRevision: Int,
    onPlanChanged: (ScrollStitchPlan, Boolean) -> Unit,
    onRenderPlan: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        color = com.sysadmindoc.snapcrop.ui.theme.Surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.long_screenshot_review_title), color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            stringResource(R.string.long_screenshot_frame_count, frameCount),
                            stopReason.ifBlank { null }
                        ).joinToString(" - "),
                        color = OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDiscard, enabled = !isBusy) {
                    Icon(Icons.Default.Close, stringResource(R.string.long_screenshot_discard), tint = OnSurface)
                }
            }

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MediaSurface),
                contentAlignment = Alignment.Center
            ) {
                if (uri == null) {
                    Text(stringResource(R.string.long_screenshot_preview_unavailable), color = OnMediaSurfaceVariant)
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .memoryCacheKey("$uri:$previewRevision")
                            .build(),
                        contentDescription = stringResource(R.string.long_screenshot_preview_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (stitchPlan != null && stitchPlan.seamCount > 0) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.long_screenshot_seam_title),
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.long_screenshot_seam_help),
                        color = OnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (nextIndex in 1 until stitchPlan.frames.size) {
                            val next = stitchPlan.frames[nextIndex]
                            val previous = stitchPlan.frames[nextIndex - 1]
                            Surface(
                                modifier = Modifier.width(280.dp),
                                color = com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        stringResource(
                                            R.string.long_screenshot_join_label,
                                            nextIndex,
                                            stitchPlan.seamCount,
                                            stitchPlan.outputJoinY(nextIndex)
                                        ),
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(
                                            R.string.long_screenshot_next_start,
                                            next.cropTop,
                                            next.detectedCropTop
                                        ),
                                        color = OnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Slider(
                                        value = next.cropTop.toFloat(),
                                        onValueChange = { value ->
                                            onPlanChanged(stitchPlan.withCropTop(nextIndex, value.roundToInt()), false)
                                        },
                                        onValueChangeFinished = onRenderPlan,
                                        valueRange = 0f..(next.frameHeight - next.bottomTrim - 1).coerceAtLeast(1).toFloat(),
                                        enabled = !isBusy
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { onPlanChanged(stitchPlan.withCropTop(nextIndex, next.cropTop - 8), true) },
                                            enabled = !isBusy
                                        ) { Text(stringResource(R.string.long_screenshot_nudge_up), color = Secondary) }
                                        TextButton(
                                            onClick = { onPlanChanged(stitchPlan.withCropTop(nextIndex, next.cropTop + 8), true) },
                                            enabled = !isBusy
                                        ) { Text(stringResource(R.string.long_screenshot_nudge_down), color = Secondary) }
                                    }
                                    Text(
                                        stringResource(
                                            R.string.long_screenshot_previous_trim,
                                            previous.bottomTrim,
                                            previous.detectedBottomTrim
                                        ),
                                        color = OnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Slider(
                                        value = previous.bottomTrim.toFloat(),
                                        onValueChange = { value ->
                                            onPlanChanged(stitchPlan.withBottomTrim(nextIndex - 1, value.roundToInt()), false)
                                        },
                                        onValueChangeFinished = onRenderPlan,
                                        valueRange = 0f..(previous.frameHeight - previous.cropTop - 1).coerceAtLeast(1).toFloat(),
                                        enabled = !isBusy
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { onPlanChanged(stitchPlan.withBottomTrim(nextIndex - 1, previous.bottomTrim - 8), true) },
                                            enabled = !isBusy
                                        ) { Text(stringResource(R.string.long_screenshot_trim_less), color = Secondary) }
                                        TextButton(
                                            onClick = { onPlanChanged(stitchPlan.withBottomTrim(nextIndex - 1, previous.bottomTrim + 8), true) },
                                            enabled = !isBusy
                                        ) { Text(stringResource(R.string.long_screenshot_trim_more), color = Secondary) }
                                    }
                                    TextButton(
                                        onClick = { onPlanChanged(stitchPlan.resetJoin(nextIndex), true) },
                                        enabled = !isBusy
                                    ) { Text(stringResource(R.string.long_screenshot_reset_join), color = Secondary) }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
                ) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.long_screenshot_retry), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.long_screenshot_retry))
                }
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.long_screenshot_discard), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.long_screenshot_discard))
                }
                Button(
                    onClick = onSave,
                    enabled = uri != null && !isBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Default.Save, stringResource(R.string.long_screenshot_save_edit), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.long_screenshot_save_edit))
                }
            }
        }
    }
}
