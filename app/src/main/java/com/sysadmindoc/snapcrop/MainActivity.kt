package com.sysadmindoc.snapcrop

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.ComponentName
import android.content.ClipDescription
import android.content.Intent
import android.view.DragEvent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.snapcrop.BuildConfig
import com.sysadmindoc.snapcrop.ui.theme.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentCrop(val uri: Uri, val thumbBitmap: androidx.compose.ui.graphics.ImageBitmap)

class MainActivity : ComponentActivity() {
    private companion object {
        const val MAX_REPORT_ITEMS = 100
        const val MAX_REPORT_TITLE_CHARS = 160
        const val MAX_REPORT_NOTES_CHARS = 4_000
        const val MAX_OCR_APPENDIX_CHARS = 4_000
        const val MIN_REPORT_IMAGE_POINTS = 72f
        const val KEY_PENDING_MUTATION_URIS = "pending_mutation_uris"
        const val KEY_PENDING_MUTATION_SUCCEEDED = "pending_mutation_succeeded"
        const val KEY_PENDING_MUTATION_CHUNK = "pending_mutation_chunk"
        const val KEY_PENDING_MUTATION_REQUESTED = "pending_mutation_requested"
        const val KEY_GALLERY_OPEN_URI = "gallery_open_uri"
        const val KEY_GALLERY_OPEN_DATE = "gallery_open_date"
    }

    private val serviceRunning = mutableStateOf(false)
    private val mediaCapabilities = mutableStateOf(
        MediaCapabilities(MediaAccess.NONE, MediaAccess.NONE, notificationAccess = Build.VERSION.SDK_INT < 33)
    )
    private var pendingMonitorStart = false
    private var pendingDelayedCaptureSeconds: Int? = null
    private val hasOverlayPermission = mutableStateOf(false)
    private val longScreenshotReady = mutableStateOf(false)
    private val galleryRefreshKey = mutableIntStateOf(0)
    private val recentCrops = mutableStateOf<List<RecentCrop>>(emptyList())
    private val recentWorkflowIds = mutableStateOf<List<WorkflowId>>(emptyList())
    private val cropCount = mutableStateOf(0)
    private val pendingAccessibilityDisclosure = mutableStateOf<AccessibilityPurpose?>(null)
    private val galleryOpenRequest = mutableStateOf<GalleryOpenRequest?>(null)
    private var pendingMutationUris = mutableListOf<Uri>()
    private var pendingMutationSucceeded = mutableListOf<Uri>()
    private var pendingMutationChunk = emptyList<Uri>()
    private var pendingMutationRequested = 0
    private var pendingMutationStartedAt: Long? = null

    private val mediaMutationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (result.resultCode == RESULT_OK) {
                pendingMutationSucceeded.addAll(pendingMutationChunk)
                pendingMutationUris.removeAll(pendingMutationChunk.toSet())
                launchNextTrashChunk()
            } else {
                completeMediaMutation()
            }
        } else if (result.resultCode == RESULT_OK) {
            deleteLegacyMutationUris()
        } else {
            completeMediaMutation()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
        if (pendingMonitorStart && mediaCapabilities.value.canMonitorScreenshots) {
            pendingMonitorStart = false
            startMonitoring()
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit().putBoolean("auto_start", true).apply()
        } else {
            pendingMonitorStart = false
        }
        pendingDelayedCaptureSeconds?.let { seconds ->
            pendingDelayedCaptureSeconds = null
            if (mediaCapabilities.value.canMonitorScreenshots) startDelayedCapture(seconds)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            startActivity(Intent(this, CropActivity::class.java).apply { data = it })
            recordWorkflow(WorkflowId.EDIT_IMAGE)
        }
    }

    private val batchPickLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            batchAutocrop(uris)
            recordWorkflow(WorkflowId.BATCH_CROP)
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            startActivity(Intent(this, VideoClipActivity::class.java).apply {
                data = it
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            recordWorkflow(WorkflowId.VIDEO_CLIP)
        }
    }

    private fun recentWorkflowPrefs() =
        getSharedPreferences(RecentWorkflowStore.PREF_NAME, MODE_PRIVATE)

    private fun recordWorkflow(workflow: WorkflowId) {
        recentWorkflowIds.value = RecentWorkflowStore.record(recentWorkflowPrefs(), workflow)
    }

    private fun getSaveFormat(): Triple<android.graphics.Bitmap.CompressFormat, Int, String> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                          else android.graphics.Bitmap.CompressFormat.WEBP
                Triple(fmt, quality, "webp")
            }
            prefs.getBoolean("use_jpeg", false) -> Triple(android.graphics.Bitmap.CompressFormat.JPEG, quality, "jpg")
            else -> Triple(android.graphics.Bitmap.CompressFormat.PNG, 100, "png")
        }
    }

    private val batchProgress = mutableStateOf("")
    private val batchProgressFraction = mutableFloatStateOf(0f)
    private val batchCancelled = mutableStateOf(false)
    @Volatile private var activeNetworkCancellation: NetworkExportCancellation? = null
    @Volatile private var activeReportJob: Job? = null
    private val resizeUris = mutableStateOf<List<Uri>>(emptyList())
    private val showResizeDialogState = mutableStateOf(false)
    private val reportUris = mutableStateOf<List<Uri>>(emptyList())
    private val showReportDialogState = mutableStateOf(false)
    private val renameUris = mutableStateOf<List<Uri>>(emptyList())
    private val showRenameDialogState = mutableStateOf(false)
    private val inboundShareUris = mutableStateOf<List<Uri>>(emptyList())
    private val inboundShareFailures = mutableStateOf<List<String>>(emptyList())
    private val showInboundShareDialog = mutableStateOf(false)
    private val inboundSourceContext = mutableStateOf<ExplicitSourceContext?>(null)

    @Suppress("DEPRECATION")
    private fun handleSharedUrl(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return false
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = WebCapturePolicy.normalizeHttpsUrl(shared)
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.action = null
        if (url == null) {
            Toast.makeText(this, R.string.web_capture_invalid_url, Toast.LENGTH_LONG).show()
            return true
        }
        startActivity(Intent(this, WebCaptureActivity::class.java).putExtra(WebCaptureActivity.EXTRA_URL, url))
        return true
    }

    @Suppress("DEPRECATION")
    private fun handleInboundShares(intent: Intent) {
        val sharedContext = ExplicitSourceContext.fromIntent(intent, referrer)
        val forwarded = intent.getParcelableArrayListExtra<Uri>(InboundShareContract.EXTRA_URIS).orEmpty()
        val raw = if (forwarded.isNotEmpty()) forwarded else InboundShareContract.extractUris(intent)
        if (raw.size <= 1) return
        intent.removeExtra(InboundShareContract.EXTRA_URIS)
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.clipData = null
        intent.data = null
        intent.action = null
        lifecycleScope.launch(Dispatchers.IO) {
            val result = InboundShareContract.validateImages(contentResolver, raw)
            withContext(Dispatchers.Main) {
                inboundShareFailures.value = result.rejected.map {
                    getString(R.string.inbound_share_item_failure, it.itemIndex + 1, it.reason)
                }
                if (result.rejected.isNotEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.inbound_share_rejected,
                            result.rejected.size,
                            result.rejected.joinToString { it.reason }.take(160)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (result.accepted.size >= 2) {
                    inboundShareUris.value = result.accepted
                    inboundSourceContext.value = sharedContext
                    showInboundShareDialog.value = true
                } else if (result.accepted.size == 1) {
                    val uri = result.accepted.single()
                    val cropIntent = Intent(this@MainActivity, CropActivity::class.java).apply {
                        data = uri
                        clipData = ClipData.newRawUri("Shared image", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    sharedContext?.putInto(cropIntent)
                    startActivity(cropIntent)
                } else {
                    Toast.makeText(this@MainActivity, R.string.inbound_share_not_enough, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handlePrivacyShareIntent(intent: Intent): Boolean {
        val uris = intent.getStringArrayListExtra(EXTRA_PRIVACY_SHARE_URIS)
            ?.mapNotNull { value -> runCatching { Uri.parse(value) }.getOrNull() }
            .orEmpty()
        if (uris.isEmpty()) return false
        if (intent.getBooleanExtra(EXTRA_DISMISS_DETECTED_NOTIFICATION, false)) {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(ScreenshotService.DETECTED_NOTIF_ID)
        }
        intent.removeExtra(EXTRA_PRIVACY_SHARE_URIS)
        intent.removeExtra(EXTRA_DISMISS_DETECTED_NOTIFICATION)
        intent.clipData = null
        intent.data = null
        intent.action = null
        window.decorView.post {
            if (!isFinishing && !isDestroyed) shareImages(uris)
        }
        return true
    }

    private fun handleScreenshotReminderIntent(incoming: Intent?): Boolean {
        val request = ScreenshotReminderContract.parse(incoming) ?: return false
        galleryOpenRequest.value = request
        ScreenshotReminderContract.clear(incoming)
        return true
    }

    private fun launchInboundActivity(target: Class<out ComponentActivity>) {
        val uris = inboundShareUris.value
        val targetIntent = Intent(this, target).apply {
            putParcelableArrayListExtra(InboundShareContract.EXTRA_URIS, ArrayList(uris))
            uris.firstOrNull()?.let { first ->
                clipData = ClipData.newRawUri("Shared images", first).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        inboundSourceContext.value?.putInto(targetIntent)
        startActivity(targetIntent)
        showInboundShareDialog.value = false
        inboundSourceContext.value = null
    }

    private fun batchAutocrop(uris: List<Uri>) {
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            val statusBarPx = SystemBars.statusBarHeight(resources)
            val navBarPx = SystemBars.navigationBarHeight(resources)
            val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
            val userProfiles = UserAppProfileStore.load(prefs)
            var done = 0; var failed = 0
            val total = uris.size

            for (uri in uris) {
                if (batchCancelled.value) break
                withContext(Dispatchers.Main) {
                    batchProgress.value = getString(R.string.batch_cropping, done + 1, total)
                    batchProgressFraction.floatValue = done.toFloat() / total
                }
                try {
                    val input = contentResolver.openInputStream(uri) ?: throw IOException("Shared image is unreadable")
                    input.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                            ?: throw IOException("Shared image could not be decoded")
                        val cropRect = AutoCrop.detect(
                            bitmap = bitmap,
                            statusBarPx = statusBarPx,
                            navBarPx = navBarPx,
                            sourceHints = CropSourceHints.normalize(CropSourceHints.fromMedia(contentResolver, uri)),
                            userProfiles = userProfiles,
                            appProfilesEnabled = prefs.getBoolean("app_crop_profiles", true)
                        )
                        val isFullImage = cropRect.left == 0 && cropRect.top == 0 &&
                                cropRect.right == bitmap.width && cropRect.bottom == bitmap.height

                        val cw = cropRect.width().coerceAtMost(bitmap.width - cropRect.left.coerceAtLeast(0)).coerceAtLeast(1)
                        val ch = cropRect.height().coerceAtMost(bitmap.height - cropRect.top.coerceAtLeast(0)).coerceAtLeast(1)
                        val toSave = if (isFullImage) bitmap else {
                            android.graphics.Bitmap.createBitmap(bitmap,
                                cropRect.left.coerceAtLeast(0), cropRect.top.coerceAtLeast(0), cw, ch)
                        }

                        val (fmt, qual, ext) = getSaveFormat()
                        val mime = when (ext) { "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_${System.currentTimeMillis()}.$ext")
                            put(MediaStore.Images.Media.MIME_TYPE, mime)
                            put(MediaStore.Images.Media.RELATIVE_PATH, getSharedPreferences("snapcrop", MODE_PRIVATE).getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (savedUri != null) {
                            var ok = false
                            try {
                                contentResolver.openOutputStream(savedUri)?.use { out -> toSave.compress(fmt, qual, out) }
                                    ?: throw IOException("Output stream unavailable")
                                values.clear()
                                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(savedUri, values, null, null)
                                ok = true
                            } catch (_: Exception) {
                            }
                            if (!ok) {
                                try { contentResolver.delete(savedUri, null, null) } catch (_: Exception) {}
                                failed++
                            }
                        }
                        if (toSave !== bitmap) toSave.recycle()
                        bitmap.recycle()
                    }
                } catch (_: Exception) { failed++ }
                done++
            }
            val cancelled = batchCancelled.value
            withContext(Dispatchers.Main) {
                batchProgress.value = ""
                val msg = buildString {
                    append(getString(R.string.batch_cropped, done - failed, total))
                    if (failed > 0) append(getString(R.string.batch_failed_count, failed))
                    if (cancelled) append(getString(R.string.batch_stopped))
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                loadRecentCrops()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        pendingMutationUris = savedInstanceState?.getStringArrayList(KEY_PENDING_MUTATION_URIS)
            ?.map(Uri::parse)?.toMutableList() ?: mutableListOf()
        pendingMutationSucceeded = savedInstanceState?.getStringArrayList(KEY_PENDING_MUTATION_SUCCEEDED)
            ?.map(Uri::parse)?.toMutableList() ?: mutableListOf()
        pendingMutationChunk = savedInstanceState?.getStringArrayList(KEY_PENDING_MUTATION_CHUNK)
            ?.map(Uri::parse) ?: emptyList()
        pendingMutationRequested = savedInstanceState?.getInt(KEY_PENDING_MUTATION_REQUESTED) ?: 0
        galleryOpenRequest.value = savedInstanceState?.getString(KEY_GALLERY_OPEN_URI)?.let { rawUri ->
            val dateAdded = savedInstanceState.getLong(KEY_GALLERY_OPEN_DATE, -1L)
            runCatching { Uri.parse(rawUri) }.getOrNull()?.takeIf { dateAdded >= 0L }
                ?.let { GalleryOpenRequest(it, dateAdded) }
        }
        handleScreenshotReminderIntent(intent)
        handleAccessibilityIntent(intent)
        if (!handlePrivacyShareIntent(intent) && !handleSharedUrl(intent)) handleInboundShares(intent)

        window.decorView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_INTENT) == true ||
                    event.clipDescription?.hasMimeType("image/*") == true
                DragEvent.ACTION_DROP -> {
                    val clip = event.clipData ?: return@setOnDragListener false
                    requestDragAndDropPermissions(event)
                    handleInboundShares(Intent(Intent.ACTION_SEND_MULTIPLE).apply { clipData = clip })
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION,
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> true
                else -> false
            }
        }

        checkPermissions()

        setContent {
            SnapCropTheme {
                if (showInboundShareDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showInboundShareDialog.value = false },
                        title = { Text(stringResource(R.string.inbound_share_title), color = OnSurface) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.inbound_share_count, inboundShareUris.value.size),
                                    color = OnSurfaceVariant
                                )
                                if (inboundShareFailures.value.isNotEmpty()) {
                                    Text(
                                        inboundShareFailures.value.joinToString("\n"),
                                        color = Tertiary,
                                        fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = {
                                    val uris = inboundShareUris.value
                                    showInboundShareDialog.value = false
                                    batchAutocrop(uris)
                                }) { Text(stringResource(R.string.inbound_share_batch_crop)) }
                                TextButton(onClick = { launchInboundActivity(StitchActivity::class.java) }) {
                                    Text(stringResource(R.string.inbound_share_stitch))
                                }
                                TextButton(
                                    enabled = inboundShareUris.value.size <= CollageActivity.MAX_INBOUND_ITEMS,
                                    onClick = { launchInboundActivity(CollageActivity::class.java) }
                                ) {
                                    Text(stringResource(R.string.inbound_share_collage))
                                }
                                if (inboundShareUris.value.size > CollageActivity.MAX_INBOUND_ITEMS) {
                                    Text(
                                        stringResource(R.string.inbound_share_collage_limit),
                                        color = OnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = {
                                    showInboundShareDialog.value = false
                                    showReportDialog(inboundShareUris.value)
                                }) { Text(stringResource(R.string.inbound_share_report)) }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showInboundShareDialog.value = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                        containerColor = SurfaceVariant
                    )
                }
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val tabStateHolder = rememberSaveableStateHolder()
                var showHelp by remember { mutableStateOf(false) }
                val prefs = remember { getSharedPreferences("snapcrop", MODE_PRIVATE) }
                val credentialStore = remember { NetworkCredentialStore.open(this@MainActivity) }

                LaunchedEffect(galleryOpenRequest.value) {
                    if (galleryOpenRequest.value != null) selectedTab = 1
                }

                if (showHelp) {
                    LocalHelpDialog(
                        onOpenRoute = { route ->
                            when (route) {
                                HelpRoute.GALLERY_FILTERS, HelpRoute.GALLERY_COLLECTIONS -> selectedTab = 1
                                HelpRoute.SETTINGS_PROJECTS -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                HelpRoute.EDITOR_SAVE_ACTIONS, HelpRoute.EDITOR_REDACTION, HelpRoute.SHARE_PRIVACY -> pickImageLauncher.launch("image/*")
                                else -> selectedTab = 0
                            }
                        },
                        onDismiss = { showHelp = false }
                    )
                }

                // Opt-in, once-per-launch anonymous update check (sideload has no other update path).
                var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                LaunchedEffect(Unit) {
                    if (prefs.getBoolean(UpdateChecker.PREF_AUTO, false)) {
                        updateInfo = (UpdateChecker.check(BuildConfig.VERSION_NAME) as? UpdateChecker.Result.Available)?.info
                    }
                }
                updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        confirmButton = {
                            TextButton(onClick = {
                                updateInfo = null
                                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) } catch (_: Exception) {}
                            }) { Text(stringResource(R.string.settings_update_download), color = Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) { Text(stringResource(R.string.close), color = OnSurfaceVariant) }
                        },
                        title = { Text(stringResource(R.string.settings_update_available, info.versionName), color = OnSurface) },
                        text = {
                            Column {
                                Text(stringResource(R.string.settings_update_body, info.versionName), color = OnSurfaceVariant, fontSize = 13.sp)
                                info.apkUrl?.let {
                                    Text(stringResource(R.string.settings_update_exact_asset), color = Primary, fontSize = 12.sp)
                                }
                                info.apkSha256?.let { digest ->
                                    Text(stringResource(R.string.settings_update_checksum, digest), color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        },
                        containerColor = SurfaceVariant
                    )
                }

                Scaffold(
                    containerColor = Black,
                    bottomBar = {
                        NavigationBar(containerColor = SurfaceVariant) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, stringResource(R.string.nav_home)) },
                                label = { Text(stringResource(R.string.nav_home)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Primary,
                                    selectedTextColor = Primary,
                                    unselectedIconColor = OnSurfaceVariant,
                                    unselectedTextColor = OnSurfaceVariant,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Photo, stringResource(R.string.nav_gallery)) },
                                label = { Text(stringResource(R.string.nav_gallery)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Primary,
                                    selectedTextColor = Primary,
                                    unselectedIconColor = OnSurfaceVariant,
                                    unselectedTextColor = OnSurfaceVariant,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        tabStateHolder.SaveableStateProvider(selectedTab) {
                            when (selectedTab) {
                            0 -> HomeScreen(
                                isRunning = serviceRunning.value,
                                mediaCapabilities = mediaCapabilities.value,
                                recentCrops = recentCrops.value,
                                cropCount = cropCount.value,
                                recentWorkflows = recentWorkflowIds.value,
                                onToggleService = { toggleService() },
                                onRequestImageAccess = { requestPermissions(MediaCapabilityResolver.imageRequest(Build.VERSION.SDK_INT)) },
                                onRequestVideoAccess = { requestPermissions(MediaCapabilityResolver.videoRequest(Build.VERSION.SDK_INT)) },
                                onRequestNotificationAccess = { requestPermissions(MediaCapabilityResolver.notificationRequest(Build.VERSION.SDK_INT)) },
                                onPickImage = { pickImageLauncher.launch("image/*") },
                                onWebCapture = {
                                    startActivity(Intent(this@MainActivity, WebCaptureActivity::class.java))
                                    recordWorkflow(WorkflowId.WEB_CAPTURE)
                                },
                                onBatchCrop = { batchPickLauncher.launch(arrayOf("image/*")) },
                                onStitch = {
                                    startActivity(Intent(this@MainActivity, StitchActivity::class.java))
                                    recordWorkflow(WorkflowId.STITCH)
                                },
                                onCollage = {
                                    startActivity(Intent(this@MainActivity, CollageActivity::class.java))
                                    recordWorkflow(WorkflowId.COLLAGE)
                                },
                                onDeviceFrame = {
                                    startActivity(Intent(this@MainActivity, DeviceFrameActivity::class.java))
                                    recordWorkflow(WorkflowId.DEVICE_FRAME)
                                },
                                onVideoClip = { pickVideoLauncher.launch("video/*") },
                                longScreenshotReady = longScreenshotReady.value,
                                onLongScreenshot = { requestLongScreenshot() },
                                onDelayedCapture = { seconds ->
                                    if (mediaCapabilities.value.canMonitorScreenshots) {
                                        startDelayedCapture(seconds)
                                    } else {
                                        pendingDelayedCaptureSeconds = seconds
                                        requestPermissions(MediaCapabilityResolver.imageRequest(Build.VERSION.SDK_INT))
                                    }
                                },
                                batchProgress = batchProgress.value,
                                batchFraction = batchProgressFraction.floatValue,
                                onBatchCancel = {
                                    batchCancelled.value = true
                                    activeNetworkCancellation?.cancel()
                                    activeReportJob?.cancel()
                                },
                                hasOverlayPermission = hasOverlayPermission.value,
                                onRequestOverlay = {
                                    startActivity(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    ))
                                },
                                onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                onOpenHelp = { showHelp = true },
                                onOpenCrop = { uri ->
                                    startActivity(Intent(this@MainActivity, CropActivity::class.java).apply { data = uri })
                                },
                                onCopyCrop = { uri -> copyImageToClipboard(uri) },
                                onDeleteCrop = { uri -> requestDeleteUris(listOf(uri)) }
                            )
                            1 -> GalleryScreen(
                                refreshKey = galleryRefreshKey.intValue,
                                imageAccess = mediaCapabilities.value.imageAccess,
                                videoAccess = mediaCapabilities.value.videoAccess,
                                onRequestImageAccess = { requestPermissions(MediaCapabilityResolver.imageRequest(Build.VERSION.SDK_INT)) },
                                onRequestVideoAccess = { requestPermissions(MediaCapabilityResolver.videoRequest(Build.VERSION.SDK_INT)) },
                                notificationAccess = mediaCapabilities.value.notificationAccess,
                                onRequestNotificationAccess = { requestReminderNotificationAccess() },
                                openRequest = galleryOpenRequest.value,
                                onOpenRequestConsumed = { galleryOpenRequest.value = null },
                                onOpenEditor = { uri ->
                                    startActivity(Intent(this@MainActivity, CropActivity::class.java).apply { data = uri })
                                },
                                onPlayVideo = { uri ->
                                    startActivity(Intent(this@MainActivity, VideoClipActivity::class.java).apply {
                                        data = uri
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                },
                                onShareUris = { uris -> shareImages(uris) },
                                onDeleteUris = { uris -> requestDeleteUris(uris) },
                                onExportPdf = { uris -> showReportDialog(uris) },
                                onBatchResize = { uris -> showResizeDialog(uris) },
                                onBatchRename = { uris -> showRenameDialog(uris) },
                                onBack = { selectedTab = 0 }
                            )
                            }
                        }
                    }
                }

                pendingAccessibilityDisclosure.value?.let { purpose ->
                    AlertDialog(
                        onDismissRequest = { pendingAccessibilityDisclosure.value = null },
                        title = {
                            Text(
                                stringResource(
                                    if (purpose == AccessibilityPurpose.LONG_SCREENSHOT) {
                                        R.string.accessibility_long_title
                                    } else {
                                        R.string.accessibility_step_title
                                    }
                                ),
                                color = OnSurface
                            )
                        },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    stringResource(
                                        if (purpose == AccessibilityPurpose.LONG_SCREENSHOT) {
                                            R.string.accessibility_long_body
                                        } else {
                                            R.string.accessibility_step_body
                                        }
                                    ),
                                    color = OnSurfaceVariant,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    AccessibilityDisclosure.recordConsent(this@MainActivity, purpose)
                                    pendingAccessibilityDisclosure.value = null
                                    if (isAccessibilityPurposeReady(purpose)) {
                                        executeAccessibilityPurpose(purpose)
                                    } else {
                                        openAccessibilitySettings(purpose)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                            ) {
                                Text(stringResource(R.string.accessibility_agree_continue))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { pendingAccessibilityDisclosure.value = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant)
                            ) {
                                Text(stringResource(R.string.accessibility_not_now))
                            }
                        },
                        containerColor = Surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Resize dialog
                if (showResizeDialogState.value) {
                    var selectedSize by remember { mutableIntStateOf(1080) }
                    val sizes = listOf(480, 720, 1080, 1440, 2160)
                    AlertDialog(
                        onDismissRequest = { showResizeDialogState.value = false },
                        title = { Text(stringResource(R.string.resize_dialog_title), color = OnSurface) },
                        text = {
                            Column {
                                Text(stringResource(R.string.resize_max_dimension), color = OnSurfaceVariant, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sizes.forEach { size ->
                                        val selectedCd = stringResource(R.string.resize_target_selected_cd, size)
                                        val unselectedCd = stringResource(R.string.resize_target_cd, size)
                                        FilterChip(
                                            selected = selectedSize == size,
                                            onClick = { selectedSize = size },
                                            label = { Text("$size", fontSize = 12.sp) },
                                            modifier = Modifier.semantics {
                                                contentDescription = if (selectedSize == size) selectedCd else unselectedCd
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.resize_count, resizeUris.value.size), color = OnSurfaceVariant, fontSize = 12.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showResizeDialogState.value = false
                                batchResize(resizeUris.value, selectedSize)
                            }) { Text(stringResource(R.string.resize), color = Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResizeDialogState.value = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
                        },
                        containerColor = SurfaceVariant
                    )
                }

                if (showReportDialogState.value) {
                    val networkSettings = NetworkExportSettings.fromPrefs(prefs, credentialStore)
                    val reportDefaultTitle = stringResource(R.string.report_default_title)
                    var reportTitle by remember(reportUris.value) { mutableStateOf(reportDefaultTitle) }
                    var notes by remember(reportUris.value) { mutableStateOf("") }
                    var includeOcr by remember(reportUris.value) { mutableStateOf(false) }
                    var reviewedOcr by remember(reportUris.value) { mutableStateOf<Map<String, List<TextBlock>>?>(null) }
                    var showOcrReview by remember(reportUris.value) { mutableStateOf(false) }
                    var preparingOcr by remember(reportUris.value) { mutableStateOf(false) }
                    var uploadAfterSave by remember(reportUris.value, networkSettings) { mutableStateOf(false) }
                    var pagePreset by remember(reportUris.value) { mutableStateOf(ReportPagePreset.A4) }
                    var pageOrientation by remember(reportUris.value) { mutableStateOf(ReportPageOrientation.PORTRAIT) }
                    var customWidthMm by remember(reportUris.value) { mutableStateOf("210") }
                    var customHeightMm by remember(reportUris.value) { mutableStateOf("297") }
                    var pageMarginMm by remember(reportUris.value) { mutableStateOf("15") }
                    val pageSettings = ReportPageSettings(
                        preset = pagePreset,
                        orientation = pageOrientation,
                        customWidthMm = customWidthMm.toFloatOrNull() ?: Float.NaN,
                        customHeightMm = customHeightMm.toFloatOrNull() ?: Float.NaN,
                        marginMm = pageMarginMm.toFloatOrNull() ?: Float.NaN
                    )
                    val reportLayout = pageSettings.layoutOrNull()
                    AlertDialog(
                        onDismissRequest = { showReportDialogState.value = false },
                        title = { Text(stringResource(R.string.report_dialog_title), color = OnSurface) },
                        text = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    stringResource(R.string.report_count, reportUris.value.size),
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = reportTitle,
                                    onValueChange = { if (it.length <= MAX_REPORT_TITLE_CHARS) reportTitle = it },
                                    label = { Text(stringResource(R.string.report_title_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary,
                                        unfocusedBorderColor = Outline,
                                        focusedTextColor = OnSurface,
                                        unfocusedTextColor = OnSurface,
                                        cursorColor = Primary
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { if (it.length <= MAX_REPORT_NOTES_CHARS) notes = it },
                                    label = { Text(stringResource(R.string.report_notes_label)) },
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary,
                                        unfocusedBorderColor = Outline,
                                        focusedTextColor = OnSurface,
                                        unfocusedTextColor = OnSurface,
                                        cursorColor = Primary
                                    )
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.report_page_layout), color = OnSurface, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReportPagePreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = pagePreset == preset,
                                            onClick = { pagePreset = preset },
                                            label = {
                                                Text(stringResource(when (preset) {
                                                    ReportPagePreset.A4 -> R.string.report_page_a4
                                                    ReportPagePreset.LETTER -> R.string.report_page_letter
                                                    ReportPagePreset.CUSTOM -> R.string.report_page_custom
                                                }))
                                            }
                                        )
                                    }
                                }
                                if (pagePreset == ReportPagePreset.CUSTOM) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = customWidthMm,
                                            onValueChange = { customWidthMm = sanitizeDecimalInput(it) },
                                            label = { Text(stringResource(R.string.report_page_width_mm)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = customHeightMm,
                                            onValueChange = { customHeightMm = sanitizeDecimalInput(it) },
                                            label = { Text(stringResource(R.string.report_page_height_mm)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReportPageOrientation.entries.forEach { orientation ->
                                        FilterChip(
                                            selected = pageOrientation == orientation,
                                            onClick = { pageOrientation = orientation },
                                            label = {
                                                Text(stringResource(
                                                    if (orientation == ReportPageOrientation.PORTRAIT) R.string.report_page_portrait
                                                    else R.string.report_page_landscape
                                                ))
                                            }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = pageMarginMm,
                                    onValueChange = { pageMarginMm = sanitizeDecimalInput(it) },
                                    label = { Text(stringResource(R.string.report_page_margin_mm)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (reportLayout == null) {
                                    Text(stringResource(R.string.report_page_invalid), color = Tertiary, fontSize = 12.sp)
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(112.dp)
                                            .background(SurfaceContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier.height(96.dp)
                                                .aspectRatio(reportLayout.widthPoints.toFloat() / reportLayout.heightPoints)
                                                .background(Color.White)
                                                .border(1.dp, Outline, RoundedCornerShape(2.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                                val horizontalInset = maxWidth * (reportLayout.marginMm / reportLayout.widthMm)
                                                val verticalInset = maxHeight * (reportLayout.marginMm / reportLayout.heightMm)
                                                Box(
                                                    modifier = Modifier.fillMaxSize()
                                                        .padding(horizontal = horizontalInset, vertical = verticalInset)
                                                        .border(1.dp, Color.LightGray),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        stringResource(
                                                            R.string.report_page_preview,
                                                            formatMillimeters(reportLayout.widthMm),
                                                            formatMillimeters(reportLayout.heightMm),
                                                            formatMillimeters(reportLayout.marginMm)
                                                        ),
                                                        color = Color.DarkGray,
                                                        fontSize = 10.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(stringResource(R.string.report_pdfa_unsupported), color = OnSurfaceVariant, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = includeOcr,
                                        onCheckedChange = { includeOcr = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Primary,
                                            uncheckedColor = OnSurfaceVariant
                                        )
                                    )
                                    Text(
                                        stringResource(R.string.report_include_ocr),
                                        color = OnSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                                if (includeOcr) {
                                    TextButton(
                                        onClick = {
                                            if (preparingOcr) return@TextButton
                                            preparingOcr = true
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val prepared = linkedMapOf<String, List<TextBlock>>()
                                                reportUris.value.forEach { uri ->
                                                    val reportBitmap = decodeReportBitmap(uri)
                                                    prepared[uri.toString()] = if (reportBitmap == null) {
                                                        emptyList()
                                                    } else {
                                                        try {
                                                            TextExtractor.extract(reportBitmap, OcrScript.fromContext(this@MainActivity))
                                                                .map(TextBlock::deepCopy)
                                                        } catch (_: Exception) {
                                                            emptyList()
                                                        } finally {
                                                            reportBitmap.recycle()
                                                        }
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    reviewedOcr = prepared
                                                    preparingOcr = false
                                                    showOcrReview = true
                                                }
                                            }
                                        },
                                        enabled = !preparingOcr
                                    ) {
                                        if (preparingOcr) {
                                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            stringResource(
                                                if (reviewedOcr == null) R.string.report_scan_review_ocr
                                                else R.string.report_review_ocr_again
                                            ),
                                            color = Primary
                                        )
                                    }
                                    Text(
                                        stringResource(
                                            if (reviewedOcr == null) R.string.report_ocr_review_required
                                            else R.string.report_ocr_review_ready
                                        ),
                                        color = if (reviewedOcr == null) OnSurfaceVariant else Secondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                if (networkSettings.enabled) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = uploadAfterSave,
                                            onCheckedChange = { uploadAfterSave = it && networkSettings.isConfigured },
                                            enabled = networkSettings.isConfigured,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Primary,
                                                uncheckedColor = OnSurfaceVariant,
                                                disabledUncheckedColor = Outline
                                            )
                                        )
                                        Column {
                                            Text(
                                                stringResource(R.string.report_upload_after),
                                                color = if (networkSettings.isConfigured) OnSurfaceVariant else Outline,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                if (networkSettings.isConfigured) networkSettings.destinationLabel
                                                else stringResource(R.string.report_configure_target),
                                                color = OnSurfaceVariant,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.report_network_off),
                                        color = OnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showReportDialogState.value = false
                                    exportPdfReport(
                                        reportUris.value,
                                        reportTitle,
                                        notes,
                                        includeOcr,
                                        uploadAfterSave,
                                        reviewedOcr,
                                        checkNotNull(reportLayout)
                                    )
                                },
                                enabled = reportLayout != null && (!includeOcr || reviewedOcr != null),
                                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                            ) { Text(stringResource(R.string.create)) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showReportDialogState.value = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant)
                            ) { Text(stringResource(R.string.cancel)) }
                        },
                        containerColor = SurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showOcrReview && reviewedOcr != null) {
                        AlertDialog(
                            onDismissRequest = { showOcrReview = false },
                            title = { Text(stringResource(R.string.report_review_ocr_title), color = OnSurface) },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 440.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.report_review_ocr_help),
                                        color = OnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    reportUris.value.forEachIndexed { imageIndex, uri ->
                                        val key = uri.toString()
                                        val blocks = reviewedOcr.orEmpty()[key].orEmpty()
                                        Text(
                                            stringResource(R.string.report_ocr_image, imageIndex + 1),
                                            color = Primary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (blocks.isEmpty()) {
                                            Text(stringResource(R.string.ocr_no_text), color = OnSurfaceVariant, fontSize = 12.sp)
                                        }
                                        blocks.forEachIndexed { blockIndex, block ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = block.text,
                                                    onValueChange = { value ->
                                                        val updatedBlocks = blocks.toMutableList().apply {
                                                            this[blockIndex] = block.copy(text = value)
                                                        }
                                                        reviewedOcr = reviewedOcr.orEmpty().toMutableMap().apply {
                                                            this[key] = updatedBlocks
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    minLines = 1,
                                                    maxLines = 3,
                                                    label = { Text(stringResource(R.string.ocr_block_number, blockIndex + 1)) },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = OcrAccent,
                                                        unfocusedBorderColor = Outline,
                                                        focusedTextColor = OnSurface,
                                                        unfocusedTextColor = OnSurface,
                                                        cursorColor = OcrAccent
                                                    )
                                                )
                                                IconButton(onClick = {
                                                    reviewedOcr = reviewedOcr.orEmpty().toMutableMap().apply {
                                                        this[key] = blocks.filterIndexed { index, _ -> index != blockIndex }
                                                    }
                                                }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        stringResource(R.string.ocr_delete_block, blockIndex + 1),
                                                        tint = Tertiary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        reviewedOcr = reviewedOcr.orEmpty().mapValues { (_, blocks) ->
                                            ReviewedOcr.sanitize(blocks)
                                        }
                                        showOcrReview = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OcrAccent)
                                ) { Text(stringResource(R.string.done), color = OnPrimary) }
                            },
                            containerColor = SurfaceVariant
                        )
                    }
                }

                if (showRenameDialogState.value) {
                    var template by remember(renameUris.value) {
                        mutableStateOf(prefs.getString("batch_rename_template", "%app%_%date%_%counter%") ?: "%app%_%date%_%counter%")
                    }
                    var profileName by remember(renameUris.value) {
                        mutableStateOf(prefs.getString("batch_rename_profile", "SnapCrop") ?: "SnapCrop")
                    }
                    AlertDialog(
                        onDismissRequest = { showRenameDialogState.value = false },
                        title = { Text(stringResource(R.string.rename_dialog_title), color = OnSurface) },
                        text = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    stringResource(R.string.rename_count, renameUris.value.size),
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = template,
                                    onValueChange = { template = it },
                                    label = { Text(stringResource(R.string.rename_template_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary,
                                        unfocusedBorderColor = Outline,
                                        focusedTextColor = OnSurface,
                                        unfocusedTextColor = OnSurface,
                                        cursorColor = Primary
                                    )
                                )
                                Text(
                                    stringResource(R.string.rename_tokens),
                                    color = OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = profileName,
                                    onValueChange = { profileName = it },
                                    label = { Text(stringResource(R.string.rename_profile_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary,
                                        unfocusedBorderColor = Outline,
                                        focusedTextColor = OnSurface,
                                        unfocusedTextColor = OnSurface,
                                        cursorColor = Primary
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    prefs.edit()
                                        .putString("batch_rename_template", template)
                                        .putString("batch_rename_profile", profileName)
                                        .apply()
                                    showRenameDialogState.value = false
                                    batchRename(renameUris.value, template, profileName)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                            ) { Text(stringResource(R.string.rename)) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showRenameDialogState.value = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant)
                            ) { Text(stringResource(R.string.cancel)) }
                        },
                        containerColor = SurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
        checkPermissions()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        refreshLongScreenshotState()
        recentWorkflowIds.value = RecentWorkflowStore.load(recentWorkflowPrefs())

        val shouldRun = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getBoolean("auto_start", false)
        if (shouldRun && mediaCapabilities.value.canMonitorScreenshots && !ScreenshotService.isRunning) {
            startMonitoring()
        }
        serviceRunning.value = ScreenshotService.isRunning

        if (mediaCapabilities.value.canQueryImages) loadRecentCrops()
        galleryRefreshKey.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScreenshotReminderIntent(intent)
        handleAccessibilityIntent(intent)
        if (!handlePrivacyShareIntent(intent) && !handleSharedUrl(intent)) handleInboundShares(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(KEY_PENDING_MUTATION_URIS, ArrayList(pendingMutationUris.map(Uri::toString)))
        outState.putStringArrayList(KEY_PENDING_MUTATION_SUCCEEDED, ArrayList(pendingMutationSucceeded.map(Uri::toString)))
        outState.putStringArrayList(KEY_PENDING_MUTATION_CHUNK, ArrayList(pendingMutationChunk.map(Uri::toString)))
        outState.putInt(KEY_PENDING_MUTATION_REQUESTED, pendingMutationRequested)
        galleryOpenRequest.value?.let { request ->
            outState.putString(KEY_GALLERY_OPEN_URI, request.uri.toString())
            outState.putLong(KEY_GALLERY_OPEN_DATE, request.dateAdded)
        }
        super.onSaveInstanceState(outState)
    }

    private fun checkPermissions() {
        mediaCapabilities.value = MediaCapabilityResolver.current(this)
    }

    private fun requestPermissions(permissions: Array<String>, startMonitorAfter: Boolean = false) {
        if (permissions.isEmpty()) return
        pendingMonitorStart = startMonitorAfter
        permissionLauncher.launch(permissions)
    }

    private fun requestReminderNotificationAccess() {
        val request = MediaCapabilityResolver.notificationRequest(Build.VERSION.SDK_INT)
        if (!mediaCapabilities.value.notificationAccess && request.isNotEmpty()) {
            requestPermissions(request)
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }.onFailure {
            Toast.makeText(this, R.string.gallery_note_notifications_required, Toast.LENGTH_LONG).show()
        }
    }

    private fun showResizeDialog(uris: List<Uri>) {
        resizeUris.value = uris
        showResizeDialogState.value = true
    }

    private fun showReportDialog(uris: List<Uri>) {
        reportUris.value = uris
        showReportDialogState.value = true
    }

    private fun sanitizeDecimalInput(value: String): String {
        var decimalSeen = false
        return buildString {
            value.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    character == '.' && !decimalSeen -> {
                        append(character)
                        decimalSeen = true
                    }
                }
            }
        }.take(7)
    }

    private fun formatMillimeters(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private fun showRenameDialog(uris: List<Uri>) {
        renameUris.value = uris
        showRenameDialogState.value = true
    }

    private fun batchResize(uris: List<Uri>, maxDim: Int) {
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            var done = 0; var failed = 0
            for (uri in uris) {
                if (batchCancelled.value) break
                withContext(Dispatchers.Main) {
                    batchProgress.value = getString(R.string.batch_resizing, done + 1, uris.size)
                    batchProgressFraction.floatValue = done.toFloat() / uris.size
                }
                try {
                    val input = contentResolver.openInputStream(uri) ?: throw IOException("Shared image is unreadable")
                    input.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                            ?: throw IOException("Shared image could not be decoded")
                        if (bmp.width <= maxDim && bmp.height <= maxDim) { bmp.recycle(); return@use }
                        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                        val newW = (bmp.width * scale).toInt()
                        val newH = (bmp.height * scale).toInt()
                        val resized = android.graphics.Bitmap.createScaledBitmap(bmp, newW, newH, true)
                        bmp.recycle()
                        val (fmt, qual, ext) = getSaveFormat()
                        val mime = when (ext) { "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Resize_${System.currentTimeMillis()}.$ext")
                            put(MediaStore.Images.Media.MIME_TYPE, mime)
                            put(MediaStore.Images.Media.RELATIVE_PATH, getSharedPreferences("snapcrop", MODE_PRIVATE).getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (savedUri != null) {
                            var ok = false
                            try {
                                contentResolver.openOutputStream(savedUri)?.use { out -> resized.compress(fmt, qual, out) }
                                    ?: throw IOException("Output stream unavailable")
                                values.clear()
                                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(savedUri, values, null, null)
                                ok = true
                            } catch (_: Exception) {
                            }
                            if (!ok) {
                                try { contentResolver.delete(savedUri, null, null) } catch (_: Exception) {}
                                failed++
                            }
                        }
                        resized.recycle()
                    }
                } catch (_: Exception) { failed++ }
                done++
            }
            val cancelled = batchCancelled.value
            withContext(Dispatchers.Main) {
                batchProgress.value = ""
                val msg = buildString {
                    append(getString(R.string.batch_resized, done - failed, uris.size, maxDim))
                    if (failed > 0) append(getString(R.string.batch_failed_count, failed))
                    if (cancelled) append(getString(R.string.batch_stopped))
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                galleryRefreshKey.intValue++
            }
        }
    }

    private fun batchRename(uris: List<Uri>, template: String, profileName: String) {
        if (uris.isEmpty()) return
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val usedNames = mutableSetOf<String>()
            var renamed = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                if (batchCancelled.value) return@forEachIndexed
                withContext(Dispatchers.Main) {
                    batchProgress.value = getString(R.string.batch_renaming, index + 1, uris.size)
                    batchProgressFraction.floatValue = index.toFloat() / uris.size
                }
                val metadata = loadExportItemMetadata(uri)
                val extension = metadata.displayName.substringAfterLast('.', "")
                    .ifBlank { extensionForMime(contentResolver.getType(uri)) }
                val base = BatchRenameTemplate.resolve(template, metadata, index + 1, now, profileName)
                val displayName = uniqueDisplayName(base, extension, usedNames)
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    }
                    if (contentResolver.update(uri, values, null, null) > 0) renamed++ else failed++
                } catch (_: Exception) {
                    failed++
                }
            }
            withContext(Dispatchers.Main) {
                batchProgress.value = ""
                val msg = buildString {
                    append(getString(R.string.batch_renamed, renamed, uris.size))
                    if (failed > 0) append(getString(R.string.batch_rename_failed_count, failed))
                    if (batchCancelled.value) append(getString(R.string.batch_stopped))
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                galleryRefreshKey.intValue++
            }
        }
    }

    private fun exportPdfReport(
        uris: List<Uri>,
        title: String,
        notes: String,
        includeOcr: Boolean,
        uploadAfterSave: Boolean,
        reviewedOcr: Map<String, List<TextBlock>>? = null,
        layout: PdfReportLayout
    ) {
        if (uris.isEmpty()) return
        if (uris.size > MAX_REPORT_ITEMS) {
            Toast.makeText(this, getString(R.string.report_too_many_items, MAX_REPORT_ITEMS), Toast.LENGTH_LONG).show()
            return
        }
        batchCancelled.value = false
        activeReportJob = lifecycleScope.launch(Dispatchers.IO) {
            val doc = PdfDocument()
            var temporaryPdf: java.io.File? = null
            val createdAt = System.currentTimeMillis()
            val renderableUris = uris.filter(::hasReportImageBounds)
            if (renderableUris.isEmpty()) {
                withContext(Dispatchers.Main) {
                    batchProgress.value = ""
                    Toast.makeText(this@MainActivity, getString(R.string.report_no_images), Toast.LENGTH_SHORT).show()
                }
                doc.close()
                activeReportJob = null
                return@launch
            }
            val indexEntries = if (getSharedPreferences("snapcrop", MODE_PRIVATE)
                    .getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) {
                try { ScreenshotIndexStore(this@MainActivity).loadEntryMap() } catch (_: Exception) { emptyMap() }
            } else {
                emptyMap()
            }
            val appendix = mutableListOf<Pair<ExportItemMetadata, String>>()
            var pageNumber = 1
            var imagePages = 0
            try {
                pageNumber = drawReportCoverPage(
                    doc = doc,
                    pageNumber = pageNumber,
                    title = title.take(MAX_REPORT_TITLE_CHARS).ifBlank { getString(R.string.report_default_title) },
                    notes = notes.take(MAX_REPORT_NOTES_CHARS),
                    itemCount = renderableUris.size,
                    createdAt = createdAt,
                    includeOcr = includeOcr,
                    layout = layout
                )
                renderableUris.forEachIndexed { index, uri ->
                    if (batchCancelled.value) return@forEachIndexed
                    withContext(Dispatchers.Main) {
                        batchProgress.value = getString(R.string.batch_building_report, index + 1, renderableUris.size)
                        batchProgressFraction.floatValue = index.toFloat() / renderableUris.size
                    }
                    val metadata = loadExportItemMetadata(uri, indexEntries[uri.toString()])
                    val bitmap = decodeReportBitmap(uri) ?: return@forEachIndexed
                    var ocrBlocks = emptyList<TextBlock>()
                    if (includeOcr) {
                        ocrBlocks = ReviewedOcr.sanitize(reviewedOcr?.get(uri.toString()).orEmpty())
                        val extractedText = ReviewedOcr.plainText(ocrBlocks)
                        if (extractedText.isNotBlank()) {
                            appendix.add(metadata to extractedText)
                        }
                    }
                    pageNumber = drawReportImagePage(doc, pageNumber, imagePages + 1, renderableUris.size, bitmap, metadata, ocrBlocks, layout)
                    imagePages++
                    bitmap.recycle()
                }

                if (batchCancelled.value) return@launch
                if (imagePages == 0) {
                    withContext(Dispatchers.Main) {
                        batchProgress.value = ""
                        Toast.makeText(this@MainActivity, getString(R.string.report_no_images), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (includeOcr && appendix.isNotEmpty()) {
                    pageNumber = drawOcrAppendixPages(doc, pageNumber, appendix, layout)
                }
                val displayName = "SnapCrop_Report_$createdAt.pdf"
                temporaryPdf = NetworkExportTempFiles.create(cacheDir, ".pdf")
                NetworkExportTempFiles.boundedOutput(temporaryPdf).use { output ->
                    doc.writeTo(output)
                }
                val pdfFile = requireNotNull(temporaryPdf)
                val saved = savePdfFile(displayName, pdfFile)
                val uploadResult = if (saved && uploadAfterSave) {
                    uploadReportArtifacts(displayName, pdfFile, renderableUris)
                } else {
                    null
                }
                withContext(Dispatchers.Main) {
                    batchProgress.value = ""
                    val uploadSuffix = uploadResult?.let { "\n${it.message}" }.orEmpty()
                    Toast.makeText(
                        this@MainActivity,
                        if (saved) getString(R.string.report_saved) + uploadSuffix else getString(R.string.report_failed),
                        if (uploadResult != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: CancellationException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.report_cancelled), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    batchProgress.value = ""
                    Toast.makeText(this@MainActivity, getString(R.string.report_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                doc.close()
                temporaryPdf?.delete()
                activeNetworkCancellation = null
                activeReportJob = null
                runOnUiThread {
                    batchProgress.value = ""
                    batchProgressFraction.floatValue = 0f
                }
            }
        }
    }

    private fun uploadReportArtifacts(
        displayName: String,
        pdfFile: java.io.File,
        sourceUris: List<Uri>
    ): NetworkExportResult {
        val settings = NetworkExportSettings.fromPrefs(
            getSharedPreferences("snapcrop", MODE_PRIVATE),
            NetworkCredentialStore.open(this)
        )
        if (!settings.isConfigured) {
            return NetworkExportResult(false, settings.target, 0, "Network export is not configured")
        }
        val cancellation = NetworkExportCancellation(
            if (settings.target == NetworkExportTarget.IMGUR) 15L * 60L * 1000L else 5L * 60L * 1000L
        ).also { activeNetworkCancellation = it }
        if (settings.target != NetworkExportTarget.IMGUR) {
            return NetworkExportClient.uploadReportPdf(
                settings,
                NetworkUploadSource(displayName, "application/pdf", pdfFile.length()) { pdfFile.inputStream().buffered() },
                cancellation,
                uploadProgressReporter(displayName, 1, 1)
            )
        }

        var uploaded = 0
        var lastFailure: NetworkExportResult? = null
        val imageUris = sourceUris.filter { uri ->
            try { contentResolver.getType(uri)?.startsWith("image/") == true } catch (_: Exception) { false }
        }
        if (imageUris.size > NetworkExportClient.MAX_IMGUR_BATCH_FILES) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "Imgur batch exceeds 50 images")
        }
        val prepared = imageUris.mapIndexed { index, uri ->
            val metadata = loadExportItemMetadata(uri)
            Triple(uri, metadata, metadata.displayName.ifBlank { "snapcrop_${index + 1}.png" })
        }
        val knownBytes = prepared.mapNotNull { it.second.sizeBytes.takeIf { size -> size > 0 } }.sum()
        if (prepared.any { it.second.sizeBytes > NetworkExportClient.MAX_IMGUR_UPLOAD_BYTES }) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "An Imgur image exceeds the 50,000,000-byte limit")
        }
        if (knownBytes > NetworkExportClient.MAX_IMGUR_BATCH_BYTES) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "Imgur batch exceeds the 256 MiB limit")
        }
        var completedBytes = 0L
        prepared.forEachIndexed { index, (uri, metadata, name) ->
            if (cancellation.isCancelled || batchCancelled.value) return@forEachIndexed
            val mime = contentResolver.getType(uri) ?: "image/png"
            var currentBytes = 0L
            val uiProgress = uploadProgressReporter(name, index + 1, prepared.size)
            val result = NetworkExportClient.uploadImageToImgur(
                settings,
                NetworkUploadSource(name, mime, metadata.sizeBytes.takeIf { it > 0 }) {
                    contentResolver.openInputStream(uri)?.buffered()
                        ?: throw IOException("Source stream unavailable")
                },
                cancellation,
                { progress ->
                    currentBytes = progress.bytesSent
                    if (completedBytes + currentBytes > NetworkExportClient.MAX_IMGUR_BATCH_BYTES) {
                        throw IOException("Imgur batch exceeds the 256 MiB limit")
                    }
                    uiProgress(progress)
                }
            )
            if (result.success) {
                uploaded++
                completedBytes += currentBytes
            } else {
                lastFailure = result
            }
        }
        if (cancellation.isCancelled) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "Upload cancelled", cancelled = true)
        }
        return if (uploaded == prepared.size && uploaded > 0) {
            NetworkExportResult(true, NetworkExportTarget.IMGUR, 200, "Imgur uploaded $uploaded image(s)")
        } else {
            lastFailure?.copy(message = "Uploaded $uploaded of ${prepared.size}; ${lastFailure.message}")
                ?: NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "No images uploaded to Imgur")
        }
    }

    private fun uploadProgressReporter(fileName: String, item: Int, totalItems: Int): (NetworkUploadProgress) -> Unit {
        var lastReported = -512L * 1024L
        return { progress ->
            if (progress.bytesSent - lastReported >= 512L * 1024L || progress.bytesSent == progress.totalBytes) {
                lastReported = progress.bytesSent
                runOnUiThread {
                    batchProgress.value = getString(
                        R.string.batch_uploading_file,
                        item,
                        totalItems,
                        fileName,
                        formatSize(progress.bytesSent),
                        progress.totalBytes?.let(::formatSize) ?: getString(R.string.batch_upload_unknown_size)
                    )
                    batchProgressFraction.floatValue = progress.totalBytes
                        ?.takeIf { it > 0 }
                        ?.let { (progress.bytesSent.toFloat() / it).coerceIn(0f, 1f) }
                        ?: 0f
                }
            }
        }
    }

    private fun loadExportItemMetadata(
        uri: Uri,
        indexEntry: ScreenshotIndexEntry? = null
    ): ExportItemMetadata {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        var displayName = uri.lastPathSegment ?: "screenshot"
        var relativePath = ""
        var owner = ""
        var width = 0
        var height = 0
        var size = 0L
        var dateAdded = 0L
        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getStringIfPresent(MediaStore.MediaColumns.DISPLAY_NAME) ?: displayName
                    relativePath = cursor.getStringIfPresent(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty()
                    owner = cursor.getStringIfPresent(MediaStore.MediaColumns.OWNER_PACKAGE_NAME).orEmpty()
                    width = cursor.getIntIfPresent(MediaStore.MediaColumns.WIDTH)
                    height = cursor.getIntIfPresent(MediaStore.MediaColumns.HEIGHT)
                    size = cursor.getLongIfPresent(MediaStore.MediaColumns.SIZE)
                    dateAdded = cursor.getLongIfPresent(MediaStore.MediaColumns.DATE_ADDED)
                }
            }
        } catch (_: Exception) {
            indexEntry?.let {
                displayName = it.name
                relativePath = it.albumPath
                width = it.width
                height = it.height
                size = it.size
                dateAdded = it.dateAdded
            }
        }
        return ExportItemMetadata(
            displayName = displayName,
            relativePath = relativePath.ifBlank { indexEntry?.albumPath.orEmpty() },
            sourceHint = owner,
            width = if (width > 0) width else indexEntry?.width ?: 0,
            height = if (height > 0) height else indexEntry?.height ?: 0,
            sizeBytes = if (size > 0) size else indexEntry?.size ?: 0L,
            dateAddedSeconds = if (dateAdded > 0) dateAdded else indexEntry?.dateAdded ?: 0L,
            categories = indexEntry?.categories ?: emptySet(),
            recognizedText = indexEntry?.searchText.orEmpty()
        )
    }

    private fun decodeReportBitmap(uri: Uri): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: Exception) {
            return null
        }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasReportImageBounds(uri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            bounds.outWidth > 0 && bounds.outHeight > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun drawReportCoverPage(
        doc: PdfDocument,
        pageNumber: Int,
        title: String,
        notes: String,
        itemCount: Int,
        createdAt: Long,
        includeOcr: Boolean,
        layout: PdfReportLayout
    ): Int {
        data class CoverLine(val text: String?, val paint: Paint?, val height: Float)

        val lines = mutableListOf<CoverLine>()
        wrapPdfTextLines(title, titlePaint(), layout.contentWidth).forEach {
            lines += CoverLine(it, titlePaint(), 34f)
        }
        lines += CoverLine(null, null, 18f)
        val summaryLines = listOf(
            getString(R.string.pdf_created, formatTimestamp(createdAt)),
            getString(R.string.pdf_image_count, itemCount),
            getString(if (includeOcr) R.string.pdf_ocr_enabled else R.string.pdf_ocr_off)
        ).joinToString("\n")
        wrapPdfTextLines(summaryLines, bodyPaint(), layout.contentWidth).forEach {
            lines += CoverLine(it, bodyPaint(), 17f)
        }
        if (notes.isNotBlank()) {
            lines += CoverLine(null, null, 22f)
            wrapPdfTextLines(getString(R.string.pdf_notes), sectionPaint(), layout.contentWidth).forEach {
                lines += CoverLine(it, sectionPaint(), 22f)
            }
            lines += CoverLine(null, null, 6f)
            wrapPdfTextLines(notes, bodyPaint(), layout.contentWidth).forEach {
                lines += CoverLine(it, bodyPaint(), 17f)
            }
        }

        var currentPageNumber = pageNumber
        val startY = layout.marginPoints + 38f
        paginatePdfLines(lines.map(CoverLine::height), startY, layout.contentBottom).forEach { indices ->
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create()
            )
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            var y = startY
            indices.forEach { index ->
                val line = lines[index]
                line.text?.let { canvas.drawText(it, layout.marginPoints, y, checkNotNull(line.paint)) }
                y += line.height
            }
            drawPdfFooter(canvas, currentPageNumber, layout)
            doc.finishPage(page)
            currentPageNumber++
        }
        return currentPageNumber
    }

    private fun drawReportImagePage(
        doc: PdfDocument,
        pageNumber: Int,
        itemNumber: Int,
        totalItems: Int,
        bitmap: android.graphics.Bitmap,
        metadata: ExportItemMetadata,
        ocrBlocks: List<TextBlock> = emptyList(),
        layout: PdfReportLayout
    ): Int {
        var currentPageNumber = pageNumber
        var page = doc.startPage(PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create())
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = layout.marginPoints + 24f
        y = drawWrappedText(
            canvas,
            getString(R.string.pdf_image_of, itemNumber, totalItems),
            layout.marginPoints,
            y,
            layout.contentWidth,
            sectionPaint(),
            20f
        )
        val metaText = buildString {
            append(metadata.displayName)
            if (metadata.sourceHint.isNotBlank()) append("\n").append(getString(R.string.pdf_source, metadata.sourceHint))
            if (metadata.relativePath.isNotBlank()) append("\n").append(getString(R.string.pdf_album, metadata.relativePath))
            append("\n").append(getString(R.string.pdf_dimensions, metadata.width.takeIf { it > 0 } ?: bitmap.width, metadata.height.takeIf { it > 0 } ?: bitmap.height))
            if (metadata.sizeBytes > 0) append("\n").append(getString(R.string.pdf_size, formatSize(metadata.sizeBytes)))
            if (metadata.dateAddedSeconds > 0) append("\n").append(getString(R.string.pdf_date_added, formatTimestamp(metadata.dateAddedSeconds * 1000)))
            if (metadata.categories.isNotEmpty()) append("\n").append(getString(R.string.pdf_tags, metadata.categories.joinToString(", ")))
        }
        y = drawWrappedText(canvas, metaText, layout.marginPoints, y + 6f, layout.contentWidth, smallPaint(), 13f)
        var imageTop = y + 24f
        var fitted = layout.fitImage(bitmap.width, bitmap.height, imageTop)
        if (fitted == null || fitted.height < MIN_REPORT_IMAGE_POINTS) {
            drawPdfFooter(canvas, currentPageNumber, layout)
            doc.finishPage(page)
            currentPageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, currentPageNumber).create())
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            val headingBottom = drawWrappedText(
                canvas,
                getString(R.string.pdf_image_of, itemNumber, totalItems),
                layout.marginPoints,
                layout.marginPoints + 24f,
                layout.contentWidth,
                sectionPaint(),
                20f
            )
            imageTop = headingBottom + 12f
            fitted = checkNotNull(layout.fitImage(bitmap.width, bitmap.height, imageTop))
        }
        val placement = checkNotNull(fitted)
        val scale = placement.width / bitmap.width
        val left = placement.left
        val top = placement.top
        val dst = RectF(placement.left, placement.top, placement.right, placement.bottom)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 218, 226)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawBitmap(bitmap, null, dst, null)
        canvas.drawRect(dst, border)
        if (ocrBlocks.isNotEmpty()) {
            val invisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.TRANSPARENT
                textSize = 1f
            }
            for (block in ocrBlocks) {
                val b = block.bounds
                val bx = left + b.left * scale
                val by = top + b.top * scale
                val bw = b.width() * scale
                val bh = b.height() * scale
                if (bw < 1f || bh < 1f) continue
                val lines = block.text.lines().filter(String::isNotBlank)
                if (lines.isEmpty()) continue
                val lineHeight = bh / lines.size
                invisPaint.textSize = (lineHeight * 0.85f).coerceIn(1f, 40f)
                lines.forEachIndexed { lineIndex, line ->
                    canvas.drawText(line, bx, by + lineHeight * (lineIndex + 0.8f), invisPaint)
                }
            }
        }
        drawPdfFooter(canvas, currentPageNumber, layout)
        doc.finishPage(page)
        return currentPageNumber + 1
    }

    private fun drawOcrAppendixPages(
        doc: PdfDocument,
        startPageNumber: Int,
        entries: List<Pair<ExportItemMetadata, String>>,
        layout: PdfReportLayout
    ): Int {
        var pageNumber = startPageNumber
        var page = doc.startPage(PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, pageNumber).create())
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = layout.marginPoints + 24f
        val ocrAppendixLabel = getString(R.string.pdf_ocr_appendix)
        y = drawWrappedText(canvas, ocrAppendixLabel, layout.marginPoints, y, layout.contentWidth, sectionPaint(), 24f)
        val textPaint = smallPaint()
        val maxWidth = layout.contentWidth
        val bottom = layout.contentBottom

        fun nextPage() {
            drawPdfFooter(canvas, pageNumber, layout)
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(layout.widthPoints, layout.heightPoints, pageNumber).create())
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = layout.marginPoints + 24f
            y = drawWrappedText(canvas, ocrAppendixLabel, layout.marginPoints, y, maxWidth, sectionPaint(), 24f)
        }

        entries.forEach { (metadata, text) ->
            val headingLines = wrapPdfTextLines(metadata.displayName, sectionPaint(), maxWidth)
            val boundedText = if (text.length > MAX_OCR_APPENDIX_CHARS) {
                text.take(MAX_OCR_APPENDIX_CHARS) + "\n" + getString(R.string.pdf_text_truncated)
            } else text
            val lines = wrapPdfTextLines(boundedText, textPaint, maxWidth)
            if (y + headingLines.size * 20f + minOf(1, lines.size) * 13f > bottom) nextPage()
            headingLines.forEach { line ->
                canvas.drawText(line, layout.marginPoints, y, sectionPaint())
                y += 20f
            }
            lines.forEach { line ->
                if (y + 13f > bottom) nextPage()
                canvas.drawText(line, layout.marginPoints, y, textPaint)
                y += 13f
            }
            y += 13f
        }
        drawPdfFooter(canvas, pageNumber, layout)
        doc.finishPage(page)
        return pageNumber + 1
    }

    private fun savePdfFile(displayName: String, pdfFile: java.io.File): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SnapCrop")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var pdfUri: Uri? = null
        return try {
            pdfUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            val uri = pdfUri ?: return false
            val opened = contentResolver.openOutputStream(uri)
            if (opened == null) {
                contentResolver.delete(uri, null, null)
                return false
            }
            opened.use { output -> pdfFile.inputStream().buffered().use { it.copyTo(output, 64 * 1024) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(contentResolver.update(uri, values, null, null) == 1) { "Could not publish PDF" }
            true
        } catch (_: Exception) {
            pdfUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            false
        }
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int = Int.MAX_VALUE
    ): Float {
        var y = startY
        var linesDrawn = 0
        for (line in wrapPdfTextLines(text, paint, maxWidth)) {
            if (linesDrawn >= maxLines) break
            canvas.drawText(line, x, y, paint)
            y += lineHeight
            linesDrawn++
        }
        return y
    }

    private fun drawPdfFooter(canvas: android.graphics.Canvas, pageNumber: Int, layout: PdfReportLayout) {
        val paint = smallPaint().apply { color = android.graphics.Color.rgb(92, 103, 115) }
        val pageLabel = getString(R.string.pdf_page, pageNumber)
        canvas.drawText(getString(R.string.pdf_footer), layout.marginPoints, layout.footerBaseline, paint)
        canvas.drawText(
            pageLabel,
            layout.widthPoints - layout.marginPoints - paint.measureText(pageLabel),
            layout.footerBaseline,
            paint
        )
    }

    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 29, 39)
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun sectionPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(25, 94, 145)
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(34, 44, 55)
        textSize = 11f
    }

    private fun smallPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(56, 68, 80)
        textSize = 9f
    }


    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun mediaIdFromUri(uri: Uri): Long? = try {
        ContentUris.parseId(uri)
    } catch (_: Exception) {
        null
    }

    private fun uniqueDisplayName(base: String, extension: String, usedNames: MutableSet<String>): String {
        var suffix = 0
        while (true) {
            val candidateBase = if (suffix == 0) base else "${base}_${suffix + 1}"
            val candidate = if (extension.isBlank()) candidateBase else "$candidateBase.$extension"
            if (usedNames.add(candidate.lowercase(Locale.US))) return candidate
            suffix++
        }
    }

    private fun extensionForMime(mime: String?): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/png" -> "png"
        else -> "png"
    }

    private fun android.database.Cursor.getStringIfPresent(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun android.database.Cursor.getIntIfPresent(column: String): Int {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else 0
    }

    private fun android.database.Cursor.getLongIfPresent(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }

    private fun shareImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            var preparedDirectory: java.io.File? = null
            try {
                val mimeTypes = uris.associateWith { uri ->
                    runCatching { contentResolver.getType(uri).orEmpty() }.getOrDefault("")
                }
                val hasVideo = mimeTypes.values.any { it.startsWith("video/") }
                val imageUris = uris.filter { !mimeTypes.getValue(it).startsWith("video/") }
                val summaries = imageUris.map { ExifTransfer.inspect(contentResolver, it) }
                if (summaries.any { it.inspectionFailed }) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.share_metadata_inspection_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }
                val summary = MetadataSummary(summaries.flatMapTo(linkedSetOf()) { it.categories })
                val contextStore = ScreenshotIndexStore(this@MainActivity)
                val sourceUrls = uris.map { uri ->
                    val dateAdded = mediaDateAdded(uri)
                    if (dateAdded == null) null else contextStore.sourceContext(uri, dateAdded)?.url
                }
                val commonSourceUrl = sourceUrls.filterNotNull().distinct().singleOrNull()
                    ?.takeIf { sourceUrls.all { value -> value == it } }
                val options = if (summary.hasMetadata || commonSourceUrl != null) {
                    chooseShareOptions(summary, allowSanitize = !hasVideo, sourceUrl = commonSourceUrl)
                } else {
                    ShareOptions(ShareMetadataPolicy.PRESERVE, includeSourceLink = false)
                } ?: return@launch
                val policy = options.metadataPolicy

                val sharedUris = if (policy == ShareMetadataPolicy.PRESERVE) {
                    uris
                } else {
                    val shareRoot = java.io.File(cacheDir, "share_clean").apply { mkdirs() }
                    shareRoot.listFiles()
                        ?.filter { it.isDirectory && System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
                        ?.forEach { it.deleteRecursively() }
                    preparedDirectory = java.io.File(shareRoot, System.nanoTime().toString()).apply {
                        if (!mkdirs()) throw IOException("Could not create a private share directory")
                    }
                    val (format, quality, ext) = getSaveFormat()
                    imageUris.mapIndexed { index, sourceUri ->
                        val bitmap = contentResolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream)
                            ?: throw IOException("Could not decode shared image")
                        try {
                            val file = java.io.File(preparedDirectory, "share_$index.$ext")
                            val encoded = file.outputStream().use { bitmap.compress(format, quality, it) }
                            if (!encoded) throw IOException("Could not encode shared image")
                            val outputUri = androidx.core.content.FileProvider.getUriForFile(
                                this@MainActivity,
                                "${packageName}.fileprovider",
                                file,
                            )
                            if (!ExifTransfer.copyForShare(contentResolver, sourceUri, outputUri, policy)) {
                                throw IOException("Could not apply selected metadata policy")
                            }
                            outputUri
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                val hasImage = imageUris.isNotEmpty()
                val shareType = when {
                    hasVideo && hasImage -> "*/*"
                    hasVideo -> "video/*"
                    else -> "image/*"
                }
                withContext(Dispatchers.Main) {
                    dispatchShareIntent(
                        sharedUris,
                        shareType,
                        commonSourceUrl.takeIf { options.includeSourceLink }
                    )
                }
            } catch (cancelled: CancellationException) {
                preparedDirectory?.deleteRecursively()
                throw cancelled
            } catch (_: Exception) {
                preparedDirectory?.deleteRecursively()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.share_metadata_export_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun dispatchShareIntent(uris: List<Uri>, shareType: String, sourceUrl: String? = null) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = shareType
                putExtra(Intent.EXTRA_STREAM, uris.single())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = shareType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.clipData = ClipData.newRawUri("SnapCrop shared media", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sourceUrl?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        startShareChooser(intent)
    }

    private fun mediaDateAdded(uri: Uri): Long? = try {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_ADDED),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun startShareChooser(baseIntent: Intent) {
        val callbackFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val callback = PendingIntent.getBroadcast(
            this,
            1001,
            Intent(this, ShareTargetReceiver::class.java),
            callbackFlags
        )
        val chooser = Intent.createChooser(baseIntent, null, callback.intentSender).apply {
            val shortcuts = ShareTargetStore.buildInitialIntents(this@MainActivity, baseIntent)
            if (shortcuts.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, shortcuts)
            }
        }
        startActivity(chooser)
    }

    private fun copyImageToClipboard(uri: Uri) {
        val clip = ClipData.newUri(contentResolver, "SnapCrop", uri)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
    }

    private fun requestDeleteUris(uris: List<Uri>) {
        if (pendingMutationUris.isNotEmpty() || pendingMutationChunk.isNotEmpty()) {
            Toast.makeText(this, R.string.toast_mutation_pending, Toast.LENGTH_SHORT).show()
            return
        }
        pendingMutationUris = uris
            .filter { it.scheme.equals("content", ignoreCase = true) }
            .distinctBy(Uri::toString)
            .toMutableList()
        pendingMutationSucceeded.clear()
        if (pendingMutationUris.isEmpty()) {
            Toast.makeText(this, R.string.toast_delete_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        pendingMutationRequested = pendingMutationUris.size
        pendingMutationStartedAt = OperationJournal.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) launchNextTrashChunk()
        else deleteLegacyMutationUris()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun launchNextTrashChunk() {
        if (pendingMutationUris.isEmpty()) {
            completeMediaMutation()
            return
        }
        pendingMutationChunk = pendingMutationUris.take(2_000)
        try {
            val pendingIntent = MediaStore.createTrashRequest(contentResolver, pendingMutationChunk, true)
            mediaMutationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (e: Exception) {
            android.util.Log.e("SnapCrop", "Unable to request scoped trash", e)
            completeMediaMutation()
        }
    }

    private fun deleteLegacyMutationUris() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (pendingMutationUris.isNotEmpty()) {
                val uri = pendingMutationUris.first()
                try {
                    if (contentResolver.delete(uri, null, null) == 1) {
                        pendingMutationSucceeded.add(uri)
                    }
                    pendingMutationUris.removeAt(0)
                } catch (recoverable: RecoverableSecurityException) {
                    withContext(Dispatchers.Main) {
                        mediaMutationLauncher.launch(
                            IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build()
                        )
                    }
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.w("SnapCrop", "Scoped media deletion failed", e)
                    pendingMutationUris.removeAt(0)
                }
            }
            withContext(Dispatchers.Main) { completeMediaMutation() }
        }
    }

    private fun completeMediaMutation() {
        val succeeded = pendingMutationSucceeded.toList()
        val outcome = MediaMutationOutcome(pendingMutationRequested, succeeded.size)
        val mutationStartedAt = pendingMutationStartedAt
        pendingMutationUris.clear()
        pendingMutationChunk = emptyList()
        pendingMutationSucceeded.clear()
        pendingMutationRequested = 0
        pendingMutationStartedAt = null
        OperationJournal.enqueue(
            this,
            DiagnosticOperation.DELETE,
            DiagnosticStage.COMPLETE,
            when (outcome.result) {
                MediaMutationResult.SUCCESS -> DiagnosticResult.SUCCESS
                MediaMutationResult.PARTIAL -> DiagnosticResult.RETRY
                MediaMutationResult.RETAINED -> DiagnosticResult.FAILED
            },
            mutationStartedAt
        )
        if (succeeded.isNotEmpty()) {
            FavoritesStore.removeAll(this, succeeded)
            lifecycleScope.launch(Dispatchers.IO) {
                val store = ScreenshotIndexStore(this@MainActivity)
                runCatching { store.deleteSourceContexts(succeeded) }
                runCatching {
                    store.deleteNoteReminders(succeeded).forEach { reminder ->
                        ScreenshotReminderScheduler.cancel(
                            this@MainActivity,
                            reminder.uri,
                            reminder.dateAdded
                        )
                    }
                }
            }
            galleryRefreshKey.intValue++
            loadRecentCrops()
        }
        val message = when (outcome.result) {
            MediaMutationResult.RETAINED -> getString(R.string.toast_items_retained)
            MediaMutationResult.PARTIAL -> getString(R.string.toast_trashed_partial, outcome.succeeded, outcome.retained)
            MediaMutationResult.SUCCESS -> getString(R.string.toast_trashed_count, outcome.succeeded)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleService() {
        if (serviceRunning.value) {
            stopService(Intent(this, ScreenshotService::class.java))
            serviceRunning.value = false
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit()
                .putBoolean("auto_start", false).apply()
        } else {
            if (!mediaCapabilities.value.canMonitorScreenshots) {
                requestPermissions(MediaCapabilityResolver.imageRequest(Build.VERSION.SDK_INT), startMonitorAfter = true)
                return
            }
            startMonitoring()
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit()
                .putBoolean("auto_start", true).apply()
        }
    }

    private fun startMonitoring() {
        val intent = Intent(this, ScreenshotService::class.java)
        ContextCompat.startForegroundService(this, intent)
        serviceRunning.value = true
    }

    private fun startDelayedCapture(seconds: Int) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_DELAYED_CAPTURE
                putExtra(ScreenshotService.EXTRA_DELAY_SECONDS, seconds)
            }
        )
        recordWorkflow(WorkflowId.DELAYED_CAPTURE)
        Toast.makeText(this, getString(R.string.toast_capturing_in, seconds), Toast.LENGTH_SHORT).show()
    }

    private fun refreshLongScreenshotState() {
        longScreenshotReady.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (ScrollCaptureService.isReady() || isScrollCaptureEnabled())
    }

    private fun requestLongScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
            return
        }

        requestAccessibilityPurpose(AccessibilityPurpose.LONG_SCREENSHOT)
    }

    private fun handleAccessibilityIntent(incoming: Intent?) {
        val purpose = AccessibilityDisclosure.purpose(incoming) ?: return
        incoming?.removeExtra(AccessibilityDisclosure.EXTRA_PURPOSE)
        requestAccessibilityPurpose(purpose)
    }

    private fun requestAccessibilityPurpose(purpose: AccessibilityPurpose) {
        val action = AccessibilityDisclosure.route(
            purpose = purpose,
            serviceReady = isAccessibilityPurposeReady(purpose),
            stepCaptureActive = StepCaptureService.isCapturing(),
            hasCurrentConsent = AccessibilityDisclosure.hasCurrentConsent(this, purpose)
        )
        when (action) {
            AccessibilityAction.SHOW_DISCLOSURE -> pendingAccessibilityDisclosure.value = purpose
            AccessibilityAction.OPEN_SETTINGS -> openAccessibilitySettings(purpose)
            AccessibilityAction.START, AccessibilityAction.STOP -> executeAccessibilityPurpose(purpose)
        }
    }

    private fun executeAccessibilityPurpose(purpose: AccessibilityPurpose) {
        val started = when (purpose) {
            AccessibilityPurpose.LONG_SCREENSHOT ->
                ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 2500L)
            AccessibilityPurpose.STEP_CAPTURE -> StepCaptureService.toggleCapture(this)
        }
        if (started) {
            if (purpose == AccessibilityPurpose.LONG_SCREENSHOT) {
                recordWorkflow(WorkflowId.LONG_SCREENSHOT)
            }
            moveTaskToBack(true)
        } else {
            openAccessibilitySettings(purpose)
        }
    }

    private fun isAccessibilityPurposeReady(purpose: AccessibilityPurpose): Boolean = when (purpose) {
        AccessibilityPurpose.LONG_SCREENSHOT -> ScrollCaptureService.isReady()
        AccessibilityPurpose.STEP_CAPTURE -> StepCaptureService.isReady()
    }

    private fun openAccessibilitySettings(purpose: AccessibilityPurpose) {
        Toast.makeText(
            this,
            getString(
                if (purpose == AccessibilityPurpose.LONG_SCREENSHOT) {
                    R.string.toast_enable_long_accessibility
                } else {
                    R.string.toast_enable_step_accessibility
                }
            ),
            Toast.LENGTH_LONG
        ).show()
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, R.string.toast_accessibility_settings_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    private fun isScrollCaptureEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(this, ScrollCaptureService::class.java)
        val fullName = component.flattenToString()
        val shortName = component.flattenToShortString()
        return enabledServices.split(':').any {
            it.equals(fullName, ignoreCase = true) ||
                    it.equals(shortName, ignoreCase = true)
        }
    }

    private fun loadRecentCrops() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val crops = mutableListOf<RecentCrop>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                val savePath = getSharedPreferences("snapcrop", MODE_PRIVATE)
                    .getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("$savePath%")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    var count = 0
                    while (cursor.moveToNext()) {
                        count++
                        if (crops.size < 10) {
                            val id = cursor.getLong(idCol)
                            val uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                            )
                            try {
                                val opts = BitmapFactory.Options().apply {
                                    inSampleSize = 8
                                }
                                contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream, null, opts)?.let { thumb ->
                                        // Don't hold the native Bitmap separately — recycling it
                                        // while Compose still draws the wrapped ImageBitmap crashes
                                        // with "Cannot draw recycled bitmaps". Thumbnails are small
                                        // (inSampleSize=8), so we let GC reclaim them on swap.
                                        crops.add(RecentCrop(uri, thumb.asImageBitmap()))
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    withContext(Dispatchers.Main) {
                        cropCount.value = count
                    }
                }
                withContext(Dispatchers.Main) {
                    recentCrops.value = crops
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun CapabilityCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = Tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(body, color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) { Text(action, color = OnPrimary) }
    }
}

@Composable
private fun HomeScreen(
    isRunning: Boolean,
    mediaCapabilities: MediaCapabilities,
    recentCrops: List<RecentCrop>,
    cropCount: Int,
    recentWorkflows: List<WorkflowId>,
    onToggleService: () -> Unit,
    onRequestImageAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onPickImage: () -> Unit,
    onWebCapture: () -> Unit,
    onBatchCrop: () -> Unit,
    onStitch: () -> Unit,
    onCollage: () -> Unit,
    onDeviceFrame: () -> Unit,
    onVideoClip: () -> Unit,
    longScreenshotReady: Boolean,
    onLongScreenshot: () -> Unit,
    onDelayedCapture: (Int) -> Unit,
    batchProgress: String,
    batchFraction: Float,
    onBatchCancel: () -> Unit,
    hasOverlayPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenCrop: (Uri) -> Unit,
    onCopyCrop: (Uri) -> Unit,
    onDeleteCrop: (Uri) -> Unit
) {
    var cropPendingDelete by remember { mutableStateOf<RecentCrop?>(null) }
    var showDelayPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CropOriginal,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    stringResource(R.string.home_subtitle, BuildConfig.VERSION_NAME),
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            IconButton(onClick = onOpenHelp) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.help_content_description), tint = OnSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.home_settings), tint = OnSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Permission warning
        if (!mediaCapabilities.canMonitorScreenshots) {
            CapabilityCard(
                title = stringResource(
                    if (mediaCapabilities.imageAccess == MediaAccess.SELECTED) R.string.home_permission_partial_title
                    else R.string.home_permission_images_title
                ),
                body = stringResource(
                    if (mediaCapabilities.imageAccess == MediaAccess.SELECTED) R.string.home_permission_partial_body
                    else R.string.home_permission_images_body
                ),
                action = stringResource(
                    if (mediaCapabilities.imageAccess == MediaAccess.SELECTED) R.string.home_permission_partial_grant
                    else R.string.home_permission_images_grant
                ),
                onClick = onRequestImageAccess
            )
        }
        if (Build.VERSION.SDK_INT >= 33 && mediaCapabilities.videoAccess != MediaAccess.FULL) {
            CapabilityCard(
                title = stringResource(if (mediaCapabilities.videoAccess == MediaAccess.SELECTED) R.string.home_permission_video_partial_title else R.string.home_permission_video_title),
                body = stringResource(if (mediaCapabilities.videoAccess == MediaAccess.SELECTED) R.string.home_permission_video_partial_body else R.string.home_permission_video_body),
                action = stringResource(if (mediaCapabilities.videoAccess == MediaAccess.SELECTED) R.string.home_permission_video_partial_grant else R.string.home_permission_video_grant),
                onClick = onRequestVideoAccess
            )
        }
        if (Build.VERSION.SDK_INT >= 33 && !mediaCapabilities.notificationAccess) {
            CapabilityCard(
                title = stringResource(R.string.home_permission_notification_title),
                body = stringResource(R.string.home_permission_notification_body),
                action = stringResource(R.string.home_permission_notification_grant),
                onClick = onRequestNotificationAccess
            )
        }

        // Overlay permission (optional on Android 12+; notifications remain the fallback)
        if (mediaCapabilities.canMonitorScreenshots && !hasOverlayPermission && Build.VERSION.SDK_INT >= 31) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_overlay_title), color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.home_overlay_body),
                            color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Button(
                    onClick = onRequestOverlay,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.home_overlay_grant), color = OnPrimary) }
            }
        }

        // Service toggle
        val monitorStatusLabel = if (isRunning) stringResource(R.string.home_monitor_status_active) else stringResource(R.string.home_monitor_status_paused)
        val monitorCd = stringResource(R.string.home_monitor_cd, monitorStatusLabel)
        val monitorSwitchCd = stringResource(R.string.home_monitor_switch_cd, monitorStatusLabel)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) PrimaryContainer else SurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = monitorCd
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        stringResource(R.string.home_monitor_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        if (isRunning) stringResource(R.string.home_monitor_active) else stringResource(R.string.home_monitor_paused),
                        color = OnSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Surface(
                    color = if (isRunning) Secondary.copy(alpha = 0.16f) else SurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(if (isRunning) R.string.home_monitor_status_active else R.string.home_monitor_status_paused),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (isRunning) Secondary else OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggleService() },
                    modifier = Modifier.semantics {
                        contentDescription = monitorSwitchCd
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnPrimary,
                        checkedTrackColor = Primary,
                        uncheckedThumbColor = OnSurfaceVariant,
                        uncheckedTrackColor = SurfaceVariant
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.home_workflows), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        RecentWorkflowRow(recentWorkflows) { workflow ->
            when (workflow) {
                WorkflowId.EDIT_IMAGE -> onPickImage()
                WorkflowId.WEB_CAPTURE -> onWebCapture()
                WorkflowId.BATCH_CROP -> onBatchCrop()
                WorkflowId.DELAYED_CAPTURE -> showDelayPicker = true
                WorkflowId.LONG_SCREENSHOT -> onLongScreenshot()
                WorkflowId.STITCH -> onStitch()
                WorkflowId.COLLAGE -> onCollage()
                WorkflowId.DEVICE_FRAME -> onDeviceFrame()
                WorkflowId.VIDEO_CLIP -> onVideoClip()
                else -> Unit
            }
        }

        // Batch progress bar
        if (batchProgress.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(batchProgress, color = OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${(batchFraction.coerceIn(0f, 1f) * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { batchFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                        trackColor = SurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = onBatchCancel,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(stringResource(R.string.home_cancel_batch), fontSize = 12.sp) }
                    }
                }
            }
        }

        HomeActionTile(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.home_crop_one_title),
            subtitle = stringResource(R.string.home_crop_one_subtitle),
            onClick = onPickImage
        )

        Spacer(Modifier.height(8.dp))

        HomeActionTile(
            icon = Icons.Default.Language,
            title = stringResource(R.string.home_web_capture_title),
            subtitle = stringResource(R.string.home_web_capture_subtitle),
            onClick = onWebCapture
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.Default.BurstMode,
                title = stringResource(R.string.home_batch_title),
                subtitle = stringResource(R.string.home_batch_subtitle),
                enabled = batchProgress.isEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onBatchCrop
            )
            HomeActionTile(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.home_delay_title),
                subtitle = stringResource(R.string.home_delay_subtitle),
                modifier = Modifier.weight(1f),
                onClick = { showDelayPicker = !showDelayPicker }
            )
        }

        if (showDelayPicker) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.home_delay_choose), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.home_delay_body),
                        color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { sec ->
                            FilledTonalButton(
                                onClick = { onDelayedCapture(sec); showDelayPicker = false },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer)
                            ) { Text(stringResource(R.string.home_delay_seconds, sec), color = Primary, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        HomeActionTile(
            icon = Icons.Default.ScreenshotMonitor,
            title = stringResource(R.string.home_long_title),
            subtitle = if (longScreenshotReady) {
                stringResource(R.string.home_long_subtitle_ready)
            } else {
                stringResource(R.string.home_long_subtitle_setup)
            },
            onClick = onLongScreenshot
        )

        Spacer(Modifier.height(8.dp))

        // Stitch + Collage row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.AutoMirrored.Filled.MergeType,
                title = stringResource(R.string.home_stitch_title),
                subtitle = stringResource(R.string.home_stitch_subtitle),
                modifier = Modifier.weight(1f),
                onClick = onStitch
            )
            HomeActionTile(
                icon = Icons.Default.GridView,
                title = stringResource(R.string.home_collage_title),
                subtitle = stringResource(R.string.home_collage_subtitle),
                modifier = Modifier.weight(1f),
                onClick = onCollage
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.home_mockup_title),
                subtitle = stringResource(R.string.home_mockup_subtitle),
                modifier = Modifier.weight(1f),
                onClick = onDeviceFrame
            )
            HomeActionTile(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.home_video_title),
                subtitle = stringResource(R.string.home_video_subtitle),
                modifier = Modifier.weight(1f),
                onClick = onVideoClip
            )
        }

        // Stats
        if (cropCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_crop_count, cropCount),
                color = OnSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // Recent crops gallery
        if (recentCrops.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_recent),
                color = OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentCrops, key = { it.uri }) { crop ->
                    RecentCropTile(
                        crop = crop,
                        onOpen = { onOpenCrop(crop.uri) },
                        onCopy = { onCopyCrop(crop.uri) },
                        onDelete = { cropPendingDelete = crop }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            stringResource(R.string.home_footer),
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(16.dp))
    }

    val pendingDelete = cropPendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { cropPendingDelete = null },
            title = { Text(stringResource(R.string.home_delete_crop_title), color = OnSurface) },
            text = {
                Text(
                    stringResource(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.home_delete_crop_body
                        else R.string.home_delete_crop_body_legacy
                    ),
                    color = OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cropPendingDelete = null
                        onDeleteCrop(pendingDelete.uri)
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
                TextButton(onClick = { cropPendingDelete = null }) {
                    Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun RecentCropTile(
    crop: RecentCrop,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(84.dp, 144.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
    ) {
        Image(
            bitmap = crop.thumbBitmap,
            contentDescription = stringResource(R.string.home_open_crop_cd),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.home_copy_crop_cd),
                    tint = OnMediaSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete_crop_cd),
                    tint = Tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.48f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 84.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
            }
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = PrimaryContainer.copy(alpha = contentAlpha),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                    tint = Primary.copy(alpha = contentAlpha)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = OnSurface.copy(alpha = contentAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = OnSurfaceVariant.copy(alpha = contentAlpha),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
