package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sysadmindoc.snapcrop.BuildConfig
import com.sysadmindoc.snapcrop.ui.theme.*

data class RecentCrop(val uri: Uri, val thumbBitmap: androidx.compose.ui.graphics.ImageBitmap)

class MainActivity : ComponentActivity() {

    private val serviceRunning = mutableStateOf(false)
    private val hasPermissions = mutableStateOf(false)
    private val hasOverlayPermission = mutableStateOf(false)
    private val hasFileManagePermission = mutableStateOf(false)
    private val longScreenshotReady = mutableStateOf(false)
    private val galleryRefreshKey = mutableIntStateOf(0)
    private val recentCrops = mutableStateOf<List<RecentCrop>>(emptyList())
    private val cropCount = mutableStateOf(0)

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

    private fun batchAutocrop(uris: List<Uri>) {
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            val statusBarPx = SystemBars.statusBarHeight(resources)
            val navBarPx = SystemBars.navigationBarHeight(resources)
            var done = 0; var failed = 0
            val total = uris.size

            for (uri in uris) {
                if (batchCancelled.value) break
                withContext(Dispatchers.Main) {
                    batchProgress.value = "Cropping ${done + 1}/$total..."
                    batchProgressFraction.floatValue = done.toFloat() / total
                }
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream) ?: return@use
                        val cropRect = AutoCrop.detect(
                            bitmap = bitmap,
                            statusBarPx = statusBarPx,
                            navBarPx = navBarPx,
                            appProfilesEnabled = getSharedPreferences("snapcrop", MODE_PRIVATE)
                                .getBoolean("app_crop_profiles", true)
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
                    append("Cropped ${done - failed}/$total")
                    if (failed > 0) append(" ($failed failed)")
                    if (cancelled) append(" (stopped)")
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                loadRecentCrops()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            SnapCropTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        NavigationBar(containerColor = SurfaceVariant) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, "Home") },
                                label = { Text("Home") },
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
                                icon = { Icon(Icons.Default.Photo, "Gallery") },
                                label = { Text("Gallery") },
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
                                    Toast.makeText(this@MainActivity, "Capturing in ${seconds}s...", Toast.LENGTH_SHORT).show()
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
                                hasFileManagePermission = hasFileManagePermission.value,
                                onRequestFileManage = {
                                    try {
                                        startActivity(Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        ))
                                    } catch (_: Exception) {
                                        try {
                                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                        } catch (_: Exception) {
                                            Toast.makeText(this@MainActivity, "Open Settings > Apps > SnapCrop > Permissions to grant file access", Toast.LENGTH_LONG).show()
                                        }
                                    }
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
                                onExportPdf = { uris -> exportPdf(uris) },
                                onBatchResize = { uris -> showResizeDialog(uris) },
                                onBack = { selectedTab = 0 }
                            )
                        }
                    }
                }

                // Resize dialog
                if (showResizeDialogState.value) {
                    var selectedSize by remember { mutableIntStateOf(1080) }
                    val sizes = listOf(480, 720, 1080, 1440, 2160)
                    AlertDialog(
                        onDismissRequest = { showResizeDialogState.value = false },
                        title = { Text("Batch Resize", color = OnSurface) },
                        text = {
                            Column {
                                Text("Max dimension (px):", color = OnSurfaceVariant, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sizes.forEach { size ->
                                        FilterChip(
                                            selected = selectedSize == size,
                                            onClick = { selectedSize = size },
                                            label = { Text("$size", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("${resizeUris.value.size} images will be resized", color = OnSurfaceVariant, fontSize = 12.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showResizeDialogState.value = false
                                batchResize(resizeUris.value, selectedSize)
                            }) { Text("Resize", color = Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResizeDialogState.value = false }) { Text("Cancel", color = OnSurfaceVariant) }
                        },
                        containerColor = SurfaceVariant
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        hasFileManagePermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager() else true
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

    private fun batchResize(uris: List<Uri>, maxDim: Int) {
        batchCancelled.value = false
        lifecycleScope.launch(Dispatchers.IO) {
            var done = 0; var failed = 0
            for (uri in uris) {
                if (batchCancelled.value) break
                withContext(Dispatchers.Main) {
                    batchProgress.value = "Resizing ${done + 1}/${uris.size}..."
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
                    append("Resized ${done - failed}/${uris.size} to ${maxDim}px")
                    if (failed > 0) append(" ($failed failed)")
                    if (cancelled) append(" (stopped)")
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                galleryRefreshKey.intValue++
            }
        }
    }

    private fun exportPdf(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val doc = PdfDocument()
            try {
                var pageNum = 1
                val maxPdfDim = 2048 // Limit page dimensions to prevent OOM on large images
                for (uri in uris) {
                    var bmp = contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: continue
                    // Downscale large images to prevent OOM
                    if (bmp.width > maxPdfDim || bmp.height > maxPdfDim) {
                        val scale = maxPdfDim.toFloat() / maxOf(bmp.width, bmp.height)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                        bmp.recycle()
                        bmp = scaled
                    }
                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageNum).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    doc.finishPage(page)
                    bmp.recycle()
                    pageNum++
                }
                if (pageNum == 1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No images to export", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "SnapCrop_${System.currentTimeMillis()}.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SnapCrop")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                var pdfUri: Uri? = null
                try {
                    pdfUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    if (pdfUri != null) {
                        val pdfOs = contentResolver.openOutputStream(pdfUri)
                        if (pdfOs != null) {
                            pdfOs.use { doc.writeTo(it) }
                        } else {
                            contentResolver.delete(pdfUri, null, null)
                            pdfUri = null
                            throw Exception("Failed to open output stream for PDF")
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(pdfUri!!, values, null, null)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "PDF saved to Documents/SnapCrop", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    pdfUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "PDF export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "PDF export failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                doc.close()
            }
        }
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
                @Suppress("DEPRECATION")
                val isWebp = fmt == android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                        || fmt == android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                        || fmt == android.graphics.Bitmap.CompressFormat.WEBP
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
                    startActivity(Intent.createChooser(intent, null))
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = shareType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        }
    }

    private fun requestDeleteUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // MANAGE_EXTERNAL_STORAGE grants direct delete without system confirmation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            lifecycleScope.launch(Dispatchers.IO) {
                var count = 0
                for (uri in uris) {
                    try { contentResolver.delete(uri, null, null); count++ } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Deleted $count photos", Toast.LENGTH_SHORT).show()
                    galleryRefreshKey.intValue++
                    loadRecentCrops()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ without MANAGE_EXTERNAL_STORAGE: system delete confirmation dialog
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                @Suppress("DEPRECATION")
                startIntentSenderForResult(pendingIntent.intentSender, 42, null, 0, 0, 0)
            } catch (e: Exception) {
                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 10: direct delete (may throw RecoverableSecurityException)
            var count = 0
            for (uri in uris) {
                try { contentResolver.delete(uri, null, null); count++ } catch (_: Exception) {}
            }
            Toast.makeText(this, "Deleted $count photos", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
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
            // Android 12+ needs SYSTEM_ALERT_WINDOW to launch activities from services
            if (Build.VERSION.SDK_INT >= 31 && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
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
            Toast.makeText(this, "Long screenshot requires Android 11 or newer", Toast.LENGTH_LONG).show()
            return
        }

        if (!ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 2500L)) {
            openAccessibilitySettings()
            return
        }

        moveTaskToBack(true)
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(
            this,
            "Enable SnapCrop Long screenshot, then add its Quick Settings tile.",
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
    hasFileManagePermission: Boolean,
    onRequestFileManage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCrop: (Uri) -> Unit,
    onDeleteCrop: (Uri) -> Unit
) {
    var cropPendingDelete by remember { mutableStateOf<RecentCrop?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                Text("SnapCrop", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    "Clean, redact, and export screenshots - v${BuildConfig.VERSION_NAME}",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = OnSurfaceVariant)
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
                        Text("Media permissions required", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(
                            "Allow SnapCrop to detect screenshots, show notifications, and open images for editing.",
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
                    Text("Grant media access", color = Color.Black)
                }
            }
        }

        // Overlay permission (needed on Android 12+ to open editor from background)
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
                        Text("Open editor from screenshots", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text("Display-over-apps lets the crop editor appear immediately after a screenshot.",
                            color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Button(
                    onClick = onRequestOverlay,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Grant display access", color = Color.Black) }
            }
        }

        // File management permission (needed on Android 11+ to delete without prompts)
        if (hasPermissions && !hasFileManagePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Seamless cleanup", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text("All-files access removes the source screenshot after Save without Android asking each time.",
                            color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Button(
                    onClick = onRequestFileManage,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Grant file access", color = Color.Black) }
            }
        }

        // Service toggle
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
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Screenshot Monitor",
                        color = OnSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        if (isRunning) "Active - new screenshots open in the editor" else "Paused - turn on to catch screenshots automatically",
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
                        if (isRunning) "Active" else "Paused",
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
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Primary,
                        uncheckedThumbColor = OnSurfaceVariant,
                        uncheckedTrackColor = SurfaceVariant
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Workflows", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                        ) { Text("Cancel batch", fontSize = 12.sp) }
                    }
                }
            }
        }

        var showDelayPicker by remember { mutableStateOf(false) }
        HomeActionTile(
            icon = Icons.Default.PhotoLibrary,
            title = "Crop one image",
            subtitle = "Open the editor with auto-crop, redaction, OCR, and export tools.",
            onClick = onPickImage
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.Default.BurstMode,
                title = "Batch autocrop",
                subtitle = "Clean several screenshots at once.",
                enabled = batchProgress.isEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onBatchCrop
            )
            HomeActionTile(
                icon = Icons.Default.Timer,
                title = "Delayed capture",
                subtitle = "Start a timed screenshot.",
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
                    Text("Choose delay", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("SnapCrop waits, then opens the next screenshot it detects.",
                        color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { sec ->
                            FilledTonalButton(
                                onClick = { onDelayedCapture(sec); showDelayPicker = false },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer)
                            ) { Text("${sec}s", color = Primary, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        HomeActionTile(
            icon = Icons.Default.ScreenshotMonitor,
            title = "Long screenshot",
            subtitle = if (longScreenshotReady) {
                "Scroll, stitch, save, and open the result automatically."
            } else {
                "Enable Accessibility once, then capture from Quick Settings."
            },
            onClick = onLongScreenshot
        )

        Spacer(Modifier.height(8.dp))

        // Stitch + Collage row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.Default.MergeType,
                title = "Stitch",
                subtitle = "Join images vertically or horizontally.",
                modifier = Modifier.weight(1f),
                onClick = onStitch
            )
            HomeActionTile(
                icon = Icons.Default.GridView,
                title = "Collage",
                subtitle = "Build a clean multi-image layout.",
                modifier = Modifier.weight(1f),
                onClick = onCollage
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeActionTile(
                icon = Icons.Default.PhoneAndroid,
                title = "Device mockup",
                subtitle = "Wrap a screenshot in a polished phone frame.",
                modifier = Modifier.weight(1f),
                onClick = onDeviceFrame
            )
            HomeActionTile(
                icon = Icons.Default.PlayCircle,
                title = "Video frame",
                subtitle = "Trim recordings or grab a frame to edit.",
                modifier = Modifier.weight(1f),
                onClick = onVideoClip
            )
        }

        // Stats
        if (cropCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "$cropCount screenshots cropped",
                color = OnSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // Recent crops gallery
        if (recentCrops.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Recent",
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
            "Screenshots are detected automatically when the monitor is active. Exports follow your format and location settings.",
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
            title = { Text("Delete recent crop?", color = OnSurface) },
            text = {
                Text(
                    "This removes the exported crop from your media library. The source screenshot is not touched.",
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
                    Text("Delete crop")
                }
            },
            dismissButton = {
                TextButton(onClick = { cropPendingDelete = null }) {
                    Text("Cancel", color = OnSurfaceVariant)
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
            contentDescription = "Open recent crop",
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
                contentDescription = "Delete recent crop",
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
