package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.ClipboardManager
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
import android.graphics.Typeface
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
import android.widget.TextView
import android.widget.ScrollView
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
import androidx.compose.runtime.Composable
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
import com.sysadmindoc.snapcrop.ui.theme.Danger
import com.sysadmindoc.snapcrop.ui.theme.Black
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

private data class ShareRedactionCandidate(
    val bounds: Rect,
    val categories: Set<SensitiveTextCategory>,
)

private data class DraftSourceSnapshot(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val context: ExplicitSourceContext?,
)

class CropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_FLASH = "show_flash"
        private const val DRAFT_DEBOUNCE_MILLIS = 750L
        private const val KEY_HAS_DRAFT = "has_editor_draft"
        private const val KEY_MUTATION_PURPOSE = "source_mutation_purpose"
        private const val KEY_MUTATION_MESSAGE = "source_mutation_message"
        private const val KEY_REPLACEMENT_URI = "replacement_uri"
        private const val KEY_REPLACEMENT_DATE = "replacement_date"
        private const val KEY_REPLACED_SOURCE_DATE = "replaced_source_date"
    }

    private var originalBitmap: Bitmap? = null
    private val bitmapState = mutableStateOf<Bitmap?>(null)
    private val cropRect = mutableStateOf(Rect(0, 0, 0, 0))
    private val cropMethod = mutableStateOf("")
    private val isLoading = mutableStateOf(true)
    private val isSaving = mutableStateOf(false)
    private val showFlash = mutableStateOf(false)
    private var sourceUri: Uri? = null
    private var selectedExportPresetId: String? = null
    private var draftStateProvider: (() -> EditorDraft)? = null
    private val draftStore by lazy { EditorDraftStore(filesDir) }
    private val draftCheckpointScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var draftCheckpointJob: Job? = null
    private val pendingDraftRecovery = mutableStateOf<SnapCropProject?>(null)
    private val draftRecoveryBlocked = mutableStateOf(false)
    @Volatile private var restoringDraft = false
    private var intentSourceHints: List<String> = emptyList()
    private var currentCropHints: List<String> = emptyList()
    private val explicitSourceContext = mutableStateOf<ExplicitSourceContext?>(null)
    private val initialRedactions = mutableStateOf<List<RedactionRegion>>(emptyList())
    private val initialDrawPaths = mutableStateOf<List<DrawPath>>(emptyList())
    private val initialAdjustments = mutableStateOf<FloatArray?>(null)
    private val initialCutout = mutableStateOf(CutoutEditState())
    private val initialOcrBlocks = mutableStateOf<List<TextBlock>>(emptyList())
    private val initialOcrReviewed = mutableStateOf(false)
    private val projectLoadError = mutableStateOf<String?>(null)
    private val rotationKey = mutableIntStateOf(0)
    private var pendingMutationPurpose: SourceMutationPurpose? = null
    private var pendingMutationStartedAt: Long? = null
    private var pendingExportMessage: String? = null
    private var pendingReplacementUri: Uri? = null
    private var pendingReplacementDateAdded: Long = -1L
    private var pendingReplacedSourceDateAdded: Long = -1L
    private var pendingRelinkProject: SnapCropProject? = null
    private var pendingProjectPolicy = SourceVerificationPolicy.REQUIRE_FINGERPRINT
    private val projectCanRelink = mutableStateOf(false)

    private val sourceTrashLauncher = registerForActivityResult(
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
        val sharedUris = InboundShareContract.extractUris(incomingIntent)
        if (sharedUris.size > 1) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putParcelableArrayListExtra(InboundShareContract.EXTRA_URIS, ArrayList(sharedUris))
                clipData = ClipData.newRawUri("Shared images", sharedUris.first()).apply {
                    sharedUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            finish()
            return
        }
        val newUri = when {
            incomingIntent.data != null -> incomingIntent.data
            sharedUris.size == 1 -> sharedUris.single()
            else -> null
        }
        if (newUri == null) { finish(); return }

        // Reset state for the new image or project sidecar.
        sourceUri = null
        intentSourceHints = CropSourceHints.fromIntent(incomingIntent, newUri)
        explicitSourceContext.value = ExplicitSourceContext.fromIntent(incomingIntent, referrer)
        isLoading.value = true
        bitmapState.value = null
        cropMethod.value = ""
        initialRedactions.value = emptyList()
        initialDrawPaths.value = emptyList()
        initialAdjustments.value = null
        initialCutout.value = CutoutEditState()
        initialOcrBlocks.value = emptyList()
        initialOcrReviewed.value = false
        selectedExportPresetId = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getString(ExportPresetStore.PREF_EDITOR_PRESET_ID, null)
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
                val storedContext = mediaDateAdded(newUri)?.let { dateAdded ->
                    runCatching {
                        ScreenshotIndexStore(this@CropActivity).sourceContext(newUri, dateAdded)
                    }.getOrNull()
                }
                explicitSourceContext.value = storedContext?.mergedWith(explicitSourceContext.value)
                loadBitmap(newUri)
            }
            withContext(Dispatchers.Main) { isLoading.value = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDraftRecovery.value = null
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureScreen()
        pendingMutationPurpose = savedInstanceState?.getString(KEY_MUTATION_PURPOSE)
            ?.let { runCatching { SourceMutationPurpose.valueOf(it) }.getOrNull() }
        pendingExportMessage = savedInstanceState?.getString(KEY_MUTATION_MESSAGE)
        pendingReplacementUri = savedInstanceState?.getString(KEY_REPLACEMENT_URI)?.let(Uri::parse)
        pendingReplacementDateAdded = savedInstanceState?.getLong(KEY_REPLACEMENT_DATE, -1L) ?: -1L
        pendingReplacedSourceDateAdded = savedInstanceState?.getLong(KEY_REPLACED_SOURCE_DATE, -1L) ?: -1L
        setContent {
            SnapCropTheme {
                val showDeleteConfirm = remember { mutableStateOf(false) }
                val showSourceContextEditor = remember { mutableStateOf(false) }
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
                            initialRedactions = initialRedactions.value,
                            initialDrawPaths = initialDrawPaths.value,
                            initialAdjustments = initialAdjustments.value,
                            initialCutout = initialCutout.value,
                            initialOcrBlocks = initialOcrBlocks.value,
                            onOcrChanged = { initialOcrBlocks.value = it.map(TextBlock::deepCopy) },
                            initialOcrReviewed = initialOcrReviewed.value,
                            onOcrReviewedChanged = { initialOcrReviewed.value = it },
                            initialExportPresetId = selectedExportPresetId,
                            onExportPresetChanged = { presetId ->
                                selectedExportPresetId = presetId
                                getSharedPreferences("snapcrop", MODE_PRIVATE).edit().apply {
                                    presetId?.let { putString(ExportPresetStore.PREF_EDITOR_PRESET_ID, it) }
                                        ?: remove(ExportPresetStore.PREF_EDITOR_PRESET_ID)
                                }.apply()
                            },
                            registerStateProvider = { provider -> draftStateProvider = provider },
                            onDraftChanged = { scheduleDraftCheckpoint(DRAFT_DEBOUNCE_MILLIS) },
                            onSave = { rect, redactions, draw, adj, cutout -> saveCropped(bmp, rect, redactions, draw, adj, cutout, deleteOriginal = effectiveDeleteOriginalOnSave()) },
                            onSaveCopy = { rect, redactions, draw, adj, cutout -> saveCropped(bmp, rect, redactions, draw, adj, cutout, deleteOriginal = false) },
                            onShare = { rect, redactions, draw, adj, cutout -> shareCropped(bmp, rect, redactions, draw, adj, cutout) },
                            onCopyClipboard = { rect, redactions, draw, adj, cutout -> copyToClipboard(bmp, rect, redactions, draw, adj, cutout) },
                            hasSourceContext = explicitSourceContext.value != null,
                            onEditSourceContext = { showSourceContextEditor.value = true },
                            onDiscard = ::discardDraftAndFinish,
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
                                invalidateOcrReview()
                                bitmapState.value = resized
                                cropRect.value = Rect(0, 0, newW, newH)
                                cropMethod.value = ""
                            },
                            onRemoveBg = { onDone ->
                                lifecycleScope.launch {
                                    val journalStarted = OperationJournal.start()
                                    val result = BackgroundRemover.removeWithStatus(this@CropActivity, bmp)
                                    OperationJournal.enqueue(
                                        this@CropActivity,
                                        DiagnosticOperation.MODEL,
                                        DiagnosticStage.PROCESS,
                                        if (result.changed) DiagnosticResult.SUCCESS else DiagnosticResult.FAILED,
                                        journalStarted
                                    )
                                    if (result.changed && result.bitmap !== bmp) {
                                        val old = bitmapState.value
                                        if (old != null && old !== originalBitmap) old.recycle()
                                        originalBitmap = null
                                        invalidateOcrReview()
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

                    if (showSourceContextEditor.value) {
                        SourceContextEditorDialog(
                            initial = explicitSourceContext.value,
                            onSave = { contextValue ->
                                explicitSourceContext.value = contextValue
                                showSourceContextEditor.value = false
                                sourceUri?.let { mediaUri ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        mediaDateAdded(mediaUri)?.let { dateAdded ->
                                            runCatching {
                                                ScreenshotIndexStore(this@CropActivity)
                                                    .putSourceContext(mediaUri, dateAdded, contextValue)
                                            }.onFailure {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        this@CropActivity,
                                                        R.string.source_context_save_failed,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onDismiss = { showSourceContextEditor.value = false }
                        )
                    }

                    projectLoadError.value?.let { message ->
                        ProjectLoadErrorPanel(
                            message = message,
                            canRelink = projectCanRelink.value,
                            onRelink = { projectSourcePicker.launch(arrayOf("image/*")) },
                            onClose = ::discardDraftAndFinish,
                        )
                    }

                    pendingDraftRecovery.value?.let { project ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.crop_draft_recovery_title)) },
                            text = { Text(stringResource(R.string.crop_draft_recovery_message)) },
                            confirmButton = {
                                TextButton(onClick = { restoreDraft(project) }) {
                                    Text(stringResource(R.string.crop_draft_restore))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = ::discardDraftAndContinue) {
                                    Text(stringResource(R.string.crop_draft_discard))
                                }
                            },
                        )
                    }

                    if (draftRecoveryBlocked.value) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.crop_draft_recovery_failed_title)) },
                            text = { Text(stringResource(R.string.crop_draft_recovery_failed_message)) },
                            confirmButton = {
                                TextButton(onClick = ::discardDraftAndContinue) {
                                    Text(stringResource(R.string.crop_draft_discard_continue))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.close))
                                }
                            },
                        )
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
                                    requestSourceMutation(SourceMutationPurpose.DELETE_FROM_EDITOR)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Danger)
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
        recoverDraftOnLaunch(savedInstanceState?.getBoolean(KEY_HAS_DRAFT) == true)
    }

    private fun getDeletePref(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("delete_original", true)

    private fun appCropProfilesEnabled(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("app_crop_profiles", true)

    private fun projectSidecarsEnabled(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("project_sidecars", false)

    private fun ocrTextSidecarsEnabled(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("ocr_text_sidecars", false)

    private fun invalidateOcrReview() {
        initialOcrBlocks.value = emptyList()
        initialOcrReviewed.value = false
    }

    private fun currentExportSettings(): ExportSettings = ExportPresetStore.resolve(
        getSharedPreferences("snapcrop", MODE_PRIVATE),
        selectedExportPresetId
    )

    private fun effectiveDeleteOriginalOnSave(): Boolean =
        getDeletePref() && !projectSidecarsEnabled()

    private fun getSaveFormat(
        bitmap: Bitmap? = null,
        settings: ExportSettings = currentExportSettings()
    ): Pair<Bitmap.CompressFormat, Int> {
        val resolved = getExportFormat(
            forcePng = false,
            ultraHdr = bitmap?.hasUltraHdrGainmap() == true,
            settings = settings
        )
        return resolved.format to resolved.quality
    }

    private fun getExportFormat(
        forcePng: Boolean,
        ultraHdr: Boolean = false,
        settings: ExportSettings = currentExportSettings()
    ): CropExportFormat {
        val r = resolveExportFormat(settings.format, settings.quality, forcePng, ultraHdr)
        return CropExportFormat(r.format, r.quality, r.ext, r.mime)
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

    private fun recoverDraftOnLaunch(restoreWithoutPrompt: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = draftStore.readValidated()
            withContext(Dispatchers.Main) {
                when (result) {
                    EditorDraftReadResult.None -> handleIntent(intent)
                    is EditorDraftReadResult.Ready -> {
                        if (restoreWithoutPrompt) {
                            restoreDraft(result.project)
                        } else {
                            pendingDraftRecovery.value = result.project
                            isLoading.value = false
                        }
                    }
                    is EditorDraftReadResult.Quarantined -> {
                        Toast.makeText(
                            this@CropActivity,
                            R.string.crop_draft_quarantined,
                            Toast.LENGTH_LONG,
                        ).show()
                        handleIntent(intent)
                    }
                    is EditorDraftReadResult.Failed -> {
                        draftRecoveryBlocked.value = true
                        isLoading.value = false
                    }
                }
            }
        }
    }

    private fun restoreDraft(project: SnapCropProject) {
        pendingDraftRecovery.value = null
        draftRecoveryBlocked.value = false
        restoringDraft = true
        isLoading.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            handleProjectDecode(
                ProjectDecodeResult.Success(project),
                ProjectImportOrigin.INTERNAL_DRAFT,
            )
            withContext(Dispatchers.Main) { isLoading.value = false }
        }
    }

    private fun discardDraftAndContinue() {
        draftCheckpointJob?.cancel()
        draftCheckpointScope.launch {
            val discarded = draftStore.discard()
            withContext(Dispatchers.Main) {
                if (discarded.complete) {
                    pendingDraftRecovery.value = null
                    draftRecoveryBlocked.value = false
                    restoringDraft = false
                    handleIntent(intent)
                } else {
                    Toast.makeText(
                        this@CropActivity,
                        R.string.crop_draft_discard_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun discardDraftAndFinish() {
        draftCheckpointJob?.cancel()
        draftCheckpointScope.launch {
            val discarded = draftStore.discard()
            withContext(Dispatchers.Main) {
                if (!discarded.complete) {
                    Toast.makeText(
                        this@CropActivity,
                        R.string.crop_draft_discard_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
                finish()
            }
        }
    }

    private fun scheduleDraftCheckpoint(delayMillis: Long): Boolean {
        val provider = draftStateProvider ?: return false
        val source = sourceUri ?: return false
        val bitmap = bitmapState.value ?: return false
        val draft = runCatching(provider).getOrNull() ?: return false
        val sourceSnapshot = DraftSourceSnapshot(
            uri = source,
            width = bitmap.width,
            height = bitmap.height,
            context = explicitSourceContext.value,
        )
        val exportFormat = getExportFormat(forcePng = false)
        val exportSettings = currentExportSettings()
        val deleteOriginal = effectiveDeleteOriginalOnSave()
        draftCheckpointJob?.cancel()
        draftCheckpointJob = draftCheckpointScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val json = buildProjectSidecarJson(
                rect = draft.crop,
                redactions = draft.redactions,
                drawPaths = draft.draws,
                adj = draft.adj,
                cutout = draft.cutout,
                ocrBlocks = draft.ocrBlocks,
                ocrReviewed = draft.ocrReviewed,
                deleteOriginal = deleteOriginal,
                exportFormat = exportFormat,
                savePath = exportSettings.savePath,
                computeHash = false,
                sourceSnapshot = sourceSnapshot,
            )
            draftStore.write(json)
        }
        return true
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
        pendingReplacementUri?.let { outState.putString(KEY_REPLACEMENT_URI, it.toString()) }
        outState.putLong(KEY_REPLACEMENT_DATE, pendingReplacementDateAdded)
        outState.putLong(KEY_REPLACED_SOURCE_DATE, pendingReplacedSourceDateAdded)
        // Capture Compose state on main, then encode/fsync/promote on the serialized IO scope.
        outState.putBoolean(KEY_HAS_DRAFT, scheduleDraftCheckpoint(delayMillis = 0L))
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
                    explicitSourceContext.value = project.sourceContext
                    cropRect.value = project.cropRect
                    cropMethod.value = "project"
                    initialRedactions.value = project.redactions
                    initialDrawPaths.value = project.drawLayers
                    initialAdjustments.value = project.adjustments
                    initialCutout.value = CutoutEditState(project.cutBands, project.cutSeparatorStyle)
                    initialOcrBlocks.value = project.ocrBlocks.map(TextBlock::deepCopy)
                    initialOcrReviewed.value = project.ocrReviewed
                    if (restoringDraft) {
                        restoringDraft = false
                        val discarded = draftStore.discard()
                        if (!discarded.complete) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@CropActivity,
                                    R.string.crop_draft_discard_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
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
        invalidateOcrReview()
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
        invalidateOcrReview()
        bitmapState.value = flipped
    }

    private fun buildAnnotationSvg(
        cropRect: Rect,
        redactions: List<RedactionRegion>,
        drawPaths: List<DrawPath>,
        cutout: CutoutEditState = CutoutEditState(),
    ): String? {
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

        redactions.filter(RedactionRegion::enabled).forEachIndexed { index, region ->
            val rect = region.bounds
            val left = (rect.left - cropRect.left).coerceIn(0, width).toFloat()
            val top = (rect.top - cropRect.top).coerceIn(0, height).toFloat()
            val right = (rect.right - cropRect.left).coerceIn(0, width).toFloat()
            val bottom = (rect.bottom - cropRect.top).coerceIn(0, height).toFloat()
            if (right > left && bottom > top) {
                val categories = region.categories.joinToString(",") { it.name.lowercase(Locale.US) }
                val opacity = if (region.style == RedactionStyle.SOLID) "1.00" else "0.65"
                elements.append("""  <rect id="${xml(region.id)}" data-style="${region.style.preferenceValue}" data-categories="${xml(categories)}" x="${left.svgNum()}" y="${top.svgNum()}" width="${(right - left).svgNum()}" height="${(bottom - top).svgNum()}" fill="#000000" opacity="$opacity" stroke="#F38BA8" stroke-width="2"/>""")
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
        val source = bitmapState.value
        val cutPlan = if (cutout.bands.isNotEmpty() && source != null) {
            CutoutSqueeze.createForCrop(
                source.width,
                source.height,
                cropRect.left,
                cropRect.top,
                cropRect.right,
                cropRect.bottom,
                cutout.bands,
                cutout.separatorStyle,
            )
        } else null
        val outputWidth = cutPlan?.outputWidth ?: width
        val outputHeight = cutPlan?.outputHeight ?: height
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$outputWidth" height="$outputHeight" viewBox="0 0 $outputWidth $outputHeight">""").append('\n')
            append("""  <title>SnapCrop annotation layers</title>""").append('\n')
            if (cutPlan == null) {
                append(elements)
            } else {
                append("  <defs>\n    <g id=\"annotation-layers\">\n")
                append(elements)
                append("    </g>\n")
                cutPlan.retainedSourceRects.forEachIndexed { index, tile ->
                    val out = tile.outputRect
                    append("    <clipPath id=\"cut-clip-$index\"><rect x=\"${out.left.svgNum()}\" y=\"${out.top.svgNum()}\" width=\"${out.width.svgNum()}\" height=\"${out.height.svgNum()}\"/></clipPath>\n")
                }
                append("  </defs>\n")
                cutPlan.retainedSourceRects.forEachIndexed { index, tile ->
                    val dx = tile.outputOffset.x - tile.sourceRect.left
                    val dy = tile.outputOffset.y - tile.sourceRect.top
                    append("  <g clip-path=\"url(#cut-clip-$index)\"><use xlink:href=\"#annotation-layers\" transform=\"translate(${dx.svgNum()} ${dy.svgNum()})\"/></g>\n")
                }
                cutPlan.separators.forEachIndexed { index, seam ->
                    val dash = if (seam.style == CutSeparatorStyle.DASHED) " stroke-dasharray=\"14 10\"" else ""
                    if (seam.style == CutSeparatorStyle.TORN) {
                        val length = if (seam.axis == CutAxis.HORIZONTAL) outputWidth else outputHeight
                        val points = buildString {
                            var cursor = 0
                            var direction = 1
                            while (cursor <= length) {
                                val x = if (seam.axis == CutAxis.HORIZONTAL) cursor else seam.outputPosition + 4 * direction
                                val y = if (seam.axis == CutAxis.HORIZONTAL) seam.outputPosition + 4 * direction else cursor
                                append(if (isEmpty()) "M " else " L ").append(x).append(' ').append(y)
                                cursor += 18
                                direction = -direction
                            }
                        }
                        append("  <path id=\"cut-seam-$index\" d=\"$points\" fill=\"none\" stroke=\"#FFFFFF\" stroke-width=\"2\"/>\n")
                    } else if (seam.axis == CutAxis.HORIZONTAL) {
                        append("  <line id=\"cut-seam-$index\" x1=\"0\" y1=\"${seam.outputPosition}\" x2=\"$outputWidth\" y2=\"${seam.outputPosition}\" stroke=\"#FFFFFF\" stroke-width=\"2\"$dash/>\n")
                    } else {
                        append("  <line id=\"cut-seam-$index\" x1=\"${seam.outputPosition}\" y1=\"0\" x2=\"${seam.outputPosition}\" y2=\"$outputHeight\" stroke=\"#FFFFFF\" stroke-width=\"2\"$dash/>\n")
                    }
                }
            }
            append("</svg>\n")
        }
    }

    private fun buildProjectSidecarJson(
        rect: Rect,
        redactions: List<RedactionRegion>,
        drawPaths: List<DrawPath>,
        adj: FloatArray,
        cutout: CutoutEditState,
        deleteOriginal: Boolean,
        exportFormat: CropExportFormat,
        savePath: String,
        ocrBlocks: List<TextBlock> = initialOcrBlocks.value,
        ocrReviewed: Boolean = initialOcrReviewed.value,
        computeHash: Boolean = true,
        sourceSnapshot: DraftSourceSnapshot? = null,
    ): String {
        val source = sourceSnapshot?.uri ?: sourceUri
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
            sourceWidth = sourceSnapshot?.width ?: bitmap?.width ?: 0,
            sourceHeight = sourceSnapshot?.height ?: bitmap?.height ?: 0,
            cropRect = Rect(rect),
            adjustments = adj.copyOf(),
            cutBands = cutout.bands,
            cutSeparatorStyle = cutout.separatorStyle,
            redactions = redactions.take(limits.maxRedactions).map { it.copy() },
            ocrReviewed = ocrReviewed,
            drawLayers = boundedDrawPaths,
            ocrBlocks = ocrBlocks
                .filterNot { it.text.isBlank() }
                .take(limits.maxOcrBlocks)
                .map(TextBlock::deepCopy),
            exportFormat = exportFormat.ext,
            exportMimeType = exportFormat.mime,
            exportQuality = exportFormat.quality,
            exportSavePath = savePath.take(limits.maxPathChars),
            deleteOriginal = deleteOriginal,
            sourceContext = if (sourceSnapshot != null) {
                sourceSnapshot.context
            } else {
                explicitSourceContext.value
            }
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

    private fun saveCropped(bitmap: Bitmap, rect: Rect, redactions: List<RedactionRegion>, drawPaths: List<DrawPath>, adj: FloatArray, cutout: CutoutEditState, deleteOriginal: Boolean) {
        if (isSaving.value) return
        isSaving.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            var cropped: Bitmap? = null
            try {
                val exportSettings = currentExportSettings()
                cropped = CropImageRenderer.render(bitmap, rect, redactions, drawPaths, adj, cutout)
                val bordered = ExportPresetRenderer.applyBorder(cropped, exportSettings)
                if (bordered !== cropped) cropped.recycle()
                cropped = bordered
                val watermarked = ExportPresetRenderer.applyWatermark(cropped, exportSettings)
                if (watermarked !== cropped) cropped.recycle()
                cropped = watermarked
                val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
                val name = resolveFilename(exportSettings)
                val annotationSvg = buildAnnotationSvg(rect, redactions, drawPaths, cutout)
                val savePath = exportSettings.savePath
                val exportFormat = getExportFormat(
                    forcePng = hasShapeCrop,
                    ultraHdr = cropped.hasUltraHdrGainmap(),
                    settings = exportSettings
                )
                val projectSidecarJson = if (projectSidecarsEnabled()) {
                    buildProjectSidecarJson(rect, redactions, drawPaths, adj, cutout, deleteOriginal, exportFormat, savePath)
                } else {
                    null
                }
                val correctedOcrText = if (ocrTextSidecarsEnabled()) {
                    val retainedBlocks = initialOcrBlocks.value.filter { block ->
                        block.bounds.left >= rect.left && block.bounds.top >= rect.top &&
                            block.bounds.right <= rect.right && block.bounds.bottom <= rect.bottom &&
                            cutout.bands.none { band ->
                                if (band.axis == CutAxis.HORIZONTAL) {
                                    block.bounds.top < band.endExclusive && band.start < block.bounds.bottom
                                } else {
                                    block.bounds.left < band.endExclusive && band.start < block.bounds.right
                                }
                            }
                    }
                    ReviewedOcr.plainText(retainedBlocks)
                        .takeIf(String::isNotEmpty)
                } else {
                    null
                }
                saveToGallery(
                    bitmap = cropped,
                    name = name,
                    deleteOriginal = deleteOriginal,
                    forcePng = hasShapeCrop,
                    annotationSvg = annotationSvg,
                    projectSidecarJson = projectSidecarJson,
                    correctedOcrText = correctedOcrText,
                    exportSettings = exportSettings
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

    private fun copyToClipboard(bitmap: Bitmap, rect: Rect, redactions: List<RedactionRegion>, drawPaths: List<DrawPath>, adj: FloatArray, cutout: CutoutEditState) {
        lifecycleScope.launch(Dispatchers.IO) {
            var cropped: Bitmap? = null
            try {
                val exportSettings = currentExportSettings()
                cropped = applyExportDecorations(
                    CropImageRenderer.render(bitmap, rect, redactions, drawPaths, adj, cutout),
                    exportSettings
                )
                val clipDir = File(cacheDir, CacheCleanupPolicy.CLIPBOARD_DIRECTORY)
                // Clipboard stays lossless PNG unless HDR forces JPEG (pre-Android 16). On API 36+
                // the PNG codec carries the gain map, so HDR pastes keep PNG too.
                val clipFormat = getExportFormat(
                    forcePng = true,
                    ultraHdr = cropped.hasUltraHdrGainmap(),
                    settings = exportSettings
                )
                val file = File(clipDir, "clip.${clipFormat.ext}")
                val result = CropCacheArtifactPublisher.publish(
                    file = file,
                    writer = { target ->
                        target.outputStream().use { output ->
                            cropped.compress(clipFormat.format, clipFormat.quality, output)
                        }
                    },
                    dispatcher = { target ->
                        withContext(Dispatchers.Main) {
                            val clipUri = FileProvider.getUriForFile(
                                this@CropActivity,
                                "${packageName}.fileprovider",
                                target,
                            )
                            val clip = ClipData.newUri(contentResolver, "SnapCrop", clipUri)
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clip)
                        }
                    },
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CropActivity,
                        if (result is CropCacheArtifactPublisher.Result.Success) {
                            getString(R.string.toast_copied)
                        } else {
                            getString(R.string.toast_copy_failed)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
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

    private fun shareCropped(bitmap: Bitmap, rect: Rect, redactions: List<RedactionRegion>, drawPaths: List<DrawPath>, adj: FloatArray, cutout: CutoutEditState) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cropped = try {
                CropImageRenderer.render(bitmap, rect, redactions, drawPaths, adj, cutout)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val metadataSummary = sourceUri?.let { ExifTransfer.inspect(contentResolver, it) }
                ?: MetadataSummary(emptySet())
            if (metadataSummary.inspectionFailed) {
                Toast.makeText(
                    this@CropActivity,
                    R.string.share_metadata_inspection_failed,
                    Toast.LENGTH_LONG,
                ).show()
                cropped.recycle()
                return@launch
            }
            val shareOptions = try {
                if (metadataSummary.hasMetadata || explicitSourceContext.value?.url != null) {
                    chooseShareOptions(
                        metadataSummary,
                        sourceUrl = explicitSourceContext.value?.url
                    )
                } else {
                    ShareOptions(ShareMetadataPolicy.STRIP_ALL, includeSourceLink = false)
                }
            } catch (cancelled: CancellationException) {
                cropped.recycle()
                throw cancelled
            }
            if (shareOptions == null) {
                cropped.recycle()
                return@launch
            }
            val metadataPolicy = shareOptions.metadataPolicy
            val scanEnabled = getSharedPreferences("snapcrop", MODE_PRIVATE)
                .getBoolean("redact_on_share", true)
            if (!scanEnabled) {
                dispatchShare(cropped, adj, metadataPolicy, shareOptions.includeSourceLink)
                return@launch
            }

            val detection = try {
                SensitiveTextDetector.detect(
                    cropped,
                    OcrScript.fromContext(this@CropActivity),
                    failOnOcrError = true,
                    customPatterns = CustomRedactionPatternStore.load(
                        getSharedPreferences("snapcrop", MODE_PRIVATE)
                    ),
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
                dispatchShare(cropped, adj, metadataPolicy, shareOptions.includeSourceLink)
                return@launch
            }

            val redactionStyle = RedactionStyle.fromPreference(
                getSharedPreferences("snapcrop", MODE_PRIVATE).getString(
                    ImageRedactor.PREF_REDACTION_STYLE,
                    RedactionStyle.SOLID.preferenceValue
                )
            )
            val candidates = detection.detections
                .groupBy { with(it.bounds) { "$left:$top:$right:$bottom" } }
                .values
                .map { matches ->
                    ShareRedactionCandidate(
                        Rect(matches.first().bounds),
                        matches.mapTo(linkedSetOf()) { it.category },
                    )
                }
            val preview = buildShareRedactionReviewPreview(
                cropped,
                candidates.mapIndexed { index, candidate -> (index + 1) to candidate.bounds },
                redactionStyle,
            )
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
                val checks = candidates.mapIndexed { index, candidate ->
                    android.widget.CheckBox(this@CropActivity).apply {
                        isChecked = true
                        text = getString(
                            R.string.redact_share_candidate,
                            index + 1,
                            candidate.categories.joinToString(", ") { sensitiveCategoryLabel(it) },
                        )
                    }

                }
                val checkList = LinearLayout(this@CropActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    checks.forEach(::addView)
                }
                container.addView(TextView(this@CropActivity).apply {
                    text = getString(R.string.redact_share_review_hint)
                    val pad = (16 * resources.displayMetrics.density).toInt()
                    setPadding(pad, 0, pad, 0)
                })
                container.addView(ScrollView(this@CropActivity).apply {
                    addView(checkList)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (220 * resources.displayMetrics.density).toInt(),
                    )
                })
                var activePreview = preview
                var previewGeneration = 0
                var previewJob: Job? = null
                lateinit var dialog: android.app.AlertDialog
                fun refreshPreview() {
                    val generation = ++previewGeneration
                    val selected = candidates.mapIndexedNotNull { index, candidate ->
                        ((index + 1) to candidate.bounds).takeIf { checks[index].isChecked }
                    }
                    previewJob?.cancel()
                    previewJob = lifecycleScope.launch(Dispatchers.IO) {
                        val updated = buildShareRedactionReviewPreview(cropped, selected, redactionStyle)
                        withContext(Dispatchers.Main) {
                            if (generation == previewGeneration && dialog.isShowing) {
                                val old = activePreview
                                activePreview = updated
                                previewView.setImageBitmap(updated)
                                if (!old.isRecycled) old.recycle()
                            } else if (!updated.isRecycled) {
                                updated.recycle()
                            }
                        }
                    }
                }
                checks.forEach { check ->
                    check.setOnCheckedChangeListener { _, _ ->
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = checks.any { it.isChecked }
                        refreshPreview()
                    }
                }
                dialog = android.app.AlertDialog.Builder(this@CropActivity)
                    .setTitle(getString(R.string.redact_share_dialog_title))
                    .setMessage(getString(R.string.redact_share_dialog_message, candidates.size))
                    .setView(container)
                    .setPositiveButton(getString(R.string.redact_share_action_redacted)) { _, _ ->
                        val selected = candidates.filterIndexed { index, _ -> checks[index].isChecked }
                            .map { it.bounds }
                        val pendingPreview = previewJob
                        pendingPreview?.cancel()
                        lifecycleScope.launch(Dispatchers.IO) {
                            pendingPreview?.join()
                            try {
                                val reviewed = if (selected.isEmpty()) cropped else {
                                    ImageRedactor.redact(cropped, selected, redactionStyle).also {
                                        if (!cropped.isRecycled) cropped.recycle()
                                    }
                                }
                                dispatchShare(reviewed, adj, metadataPolicy, shareOptions.includeSourceLink)
                            } catch (_: Exception) {
                                if (!cropped.isRecycled) cropped.recycle()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@CropActivity, R.string.toast_share_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNeutralButton(getString(R.string.redact_share_action_original)) { _, _ ->
                        val pendingPreview = previewJob
                        pendingPreview?.cancel()
                        lifecycleScope.launch(Dispatchers.IO) {
                            pendingPreview?.join()
                            dispatchShare(cropped, adj, metadataPolicy, shareOptions.includeSourceLink)
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                        val pendingPreview = previewJob
                        pendingPreview?.cancel()
                        lifecycleScope.launch(Dispatchers.IO) {
                            pendingPreview?.join()
                            if (!cropped.isRecycled) cropped.recycle()
                        }
                    }
                    .setOnCancelListener {
                        val pendingPreview = previewJob
                        pendingPreview?.cancel()
                        lifecycleScope.launch(Dispatchers.IO) {
                            pendingPreview?.join()
                            if (!cropped.isRecycled) cropped.recycle()
                        }
                    }
                    .setOnDismissListener {
                        previewGeneration++
                        if (!activePreview.isRecycled) activePreview.recycle()
                    }
                    .create()
                dialog.show()
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = checks.any { it.isChecked }
            }
        }
    }

    private fun mediaDateAdded(uri: Uri): Long? = try {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_ADDED),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Compresses [shareBitmap] per the current format/shape settings and fires the share chooser.
     *  Recycles [shareBitmap] when done. */
    private fun dispatchShare(
        shareBitmap: Bitmap,
        adj: FloatArray,
        metadataPolicy: ShareMetadataPolicy,
        includeSourceLink: Boolean
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val exportSettings = currentExportSettings()
            val out = applyExportDecorations(shareBitmap, exportSettings)
            val (format, quality) = getSaveFormat(out, exportSettings)
            val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
            val (shareFmt, shareQual) = if (hasShapeCrop && !out.hasUltraHdrGainmap()) Bitmap.CompressFormat.PNG to 100 else format to quality
            val isWebp = shareFmt.isWebpFormat()
            val ext = when { shareFmt == Bitmap.CompressFormat.JPEG -> "jpg"; isWebp -> "webp"; else -> "png" }
            val mime = when { shareFmt == Bitmap.CompressFormat.JPEG -> "image/jpeg"; isWebp -> "image/webp"; else -> "image/png" }
            val useTargetSize = exportSettings.targetSizeEnabled && shareFmt != Bitmap.CompressFormat.PNG
            val targetBytes = exportSettings.targetSizeKb.coerceIn(50, 5000).toLong() * 1024L
            val shareDir = File(cacheDir, CacheCleanupPolicy.SHARED_CROPS_DIRECTORY)
            val shareFile = File(shareDir, "${resolveFilename(exportSettings)}.$ext")
            try {
                val result = CropCacheArtifactPublisher.publish(
                    file = shareFile,
                    writer = { target ->
                        val encoded = if (useTargetSize) {
                            when (val compressed = ExportPresetRenderer.compressToTarget(
                                out,
                                shareFmt,
                                exportSettings.targetSizeKb,
                                exportSettings.targetDownscalePolicy(),
                            )) {
                                is TargetCompressionResult.WithinBudget -> target.writeBytes(compressed.bytes)
                                is TargetCompressionResult.CannotMeetWithoutResize ->
                                    throw IOException(getString(R.string.target_size_unmet))
                                is TargetCompressionResult.EncoderFailure ->
                                    throw IOException(getString(R.string.target_size_encode_failed), compressed.cause)
                            }
                            true
                        } else {
                            target.outputStream().use { output -> out.compress(shareFmt, shareQual, output) }
                        }
                        if (!encoded) {
                            false
                        } else {
                            val shareUri = FileProvider.getUriForFile(
                                this@CropActivity,
                                "${packageName}.fileprovider",
                                target,
                            )
                            if (metadataPolicy != ShareMetadataPolicy.STRIP_ALL) {
                                val source = sourceUri
                                if (source == null || !ExifTransfer.copyForShare(
                                        contentResolver,
                                        source,
                                        shareUri,
                                        metadataPolicy,
                                    )
                                ) {
                                    throw IOException("Could not apply selected metadata policy")
                                }
                            }
                            if (useTargetSize && target.length() !in 1L..targetBytes) {
                                throw IOException(getString(R.string.target_size_unmet))
                            }
                            true
                        }
                    },
                    dispatcher = { target ->
                        val shareUri = FileProvider.getUriForFile(
                            this@CropActivity,
                            "${packageName}.fileprovider",
                            target,
                        )
                        withContext(Dispatchers.Main) {
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                if (includeSourceLink) explicitSourceContext.value?.shareText?.let {
                                    putExtra(Intent.EXTRA_TEXT, it)
                                }
                                clipData = ClipData.newRawUri("SnapCrop shared image", shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, null))
                        }
                    },
                )
                val failure = result as? CropCacheArtifactPublisher.Result.Failure
                if (failure != null) {
                    val errorMessage = failure.cause?.message
                    withContext(Dispatchers.Main) {
                        val message = when (errorMessage) {
                            getString(R.string.target_size_unmet), getString(R.string.target_size_encode_failed) -> errorMessage
                            else -> getString(R.string.share_metadata_export_failed)
                        }
                        Toast.makeText(this@CropActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val message = when (e.message) {
                        getString(R.string.target_size_unmet), getString(R.string.target_size_encode_failed) -> e.message
                        else -> getString(R.string.share_metadata_export_failed)
                    }
                    Toast.makeText(
                        this@CropActivity,
                        message,
                        Toast.LENGTH_LONG,
                    ).show()
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

    private fun buildShareRedactionReviewPreview(
        source: Bitmap,
        selected: List<Pair<Int, Rect>>,
        style: RedactionStyle,
    ): Bitmap {
        if (selected.isEmpty()) return scaledPreview(source, 720)
        val redacted = ImageRedactor.redact(source, selected.map { it.second }, style)
        val canvas = Canvas(redacted)
        val radius = (redacted.width.coerceAtMost(redacted.height) * 0.018f).coerceIn(12f, 30f)
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.15f
            typeface = Typeface.DEFAULT_BOLD
        }
        selected.forEach { (index, rect) ->
            val x = (rect.left + radius).coerceIn(radius, redacted.width - radius)
            val y = (rect.top + radius).coerceIn(radius, redacted.height - radius)
            canvas.drawCircle(x, y, radius, badge)
            canvas.drawText(index.toString(), x, y - (label.ascent() + label.descent()) / 2f, label)
        }
        return scaledPreview(redacted, 720).also { redacted.recycle() }
    }

    private fun sensitiveCategoryLabel(category: SensitiveTextCategory): String = getString(
        when (category) {
            SensitiveTextCategory.EMAIL -> R.string.redaction_category_email
            SensitiveTextCategory.PHONE -> R.string.redaction_category_phone
            SensitiveTextCategory.PAYMENT_CARD -> R.string.redaction_category_payment_card
            SensitiveTextCategory.IPV4, SensitiveTextCategory.IPV6 -> R.string.redaction_category_ip
            SensitiveTextCategory.MAC_ADDRESS -> R.string.redaction_category_mac
            SensitiveTextCategory.IBAN -> R.string.redaction_category_iban
            SensitiveTextCategory.POSTAL_ADDRESS -> R.string.redaction_category_address
            SensitiveTextCategory.DEVELOPER_SECRET -> R.string.redaction_category_developer_secret
            SensitiveTextCategory.CUSTOM -> R.string.redaction_category_custom
        }
    )

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
    private fun applyExportDecorations(
        input: Bitmap,
        settings: ExportSettings = currentExportSettings()
    ): Bitmap {
        var out = input
        val bordered = ExportPresetRenderer.applyBorder(out, settings)
        if (bordered !== out) out.recycle()
        out = bordered
        val watermarked = ExportPresetRenderer.applyWatermark(out, settings)
        if (watermarked !== out) out.recycle()
        return watermarked
    }

    private fun resolveFilename(settings: ExportSettings = currentExportSettings()): String {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        return ExportPresetStore.nextFilename(prefs, settings)
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
        return saveTextSidecar(
            displayName = "$name.svg",
            mimeType = "image/svg+xml",
            relativePath = sidecarPath(savePath),
            collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            text = svg,
        )
    }

    private fun saveProjectSidecar(name: String, savePath: String, json: String): Boolean {
        return saveTextSidecar(
            displayName = "$name.snapcrop.json",
            mimeType = SnapCropProjectSidecar.MIME_TYPE,
            relativePath = sidecarPath(savePath),
            collectionUri = MediaStore.Files.getContentUri("external"),
            text = json,
        )
    }

    private fun saveOcrTextSidecar(name: String, savePath: String, text: String): Boolean {
        return saveTextSidecar(
            displayName = "$name.txt",
            mimeType = "text/plain",
            relativePath = sidecarPath(savePath),
            collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            text = text.take(ReviewedOcr.MAX_PLAIN_TEXT_CHARS),
        )
    }

    private fun saveTextSidecar(
        displayName: String,
        mimeType: String,
        relativePath: String,
        collectionUri: Uri,
        text: String,
    ): Boolean {
        val result = MediaStoreImageWriter.writeUtf8(
            resolver = contentResolver,
            request = MediaStoreImageWriter.Request(
                displayName = displayName,
                mimeType = mimeType,
                relativePath = relativePath,
                collectionUri = collectionUri,
            ),
            text = text,
        )
        return result is MediaStoreImageWriter.Result.Success
    }

    private suspend fun saveToGallery(
        bitmap: Bitmap,
        name: String,
        deleteOriginal: Boolean,
        forcePng: Boolean = false,
        annotationSvg: String? = null,
        projectSidecarJson: String? = null,
        correctedOcrText: String? = null,
        exportSettings: ExportSettings = currentExportSettings()
    ) {
        val journalStarted = OperationJournal.start()
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val stripExif = prefs.getBoolean("strip_exif", false)
        val exportFormat = getExportFormat(
            forcePng,
            ultraHdr = bitmap.hasUltraHdrGainmap(),
            settings = exportSettings
        )
        val format = exportFormat.format
        val quality = exportFormat.quality
        val ext = exportFormat.ext
        val mime = exportFormat.mime

        val savePath = exportSettings.savePath

        // Target file size compression (JPEG/WebP only, not PNG)
        val targetSizeEnabled = exportSettings.targetSizeEnabled
        val targetSizeKb = exportSettings.targetSizeKb
        val useTargetSize = targetSizeEnabled && !forcePng && format != Bitmap.CompressFormat.PNG
        val targetBytes = targetSizeKb.coerceIn(50, 5000).toLong() * 1024L

        var targetQuality: Int? = null
        var targetFinalSize: Long? = null
        var targetFailure: TargetCompressionResult? = null
        val publication = MediaStoreImageWriter.write(
            resolver = contentResolver,
            request = MediaStoreImageWriter.Request(
                displayName = "$name.$ext",
                mimeType = mime,
                relativePath = savePath,
            ),
            beforePublish = { uri ->
                sourceUri?.let { src -> ExifTransfer.copyExif(contentResolver, src, uri, stripExif) }
                if (useTargetSize) {
                    val measuredSize = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                    if (measuredSize !in 1L..targetBytes) throw IOException(getString(R.string.target_size_unmet))
                    targetFinalSize = measuredSize
                }
            },
        ) { output ->
            if (useTargetSize) {
                when (val result = ExportPresetRenderer.compressToTarget(
                    bitmap,
                    format,
                    targetSizeKb,
                    exportSettings.targetDownscalePolicy(),
                )) {
                    is TargetCompressionResult.WithinBudget -> {
                        targetQuality = result.quality
                        output.write(result.bytes)
                        true
                    }
                    else -> {
                        targetFailure = result
                        false
                    }
                }
            } else {
                bitmap.compress(format, quality, output)
            }
        }
        if (publication is MediaStoreImageWriter.Result.Failure) {
            OperationJournal.record(
                this, DiagnosticOperation.EXPORT, DiagnosticStage.SAVE, DiagnosticResult.FAILED,
                journalStarted, DiagnosticCode.PUBLISH_FAILURE, publication.cause,
            )
            val message = when (targetFailure) {
                is TargetCompressionResult.CannotMeetWithoutResize -> getString(R.string.target_size_unmet)
                is TargetCompressionResult.EncoderFailure -> getString(R.string.target_size_encode_failed)
                else -> when (publication.cause?.message) {
                    getString(R.string.target_size_unmet) -> getString(R.string.target_size_unmet)
                    else -> getString(R.string.toast_save_failed)
                }
            }
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            return
        }
        val success = publication as MediaStoreImageWriter.Result.Success
        val uri = success.uri

        try {
            val baseMessage = if (useTargetSize) {
                val sizeKb = checkNotNull(targetFinalSize) / 1024
                getString(R.string.crop_saved_size, "${sizeKb}KB", checkNotNull(targetQuality))
            } else {
                if (deleteOriginal) getString(R.string.crop_saved_path, savePath)
                    else getString(R.string.crop_copy_saved_path, savePath)
            }

            val savedDateAdded = if (explicitSourceContext.value != null || deleteOriginal) {
                mediaDateAdded(uri) ?: throw IOException("Could not read saved media identity")
            } else null
            explicitSourceContext.value?.let { contextValue ->
                ScreenshotIndexStore(this).putSourceContext(uri, checkNotNull(savedDateAdded), contextValue)
            }
            if (deleteOriginal) {
                pendingReplacementUri = uri
                pendingReplacementDateAdded = checkNotNull(savedDateAdded)
                pendingReplacedSourceDateAdded = sourceUri?.let(::mediaDateAdded) ?: -1L
            }

            val svgSaved = annotationSvg == null || saveSvgSidecar(name, savePath, annotationSvg)
            val projectSaved = projectSidecarJson == null || saveProjectSidecar(name, savePath, projectSidecarJson)
            val ocrTextSaved = correctedOcrText == null || saveOcrTextSidecar(name, savePath, correctedOcrText)
            val omittedSidecars = buildSet {
                if (annotationSvg != null && !svgSaved) add(DiagnosticSidecar.SVG)
                if (projectSidecarJson != null && !projectSaved) add(DiagnosticSidecar.PROJECT)
                if (correctedOcrText != null && !ocrTextSaved) add(DiagnosticSidecar.OCR_TEXT)
            }
            val requestedSidecarsSaved = omittedSidecars.isEmpty()
            val detailMessage = baseMessage +
                (if (annotationSvg != null && svgSaved) " + SVG" else "") +
                (if (projectSidecarJson != null && projectSaved) " + Project" else "") +
                (if (correctedOcrText != null && ocrTextSaved) " + OCR text" else "")

            OperationJournal.record(
                this,
                DiagnosticOperation.EXPORT,
                DiagnosticStage.COMPLETE,
                if (requestedSidecarsSaved) DiagnosticResult.SUCCESS else DiagnosticResult.PARTIAL,
                journalStarted,
                partial = omittedSidecars.takeIf(Set<DiagnosticSidecar>::isNotEmpty)
                    ?.let(DiagnosticPartial::Sidecars),
            )

            runOnUiThread {
                when {
                    deleteOriginal && requestedSidecarsSaved -> {
                        requestSourceMutation(SourceMutationPurpose.REPLACE_AFTER_SAVE, detailMessage)
                    }
                    deleteOriginal -> {
                        Toast.makeText(this, R.string.crop_sidecar_failed_original_retained, Toast.LENGTH_LONG).show()
                        discardDraftAndFinish()
                    }
                    else -> {
                        val message = if (requestedSidecarsSaved) detailMessage
                            else getString(R.string.crop_saved_sidecar_omitted)
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        discardDraftAndFinish()
                    }
                }
            }
        } catch (e: Exception) {
            pendingReplacementUri = null
            pendingReplacementDateAdded = -1L
            pendingReplacedSourceDateAdded = -1L
            OperationJournal.record(
                this, DiagnosticOperation.EXPORT, DiagnosticStage.SAVE, DiagnosticResult.FAILED,
                journalStarted, DiagnosticCode.INTERNAL, e
            )
            android.util.Log.e("SnapCrop", "saveToGallery failed", e)
            runOnUiThread { Toast.makeText(this, getString(R.string.toast_save_failed) + ": " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show() }
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            runCatching { ScreenshotIndexStore(this).deleteSourceContexts(listOf(uri)) }
        }
    }

    private fun requestSourceMutation(
        purpose: SourceMutationPurpose,
        exportMessage: String? = null
    ) {
        if (pendingMutationPurpose != null) return
        pendingMutationPurpose = purpose
        pendingMutationStartedAt = OperationJournal.start()
        pendingExportMessage = exportMessage
        isSaving.value = true
        val uri = sourceUri
        if (uri == null || !uri.scheme.equals("content", ignoreCase = true)) {
            completeSourceMutation(false)
            return
        }
        // Both paths send the original to the MediaStore trash. Direct resolver.update
        // moves are not authorized by MANAGE_MEDIA for media the app does not own (only
        // the request APIs are), so createTrashRequest is the reliable prompt-free route.
        requestSourceTrash()
    }

    private fun requestSourceTrash() {
        val uri = sourceUri ?: run {
            completeSourceMutation(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
                sourceTrashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
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
                    sourceTrashLauncher.launch(
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
        val mutationStartedAt = pendingMutationStartedAt
        val deletedSource = sourceUri
        val deletedSourceDate = pendingReplacedSourceDateAdded
        val replacementUri = pendingReplacementUri
        val replacementDate = pendingReplacementDateAdded
        pendingMutationPurpose = null
        pendingMutationStartedAt = null
        pendingExportMessage = null
        pendingReplacementUri = null
        pendingReplacementDateAdded = -1L
        pendingReplacedSourceDateAdded = -1L
        OperationJournal.enqueue(
            this,
            if (purpose == SourceMutationPurpose.REPLACE_AFTER_SAVE) {
                DiagnosticOperation.ARCHIVE
            } else {
                DiagnosticOperation.DELETE
            },
            DiagnosticStage.COMPLETE,
            if (succeeded) DiagnosticResult.SUCCESS else DiagnosticResult.FAILED,
            mutationStartedAt,
            if (succeeded) DiagnosticCode.NONE else DiagnosticCode.PERMISSION_DENIED
        )
        if (succeeded && deletedSource != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val store = ScreenshotIndexStore(this@CropActivity)
                    runCatching { store.deleteSourceContexts(listOf(deletedSource)) }
                    if (purpose == SourceMutationPurpose.REPLACE_AFTER_SAVE &&
                        replacementUri != null && replacementDate >= 0L && deletedSourceDate >= 0L
                    ) {
                        val previous = runCatching {
                            store.noteReminder(deletedSource, deletedSourceDate)
                        }.getOrNull()
                        val moved = previous?.let { note ->
                            runCatching {
                                store.putNoteReminder(
                                    replacementUri,
                                    replacementDate,
                                    note.note,
                                    note.reminderAt?.takeIf { it > System.currentTimeMillis() }
                                )
                            }.getOrNull()
                        }
                        // Delete the old metadata only after its durable replacement exists.
                        if (previous == null || moved != null) {
                            store.deleteNoteReminders(listOf(deletedSource)).forEach { note ->
                                ScreenshotReminderScheduler.cancel(this@CropActivity, note.uri, note.dateAdded)
                            }
                            moved?.takeIf { it.reminderAt != null }?.let { reminder ->
                                runCatching { ScreenshotReminderScheduler.schedule(this@CropActivity, reminder) }
                            }
                        }
                    } else {
                        runCatching {
                            store.deleteNoteReminders(listOf(deletedSource)).forEach { note ->
                                ScreenshotReminderScheduler.cancel(this@CropActivity, note.uri, note.dateAdded)
                            }
                        }
                    }
                }
                finishSourceMutationUi(purpose, succeeded = true, exportMessage)
            }
            return
        }
        finishSourceMutationUi(purpose, succeeded, exportMessage)
    }

    private fun finishSourceMutationUi(
        purpose: SourceMutationPurpose,
        succeeded: Boolean,
        exportMessage: String?
    ) {
        isSaving.value = false
        when (purpose) {
            SourceMutationPurpose.REPLACE_AFTER_SAVE -> {
                val message = if (succeeded) {
                    listOfNotNull(exportMessage, getString(R.string.crop_original_archived)).joinToString(". ")
                } else {
                    getString(R.string.crop_copy_saved_original_retained)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                discardDraftAndFinish()
            }
            SourceMutationPurpose.DELETE_FROM_EDITOR -> {
                Toast.makeText(
                    this,
                    if (succeeded) R.string.toast_moved_to_trash else R.string.toast_items_retained,
                    Toast.LENGTH_SHORT
                ).show()
                if (succeeded) discardDraftAndFinish()
            }
        }
    }

    override fun onDestroy() {
        val current = bitmapState.value
        if (current != null && current !== originalBitmap) current.recycle()
        originalBitmap?.recycle()
        originalBitmap = null; bitmapState.value = null
        super.onDestroy()
    }
}

@Composable
internal fun ProjectLoadErrorPanel(
    message: String,
    canRelink: Boolean,
    onRelink: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = SurfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.crop_project_unavailable),
                    color = OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (canRelink) {
                    TextButton(onClick = onRelink) {
                        Text(stringResource(R.string.crop_choose_source), color = Primary)
                    }
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close), color = Primary)
                }
            }
        }
    }
}
