package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sysadmindoc.snapcrop.ui.theme.*

data class RecentCrop(val uri: Uri, val thumbBitmap: androidx.compose.ui.graphics.ImageBitmap)

class MainActivity : ComponentActivity() {

    private val serviceRunning = mutableStateOf(false)
    private val hasPermissions = mutableStateOf(false)
    private val hasOverlayPermission = mutableStateOf(false)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            SnapCropTheme {
                HomeScreen(
                    isRunning = serviceRunning.value,
                    hasPermissions = hasPermissions.value,
                    recentCrops = recentCrops.value,
                    cropCount = cropCount.value,
                    onToggleService = { toggleService() },
                    onRequestPermissions = { requestPermissions() },
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    hasOverlayPermission = hasOverlayPermission.value,
                    onRequestOverlay = {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ))
                    },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenCrop = { uri ->
                        startActivity(Intent(this, CropActivity::class.java).apply { data = uri })
                    },
                    onDeleteCrop = { uri ->
                        try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                        loadRecentCrops()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)

        val shouldRun = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getBoolean("auto_start", false)
        if (shouldRun && hasPermissions.value && !ScreenshotService.isRunning) {
            startMonitoring()
        }
        serviceRunning.value = ScreenshotService.isRunning || shouldRun

        if (hasPermissions.value) loadRecentCrops()
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
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
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(needed.toTypedArray())
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

    private fun loadRecentCrops() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crops = mutableListOf<RecentCrop>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("Pictures/SnapCrop%")
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
    hasOverlayPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCrop: (Uri) -> Unit,
    onDeleteCrop: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(48.dp))

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
                Text("v3.0.0", fontSize = 13.sp, color = OnSurfaceVariant)
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
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permissions Required", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(
                            "Media access needed to detect and edit screenshots",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp
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
                    Text("Grant Permissions", color = Color.Black)
                }
            }
        }

        // Overlay permission (needed on Android 12+ to open editor from background)
        if (hasPermissions && !hasOverlayPermission && Build.VERSION.SDK_INT >= 31) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Display Over Apps", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text("Required to open editor when screenshot is taken",
                            color = OnSurfaceVariant, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = onRequestOverlay,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Grant Permission", color = Color.Black) }
            }
        }

        // Service toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) PrimaryContainer else SurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Screenshot Monitor",
                        color = OnSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        if (isRunning) "Active — watching for screenshots" else "Tap to start monitoring",
                        color = OnSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
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

        Spacer(Modifier.height(16.dp))

        // Manual pick
        OutlinedButton(
            onClick = onPickImage,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface)
        ) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick Image to Crop")
        }

        // Stats
        if (cropCount > 0) {
            Spacer(Modifier.height(20.dp))
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
                    @OptIn(ExperimentalFoundationApi::class)
                    Image(
                        bitmap = crop.thumbBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp, 140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onOpenCrop(crop.uri) },
                                onLongClick = { onDeleteCrop(crop.uri) }
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Screenshots are detected automatically when the monitor is active.\nCropped images are saved to Pictures/SnapCrop.",
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(16.dp))
    }
}
