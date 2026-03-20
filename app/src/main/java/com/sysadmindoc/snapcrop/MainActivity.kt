package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sysadmindoc.snapcrop.ui.theme.*

class MainActivity : ComponentActivity() {

    private val serviceRunning = mutableStateOf(false)
    private val hasPermissions = mutableStateOf(false)
    private val hasOverlayPermission = mutableStateOf(false)

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

        hasOverlayPermission.value = Settings.canDrawOverlays(this)

        setContent {
            SnapCropTheme {
                HomeScreen(
                    isRunning = serviceRunning.value,
                    hasPermissions = hasPermissions.value,
                    hasOverlayPermission = hasOverlayPermission.value,
                    onToggleService = { toggleService() },
                    onRequestPermissions = { requestPermissions() },
                    onRequestOverlay = { requestOverlayPermission() },
                    onPickImage = { pickImageLauncher.launch("image/*") }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
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
        } else {
            if (hasPermissions.value) {
                startMonitoring()
            } else {
                requestPermissions()
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startMonitoring() {
        val intent = Intent(this, ScreenshotService::class.java)
        ContextCompat.startForegroundService(this, intent)
        serviceRunning.value = true
    }
}

@Composable
private fun HomeScreen(
    isRunning: Boolean,
    hasPermissions: Boolean,
    hasOverlayPermission: Boolean,
    onToggleService: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onPickImage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Icon(
            Icons.Default.CropOriginal,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "SnapCrop",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )

        Text(
            "v2.1.0",
            fontSize = 13.sp,
            color = OnSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Auto-crop screenshots instantly",
            fontSize = 15.sp,
            color = OnSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

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

        // Overlay permission warning
        AnimatedVisibility(visible = hasPermissions && !hasOverlayPermission) {
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
                    Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overlay Permission", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(
                            "Required to show screenshot thumbnail preview",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                Button(
                    onClick = onRequestOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Overlay Permission", color = Color.Black)
                }
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

        Spacer(Modifier.weight(1f))

        Text(
            "Screenshots are detected automatically when the monitor is active.\nCropped images are saved to Pictures/SnapCrop.",
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
