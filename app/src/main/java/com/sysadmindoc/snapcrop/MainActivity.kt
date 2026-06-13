package com.sysadmindoc.snapcrop

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.ComponentName
import android.content.ClipDescription
import android.content.Intent
import android.view.DragEvent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.snapcrop.BuildConfig
import com.sysadmindoc.snapcrop.ui.theme.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentCrop(val uri: Uri, val thumbBitmap: androidx.compose.ui.graphics.ImageBitmap)

class MainActivity : ComponentActivity() {
    private companion object {
        const val PDF_WIDTH = 1240
        const val PDF_HEIGHT = 1754
        const val PAGE_MARGIN = 72f
    }

    private val serviceRunning = mutableStateOf(false)
    private val hasPermissions = mutableStateOf(false)
    private val hasOverlayPermission = mutableStateOf(false)
    private val longScreenshotReady = mutableStateOf(false)
    private val galleryRefreshKey = mutableIntStateOf(0)
    private val recentCrops = mutableStateOf<List<RecentCrop>>(emptyList())
    private val cropCount = mutableStateOf(0)
    private val showAccessibilityDisclosure = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions.value = results.values.all { it }
        if (hasPermissions.value) startMonitoring()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            startActivity(Intent(this, CropActivity::class.java).apply { data = it })
        }
    }

    private val batchPickLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) batchAutocrop(uris)
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            startActivity(Intent(this, VideoClipActivity::class.java).apply {
                data = it
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
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
    private val resizeUris = mutableStateOf<List<Uri>>(emptyList())
    private val showResizeDialogState = mutableStateOf(false)
    private val reportUris = mutableStateOf<List<Uri>>(emptyList())
    private val showReportDialogState = mutableStateOf(false)
    private val renameUris = mutableStateOf<List<Uri>>(emptyList())
    private val showRenameDialogState = mutableStateOf(false)

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
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream) ?: return@use
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
                            val os = contentResolver.openOutputStream(savedUri)
                            if (os != null) {
                                os.use { out -> toSave.compress(fmt, qual, out) }
                                values.clear()
                                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(savedUri, values, null, null)
                            } else {
                                contentResolver.delete(savedUri, null, null)
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

        window.decorView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_INTENT) == true ||
                    event.clipDescription?.hasMimeType("image/*") == true
                DragEvent.ACTION_DROP -> {
                    val uri = event.clipData?.getItemAt(0)?.uri ?: return@setOnDragListener false
                    requestDragAndDropPermissions(event)
                    startActivity(Intent(this, CropActivity::class.java).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
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
                var selectedTab by remember { mutableIntStateOf(0) }
                val prefs = remember { getSharedPreferences("snapcrop", MODE_PRIVATE) }
                val credPrefs = remember { NetworkExportSettings.encryptedPrefs(this@MainActivity) }

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
                        when (selectedTab) {
                            0 -> HomeScreen(
                                isRunning = serviceRunning.value,
                                hasPermissions = hasPermissions.value,
                                recentCrops = recentCrops.value,
                                cropCount = cropCount.value,
                                onToggleService = { toggleService() },
                                onRequestPermissions = { requestPermissions() },
                                onPickImage = { pickImageLauncher.launch("image/*") },
                                onBatchCrop = { batchPickLauncher.launch(arrayOf("image/*")) },
                                onStitch = { startActivity(Intent(this@MainActivity, StitchActivity::class.java)) },
                                onCollage = { startActivity(Intent(this@MainActivity, CollageActivity::class.java)) },
                                onDeviceFrame = { startActivity(Intent(this@MainActivity, DeviceFrameActivity::class.java)) },
                                onVideoClip = { pickVideoLauncher.launch("video/*") },
                                longScreenshotReady = longScreenshotReady.value,
                                onLongScreenshot = { requestLongScreenshot() },
                                onDelayedCapture = { seconds ->
                                    val intent = Intent(this@MainActivity, ScreenshotService::class.java).apply {
                                        action = ScreenshotService.ACTION_DELAYED_CAPTURE
                                        putExtra(ScreenshotService.EXTRA_DELAY_SECONDS, seconds)
                                    }
                                    startService(intent)
                                    Toast.makeText(this@MainActivity, getString(R.string.toast_capturing_in, seconds), Toast.LENGTH_SHORT).show()
                                },
                                batchProgress = batchProgress.value,
                                batchFraction = batchProgressFraction.floatValue,
                                onBatchCancel = { batchCancelled.value = true },
                                hasOverlayPermission = hasOverlayPermission.value,
                                onRequestOverlay = {
                                    startActivity(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    ))
                                },
                                onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                onOpenCrop = { uri ->
                                    startActivity(Intent(this@MainActivity, CropActivity::class.java).apply { data = uri })
                                },
                                onDeleteCrop = { uri -> requestDeleteUris(listOf(uri)) }
                            )
                            1 -> GalleryScreen(
                                refreshKey = galleryRefreshKey.intValue,
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

                if (showAccessibilityDisclosure.value) {
                    AlertDialog(
                        onDismissRequest = { showAccessibilityDisclosure.value = false },
                        title = { Text(stringResource(R.string.accessibility_dialog_title), color = OnSurface) },
                        text = {
                            Text(
                                stringResource(R.string.accessibility_dialog_body),
                                color = OnSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showAccessibilityDisclosure.value = false
                                    openAccessibilitySettings()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                            ) {
                                Text(stringResource(R.string.accessibility_open_settings))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showAccessibilityDisclosure.value = false },
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
                    val networkSettings = NetworkExportSettings.fromPrefs(prefs, credPrefs)
                    val reportDefaultTitle = stringResource(R.string.report_default_title)
                    var reportTitle by remember(reportUris.value) { mutableStateOf(reportDefaultTitle) }
                    var notes by remember(reportUris.value) { mutableStateOf("") }
                    var includeOcr by remember(reportUris.value) { mutableStateOf(false) }
                    var uploadAfterSave by remember(reportUris.value, networkSettings) { mutableStateOf(false) }
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
                                    onValueChange = { reportTitle = it },
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
                                    onValueChange = { notes = it },
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
                                    exportPdfReport(reportUris.value, reportTitle, notes, includeOcr, uploadAfterSave)
                                },
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
        checkPermissions()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        refreshLongScreenshotState()

        val shouldRun = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getBoolean("auto_start", false)
        if (shouldRun && hasPermissions.value && !ScreenshotService.isRunning) {
            startMonitoring()
        }
        serviceRunning.value = ScreenshotService.isRunning

        if (hasPermissions.value) loadRecentCrops()
        galleryRefreshKey.intValue++
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        hasPermissions.value = needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun showResizeDialog(uris: List<Uri>) {
        resizeUris.value = uris
        showResizeDialogState.value = true
    }

    private fun showReportDialog(uris: List<Uri>) {
        reportUris.value = uris
        showReportDialogState.value = true
    }

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
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream) ?: return@use
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
                            val os = contentResolver.openOutputStream(savedUri)
                            if (os != null) {
                                os.use { out -> resized.compress(fmt, qual, out) }
                                values.clear()
                                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(savedUri, values, null, null)
                            } else {
                                contentResolver.delete(savedUri, null, null)
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
        uploadAfterSave: Boolean
    ) {
        if (uris.isEmpty()) return
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            val doc = PdfDocument()
            val createdAt = System.currentTimeMillis()
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
                    title = title.ifBlank { getString(R.string.report_default_title) },
                    notes = notes,
                    itemCount = uris.size,
                    createdAt = createdAt,
                    includeOcr = includeOcr
                )
                uris.forEachIndexed { index, uri ->
                    if (batchCancelled.value) return@forEachIndexed
                    withContext(Dispatchers.Main) {
                        batchProgress.value = getString(R.string.batch_building_report, index + 1, uris.size)
                        batchProgressFraction.floatValue = index.toFloat() / uris.size
                    }
                    val metadata = loadExportItemMetadata(uri, mediaIdFromUri(uri)?.let { indexEntries[it] })
                    val bitmap = decodeReportBitmap(uri) ?: return@forEachIndexed
                    if (includeOcr) {
                        val extractedText = try {
                            TextExtractor.extract(bitmap).joinToString("\n") { it.text.trim() }.trim()
                        } catch (_: Exception) {
                            ""
                        }
                        val appendixText = extractedText.ifBlank { metadata.recognizedText }.trim()
                        if (appendixText.isNotBlank()) {
                            appendix.add(metadata to appendixText)
                        }
                    }
                    pageNumber = drawReportImagePage(doc, pageNumber, index + 1, uris.size, bitmap, metadata)
                    imagePages++
                    bitmap.recycle()
                }

                if (imagePages == 0) {
                    withContext(Dispatchers.Main) {
                        batchProgress.value = ""
                        Toast.makeText(this@MainActivity, getString(R.string.report_no_images), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (includeOcr && appendix.isNotEmpty()) {
                    pageNumber = drawOcrAppendixPages(doc, pageNumber, appendix)
                }
                val displayName = "SnapCrop_Report_$createdAt.pdf"
                val pdfBytes = ByteArrayOutputStream().use { out ->
                    doc.writeTo(out)
                    out.toByteArray()
                }
                val saved = savePdfBytes(displayName, pdfBytes)
                val uploadResult = if (saved && uploadAfterSave) {
                    uploadReportArtifacts(displayName, pdfBytes, uris)
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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    batchProgress.value = ""
                    Toast.makeText(this@MainActivity, getString(R.string.report_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                doc.close()
            }
        }
    }

    private fun uploadReportArtifacts(
        displayName: String,
        pdfBytes: ByteArray,
        sourceUris: List<Uri>
    ): NetworkExportResult {
        val settings = NetworkExportSettings.fromPrefs(
            getSharedPreferences("snapcrop", MODE_PRIVATE),
            NetworkExportSettings.encryptedPrefs(this)
        )
        if (!settings.isConfigured) {
            return NetworkExportResult(false, settings.target, 0, "Network export is not configured")
        }
        if (settings.target != NetworkExportTarget.IMGUR) {
            return NetworkExportClient.uploadReportPdf(settings, displayName, pdfBytes)
        }

        var uploaded = 0
        var lastFailure: NetworkExportResult? = null
        sourceUris.forEachIndexed { index, uri ->
            val metadata = loadExportItemMetadata(uri)
            val mime = contentResolver.getType(uri) ?: "image/png"
            if (!mime.startsWith("image/")) return@forEachIndexed
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            } ?: return@forEachIndexed
            val name = metadata.displayName.ifBlank { "snapcrop_${index + 1}.png" }
            val result = NetworkExportClient.uploadImageToImgur(settings, name, mime, bytes)
            if (result.success) uploaded++ else lastFailure = result
        }
        return if (uploaded > 0) {
            NetworkExportResult(true, NetworkExportTarget.IMGUR, 200, "Imgur uploaded $uploaded image(s)")
        } else {
            lastFailure ?: NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "No images uploaded to Imgur")
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

    private fun drawReportCoverPage(
        doc: PdfDocument,
        pageNumber: Int,
        title: String,
        notes: String,
        itemCount: Int,
        createdAt: Long,
        includeOcr: Boolean
    ): Int {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = 110f
        y = drawWrappedText(canvas, title, PAGE_MARGIN, y, PDF_WIDTH - PAGE_MARGIN * 2, titlePaint(), 48f)
        y += 34f
        val summaryLines = listOf(
            getString(R.string.pdf_created, formatTimestamp(createdAt)),
            getString(R.string.pdf_image_count, itemCount),
            getString(if (includeOcr) R.string.pdf_ocr_enabled else R.string.pdf_ocr_off)
        ).joinToString("\n")
        y = drawWrappedText(
            canvas,
            summaryLines,
            PAGE_MARGIN,
            y,
            PDF_WIDTH - PAGE_MARGIN * 2,
            bodyPaint(),
            28f
        )
        if (notes.isNotBlank()) {
            y += 42f
            y = drawWrappedText(canvas, getString(R.string.pdf_notes), PAGE_MARGIN, y, PDF_WIDTH - PAGE_MARGIN * 2, sectionPaint(), 30f)
            y += 10f
            drawWrappedText(canvas, notes, PAGE_MARGIN, y, PDF_WIDTH - PAGE_MARGIN * 2, bodyPaint(), 26f, maxLines = 18)
        }
        drawPdfFooter(canvas, pageNumber)
        doc.finishPage(page)
        return pageNumber + 1
    }

    private fun drawReportImagePage(
        doc: PdfDocument,
        pageNumber: Int,
        itemNumber: Int,
        totalItems: Int,
        bitmap: android.graphics.Bitmap,
        metadata: ExportItemMetadata
    ): Int {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = 72f
        y = drawWrappedText(
            canvas,
            getString(R.string.pdf_image_of, itemNumber, totalItems),
            PAGE_MARGIN,
            y,
            PDF_WIDTH - PAGE_MARGIN * 2,
            sectionPaint(),
            32f
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
        y = drawWrappedText(canvas, metaText, PAGE_MARGIN, y + 8f, PDF_WIDTH - PAGE_MARGIN * 2, smallPaint(), 22f, maxLines = 7)
        val imageTop = y + 24f
        val imageBottom = PDF_HEIGHT - 120f
        val maxWidth = PDF_WIDTH - PAGE_MARGIN * 2
        val maxHeight = imageBottom - imageTop
        val scale = minOf(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = PAGE_MARGIN + (maxWidth - drawWidth) / 2f
        val top = imageTop + (maxHeight - drawHeight) / 2f
        val dst = RectF(left, top, left + drawWidth, top + drawHeight)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 218, 226)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawBitmap(bitmap, null, dst, null)
        canvas.drawRect(dst, border)
        drawPdfFooter(canvas, pageNumber)
        doc.finishPage(page)
        return pageNumber + 1
    }

    private fun drawOcrAppendixPages(
        doc: PdfDocument,
        startPageNumber: Int,
        entries: List<Pair<ExportItemMetadata, String>>
    ): Int {
        var pageNumber = startPageNumber
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = 76f
        val ocrAppendixLabel = getString(R.string.pdf_ocr_appendix)
        y = drawWrappedText(canvas, ocrAppendixLabel, PAGE_MARGIN, y, PDF_WIDTH - PAGE_MARGIN * 2, sectionPaint(), 34f)
        val textPaint = smallPaint()
        val maxWidth = PDF_WIDTH - PAGE_MARGIN * 2
        val bottom = PDF_HEIGHT - 120f

        fun nextPage() {
            drawPdfFooter(canvas, pageNumber)
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = 76f
            y = drawWrappedText(canvas, ocrAppendixLabel, PAGE_MARGIN, y, maxWidth, sectionPaint(), 34f)
        }

        entries.forEach { (metadata, text) ->
            val headingLines = wrapTextLines(metadata.displayName, sectionPaint(), maxWidth)
            if (y + headingLines.size * 30f > bottom) nextPage()
            headingLines.forEach { line ->
                canvas.drawText(line, PAGE_MARGIN, y, sectionPaint())
                y += 30f
            }
            val lines = wrapTextLines(text.take(4000), textPaint, maxWidth)
            lines.forEach { line ->
                if (y + 22f > bottom) nextPage()
                canvas.drawText(line, PAGE_MARGIN, y, textPaint)
                y += 22f
            }
            y += 22f
        }
        drawPdfFooter(canvas, pageNumber)
        doc.finishPage(page)
        return pageNumber + 1
    }

    private fun savePdfBytes(displayName: String, pdfBytes: ByteArray): Boolean {
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
            opened.use { it.write(pdfBytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
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
        for (line in wrapTextLines(text, paint, maxWidth)) {
            if (linesDrawn >= maxLines) break
            canvas.drawText(line, x, y, paint)
            y += lineHeight
            linesDrawn++
        }
        return y
    }

    private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.split(' ').filter { it.isNotBlank() }.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                    current = candidate
                } else {
                    lines.add(current)
                    current = word
                }
            }
            if (current.isNotBlank()) lines.add(current)
            if (paragraph.isBlank()) lines.add("")
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun drawPdfFooter(canvas: android.graphics.Canvas, pageNumber: Int) {
        val paint = smallPaint().apply { color = android.graphics.Color.rgb(92, 103, 115) }
        canvas.drawText(getString(R.string.pdf_footer), PAGE_MARGIN, PDF_HEIGHT - 54f, paint)
        canvas.drawText(getString(R.string.pdf_page, pageNumber), PDF_WIDTH - PAGE_MARGIN - 96f, PDF_HEIGHT - 54f, paint)
    }

    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 29, 39)
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun sectionPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(25, 94, 145)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(34, 44, 55)
        textSize = 21f
    }

    private fun smallPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(56, 68, 80)
        textSize = 16f
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
        val stripExif = getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("strip_exif", false)

        // Detect content types so mixed image/video selections aren't filtered to image-only handlers.
        val hasVideo = uris.any { uri ->
            try { contentResolver.getType(uri)?.startsWith("video/") == true } catch (_: Exception) { false }
        }
        val hasImage = uris.any { uri ->
            try { contentResolver.getType(uri)?.startsWith("image/") != false } catch (_: Exception) { true }
        }
        val shareType = when {
            hasVideo && hasImage -> "*/*"
            hasVideo -> "video/*"
            else -> "image/*"
        }

        if (stripExif && hasImage) {
            // Re-encode images to strip EXIF metadata. Videos pass through unchanged
            // (we don't transcode — that would be slow and lossy).
            lifecycleScope.launch(Dispatchers.IO) {
                val shareDir = java.io.File(cacheDir, "share_clean")
                shareDir.mkdirs()
                shareDir.listFiles()?.forEach { it.delete() } // clean old
                val (fmt, qual, ext) = getSaveFormat()
                val isWebp = fmt.isWebpFormat()
                val cleanUris = mutableListOf<Uri>()
                for ((i, uri) in uris.withIndex()) {
                    val mime = try { contentResolver.getType(uri) ?: "" } catch (_: Exception) { "" }
                    if (mime.startsWith("video/")) { cleanUris.add(uri); continue }
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bmp = android.graphics.BitmapFactory.decodeStream(stream) ?: return@use
                            val outExt = if (isWebp) "webp" else ext
                            val file = java.io.File(shareDir, "share_${i}.$outExt")
                            file.outputStream().use { out -> bmp.compress(fmt, qual, out) }
                            bmp.recycle()
                            cleanUris.add(androidx.core.content.FileProvider.getUriForFile(
                                this@MainActivity, "${packageName}.fileprovider", file))
                        }
                    } catch (_: Exception) { cleanUris.add(uri) } // fallback to original
                }
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = shareType
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(cleanUris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startShareChooser(intent)
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = shareType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startShareChooser(intent)
        }
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

    private fun requestDeleteUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: use scoped-storage delete confirmation instead of all-files access.
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                @Suppress("DEPRECATION")
                startIntentSenderForResult(pendingIntent.intentSender, 42, null, 0, 0, 0)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.toast_delete_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 10: direct delete (may throw RecoverableSecurityException)
            var count = 0
            for (uri in uris) {
                try { contentResolver.delete(uri, null, null); count++ } catch (_: Exception) {}
            }
            Toast.makeText(this, getString(R.string.toast_deleted_count, count), Toast.LENGTH_SHORT).show()
            galleryRefreshKey.intValue++
            loadRecentCrops()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) {
            galleryRefreshKey.intValue++
            loadRecentCrops()
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleService() {
        if (serviceRunning.value) {
            stopService(Intent(this, ScreenshotService::class.java))
            serviceRunning.value = false
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit()
                .putBoolean("auto_start", false).apply()
        } else {
            if (!hasPermissions.value) {
                requestPermissions()
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

    private fun refreshLongScreenshotState() {
        longScreenshotReady.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (ScrollCaptureService.isReady() || isScrollCaptureEnabled())
    }

    private fun requestLongScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
            return
        }

        if (!ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 2500L)) {
            showAccessibilityDisclosure.value = true
            return
        }

        moveTaskToBack(true)
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(
            this,
            getString(R.string.toast_enable_accessibility),
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
private fun HomeScreen(
    isRunning: Boolean,
    hasPermissions: Boolean,
    recentCrops: List<RecentCrop>,
    cropCount: Int,
    onToggleService: () -> Unit,
    onRequestPermissions: () -> Unit,
    onPickImage: () -> Unit,
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
    onOpenCrop: (Uri) -> Unit,
    onDeleteCrop: (Uri) -> Unit
) {
    var cropPendingDelete by remember { mutableStateOf<RecentCrop?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding()
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
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.home_settings), tint = OnSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Permission warning
        AnimatedVisibility(visible = !hasPermissions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_permission_title), color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.home_permission_body),
                            color = OnSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.home_permission_grant), color = OnPrimary)
                }
            }
        }

        // Overlay permission (optional on Android 12+; notifications remain the fallback)
        if (hasPermissions && !hasOverlayPermission && Build.VERSION.SDK_INT >= 31) {
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

        var showDelayPicker by remember { mutableStateOf(false) }
        HomeActionTile(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.home_crop_one_title),
            subtitle = stringResource(R.string.home_crop_one_subtitle),
            onClick = onPickImage
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
                icon = Icons.Default.MergeType,
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
                items(recentCrops) { crop ->
                    RecentCropTile(
                        crop = crop,
                        onOpen = { onOpenCrop(crop.uri) },
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
                    stringResource(R.string.home_delete_crop_body),
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
                    Text(stringResource(R.string.home_delete_crop_confirm))
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
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
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
                    maxLines = 1
                )
                Text(
                    subtitle,
                    color = OnSurfaceVariant.copy(alpha = contentAlpha),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2
                )
            }
        }
    }
}
