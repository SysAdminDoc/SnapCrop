package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.app.RecoverableSecurityException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import kotlin.math.roundToInt
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import com.sysadmindoc.snapcrop.ui.theme.Black
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

private data class CropExportFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String
)

private enum class SourceMutationPurpose { REPLACE_AFTER_SAVE, DELETE_FROM_EDITOR }

class CropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_FLASH = "show_flash"
        private const val KEY_HAS_DRAFT = "has_editor_draft"
        private const val KEY_MUTATION_PURPOSE = "source_mutation_purpose"
        private const val KEY_MUTATION_MESSAGE = "source_mutation_message"
    }

    private var originalBitmap: Bitmap? = null
    private val bitmapState = mutableStateOf<Bitmap?>(null)
    private val cropRect = mutableStateOf(Rect(0, 0, 0, 0))
    private val cropMethod = mutableStateOf("")
    private val isLoading = mutableStateOf(true)
    private val isSaving = mutableStateOf(false)
    private val showFlash = mutableStateOf(false)
    private var sourceUri: Uri? = null
    private var draftStateProvider: (() -> EditorDraft)? = null
    private val draftFile get() = File(filesDir, "editor_draft.json")
    private var intentSourceHints: List<String> = emptyList()
    private var currentCropHints: List<String> = emptyList()
    private val initialPixelateRects = mutableStateOf<List<Rect>>(emptyList())
    private val initialDrawPaths = mutableStateOf<List<DrawPath>>(emptyList())
    private val initialAdjustments = mutableStateOf<FloatArray?>(null)
    private val projectLoadError = mutableStateOf<String?>(null)
    private val rotationKey = mutableIntStateOf(0)
    private var pendingMutationPurpose: SourceMutationPurpose? = null
    private var pendingExportMessage: String? = null
    private var pendingRelinkProject: SnapCropProject? = null
    private var pendingProjectPolicy = SourceVerificationPolicy.REQUIRE_FINGERPRINT
    private val projectCanRelink = mutableStateOf(false)

    private val sourceMutationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            completeSourceMutation(result.resultCode == RESULT_OK)
        } else if (result.resultCode == RESULT_OK) {
            deleteSourceOnLegacyAndroid()
        } else {
            completeSourceMutation(false)
        }
    }

    private val projectSourcePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val project = pendingRelinkProject ?: return@registerForActivityResult
        if (uri == null || !uri.scheme.equals("content", ignoreCase = true)) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        isLoading.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            verifyAndLoadProjectSource(uri, project, pendingProjectPolicy)
            withContext(Dispatchers.Main) { isLoading.value = false }
        }
    }

    private fun handleIntent(incomingIntent: Intent) {
        val newUri = when {
            incomingIntent.data != null -> incomingIntent.data
            incomingIntent.action == Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }
        if (newUri == null) { finish(); return }

        // Reset state for the new image or project sidecar.
        sourceUri = null
        intentSourceHints = CropSourceHints.fromIntent(incomingIntent, newUri)
        isLoading.value = true
        bitmapState.value = null
        cropMethod.value = ""
        initialPixelateRects.value = emptyList()
        initialDrawPaths.value = emptyList()
        initialAdjustments.value = null
        projectLoadError.value = null
        projectCanRelink.value = false
        pendingRelinkProject = null

        showFlash.value = incomingIntent.getBooleanExtra(EXTRA_SHOW_FLASH, false)
        if (showFlash.value) vibrateShort()

        lifecycleScope.launch(Dispatchers.IO) {
            val mimeType = incomingIntent.type ?: contentResolver.getType(newUri)
            if (SnapCropProjectSidecar.looksLikeProject(mimeType, newUri.lastPathSegment)) {
                loadProjectSidecar(newUri)
            } else {
                sourceUri = newUri
                loadBitmap(newUri)
            }
            withContext(Dispatchers.Main) { isLoading.value = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureScreen()
        pendingMutationPurpose = savedInstanceState?.getString(KEY_MUTATION_PURPOSE)
            ?.let { runCatching { SourceMutationPurpose.valueOf(it) }.getOrNull() }
        pendingExportMessage = savedInstanceState?.getString(KEY_MUTATION_MESSAGE)
        // Restore an in-progress edit checkpointed before a process death; otherwise open the intent.
        val draft = if (savedInstanceState?.getBoolean(KEY_HAS_DRAFT) == true) {
            runCatching { draftFile.takeIf { it.exists() }?.readText() }.getOrNull()
        } else null
        if (draft != null) {
            runCatching { draftFile.delete() }
            loadProjectFromJson(draft)
        } else {
            handleIntent(intent)
        }

        setContent {
            SnapCropTheme {
                val showDeleteConfirm = remember { mutableStateOf(false) }
                val replaceOriginalOnSave = remember { effectiveDeleteOriginalOnSave() }
                Box(Modifier.fillMaxSize().background(Black)) {
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Primary
                        )
                    }

                    bitmapState.value?.let { bmp ->
                        CropEditorScreen(
                            bitmap = bmp,
                            initialCropRect = cropRect.value,
                            cropMethod = cropMethod.value,
                            initialPixelateRects = initialPixelateRects.value,
                            initialDrawPaths = initialDrawPaths.value,
                            initialAdjustments = initialAdjustments.value,
                            registerStateProvider = { provider -> draftStateProvider = provider },
                            onSave = { rect, pix, draw, adj -> saveCropped(bmp, rect, pix, draw, adj, deleteOriginal = effectiveDeleteOriginalOnSave()) },
                            onSaveCopy = { rect, pix, draw, adj -> saveCropped(bmp, rect, pix, draw, adj, deleteOriginal = false) },
                            onShare = { rect, pix, draw, adj -> shareCropped(bmp, rect, pix, draw, adj) },
                            onCopyClipboard = { rect, pix, draw, adj -> copyToClipboard(bmp, rect, pix, draw, adj) },
                            onDiscard = { finish() },
                            onDelete = { showDeleteConfirm.value = true },
                            onAutoCrop = {
                                val sbPx = SystemBars.statusBarHeight(resources)
                                val nbPx = SystemBars.navigationBarHeight(resources)
                                val result = AutoCrop.detectWithMethod(
                                    bitmap = bmp,
                                    statusBarPx = sbPx,
                                    navBarPx = nbPx,
                                    sourceHints = currentCropHints,
                                    userProfiles = UserAppProfileStore.load(getSharedPreferences("snapcrop", MODE_PRIVATE)),
                                    appProfilesEnabled = appCropProfilesEnabled()
                                )
                                cropMethod.value = result.method
                                result.rect
                            },
                            onSmartCrop = {
                                lifecycleScope.launch {
                                    val rect = SmartCropEngine.detect(bmp)
                                    cropRect.value = rect
                                    cropMethod.value = "ai"
                                }
                            },
                            onResize = { maxDim ->
                                val current = bitmapState.value ?: return@CropEditorScreen
                                if (current.width <= maxDim && current.height <= maxDim) return@CropEditorScreen
                                val scale = maxDim.toFloat() / maxOf(current.width, current.height)
                                val newW = (current.width * scale).toInt()
                                val newH = (current.height * scale).toInt()
                                val resized = Bitmap.createScaledBitmap(current, newW, newH, true)
                                if (current !== originalBitmap) current.recycle()
                                originalBitmap?.recycle(); originalBitmap = null
                                bitmapState.value = resized
                                cropRect.value = Rect(0, 0, newW, newH)
                                cropMethod.value = ""
                            },
                            onRemoveBg = { onDone ->
                                lifecycleScope.launch {
                                    val result = BackgroundRemover.removeWithStatus(this@CropActivity, bmp)
                                    if (result.changed && result.bitmap !== bmp) {
                                        val old = bitmapState.value
                                        if (old != null && old !== originalBitmap) old.recycle()
                                        originalBitmap = null
                                        bitmapState.value = result.bitmap
                                        cropRect.value = android.graphics.Rect(0, 0, result.bitmap.width, result.bitmap.height)
                                        cropMethod.value = ""
                                    }
                                    onDone(result.statusMessage)
                                }
                            },
                            onRotate = { rotateBitmap() },
                            onFlipH = { flipBitmap(horizontal = true) },
                            onFlipV = { flipBitmap(horizontal = false) },
                            onOcrIndexed = { text, codes ->
                                val uri = sourceUri
                                val indexEnabled = getSharedPreferences("snapcrop", MODE_PRIVATE)
                                    .getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
                                if (uri != null && indexEnabled) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        ScreenshotIndexStore(this@CropActivity)
                                            .updateRecognizedText(uri, text, codes)
                                    }
                                }
                            },
                            replaceOriginalOnSave = replaceOriginalOnSave
                        )
                    }

                    projectLoadError.value?.let { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Black)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = SurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(R.string.crop_project_unavailable),
                                        color = OnSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        message,
                                        color = OnSurfaceVariant,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    if (projectCanRelink.value) {
                                        TextButton(onClick = { projectSourcePicker.launch(arrayOf("image/*")) }) {
                                            Text(stringResource(R.string.crop_choose_source), color = Primary)
                                        }
                                    }
                                    TextButton(onClick = { finish() }) {
                                        Text(stringResource(R.string.close), color = Primary)
                                    }
                                }
                            }
                        }
                    }

                    // Saving overlay
                    if (isSaving.value) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center) {
                            Surface(
                                color = SurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.crop_saving_title),
                                        color = OnSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (effectiveDeleteOriginalOnSave())
                                            stringResource(R.string.crop_saving_replace)
                                        else
                                            stringResource(R.string.crop_saving_copy),
                                        color = OnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (showFlash.value) {
                        val flashAlpha = remember { Animatable(0.9f) }
                        LaunchedEffect(Unit) { flashAlpha.animateTo(0f, tween(300)) }
                        if (flashAlpha.value > 0.01f) {
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
                        }
                    }
                }
                if (showDeleteConfirm.value) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm.value = false },
                        title = { Text(stringResource(R.string.crop_delete_title), color = OnSurface) },
                        text = {
                            Text(
                                stringResource(R.string.crop_delete_body),
                                color = OnSurfaceVariant,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteConfirm.value = false
                                    requestSourceTrash(SourceMutationPurpose.DELETE_FROM_EDITOR)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Tertiary)
                            ) {
                                Text(stringResource(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.move_to_trash
                                    else R.string.delete_permanently
                                ))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm.value = false }) {
                                Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                            }
                        },
                        containerColor = SurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }

    private fun getDeletePref(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("delete_original", true)

    private fun appCropProfilesEnabled(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("app_crop_profiles", true)

    private fun projectSidecarsEnabled(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("project_sidecars", false)

    private fun effectiveDeleteOriginalOnSave(): Boolean =
        getDeletePref() && !projectSidecarsEnabled()

    private fun getSaveFormat(): Pair<Bitmap.CompressFormat, Int> {
        val resolved = getExportFormat(forcePng = false)
        return resolved.format to resolved.quality
    }

    private fun getExportFormat(forcePng: Boolean): CropExportFormat {
        if (forcePng) {
            return CropExportFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
        }
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                          else Bitmap.CompressFormat.WEBP
                CropExportFormat(fmt, quality, "webp", "image/webp")
            }
            prefs.getBoolean("use_jpeg", false) -> CropExportFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
            else -> CropExportFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
        }
    }

    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun loadProjectSidecar(uri: Uri) {
        val result = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                SnapCropProjectSidecar.decode(stream, ProjectImportOrigin.EXTERNAL)
            } ?: ProjectDecodeResult.Rejected(ProjectRejectReason.MALFORMED)
        } catch (_: Exception) {
            ProjectDecodeResult.Rejected(ProjectRejectReason.MALFORMED)
        }
        handleProjectDecode(result, ProjectImportOrigin.EXTERNAL)
    }

    private fun loadProjectFromJson(json: String) {
        handleProjectDecode(
            SnapCropProjectSidecar.decodeString(json, ProjectImportOrigin.INTERNAL_DRAFT),
            ProjectImportOrigin.INTERNAL_DRAFT
        )
    }

    private fun handleProjectDecode(result: ProjectDecodeResult, origin: ProjectImportOrigin) {
        if (result is ProjectDecodeResult.Rejected) {
            val message = when (result.reason) {
                ProjectRejectReason.TOO_LARGE -> getString(R.string.crop_project_too_large)
                ProjectRejectReason.UNSUPPORTED_SCHEMA, ProjectRejectReason.UNSUPPORTED_VERSION ->
                    getString(R.string.crop_project_unsupported)
                ProjectRejectReason.MISSING_FINGERPRINT -> getString(R.string.crop_project_no_fingerprint)
                else -> getString(R.string.crop_project_invalid)
            }
            showProjectError(message)
            return
        }
        val project = (result as ProjectDecodeResult.Success).project
        val policy = if (origin == ProjectImportOrigin.EXTERNAL) {
            SourceVerificationPolicy.REQUIRE_FINGERPRINT
        } else {
            SourceVerificationPolicy.ALLOW_MISSING_FINGERPRINT
        }
        pendingRelinkProject = project
        pendingProjectPolicy = policy
        val source = project.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (source == null || !source.scheme.equals("content", ignoreCase = true) ||
            (origin == ProjectImportOrigin.EXTERNAL && !canAutoOpenProjectSource(source))) {
            showProjectError(getString(R.string.crop_project_choose_source), canRelink = true)
            return
        }
        verifyAndLoadProjectSource(source, project, policy)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingMutationPurpose?.let { outState.putString(KEY_MUTATION_PURPOSE, it.name) }
        pendingExportMessage?.let { outState.putString(KEY_MUTATION_MESSAGE, it) }
        // Checkpoint the in-progress edit as a project draft so a low-memory kill doesn't lose work.
        val provider = draftStateProvider ?: return
        if (sourceUri == null || bitmapState.value == null) return
        try {
            val d = provider()
            val json = buildProjectSidecarJson(
                rect = d.crop,
                pixRects = d.pix,
                drawPaths = d.draws,
                adj = d.adj,
                deleteOriginal = effectiveDeleteOriginalOnSave(),
                exportFormat = getExportFormat(forcePng = false),
                savePath = getSharedPreferences("snapcrop", MODE_PRIVATE).getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop",
                computeHash = false
            )
            draftFile.writeText(json)
            outState.putBoolean(KEY_HAS_DRAFT, true)
        } catch (_: Exception) {
        }
    }

    private fun showProjectError(message: String, canRelink: Boolean = false) {
        runOnUiThread {
            bitmapState.value = null
            projectLoadError.value = message
            projectCanRelink.value = canRelink
            isLoading.value = false
        }
    }

    private fun canAutoOpenProjectSource(uri: Uri): Boolean {
        if (uri.authority == "media" || uri.authority?.startsWith("com.android.providers.media") == true) return true
        return contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri == uri }
    }

    private fun verifyAndLoadProjectSource(
        uri: Uri,
        project: SnapCropProject,
        policy: SourceVerificationPolicy
    ) {
        val hash = sha256OfUri(uri, maxBytes = 512L * 1024 * 1024)
        if (hash == null) {
            showProjectError(getString(R.string.crop_project_source_unavailable), canRelink = true)
            return
        }
        val expectedHash = project.sourceSha256
        if (expectedHash != null && !hash.equals(expectedHash, ignoreCase = true)) {
            showProjectError(getString(R.string.crop_project_hash_mismatch), canRelink = true)
            return
        }
        sourceUri = uri
        intentSourceHints = listOf("snapcrop_project")
        loadBitmap(uri, project, hash, policy)
    }

    private fun loadBitmap(
        uri: Uri,
        project: SnapCropProject? = null,
        sourceHash: String? = null,
        policy: SourceVerificationPolicy = SourceVerificationPolicy.ALLOW_MISSING_FINGERPRINT
    ) {
        try {
            // First pass: get dimensions
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

            // Scale down if very large
            val maxDim = 4096
            var sampleSize = 1
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
            }

            // Second pass: decode
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }

            decoded?.let { bmp ->
                if (project != null) {
                    when (SnapCropProjectSidecar.compareSource(
                        project,
                        SourceIdentity(sourceHash, bmp.width, bmp.height),
                        policy
                    )) {
                        SourceMatch.MATCH -> Unit
                        SourceMatch.HASH_MISMATCH -> {
                            bmp.recycle()
                            showProjectError(getString(R.string.crop_project_hash_mismatch), canRelink = true)
                            return
                        }
                        SourceMatch.DIMENSION_MISMATCH -> {
                            val actualWidth = bmp.width
                            val actualHeight = bmp.height
                            bmp.recycle()
                            showProjectError(
                                getString(R.string.crop_project_dimension_mismatch, project.sourceWidth, project.sourceHeight, actualWidth, actualHeight),
                                canRelink = true
                            )
                            return
                        }
                        SourceMatch.MISSING_FINGERPRINT -> {
                            bmp.recycle()
                            showProjectError(getString(R.string.crop_project_no_fingerprint))
                            return
                        }
                    }
                }
                originalBitmap = bmp
                bitmapState.value = bmp
                projectLoadError.value = null
                projectCanRelink.value = false
                pendingRelinkProject = null
                currentCropHints = CropSourceHints.normalize(
                    intentSourceHints + CropSourceHints.fromMedia(contentResolver, uri)
                )
                if (project != null) {
                    cropRect.value = project.cropRect
                    cropMethod.value = "project"
                    initialPixelateRects.value = project.pixelateRects
                    initialDrawPaths.value = project.drawLayers
                    initialAdjustments.value = project.adjustments
                } else {
                    val statusBarPx = SystemBars.statusBarHeight(resources)
                    val navBarPx = SystemBars.navigationBarHeight(resources)
                    val result = AutoCrop.detectWithMethod(
                        bitmap = bmp,
                        statusBarPx = statusBarPx,
                        navBarPx = navBarPx,
                        sourceHints = currentCropHints,
                        userProfiles = UserAppProfileStore.load(getSharedPreferences("snapcrop", MODE_PRIVATE)),
                        appProfilesEnabled = appCropProfilesEnabled()
                    )
                    cropRect.value = result.rect
                    cropMethod.value = result.method
                }
            } ?: run {
                if (project != null) {
                    showProjectError(getString(R.string.crop_source_missing, project.sourceUri ?: ""))
                } else {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.toast_failed_load_image), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            if (project != null) {
                showProjectError(getString(R.string.crop_source_missing, project.sourceUri ?: ""))
            } else {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_failed_load_image), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun Rect.coerceInside(width: Int, height: Int): Rect {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val l = left.coerceIn(0, safeW - 1)
        val t = top.coerceIn(0, safeH - 1)
        val r = right.coerceIn(l + 1, safeW)
        val b = bottom.coerceIn(t + 1, safeH)
        return Rect(l, t, r, b)
    }

    private fun rotateBitmap() {
        val current = bitmapState.value ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        // Recycle the old bitmap (including originalBitmap if it was the current one)
        if (rotated !== current) current.recycle()
        if (originalBitmap != null && originalBitmap !== current && originalBitmap !== rotated) {
            originalBitmap?.recycle()
        }
        originalBitmap = null
        bitmapState.value = rotated
        cropRect.value = Rect(0, 0, rotated.width, rotated.height)
        cropMethod.value = ""
        rotationKey.intValue++
    }

    private fun flipBitmap(horizontal: Boolean) {
        val current = bitmapState.value ?: return
        val matrix = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        if (flipped !== current) current.recycle()
        if (originalBitmap != null && originalBitmap !== current && originalBitmap !== flipped) {
            originalBitmap?.recycle()
        }
        originalBitmap = null
        bitmapState.value = flipped
    }

    private fun applyPixelate(bitmap: Bitmap, pixRects: List<Rect>): Bitmap {
        return ImageRedactor.pixelate(bitmap, pixRects)
    }

    private fun applyDraw(bitmap: Bitmap, paths: List<DrawPath>): Bitmap {
        val visiblePaths = paths.filter { it.visible }
        if (visiblePaths.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (dp in visiblePaths) {
            if (dp.points.isEmpty()) continue
            // Apply the per-layer move/resize/rotate transform (pivoted on centroid). Pixel-based
            // tools (fill/smart erase) operate on the bitmap directly and are unaffected by the matrix.
            val layerMatrix = dp.transformMatrix()
            val layerSaveCount = if (layerMatrix != null) {
                val sc = canvas.save(); canvas.concat(layerMatrix); sc
            } else -1
            try {
            paint.color = dp.color
            paint.strokeWidth = dp.strokeWidth
            paint.alpha = 255
            paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null

            // Line tool — straight line between two points
            if (dp.shapeType == "line" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                continue
            }

            // Measurement/ruler — line with end ticks and a pixel-distance label
            if (dp.shapeType == "measure" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                paint.pathEffect = null
                val dist = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble())
                val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                val tick = dp.strokeWidth * 2.5f
                val nx = (-kotlin.math.sin(angle)).toFloat() * tick
                val ny = (kotlin.math.cos(angle)).toFloat() * tick
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                canvas.drawLine(p1.x - nx, p1.y - ny, p1.x + nx, p1.y + ny, paint)
                canvas.drawLine(p2.x - nx, p2.y - ny, p2.x + nx, p2.y + ny, paint)
                val label = "${dist.roundToInt()} px"
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color
                    textSize = (dp.strokeWidth * 5f).coerceAtLeast(20f)
                    textAlign = Paint.Align.CENTER
                }
                val mx = (p1.x + p2.x) / 2f; val my = (p1.y + p2.y) / 2f - tick - dp.strokeWidth
                val bounds = Rect()
                labelPaint.getTextBounds(label, 0, label.length, bounds)
                val pad = labelPaint.textSize * 0.3f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt(); style = Paint.Style.FILL }
                canvas.drawRoundRect(
                    mx - bounds.width() / 2f - pad, my + bounds.top - pad,
                    mx + bounds.width() / 2f + pad, my + bounds.bottom + pad,
                    pad.coerceAtMost(8f), pad.coerceAtMost(8f), bgPaint)
                canvas.drawText(label, mx, my, labelPaint)
                continue
            }

            // Eraser — paint transparent along stroke path
            if (dp.shapeType == "eraser" && dp.points.size >= 2) {
                val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    strokeWidth = dp.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val eraserPath = Path()
                eraserPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) eraserPath.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(eraserPath, eraserPaint)
                continue
            }

            // Blur brush — Gaussian blur along the stroke path
            if (dp.shapeType == "blur" && dp.points.size >= 2) {
                val radius = (dp.strokeWidth * 2).toInt().coerceAtLeast(4)
                for (pt in dp.points) {
                    val cx = pt.x.toInt(); val cy = pt.y.toInt()
                    val half = radius
                    val l = (cx - half).coerceAtLeast(0)
                    val t = (cy - half).coerceAtLeast(0)
                    val r = (cx + half).coerceAtMost(result.width)
                    val b = (cy + half).coerceAtMost(result.height)
                    val w = r - l; val h = b - t
                    if (w < 3 || h < 3) continue
                    // Box blur by downscale + upscale
                    var region: Bitmap? = null; var tiny: Bitmap? = null; var blurred: Bitmap? = null
                    try {
                        region = Bitmap.createBitmap(result, l, t, w, h).copy(Bitmap.Config.ARGB_8888, false)
                        val scale = 4
                        tiny = Bitmap.createScaledBitmap(region, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
                        blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
                        canvas.drawBitmap(blurred, l.toFloat(), t.toFloat(), null)
                    } finally {
                        region?.recycle(); tiny?.recycle(); blurred?.recycle()
                    }
                }
                continue
            }

            // Emoji overlay
            if (dp.shapeType == "emoji" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = dp.strokeWidth * 5
                }
                val fm = emojiPaint.fontMetrics
                canvas.drawText(dp.text, p.x, p.y - (fm.ascent + fm.descent) / 2f, emojiPaint)
                continue
            }

            // Callout (numbered circle)
            if (dp.shapeType == "callout" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val radius = dp.strokeWidth * 2
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.FILL
                }
                canvas.drawCircle(p.x, p.y, radius, fillPaint)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dp.color == 0xFFFFFFFF.toInt() || dp.color == 0xFFFFFF00.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    textSize = radius * 1.2f
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL
                }
                val fm = textPaint.fontMetrics
                canvas.drawText(dp.text, p.x, p.y - (fm.ascent + fm.descent) / 2f, textPaint)
                continue
            }

            // Neon glow pen
            if (dp.shapeType == "neon" && dp.points.size >= 2) {
                val neonPath = Path()
                neonPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) neonPath.lineTo(dp.points[i].x, dp.points[i].y)
                // Outer glow
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth * 3; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 80
                    maskFilter = android.graphics.BlurMaskFilter(dp.strokeWidth * 2, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(neonPath, glowPaint)
                // Mid layer
                val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 200
                }
                canvas.drawPath(neonPath, midPaint)
                // Bright core
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); strokeWidth = dp.strokeWidth * 0.6f; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(neonPath, corePaint)
                continue
            }

            // Highlighter (semi-transparent wide stroke)
            if (dp.shapeType == "highlight" && dp.points.size >= 2) {
                paint.alpha = 100 // ~40% opacity
                val path = Path()
                path.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) path.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(path, paint)
                paint.alpha = 255
                continue
            }

            // Magnifier loupe — circular zoomed inset
            if (dp.shapeType == "magnifier" && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val loupeRadius = 120f // pixels in bitmap space
                val zoomFactor = 2f
                val loupeCx = p.x; val loupeCy = p.y - loupeRadius - 20f

                // Border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius + 4f, borderPaint)

                // Clip and draw zoomed content. Read from a snapshot — drawing `result` onto its
                // own backing canvas is undefined and yields a blank/corrupt loupe.
                val snapshot = result.copy(Bitmap.Config.ARGB_8888, false)
                canvas.save()
                val clipPath = Path()
                clipPath.addCircle(loupeCx, loupeCy, loupeRadius, Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.translate(loupeCx - p.x * zoomFactor, loupeCy - p.y * zoomFactor)
                canvas.scale(zoomFactor, zoomFactor)
                canvas.drawBitmap(snapshot, 0f, 0f, null)
                canvas.restore()
                snapshot.recycle()

                // Ring border
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius, ringPaint)

                // Crosshair
                val chPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = 1.5f
                }
                canvas.drawLine(loupeCx - 15f, loupeCy, loupeCx + 15f, loupeCy, chPaint)
                canvas.drawLine(loupeCx, loupeCy - 15f, loupeCx, loupeCy + 15f, chPaint)
                continue
            }

            // Spotlight — dim entire image except the selected rectangle
            if (dp.shapeType == "spotlight" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                if (p1.x == p2.x && p1.y == p2.y) continue // Skip zero-size spotlight
                val sl = minOf(p1.x, p2.x); val st = minOf(p1.y, p2.y)
                val sr = maxOf(p1.x, p2.x); val sb = maxOf(p1.y, p2.y)
                val dimPaint = Paint().apply { color = 0x99000000.toInt(); style = Paint.Style.FILL }
                canvas.drawRect(0f, 0f, result.width.toFloat(), st, dimPaint)
                canvas.drawRect(0f, sb, result.width.toFloat(), result.height.toFloat(), dimPaint)
                canvas.drawRect(0f, st, sl, sb, dimPaint)
                canvas.drawRect(sr, st, result.width.toFloat(), sb, dimPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawRect(sl, st, sr, sb, borderPaint)
                continue
            }

            // Text
            if (dp.shapeType == "text" && dp.text != null && dp.points.isNotEmpty()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color
                    textSize = dp.strokeWidth * 3
                    style = Paint.Style.FILL
                }
                val p = dp.points.first()
                if (dp.filled) {
                    val bounds = android.graphics.Rect()
                    textPaint.getTextBounds(dp.text, 0, dp.text.length, bounds)
                    val pad = textPaint.textSize * 0.3f
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt(); style = Paint.Style.FILL }
                    canvas.drawRoundRect(p.x - pad, p.y + bounds.top - pad, p.x + bounds.width() + pad,
                        p.y + bounds.bottom + pad, pad, pad, bgPaint)
                }
                canvas.drawText(dp.text, p.x, p.y, textPaint)
                continue
            }

            // Shape types
            if (dp.shapeType == "rect" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawRect(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawRect(l, t, r, b, paint)
                continue
            }
            if (dp.shapeType == "circle" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawOval(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawOval(l, t, r, b, paint)
                continue
            }

            // Flood fill — fill contiguous region at tap point with selected color
            if (dp.shapeType == "fill" && dp.points.isNotEmpty()) {
                val fx = dp.points[0].x.toInt().coerceIn(0, result.width - 1)
                val fy = dp.points[0].y.toInt().coerceIn(0, result.height - 1)
                floodFill(result, fx, fy, dp.color)
                continue
            }

            // Smart Erase — mask-based object removal with edge-aware inpainting.
            if ((dp.shapeType == "smart_erase" || dp.shapeType == "heal") && dp.points.size >= 2) {
                SmartEraseEngine.eraseInPlace(result, dp)
                continue
            }

            // Freehand or bezier path
            val path = Path()
            if (dp.controlPoint != null && dp.points.size >= 2) {
                path.moveTo(dp.points[0].x, dp.points[0].y)
                path.quadTo(dp.controlPoint.x, dp.controlPoint.y, dp.points.last().x, dp.points.last().y)
            } else {
                path.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) path.lineTo(dp.points[i].x, dp.points[i].y)
            }
            canvas.drawPath(path, paint)

            // Arrow head
            if (dp.isArrow && dp.points.size >= 2) {
                val last = dp.points.last()
                val prev = if (dp.controlPoint != null) {
                    val cp = dp.controlPoint; val t = 0.95f
                    PointF((1-t)*(1-t)*dp.points[0].x + 2*(1-t)*t*cp.x + t*t*last.x,
                           (1-t)*(1-t)*dp.points[0].y + 2*(1-t)*t*cp.y + t*t*last.y)
                } else dp.points[dp.points.size - 2]
                val dx = last.x - prev.x; val dy = last.y - prev.y
                val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len > 0) {
                    val ux = dx / len; val uy = dy / len
                    val hl = dp.strokeWidth * 4; val hw = dp.strokeWidth * 2.5f
                    val arrowPath = Path()
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl + uy * hw, last.y - uy * hl - ux * hw)
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl - uy * hw, last.y - uy * hl + ux * hw)
                    canvas.drawPath(arrowPath, paint)
                }
            }
            } finally {
                if (layerSaveCount != -1) canvas.restoreToCount(layerSaveCount)
            }
        }
        return result
    }

    private fun floodFill(bitmap: Bitmap, x: Int, y: Int, fillColor: Int) {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val targetColor = pixels[y * w + x]
        if (targetColor == fillColor) return
        val tolerance = 30 // color distance tolerance
        fun colorClose(c1: Int, c2: Int): Boolean {
            val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
            val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
            val db = (c1 and 0xFF) - (c2 and 0xFF)
            return dr * dr + dg * dg + db * db <= tolerance * tolerance * 3
        }
        val queue = ArrayDeque<Int>(1024)
        val visited = BooleanArray(w * h)
        queue.add(y * w + x)
        visited[y * w + x] = true
        var filled = 0
        val maxFill = w * h / 2 // safety limit
        while (queue.isNotEmpty() && filled < maxFill) {
            val idx = queue.removeFirst()
            pixels[idx] = fillColor
            filled++
            val cx = idx % w; val cy = idx / w
            // Explicit 4-neighbour checks — avoid allocating a List<Pair> per pixel in the hot loop.
            if (cx > 0) { val ni = idx - 1; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cx < w - 1) { val ni = idx + 1; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cy > 0) { val ni = idx - w; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
            if (cy < h - 1) { val ni = idx + w; if (!visited[ni] && colorClose(pixels[ni], targetColor)) { visited[ni] = true; queue.add(ni) } }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun getFilterColorMatrix(filterIndex: Int): ColorMatrix? {
        return when (filterIndex) {
            1 -> ColorMatrix().apply { setSaturation(0f) } // Mono
            2 -> ColorMatrix().apply { // Sepia
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,40f, 0f,1f,0f,0f,20f, 0f,0f,1f,0f,-10f, 0f,0f,0f,1f,0f)))
            }
            3 -> ColorMatrix(floatArrayOf(0.9f,0f,0f,0f,0f, 0f,0.95f,0f,0f,0f, 0f,0f,1.1f,0f,20f, 0f,0f,0f,1f,0f)) // Cool
            4 -> ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,15f, 0f,1.05f,0f,0f,5f, 0f,0f,0.9f,0f,-10f, 0f,0f,0f,1f,0f)) // Warm
            5 -> ColorMatrix().apply { // Vivid
                setSaturation(1.5f)
                postConcat(ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,10f, 0f,1.1f,0f,0f,10f, 0f,0f,1.1f,0f,10f, 0f,0f,0f,1f,0f)))
            }
            6 -> ColorMatrix().apply { // Muted
                setSaturation(0.4f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,15f, 0f,1f,0f,0f,15f, 0f,0f,1f,0f,15f, 0f,0f,0f,1f,0f)))
            }
            7 -> ColorMatrix().apply { // Vintage
                setSaturation(0.5f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.05f,0f,0f,20f, 0f,1f,0.05f,0f,10f, 0f,0f,0.9f,0f,0f, 0f,0f,0f,1f,0f)))
            }
            8 -> ColorMatrix().apply { // Noir
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1.4f,0f,0f,0f,-30f, 0f,1.4f,0f,0f,-30f, 0f,0f,1.4f,0f,-30f, 0f,0f,0f,1f,0f)))
            }
            9 -> ColorMatrix().apply { // Fade
                setSaturation(0.6f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,30f, 0f,1f,0f,0f,30f, 0f,0f,1f,0f,30f, 0f,0f,0f,0.9f,0f)))
            }
            10 -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)) // Invert
            11 -> ColorMatrix(floatArrayOf(1.438f,-0.062f,-0.062f,0f,0f, -0.122f,1.378f,-0.122f,0f,0f, -0.016f,-0.016f,1.483f,0f,0f, 0f,0f,0f,1f,0f)) // Polaroid
            12 -> ColorMatrix().apply { // Grain
                setSaturation(0.8f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.02f,0f,0f,8f, 0f,1.02f,0f,0f,4f, 0f,0f,0.95f,0f,-5f, 0f,0f,0f,1f,0f)))
            }
            // 13-16: per-pixel filters handled in applyAdjustments
            else -> null
        }
    }

    private fun applyAdjustments(bitmap: Bitmap, adj: FloatArray): Bitmap {
        val brightness = adj[0]; val contrast = adj[1]; val saturation = adj[2]
        val warmth = if (adj.size > 4) adj[4] else 0f
        val vignetteAmt = if (adj.size > 5) adj[5] else 0f
        val filterIndex = if (adj.size > 6) adj[6].toInt() else 0
        val sharpenAmt = if (adj.size > 7) adj[7] else 0f
        val highlightsAmt = if (adj.size > 9) adj[9] else 0f
        val shadowsAmt = if (adj.size > 10) adj[10] else 0f
        val tiltShiftAmt = if (adj.size > 11) adj[11] else 0f
        val denoiseAmt = if (adj.size > 12) adj[12] else 0f
        val curveRAmt = if (adj.size > 14) adj[14] else 0f
        val curveGAmt = if (adj.size > 15) adj[15] else 0f
        val curveBAmt = if (adj.size > 16) adj[16] else 0f
        if (brightness == 0f && contrast == 1f && saturation == 1f && warmth == 0f && vignetteAmt == 0f && filterIndex == 0 && sharpenAmt == 0f && highlightsAmt == 0f && shadowsAmt == 0f && tiltShiftAmt == 0f && denoiseAmt == 0f && curveRAmt == 0f && curveGAmt == 0f && curveBAmt == 0f) return bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix()
        // Apply image filter first
        val filterMat = getFilterColorMatrix(filterIndex)
        if (filterMat != null) cm.postConcat(filterMat)
        if (saturation != 1f) { val sat = ColorMatrix(); sat.setSaturation(saturation); cm.postConcat(sat) }
        if (contrast != 1f) {
            val t = (1f - contrast) / 2f * 255f
            cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        }
        if (brightness != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, brightness, 0f, 1f, 0f, 0f, brightness, 0f, 0f, 1f, 0f, brightness, 0f, 0f, 0f, 1f, 0f)))
        }
        if (warmth != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, warmth, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, -warmth, 0f, 0f, 0f, 1f, 0f)))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        // Vignette: radial gradient overlay
        if (vignetteAmt > 0.01f) {
            val vigPaint = Paint().apply {
                shader = android.graphics.RadialGradient(
                    result.width / 2f, result.height / 2f,
                    maxOf(result.width, result.height) * 0.7f,
                    intArrayOf(0x00000000, (vignetteAmt * 200).toInt().coerceAtMost(200) shl 24),
                    floatArrayOf(0.4f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), vigPaint)
        }
        // Highlights/Shadows: per-pixel luminance-based adjustment
        if (highlightsAmt != 0f || shadowsAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val px = pixels[i]
                var r = (px shr 16) and 0xFF; var g = (px shr 8) and 0xFF; var b = px and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                // Highlights affect bright pixels (lum > 128), shadows affect dark pixels (lum < 128)
                val hiFactor = ((lum - 128f) / 128f).coerceIn(0f, 1f) // 0 for darks, 1 for brights
                val shFactor = ((128f - lum) / 128f).coerceIn(0f, 1f) // 1 for darks, 0 for brights
                val adj2 = highlightsAmt * hiFactor * 0.5f + shadowsAmt * shFactor * 0.5f
                r = (r + adj2).toInt().coerceIn(0, 255)
                g = (g + adj2).toInt().coerceIn(0, 255)
                b = (b + adj2).toInt().coerceIn(0, 255)
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Curves: per-channel gamma adjustment
        if (curveRAmt != 0f || curveGAmt != 0f || curveBAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            // Build LUTs for each channel: gamma curve from -100..+100 mapped to gamma 0.5..2.0
            fun buildLut(amount: Float): IntArray {
                val lut = IntArray(256)
                if (amount == 0f) { for (i in 0..255) lut[i] = i; return lut }
                val gamma = if (amount > 0) 1f / (1f + amount / 50f) else 1f + (-amount / 50f)
                for (i in 0..255) {
                    lut[i] = (255.0 * Math.pow(i / 255.0, gamma.toDouble())).roundToInt().coerceIn(0, 255)
                }
                return lut
            }
            val lutR = buildLut(curveRAmt); val lutG = buildLut(curveGAmt); val lutB = buildLut(curveBAmt)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = lutR[(px shr 16) and 0xFF]
                val g = lutG[(px shr 8) and 0xFF]
                val b = lutB[px and 0xFF]
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Glitch effect (16): RGB channel shift
        if (filterIndex == 16) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            val shift = (w * 0.02f).toInt().coerceAtLeast(3) // 2% of width
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    val rIdx = y * w + (x + shift).coerceAtMost(w - 1)
                    val bIdx = y * w + (x - shift).coerceAtLeast(0)
                    val r = (pixels[rIdx] shr 16) and 0xFF
                    val g = (pixels[idx] shr 8) and 0xFF
                    val b = pixels[bIdx] and 0xFF
                    out[idx] = (pixels[idx] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
            result.setPixels(out, 0, w, 0, 0, w, h)
        }
        // Selective color pop filters (13=RedPop, 14=BluePop, 15=GreenPop)
        if (filterIndex in 13..15) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val targetHue = when (filterIndex) { 13 -> 0f; 14 -> 220f; else -> 120f } // Red/Blue/Green
            val hueRange = 40f // degrees of hue to keep
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hueDiff = kotlin.math.abs(hsv[0] - targetHue).let { if (it > 180) 360 - it else it }
                if (hueDiff > hueRange || hsv[1] < 0.15f) {
                    // Desaturate — convert to grayscale
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                    pixels[i] = (px and 0xFF000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Noise reduction: blend with slightly blurred version
        if (denoiseAmt > 0.01f) {
            val w = result.width; val h = result.height
            val scale = (2 + denoiseAmt * 4).toInt().coerceIn(2, 6)
            val tiny = Bitmap.createScaledBitmap(result, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
            val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
            tiny.recycle()
            val origPx = IntArray(w * h); val blurPx = IntArray(w * h)
            result.getPixels(origPx, 0, w, 0, 0, w, h)
            blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
            val blend = denoiseAmt.coerceIn(0f, 0.8f) // never fully replace
            for (i in origPx.indices) {
                val o = origPx[i]; val b = blurPx[i]
                val r = ((o shr 16 and 0xFF) * (1 - blend) + (b shr 16 and 0xFF) * blend).toInt()
                val g = ((o shr 8 and 0xFF) * (1 - blend) + (b shr 8 and 0xFF) * blend).toInt()
                val bl = ((o and 0xFF) * (1 - blend) + (b and 0xFF) * blend).toInt()
                origPx[i] = (o and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
            result.setPixels(origPx, 0, w, 0, 0, w, h)
            blurred.recycle()
        }
        // Sharpen: 3x3 convolution kernel
        if (sharpenAmt > 0.01f) {
            val sharpened = applySharpen(result, sharpenAmt)
            if (sharpened !== result) result.recycle()
            val tiltResult = applyTiltShift(sharpened, tiltShiftAmt)
            if (tiltResult !== sharpened) sharpened.recycle()
            return tiltResult
        }
        val tiltResult = applyTiltShift(result, tiltShiftAmt)
        if (tiltResult !== result) result.recycle()
        return tiltResult
    }

    private fun applyTiltShift(bitmap: Bitmap, amount: Float): Bitmap {
        if (amount < 0.01f) return bitmap
        val w = bitmap.width; val h = bitmap.height
        if (w < 2 || h < 2) return bitmap
        // Create heavily blurred version via downscale/upscale
        val scale = (8 * amount).toInt().coerceIn(2, 16)
        val tiny = Bitmap.createScaledBitmap(bitmap, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        if (tiny !== blurred) tiny.recycle()
        // result is allocated empty — setPixels below overwrites every cell, so no bitmap.copy needed.
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val focusBand = 0.3f // 30% of height is the sharp center
        val focusTop = (h * (0.5f - focusBand / 2)).toInt()
        val focusBottom = (h * (0.5f + focusBand / 2)).toInt()
        val sharpPixels = IntArray(w * h)
        val blurPixels = IntArray(w * h)
        bitmap.getPixels(sharpPixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val blendFactor = when {
                y < focusTop -> 1f - (y.toFloat() / focusTop).coerceIn(0f, 1f) // top blur
                y > focusBottom -> ((y - focusBottom).toFloat() / (h - focusBottom)).coerceIn(0f, 1f) // bottom blur
                else -> 0f // center sharp
            }
            for (x in 0 until w) {
                val idx = y * w + x
                if (blendFactor < 0.01f) { outPixels[idx] = sharpPixels[idx]; continue }
                if (blendFactor > 0.99f) { outPixels[idx] = blurPixels[idx]; continue }
                val sp = sharpPixels[idx]; val bp = blurPixels[idx]
                val r = ((sp shr 16 and 0xFF) * (1 - blendFactor) + (bp shr 16 and 0xFF) * blendFactor).toInt()
                val g = ((sp shr 8 and 0xFF) * (1 - blendFactor) + (bp shr 8 and 0xFF) * blendFactor).toInt()
                val b = ((sp and 0xFF) * (1 - blendFactor) + (bp and 0xFF) * blendFactor).toInt()
                outPixels[idx] = (sp and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        blurred.recycle()
        return result
    }

    private fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        // 3x3 unsharp-mask kernel needs at least 3px in each dimension; degenerate
        // bitmaps fall through unchanged instead of producing a one-pixel result.
        if (w < 3 || h < 3) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        // Unsharp mask: center = 1 + 4*amount, neighbors = -amount
        val center = 1f + 4f * amount
        val edge = -amount
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val t = pixels[(y - 1) * w + x]
                val b = pixels[(y + 1) * w + x]
                val l = pixels[y * w + (x - 1)]
                val r = pixels[y * w + (x + 1)]
                fun ch(px: Int, shift: Int) = (px shr shift) and 0xFF
                val nr = (ch(c, 16) * center + ch(t, 16) * edge + ch(b, 16) * edge + ch(l, 16) * edge + ch(r, 16) * edge).toInt().coerceIn(0, 255)
                val ng = (ch(c, 8) * center + ch(t, 8) * edge + ch(b, 8) * edge + ch(l, 8) * edge + ch(r, 8) * edge).toInt().coerceIn(0, 255)
                val nb = (ch(c, 0) * center + ch(t, 0) * edge + ch(b, 0) * edge + ch(l, 0) * edge + ch(r, 0) * edge).toInt().coerceIn(0, 255)
                out[idx] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        // Copy edges unchanged
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun createCroppedBitmap(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray = floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)): Bitmap {
        // Apply free rotation first (before adjustments/crop)
        val rotAngle = if (adj.size > 8) adj[8] else 0f
        val rotated = if (rotAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotAngle, bitmap.width / 2f, bitmap.height / 2f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap

        val adjusted = applyAdjustments(rotated, adj)
        if (adjusted !== rotated && rotated !== bitmap) rotated.recycle()
        val pixelated = applyPixelate(adjusted, pixRects)
        if (pixelated !== adjusted && adjusted !== bitmap) adjusted.recycle()
        val drawn = applyDraw(pixelated, drawPaths)
        if (drawn !== pixelated && pixelated !== bitmap) pixelated.recycle()

        // Perspective warp: adj[17..24] = quad TL.x, TL.y, TR.x, TR.y, BR.x, BR.y, BL.x, BL.y
        val hasPerspective = adj.size >= 25 && (adj[17] != 0f || adj[18] != 0f || adj[19] != 0f || adj[20] != 0f ||
                adj[21] != 0f || adj[22] != 0f || adj[23] != 0f || adj[24] != 0f)
        if (hasPerspective) {
            val srcQuad = floatArrayOf(adj[17], adj[18], adj[19], adj[20], adj[21], adj[22], adj[23], adj[24])
            val topW = kotlin.math.sqrt(((srcQuad[2] - srcQuad[0]) * (srcQuad[2] - srcQuad[0]) + (srcQuad[3] - srcQuad[1]) * (srcQuad[3] - srcQuad[1])).toDouble())
            val botW = kotlin.math.sqrt(((srcQuad[4] - srcQuad[6]) * (srcQuad[4] - srcQuad[6]) + (srcQuad[5] - srcQuad[7]) * (srcQuad[5] - srcQuad[7])).toDouble())
            val leftH = kotlin.math.sqrt(((srcQuad[6] - srcQuad[0]) * (srcQuad[6] - srcQuad[0]) + (srcQuad[7] - srcQuad[1]) * (srcQuad[7] - srcQuad[1])).toDouble())
            val rightH = kotlin.math.sqrt(((srcQuad[4] - srcQuad[2]) * (srcQuad[4] - srcQuad[2]) + (srcQuad[5] - srcQuad[3]) * (srcQuad[5] - srcQuad[3])).toDouble())
            val outW = maxOf(topW, botW).toInt().coerceAtLeast(1)
            val outH = maxOf(leftH, rightH).toInt().coerceAtLeast(1)
            val dstQuad = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
            val warpMatrix = Matrix()
            warpMatrix.setPolyToPoly(srcQuad, 0, dstQuad, 0, 4)
            val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val warpCanvas = Canvas(warped)
            warpCanvas.drawBitmap(drawn, warpMatrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            if (drawn !== bitmap) drawn.recycle()
            return warped
        }

        val cl = rect.left.coerceIn(0, drawn.width - 1)
        val ct = rect.top.coerceIn(0, drawn.height - 1)
        val cw = rect.width().coerceAtMost(drawn.width - cl).coerceAtLeast(1)
        val ch = rect.height().coerceAtMost(drawn.height - ct).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(drawn, cl, ct, cw, ch)
        if (drawn !== bitmap && drawn !== cropped) drawn.recycle()

        // Shape crop masking
        val shapeType = if (adj.size > 3) adj[3] else 0f
        val gradIdx = if (adj.size > 13) adj[13].toInt() else 0
        val shaped: Bitmap = if (shapeType == 1f) {
            // Circle
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawCircle(size / 2f, size / 2f, size / 2f, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 2f) {
            // Rounded rect
            val s = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = minOf(cropped.width, cropped.height) * 0.08f
            c.drawRoundRect(RectF(0f, 0f, cropped.width.toFloat(), cropped.height.toFloat()), radius, radius, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, 0f, 0f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 3f) {
            // Star (5-point)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val starPath = Path()
            val cx = size / 2f; val cy = size / 2f; val outerR = size / 2f; val innerR = outerR * 0.38f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = Math.toRadians((i * 36.0 - 90.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()
            c.drawPath(starPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 4f) {
            // Heart
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val heartPath = Path()
            val w = size.toFloat(); val h = size.toFloat()
            heartPath.moveTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.15f, h * -0.05f, -w * 0.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.lineTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.85f, h * -0.05f, w * 1.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.close()
            c.drawPath(heartPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 5f) {
            // Triangle (equilateral)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val triPath = Path()
            triPath.moveTo(size / 2f, size * 0.05f)
            triPath.lineTo(size * 0.95f, size * 0.95f)
            triPath.lineTo(size * 0.05f, size * 0.95f)
            triPath.close()
            c.drawPath(triPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 6f) {
            // Hexagon
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val hexPath = Path()
            val cx = size / 2f; val cy = size / 2f; val r = size / 2f * 0.95f
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60.0 - 30.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            c.drawPath(hexPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 7f) {
            // Diamond (rotated square)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val diaPath = Path()
            val half = size / 2f * 0.95f
            val cx = size / 2f; val cy = size / 2f
            diaPath.moveTo(cx, cy - half)
            diaPath.lineTo(cx + half, cy)
            diaPath.lineTo(cx, cy + half)
            diaPath.lineTo(cx - half, cy)
            diaPath.close()
            c.drawPath(diaPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else {
            cropped
        }

        // Gradient background fill for transparent areas (shape crops only)
        if (gradIdx > 0 && shapeType >= 1f) {
            return applyGradientBackground(shaped, gradIdx)
        }

        return shaped
    }

    private fun applyGradientBackground(bitmap: Bitmap, gradIdx: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Draw gradient background
        val (startColor, endColor) = when (gradIdx) {
            1 -> 0xFFFF6B35.toInt() to 0xFFF7C948.toInt() // Sunset (orange->yellow)
            2 -> 0xFF0077B6.toInt() to 0xFF00B4D8.toInt() // Ocean (deep blue->cyan)
            3 -> 0xFF7B2FBE.toInt() to 0xFFE040FB.toInt() // Purple (purple->pink)
            4 -> 0xFF1A1A2E.toInt() to 0xFF16213E.toInt() // Dark (dark blue shades)
            5 -> 0xFF00B09B.toInt() to 0xFF96C93D.toInt() // Mint (teal->green)
            6 -> 0xFFFF416C.toInt() to 0xFFFF4B2B.toInt() // Fire (red->orange)
            else -> return bitmap
        }
        val gradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                startColor, endColor, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradPaint)
        // Draw the shape-cropped image on top (transparent areas show gradient)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return result
    }

    private fun buildAnnotationSvg(cropRect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>): String? {
        val width = cropRect.width().coerceAtLeast(1)
        val height = cropRect.height().coerceAtLeast(1)
        val elements = StringBuilder()

        fun Float.svgNum(): String = String.format(Locale.US, "%.2f", this)
        fun colorHex(color: Int): String = String.format(Locale.US, "#%06X", color and 0x00FFFFFF)
        fun alpha(color: Int, multiplier: Float = 1f): Float =
            (((color ushr 24) and 0xFF) / 255f * multiplier).coerceIn(0f, 1f)
        fun xml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        fun sx(x: Float): Float = x - cropRect.left
        fun sy(y: Float): Float = y - cropRect.top
        fun pathData(points: List<android.graphics.PointF>): String = buildString {
            points.forEachIndexed { index, point ->
                append(if (index == 0) "M " else " L ")
                append(sx(point.x).svgNum()).append(' ').append(sy(point.y).svgNum())
            }
        }
        fun dash(dp: DrawPath): String =
            if (dp.dashed) " stroke-dasharray=\"${(dp.strokeWidth * 3).svgNum()} ${(dp.strokeWidth * 2).svgNum()}\"" else ""
        fun strokeAttrs(dp: DrawPath, opacity: Float = alpha(dp.color)): String =
            """fill="none" stroke="${colorHex(dp.color)}" stroke-width="${dp.strokeWidth.svgNum()}" stroke-linecap="round" stroke-linejoin="round" opacity="${opacity.svgNum()}"${dash(dp)}"""

        pixRects.forEachIndexed { index, rect ->
            val left = (rect.left - cropRect.left).coerceIn(0, width).toFloat()
            val top = (rect.top - cropRect.top).coerceIn(0, height).toFloat()
            val right = (rect.right - cropRect.left).coerceIn(0, width).toFloat()
            val bottom = (rect.bottom - cropRect.top).coerceIn(0, height).toFloat()
            if (right > left && bottom > top) {
                elements.append("""  <rect id="pixelate-${index + 1}" x="${left.svgNum()}" y="${top.svgNum()}" width="${(right - left).svgNum()}" height="${(bottom - top).svgNum()}" fill="#000000" opacity="0.35" stroke="#F38BA8" stroke-width="2"/>""")
                    .append('\n')
            }
        }

        drawPaths.forEachIndexed { index, dp ->
            if (!dp.visible || dp.points.isEmpty()) return@forEachIndexed
            val id = "layer-${index + 1}"
            val stroke = strokeAttrs(dp)
            // Wrap transformed layers in a <g> so move/resize/rotate matches the raster export.
            if (dp.hasTransform) {
                val c = dp.centroid()
                val pcx = sx(c.x); val pcy = sy(c.y)
                elements.append(
                    "  <g transform=\"translate(${dp.transOffsetX.svgNum()} ${dp.transOffsetY.svgNum()}) " +
                        "rotate(${dp.transRotation.svgNum()} ${pcx.svgNum()} ${pcy.svgNum()}) " +
                        "translate(${pcx.svgNum()} ${pcy.svgNum()}) scale(${dp.transScale.svgNum()}) " +
                        "translate(${(-pcx).svgNum()} ${(-pcy).svgNum()})\">"
                ).append('\n')
            }
            when (dp.shapeType) {
                "rect" -> if (dp.points.size >= 2) {
                    val p1 = dp.points.first(); val p2 = dp.points.last()
                    val left = sx(minOf(p1.x, p2.x)); val top = sy(minOf(p1.y, p2.y))
                    val w = kotlin.math.abs(p2.x - p1.x); val h = kotlin.math.abs(p2.y - p1.y)
                    val fill = if (dp.filled) colorHex(dp.color) else "none"
                    val fillOpacity = if (dp.filled) alpha(dp.color).svgNum() else "1"
                    elements.append("""  <rect id="$id" x="${left.svgNum()}" y="${top.svgNum()}" width="${w.svgNum()}" height="${h.svgNum()}" fill="$fill" fill-opacity="$fillOpacity" stroke="${colorHex(dp.color)}" stroke-width="${dp.strokeWidth.svgNum()}"${dash(dp)}/>""").append('\n')
                }
                "circle" -> if (dp.points.size >= 2) {
                    val p1 = dp.points.first(); val p2 = dp.points.last()
                    val cx = sx((p1.x + p2.x) / 2f); val cy = sy((p1.y + p2.y) / 2f)
                    val rx = kotlin.math.abs(p2.x - p1.x) / 2f; val ry = kotlin.math.abs(p2.y - p1.y) / 2f
                    val fill = if (dp.filled) colorHex(dp.color) else "none"
                    val fillOpacity = if (dp.filled) alpha(dp.color).svgNum() else "1"
                    elements.append("""  <ellipse id="$id" cx="${cx.svgNum()}" cy="${cy.svgNum()}" rx="${rx.svgNum()}" ry="${ry.svgNum()}" fill="$fill" fill-opacity="$fillOpacity" stroke="${colorHex(dp.color)}" stroke-width="${dp.strokeWidth.svgNum()}"${dash(dp)}/>""").append('\n')
                }
                "line" -> if (dp.points.size >= 2) {
                    val p1 = dp.points.first(); val p2 = dp.points.last()
                    elements.append("""  <line id="$id" x1="${sx(p1.x).svgNum()}" y1="${sy(p1.y).svgNum()}" x2="${sx(p2.x).svgNum()}" y2="${sy(p2.y).svgNum()}" $stroke/>""").append('\n')
                }
                "measure" -> if (dp.points.size >= 2) {
                    val p1 = dp.points.first(); val p2 = dp.points.last()
                    val dist = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble())
                    val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                    val tick = dp.strokeWidth * 2.5f
                    val nx = (-kotlin.math.sin(angle)).toFloat() * tick
                    val ny = (kotlin.math.cos(angle)).toFloat() * tick
                    elements.append("""  <line id="$id" x1="${sx(p1.x).svgNum()}" y1="${sy(p1.y).svgNum()}" x2="${sx(p2.x).svgNum()}" y2="${sy(p2.y).svgNum()}" $stroke/>""").append('\n')
                    elements.append("""  <line id="$id-t1" x1="${sx(p1.x - nx).svgNum()}" y1="${sy(p1.y - ny).svgNum()}" x2="${sx(p1.x + nx).svgNum()}" y2="${sy(p1.y + ny).svgNum()}" $stroke/>""").append('\n')
                    elements.append("""  <line id="$id-t2" x1="${sx(p2.x - nx).svgNum()}" y1="${sy(p2.y - ny).svgNum()}" x2="${sx(p2.x + nx).svgNum()}" y2="${sy(p2.y + ny).svgNum()}" $stroke/>""").append('\n')
                    val mx = sx((p1.x + p2.x) / 2f); val my = sy((p1.y + p2.y) / 2f) - tick - dp.strokeWidth
                    elements.append("""  <text id="$id-label" x="${mx.svgNum()}" y="${my.svgNum()}" fill="${colorHex(dp.color)}" font-size="${(dp.strokeWidth * 5f).svgNum()}" font-family="sans-serif" text-anchor="middle">${xml("${dist.roundToInt()} px")}</text>""").append('\n')
                }
                "text" -> if (!dp.text.isNullOrBlank()) {
                    val p = dp.points.first()
                    elements.append("""  <text id="$id" x="${sx(p.x).svgNum()}" y="${sy(p.y).svgNum()}" fill="${colorHex(dp.color)}" font-size="${(dp.strokeWidth * 3).svgNum()}" font-family="sans-serif">${xml(dp.text)}</text>""").append('\n')
                }
                "emoji" -> if (!dp.text.isNullOrBlank()) {
                    val p = dp.points.first()
                    elements.append("""  <text id="$id" x="${sx(p.x).svgNum()}" y="${sy(p.y).svgNum()}" font-size="${(dp.strokeWidth * 5).svgNum()}" font-family="sans-serif">${xml(dp.text)}</text>""").append('\n')
                }
                "callout" -> if (!dp.text.isNullOrBlank()) {
                    val p = dp.points.first()
                    val r = dp.strokeWidth * 2f
                    val textColor = if (dp.color == 0xFFFFFFFF.toInt() || dp.color == 0xFFFFFF00.toInt()) "#000000" else "#FFFFFF"
                    elements.append("""  <circle id="$id" cx="${sx(p.x).svgNum()}" cy="${sy(p.y).svgNum()}" r="${r.svgNum()}" fill="${colorHex(dp.color)}"/>""").append('\n')
                    elements.append("""  <text id="$id-label" x="${sx(p.x).svgNum()}" y="${(sy(p.y) + r * 0.4f).svgNum()}" fill="$textColor" font-size="${(r * 1.2f).svgNum()}" font-family="sans-serif" text-anchor="middle">${xml(dp.text)}</text>""").append('\n')
                }
                "spotlight" -> if (dp.points.size >= 2) {
                    val p1 = dp.points.first(); val p2 = dp.points.last()
                    val left = sx(minOf(p1.x, p2.x)); val top = sy(minOf(p1.y, p2.y))
                    val w = kotlin.math.abs(p2.x - p1.x); val h = kotlin.math.abs(p2.y - p1.y)
                    elements.append("""  <rect id="$id" x="${left.svgNum()}" y="${top.svgNum()}" width="${w.svgNum()}" height="${h.svgNum()}" fill="none" stroke="#FFFFFF" stroke-width="3" opacity="0.8"/>""").append('\n')
                }
                "magnifier" -> {
                    val p = dp.points.first()
                    elements.append("""  <circle id="$id" cx="${sx(p.x).svgNum()}" cy="${(sy(p.y) - 140f).svgNum()}" r="120" fill="none" stroke="${colorHex(dp.color)}" stroke-width="3"/>""").append('\n')
                }
                "fill" -> {
                    val p = dp.points.first()
                    elements.append("""  <circle id="$id" cx="${sx(p.x).svgNum()}" cy="${sy(p.y).svgNum()}" r="${(dp.strokeWidth.coerceAtLeast(8f)).svgNum()}" fill="${colorHex(dp.color)}" opacity="0.5"/>""").append('\n')
                }
                "highlight" -> if (dp.points.size >= 2) {
                    elements.append("""  <path id="$id" d="${pathData(dp.points)}" ${strokeAttrs(dp, alpha(dp.color, 0.4f))}/>""").append('\n')
                }
                "blur", "eraser", "smart_erase", "heal", "neon" -> if (dp.points.size >= 2) {
                    elements.append("""  <path id="$id" d="${pathData(dp.points)}" $stroke/>""").append('\n')
                }
                else -> if (dp.points.size >= 2) {
                    if (dp.controlPoint != null) {
                        val cp = dp.controlPoint
                        elements.append("""  <path id="$id" d="M ${sx(dp.points[0].x).svgNum()} ${sy(dp.points[0].y).svgNum()} Q ${sx(cp.x).svgNum()} ${sy(cp.y).svgNum()} ${sx(dp.points.last().x).svgNum()} ${sy(dp.points.last().y).svgNum()}" $stroke/>""").append('\n')
                    } else {
                        elements.append("""  <path id="$id" d="${pathData(dp.points)}" $stroke/>""").append('\n')
                    }
                    if (dp.isArrow && dp.points.size >= 2) {
                        val last = dp.points.last()
                        val prev = if (dp.controlPoint != null) {
                            val cp = dp.controlPoint; val t = 0.95f
                            PointF((1-t)*(1-t)*dp.points[0].x + 2*(1-t)*t*cp.x + t*t*last.x,
                                   (1-t)*(1-t)*dp.points[0].y + 2*(1-t)*t*cp.y + t*t*last.y)
                        } else dp.points[dp.points.size - 2]
                        val dx = last.x - prev.x; val dy = last.y - prev.y
                        val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (len > 0f) {
                            val ux = dx / len; val uy = dy / len
                            val headLength = dp.strokeWidth * 4f
                            val headWidth = dp.strokeWidth * 2.5f
                            val tipX = sx(last.x); val tipY = sy(last.y)
                            val leftX = sx(last.x - ux * headLength + uy * headWidth)
                            val leftY = sy(last.y - uy * headLength - ux * headWidth)
                            val rightX = sx(last.x - ux * headLength - uy * headWidth)
                            val rightY = sy(last.y - uy * headLength + ux * headWidth)
                            elements.append("""  <path id="$id-arrow" d="M ${tipX.svgNum()} ${tipY.svgNum()} L ${leftX.svgNum()} ${leftY.svgNum()} M ${tipX.svgNum()} ${tipY.svgNum()} L ${rightX.svgNum()} ${rightY.svgNum()}" $stroke/>""").append('\n')
                        }
                    }
                }
            }
            if (dp.hasTransform) elements.append("  </g>").append('\n')
        }

        if (elements.isEmpty()) return null
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""").append('\n')
            append("""  <title>SnapCrop annotation layers</title>""").append('\n')
            append(elements)
            append("</svg>\n")
        }
    }

    private fun buildProjectSidecarJson(
        rect: Rect,
        pixRects: List<Rect>,
        drawPaths: List<DrawPath>,
        adj: FloatArray,
        deleteOriginal: Boolean,
        exportFormat: CropExportFormat,
        savePath: String,
        computeHash: Boolean = true
    ): String {
        val source = sourceUri
        val bitmap = bitmapState.value
        val limits = SnapCropProjectSidecar.DEFAULT_LIMITS
        var remainingPoints = limits.maxTotalPoints
        val boundedDrawPaths = drawPaths.take(limits.maxDrawLayers).mapNotNull { path ->
            val pointCount = minOf(path.points.size, limits.maxPointsPerLayer, remainingPoints)
            if (pointCount <= 0) return@mapNotNull null
            remainingPoints -= pointCount
            path.copy(
                points = path.points.take(pointCount),
                text = path.text?.take(limits.maxTextChars),
                strokeWidth = path.strokeWidth.coerceIn(0f, 80f),
                transOffsetX = path.transOffsetX.coerceIn(-(bitmap?.width ?: 1) * 4f, (bitmap?.width ?: 1) * 4f),
                transOffsetY = path.transOffsetY.coerceIn(-(bitmap?.height ?: 1) * 4f, (bitmap?.height ?: 1) * 4f),
                transScale = path.transScale.coerceIn(0.2f, 5f),
                transRotation = path.transRotation.coerceIn(-3600f, 3600f)
            )
        }
        val project = SnapCropProject(
            sourceUri = source?.toString()?.take(limits.maxUriChars),
            sourceSha256 = if (computeHash) source?.let { sha256OfUri(it) } else null,
            sourceWidth = bitmap?.width ?: 0,
            sourceHeight = bitmap?.height ?: 0,
            cropRect = Rect(rect),
            adjustments = adj.copyOf(),
            pixelateRects = pixRects.take(limits.maxPixelateRects).map { Rect(it) },
            drawLayers = boundedDrawPaths,
            exportFormat = exportFormat.ext,
            exportMimeType = exportFormat.mime,
            exportQuality = exportFormat.quality,
            exportSavePath = savePath.take(limits.maxPathChars),
            deleteOriginal = deleteOriginal
        )
        return SnapCropProjectSidecar.encode(project)
    }

    private fun sha256OfUri(uri: Uri, maxBytes: Long = 512L * 1024 * 1024): String? {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length > maxBytes) return null
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray, deleteOriginal: Boolean) {
        if (isSaving.value) return
        isSaving.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            var cropped: Bitmap? = null
            try {
                cropped = createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj)
                val bordered = applyBorder(cropped)
                if (bordered !== cropped) cropped.recycle()
                cropped = bordered
                val watermarked = applyWatermark(cropped)
                if (watermarked !== cropped) cropped.recycle()
                cropped = watermarked
                val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
                val name = resolveFilename()
                val annotationSvg = buildAnnotationSvg(rect, pixRects, drawPaths)
                val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
                val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
                val exportFormat = getExportFormat(forcePng = hasShapeCrop)
                val projectSidecarJson = if (projectSidecarsEnabled()) {
                    buildProjectSidecarJson(rect, pixRects, drawPaths, adj, deleteOriginal, exportFormat, savePath)
                } else {
                    null
                }
                saveToGallery(
                    bitmap = cropped,
                    name = name,
                    deleteOriginal = deleteOriginal,
                    forcePng = hasShapeCrop,
                    annotationSvg = annotationSvg,
                    projectSidecarJson = projectSidecarJson
                )
            } catch (e: Exception) {
                android.util.Log.e("SnapCrop", "saveCropped failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, getString(R.string.toast_save_failed) + ": " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                }
            } finally {
                cropped?.let { if (!it.isRecycled) it.recycle() }
                withContext(Dispatchers.Main) {
                    if (pendingMutationPurpose == null) isSaving.value = false
                }
            }
        }
    }

    private fun copyToClipboard(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            var cropped: Bitmap? = null
            try {
                cropped = applyExportDecorations(createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj))
                val clipDir = File(cacheDir, "clipboard")
                clipDir.mkdirs()
                val file = File(clipDir, "clip.png")
                file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                withContext(Dispatchers.Main) {
                    val clipUri = FileProvider.getUriForFile(this@CropActivity, "${packageName}.fileprovider", file)
                    val clip = ClipData.newUri(contentResolver, "SnapCrop", clipUri)
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(clip)
                    Toast.makeText(this@CropActivity, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, getString(R.string.toast_copy_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                cropped?.let { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private fun shareCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cropped = try {
                createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val scanEnabled = getSharedPreferences("snapcrop", MODE_PRIVATE)
                .getBoolean("redact_on_share", true)
            if (!scanEnabled) {
                dispatchShare(cropped, adj)
                return@launch
            }

            val detection = try {
                SensitiveTextDetector.detect(
                    cropped,
                    OcrScript.fromContext(this@CropActivity),
                    failOnOcrError = true
                )
            } catch (e: Exception) {
                android.util.Log.w("SnapCrop", "Sensitive-text share scan failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, R.string.toast_redaction_scan_failed, Toast.LENGTH_LONG).show()
                }
                cropped.recycle()
                return@launch
            }
            if (detection.rects.isEmpty()) {
                dispatchShare(cropped, adj)
                return@launch
            }

            // Build a redacted copy and a downscaled preview off the main thread.
            val redactionStyle = RedactionStyle.fromPreference(
                getSharedPreferences("snapcrop", MODE_PRIVATE).getString(
                    ImageRedactor.PREF_REDACTION_STYLE,
                    RedactionStyle.SOLID.preferenceValue
                )
            )
            val redacted = ImageRedactor.redact(cropped, detection.rects, redactionStyle)
            val preview = scaledPreview(redacted, 720)
            withContext(Dispatchers.Main) {
                val previewView = ImageView(this@CropActivity).apply {
                    setImageBitmap(preview)
                    adjustViewBounds = true
                    val pad = (16 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                val container = LinearLayout(this@CropActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(previewView)
                }
                android.app.AlertDialog.Builder(this@CropActivity)
                    .setTitle(getString(R.string.redact_share_dialog_title))
                    .setMessage(getString(R.string.redact_share_dialog_message, detection.rects.size))
                    .setView(container)
                    .setPositiveButton(getString(R.string.redact_share_action_redacted)) { _, _ ->
                        if (!cropped.isRecycled) cropped.recycle()
                        dispatchShare(redacted, adj)
                    }
                    .setNeutralButton(getString(R.string.redact_share_action_original)) { _, _ ->
                        if (!redacted.isRecycled) redacted.recycle()
                        dispatchShare(cropped, adj)
                    }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                        if (!cropped.isRecycled) cropped.recycle()
                        if (!redacted.isRecycled) redacted.recycle()
                    }
                    .setOnCancelListener {
                        if (!cropped.isRecycled) cropped.recycle()
                        if (!redacted.isRecycled) redacted.recycle()
                    }
                    .setOnDismissListener {
                        if (!preview.isRecycled) preview.recycle()
                    }
                    .show()
            }
        }
    }

    /** Compresses [shareBitmap] per the current format/shape settings and fires the share chooser.
     *  Recycles [shareBitmap] when done. */
    private fun dispatchShare(shareBitmap: Bitmap, adj: FloatArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            val out = applyExportDecorations(shareBitmap)
            val (format, quality) = getSaveFormat()
            val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
            val (shareFmt, shareQual) = if (hasShapeCrop) Bitmap.CompressFormat.PNG to 100 else format to quality
            val isWebp = shareFmt.isWebpFormat()
            val ext = when { shareFmt == Bitmap.CompressFormat.JPEG -> "jpg"; isWebp -> "webp"; else -> "png" }
            val mime = when { shareFmt == Bitmap.CompressFormat.JPEG -> "image/jpeg"; isWebp -> "image/webp"; else -> "image/png" }
            val shareDir = File(cacheDir, "shared_crops"); shareDir.mkdirs()
            val shareFile = File(shareDir, "snapcrop_share.$ext")
            try {
                shareFile.outputStream().use { out.compress(shareFmt, shareQual, it) }
                val shareUri = FileProvider.getUriForFile(this@CropActivity, "${packageName}.fileprovider", shareFile)
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, null))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (!out.isRecycled) out.recycle()
            }
        }
    }

    /** Returns a downscaled copy of [src] no larger than [maxDim] on its longest edge. */
    private fun scaledPreview(src: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxDim) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /** When the user opts in, marks the editor window secure so the un-redacted image can't be
     *  captured by other screenshot tools and doesn't leak into the Recents thumbnail. */
    private fun applySecureScreen() = applySecureWindow(this)

    override fun onResume() {
        super.onResume()
        // Re-apply so toggling the setting (or a restored backup) takes effect without recreation.
        applySecureScreen()
    }

    /** Applies the configured export border + watermark, recycling intermediates. No-op if neither
     *  is enabled (returns the input). Used by share/copy so they match the saved file. */
    private fun applyExportDecorations(input: Bitmap): Bitmap {
        var out = input
        val bordered = applyBorder(out)
        if (bordered !== out) out.recycle()
        out = bordered
        val watermarked = applyWatermark(out)
        if (watermarked !== out) out.recycle()
        return watermarked
    }

    private fun applyBorder(bitmap: Bitmap): Bitmap {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val borderSize = prefs.getInt("border_size", 0)
        if (borderSize <= 0) return bitmap
        val borderColorIdx = prefs.getInt("border_color", 0)
        val borderColors = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E1E2E.toInt(),
            0xFF89B4FA.toInt(), 0xFFA6E3A1.toInt(), 0xFFF38BA8.toInt()
        )
        val bgColor = borderColors[borderColorIdx.coerceIn(0, borderColors.size - 1)]
        val newW = bitmap.width + borderSize * 2
        val newH = bitmap.height + borderSize * 2
        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(bitmap, borderSize.toFloat(), borderSize.toFloat(), null)
        return result
    }

    private fun applyWatermark(bitmap: Bitmap): Bitmap {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        if (!prefs.getBoolean("watermark_enabled", false)) return bitmap
        val text = prefs.getString("watermark_text", "SnapCrop") ?: return bitmap
        if (text.isBlank()) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF // 25% white
            textSize = (bitmap.width * 0.04f).coerceAtLeast(24f)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
        val spacing = paint.textSize * 3
        val diag = kotlin.math.sqrt((bitmap.width.toDouble() * bitmap.width + bitmap.height.toDouble() * bitmap.height)).toFloat()
        var y = -diag / 2
        while (y < diag * 1.5f) {
            var x = -diag / 2
            while (x < diag * 1.5f) {
                canvas.drawText(text, x, y, paint)
                x += paint.measureText(text) + spacing
            }
            y += spacing
        }
        canvas.restore()
        return result
    }

    private fun resolveFilename(): String {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val template = prefs.getString("filename_template", "SnapCrop_%timestamp%") ?: "SnapCrop_%timestamp%"
        val counter = prefs.getInt("save_counter", 1)
        prefs.edit().putInt("save_counter", counter + 1).apply()
        val now = System.currentTimeMillis()
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val timeFmt = java.text.SimpleDateFormat("HH-mm-ss", java.util.Locale.US)
        return template
            .replace("%timestamp%", now.toString())
            .replace("%date%", dateFmt.format(java.util.Date(now)))
            .replace("%time%", timeFmt.format(java.util.Date(now)))
            .replace("%counter%", String.format("%04d", counter))
    }

    private fun compressToTargetSize(bitmap: Bitmap, format: Bitmap.CompressFormat, targetKb: Int): Pair<ByteArray, Int> {
        // Binary search for quality that meets target file size
        var lo = 10; var hi = 100; var bestBytes: ByteArray? = null; var bestQuality = hi
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(format, mid, baos)
            val bytes = baos.toByteArray()
            if (bytes.size <= targetKb * 1024) {
                bestBytes = bytes; bestQuality = mid; lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (bestBytes == null) {
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(format, 10, baos)
            bestBytes = baos.toByteArray(); bestQuality = 10
        }
        return bestBytes to bestQuality
    }

    private fun sidecarPath(imageSavePath: String): String {
        val dir = imageSavePath.trimEnd('/')
        return if (dir.startsWith("Pictures") || dir.startsWith("DCIM")) {
            "Documents/" + dir.substringAfter('/')
        } else {
            dir
        }
    }

    private fun saveSvgSidecar(name: String, savePath: String, svg: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.svg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/svg+xml")
            put(MediaStore.MediaColumns.RELATIVE_PATH, sidecarPath(savePath))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (_: Exception) { null } ?: return false
        return try {
            val bytes = svg.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) throw IOException("SVG sidecar is empty")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("Failed to open SVG output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (contentResolver.update(uri, values, null, null) != 1) {
                throw IOException("Failed to publish SVG sidecar")
            }
            true
        } catch (_: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            false
        }
    }

    private fun saveProjectSidecar(name: String, savePath: String, json: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.snapcrop.json")
            put(MediaStore.MediaColumns.MIME_TYPE, SnapCropProjectSidecar.MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, sidecarPath(savePath))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        } catch (_: Exception) { null } ?: return false
        return try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) throw IOException("Project sidecar is empty")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("Failed to open project output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (contentResolver.update(uri, values, null, null) != 1) {
                throw IOException("Failed to publish project sidecar")
            }
            true
        } catch (_: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            false
        }
    }

    private fun saveToGallery(
        bitmap: Bitmap,
        name: String,
        deleteOriginal: Boolean,
        forcePng: Boolean = false,
        annotationSvg: String? = null,
        projectSidecarJson: String? = null
    ) {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val stripExif = prefs.getBoolean("strip_exif", false)
        val exportFormat = getExportFormat(forcePng)
        val format = exportFormat.format
        val quality = exportFormat.quality
        val ext = exportFormat.ext
        val mime = exportFormat.mime

        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"

        // Target file size compression (JPEG/WebP only, not PNG)
        val targetSizeEnabled = prefs.getBoolean("target_size_enabled", false)
        val targetSizeKb = prefs.getInt("target_size_kb", 500)
        val useTargetSize = targetSizeEnabled && !forcePng && format != Bitmap.CompressFormat.PNG

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            runOnUiThread { Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show() }
            return
        }

        try {
            val baseMessage: String
            if (useTargetSize) {
                val (bytes, usedQuality) = compressToTargetSize(bitmap, format, targetSizeKb)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("Failed to open output stream")
                val sizeKb = bytes.size / 1024
                sourceUri?.let { src -> ExifTransfer.copyExif(contentResolver, src, uri, stripExif) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                if (contentResolver.update(uri, values, null, null) != 1) {
                    throw IOException("Failed to publish output")
                }
                baseMessage = getString(R.string.crop_saved_size, "${sizeKb}KB", usedQuality)
            } else {
                val compressed = contentResolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
                    ?: throw IOException("Failed to open output stream")
                if (!compressed) throw IOException("Image encoder failed")
                sourceUri?.let { src -> ExifTransfer.copyExif(contentResolver, src, uri, stripExif) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                if (contentResolver.update(uri, values, null, null) != 1) {
                    throw IOException("Failed to publish output")
                }
                baseMessage = if (deleteOriginal) getString(R.string.crop_saved_path, savePath)
                    else getString(R.string.crop_copy_saved_path, savePath)
            }

            val svgSaved = annotationSvg == null || saveSvgSidecar(name, savePath, annotationSvg)
            val projectSaved = projectSidecarJson == null || saveProjectSidecar(name, savePath, projectSidecarJson)
            val requestedSidecarsSaved = svgSaved && projectSaved
            val detailMessage = baseMessage +
                (if (annotationSvg != null && svgSaved) " + SVG" else "") +
                (if (projectSidecarJson != null && projectSaved) " + Project" else "")

            runOnUiThread {
                when {
                    deleteOriginal && requestedSidecarsSaved -> {
                        requestSourceTrash(SourceMutationPurpose.REPLACE_AFTER_SAVE, detailMessage)
                    }
                    deleteOriginal -> {
                        Toast.makeText(this, R.string.crop_sidecar_failed_original_retained, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> {
                        val message = if (requestedSidecarsSaved) detailMessage
                            else getString(R.string.crop_saved_sidecar_omitted)
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SnapCrop", "saveToGallery failed", e)
            runOnUiThread { Toast.makeText(this, getString(R.string.toast_save_failed) + ": " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show() }
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
    }

    private fun requestSourceTrash(
        purpose: SourceMutationPurpose,
        exportMessage: String? = null
    ) {
        if (pendingMutationPurpose != null) return
        pendingMutationPurpose = purpose
        pendingExportMessage = exportMessage
        isSaving.value = true
        val uri = sourceUri
        if (uri == null || !uri.scheme.equals("content", ignoreCase = true)) {
            completeSourceMutation(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
                sourceMutationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                android.util.Log.w("SnapCrop", "Unable to request scoped source trash", e)
                completeSourceMutation(false)
            }
        } else {
            deleteSourceOnLegacyAndroid()
        }
    }

    private fun deleteSourceOnLegacyAndroid() {
        val uri = sourceUri ?: run {
            completeSourceMutation(false)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val deleted = contentResolver.delete(uri, null, null) == 1
                withContext(Dispatchers.Main) { completeSourceMutation(deleted) }
            } catch (recoverable: RecoverableSecurityException) {
                withContext(Dispatchers.Main) {
                    sourceMutationLauncher.launch(
                        IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SnapCrop", "Unable to delete source", e)
                withContext(Dispatchers.Main) { completeSourceMutation(false) }
            }
        }
    }

    private fun completeSourceMutation(succeeded: Boolean) {
        val purpose = pendingMutationPurpose ?: return
        val exportMessage = pendingExportMessage
        pendingMutationPurpose = null
        pendingExportMessage = null
        isSaving.value = false
        when (purpose) {
            SourceMutationPurpose.REPLACE_AFTER_SAVE -> {
                val message = if (succeeded) {
                    listOfNotNull(exportMessage, getString(R.string.crop_original_trashed)).joinToString(". ")
                } else {
                    getString(R.string.crop_copy_saved_original_retained)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
            }
            SourceMutationPurpose.DELETE_FROM_EDITOR -> {
                Toast.makeText(
                    this,
                    if (succeeded) R.string.toast_moved_to_trash else R.string.toast_items_retained,
                    Toast.LENGTH_SHORT
                ).show()
                if (succeeded) finish()
            }
        }
    }

    override fun onDestroy() {
        // The draft only exists to survive process death; on a real finish, clean it up so the
        // source URI + edit geometry don't linger in app-private storage.
        if (isFinishing) runCatching { draftFile.delete() }
        val current = bitmapState.value
        if (current != null && current !== originalBitmap) current.recycle()
        originalBitmap?.recycle()
        originalBitmap = null; bitmapState.value = null
        super.onDestroy()
    }
}
