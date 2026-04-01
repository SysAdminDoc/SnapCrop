package com.sysadmindoc.snapcrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
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
                var deleteOriginal by remember { mutableStateOf(prefs.getBoolean("delete_original", false)) }
                var useJpeg by remember { mutableStateOf(prefs.getBoolean("use_jpeg", false)) }
                var useWebp by remember { mutableStateOf(prefs.getBoolean("use_webp", false)) }
                var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }
                var jpegQuality by remember { mutableIntStateOf(prefs.getInt("jpeg_quality", 95).coerceIn(50, 100)) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp)
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

                    // Image format selector
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Image Format", color = OnSurface, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("PNG is lossless, JPEG/WebP are smaller files", color = OnSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PNG" to false to false, "JPEG" to true to false, "WebP" to false to true).forEach { (pair, isWebp) ->
                                val (label, isJpeg) = pair
                                val selected = when {
                                    isWebp -> useWebp
                                    isJpeg -> useJpeg && !useWebp
                                    else -> !useJpeg && !useWebp
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        useJpeg = isJpeg; useWebp = isWebp
                                        prefs.edit().putBoolean("use_jpeg", isJpeg).putBoolean("use_webp", isWebp).apply()
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    if (useJpeg || useWebp) {
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

                    Spacer(Modifier.height(8.dp))

                    // Target file size
                    var targetSizeEnabled by remember { mutableStateOf(prefs.getBoolean("target_size_enabled", false)) }
                    var targetSizeKb by remember { mutableIntStateOf(prefs.getInt("target_size_kb", 500).coerceIn(50, 5000)) }
                    SettingToggle(
                        title = "Target file size",
                        subtitle = "Auto-adjust quality to meet a file size budget (JPEG/WebP only)",
                        checked = targetSizeEnabled,
                        onCheckedChange = {
                            targetSizeEnabled = it
                            prefs.edit().putBoolean("target_size_enabled", it).apply()
                        }
                    )
                    if (targetSizeEnabled && (useJpeg || useWebp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target: ${targetSizeKb}KB", color = OnSurfaceVariant, fontSize = 13.sp,
                                modifier = Modifier.width(90.dp))
                            Slider(
                                value = targetSizeKb.toFloat(),
                                onValueChange = {
                                    targetSizeKb = it.toInt()
                                    prefs.edit().putInt("target_size_kb", targetSizeKb).apply()
                                },
                                valueRange = 50f..5000f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    var stripExif by remember { mutableStateOf(prefs.getBoolean("strip_exif", false)) }
                    SettingToggle(
                        title = "Strip metadata on share",
                        subtitle = "Remove EXIF data (location, device info) when sharing for privacy",
                        checked = stripExif,
                        onCheckedChange = {
                            stripExif = it
                            prefs.edit().putBoolean("strip_exif", it).apply()
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Filename template
                    var filenameTemplate by remember {
                        mutableStateOf(prefs.getString("filename_template", "SnapCrop_%timestamp%") ?: "SnapCrop_%timestamp%")
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Filename template", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("Use %timestamp%, %date%, %time%, %counter%",
                                color = OnSurfaceVariant, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = filenameTemplate,
                                onValueChange = {
                                    val sanitized = it.replace(Regex("[<>:\"/\\\\|?*]"), "_")
                                    filenameTemplate = sanitized
                                    prefs.edit().putString("filename_template", sanitized).apply()
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                    focusedTextColor = OnSurface, unfocusedTextColor = OnSurface,
                                    cursorColor = Primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Watermark section
                    Text("Watermark", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    var watermarkEnabled by remember { mutableStateOf(prefs.getBoolean("watermark_enabled", false)) }
                    SettingToggle(
                        title = "Add watermark on save",
                        subtitle = "Stamp diagonal repeating text on saved images",
                        checked = watermarkEnabled,
                        onCheckedChange = { watermarkEnabled = it; prefs.edit().putBoolean("watermark_enabled", it).apply() }
                    )
                    if (watermarkEnabled) {
                        var watermarkText by remember {
                            mutableStateOf(prefs.getString("watermark_text", "SnapCrop") ?: "SnapCrop")
                        }
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Watermark text", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = {
                                        watermarkText = it
                                        prefs.edit().putString("watermark_text", it).apply()
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                        focusedTextColor = OnSurface, unfocusedTextColor = OnSurface,
                                        cursorColor = Primary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Border/padding on export
                    var borderSize by remember { mutableIntStateOf(prefs.getInt("border_size", 0)) }
                    var borderColorIdx by remember { mutableIntStateOf(prefs.getInt("border_color", 0)) }
                    val borderColors = listOf(
                        0xFF000000.toInt() to "Black", 0xFFFFFFFF.toInt() to "White",
                        0xFF1E1E2E.toInt() to "Dark", 0xFF89B4FA.toInt() to "Blue",
                        0xFFA6E3A1.toInt() to "Green", 0xFFF38BA8.toInt() to "Pink"
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Export border", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("Add padding around saved images", color = OnSurfaceVariant, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size: ${borderSize}px", color = OnSurfaceVariant, fontSize = 11.sp,
                                    modifier = Modifier.width(56.dp))
                                Slider(
                                    value = borderSize.toFloat(), onValueChange = {
                                        borderSize = it.toInt()
                                        prefs.edit().putInt("border_size", borderSize).apply()
                                    },
                                    valueRange = 0f..100f, modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant)
                                )
                            }
                            if (borderSize > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("Color:", color = OnSurfaceVariant, fontSize = 11.sp)
                                    borderColors.forEachIndexed { i, (color, _) ->
                                        Box(
                                            Modifier.size(24.dp)
                                                .background(androidx.compose.ui.graphics.Color(color), RoundedCornerShape(4.dp))
                                                .then(if (i == borderColorIdx) Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp)) else Modifier)
                                                .clickable { borderColorIdx = i; prefs.edit().putInt("border_color", i).apply() }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Save location
                    var savePath by remember {
                        mutableStateOf(prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Save location", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("Relative path in device storage", color = OnSurfaceVariant, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Pictures/SnapCrop", "DCIM/SnapCrop", "Downloads/SnapCrop").forEach { path ->
                                    FilterChip(selected = savePath == path,
                                        onClick = { savePath = path; prefs.edit().putString("save_path", path).apply() },
                                        label = { Text(path.substringBefore("/"), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                            containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp))
                                }
                            }
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
                            Thread { cacheDir.deleteRecursively() }.start()
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
                    Text("SnapCrop v${BuildConfig.VERSION_NAME}", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
