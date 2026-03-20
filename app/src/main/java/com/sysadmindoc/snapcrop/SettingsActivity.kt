package com.sysadmindoc.snapcrop

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.*

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)

        setContent {
            SnapCropTheme {
                var deleteOriginal by remember { mutableStateOf(prefs.getBoolean("delete_original", true)) }
                var useJpeg by remember { mutableStateOf(prefs.getBoolean("use_jpeg", false)) }
                var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }
                var jpegQuality by remember { mutableIntStateOf(prefs.getInt("jpeg_quality", 95)) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp)
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(@Suppress("DEPRECATION") Icons.Default.ArrowBack, "Back", tint = OnSurface)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Save behavior section
                    Text("Save Behavior", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    SettingToggle(
                        title = "Delete original after crop",
                        subtitle = "Remove the original screenshot when using Crop & Save",
                        checked = deleteOriginal,
                        onCheckedChange = {
                            deleteOriginal = it
                            prefs.edit().putBoolean("delete_original", it).apply()
                        }
                    )

                    SettingToggle(
                        title = "Save as JPEG",
                        subtitle = "Smaller file size. PNG is used when off (lossless)",
                        checked = useJpeg,
                        onCheckedChange = {
                            useJpeg = it
                            prefs.edit().putBoolean("use_jpeg", it).apply()
                        }
                    )

                    if (useJpeg) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quality: ${jpegQuality}%", color = OnSurfaceVariant, fontSize = 13.sp,
                                modifier = Modifier.width(80.dp))
                            Slider(
                                value = jpegQuality.toFloat(),
                                onValueChange = {
                                    jpegQuality = it.toInt()
                                    prefs.edit().putInt("jpeg_quality", jpegQuality).apply()
                                },
                                valueRange = 50f..100f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = SurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Service section
                    Text("Service", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    SettingToggle(
                        title = "Start on boot",
                        subtitle = "Resume screenshot monitor after device restart",
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            prefs.edit().putBoolean("auto_start", it).apply()
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Storage section
                    Text("Storage", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            cacheDir.deleteRecursively()
                            android.widget.Toast.makeText(this@SettingsActivity,
                                "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Clear cache", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Remove temporary share/clipboard files", color = OnSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // About
                    Text("About", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("SnapCrop v4.4.0", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Auto-crop, annotate, and redact screenshots instantly.",
                        color = OnSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("github.com/SysAdminDoc/SnapCrop",
                        color = Primary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(subtitle, color = OnSurfaceVariant, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = OnSurfaceVariant,
                    uncheckedTrackColor = SurfaceVariant
                )
            )
        }
    }
}
