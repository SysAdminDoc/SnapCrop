package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val credPrefs = NetworkExportSettings.encryptedPrefs(this)
        NetworkExportSettings.migrateCredentials(prefs, credPrefs)
        val (screenW, screenH) = getScreenSize(this)

        setContent {
            SnapCropTheme {
                var deleteOriginal by remember { mutableStateOf(prefs.getBoolean("delete_original", true)) }
                var useJpeg by remember { mutableStateOf(prefs.getBoolean("use_jpeg", false)) }
                var useWebp by remember { mutableStateOf(prefs.getBoolean("use_webp", false)) }
                var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }
                var jpegQuality by remember { mutableIntStateOf(prefs.getInt("jpeg_quality", 95).coerceIn(50, 100)) }
                val outputFormat = when {
                    useWebp -> stringResource(R.string.format_webp)
                    useJpeg -> stringResource(R.string.format_jpeg)
                    else -> stringResource(R.string.format_png)
                }
                val lossyFormat = useJpeg || useWebp
                var userAppProfiles by remember { mutableStateOf(UserAppProfileStore.load(prefs)) }
                var appRuleImportText by remember { mutableStateOf("") }
                val testDefaultStr = stringResource(R.string.rules_test_default)
                val testTestingStr = stringResource(R.string.rules_test_testing)
                var appRuleTestStatus by remember { mutableStateOf(testDefaultStr) }
                val appRuleTestLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        appRuleTestStatus = testTestingStr
                        lifecycleScope.launch {
                            val message = testAppRuleImage(uri, prefs)
                            appRuleTestStatus = message
                            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(@Suppress("DEPRECATION") Icons.Default.ArrowBack, stringResource(R.string.back), tint = OnSurface)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_export_defaults), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (deleteOriginal) stringResource(R.string.settings_export_summary_replace, outputFormat)
                                else stringResource(R.string.settings_export_summary_keep, outputFormat),
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Appearance section
                    Text(stringResource(R.string.settings_section_appearance), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    var themePref by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_theme_title), color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "dark" to R.string.settings_theme_dark,
                                    "light" to R.string.settings_theme_light,
                                    "system" to R.string.settings_theme_system
                                ).forEach { (key, labelRes) ->
                                    FilterChip(
                                        selected = themePref == key,
                                        onClick = {
                                            themePref = key
                                            prefs.edit().putString("theme", key).apply()
                                            isDarkTheme = when (key) {
                                                "light" -> false
                                                "system" -> resources.configuration.uiMode and
                                                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                                                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                                                else -> true
                                            }
                                        },
                                        label = { Text(stringResource(labelRes), fontSize = 13.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryContainer,
                                            selectedLabelColor = Primary,
                                            containerColor = SurfaceContainer,
                                            labelColor = OnSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Save behavior section
                    Text(stringResource(R.string.settings_section_save), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    SettingToggle(
                        title = stringResource(R.string.settings_replace_title),
                        subtitle = stringResource(R.string.settings_replace_subtitle),
                        checked = deleteOriginal,
                        onCheckedChange = {
                            deleteOriginal = it
                            prefs.edit().putBoolean("delete_original", it).apply()
                        }
                    )

                    var projectSidecars by remember { mutableStateOf(prefs.getBoolean("project_sidecars", true)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_sidecar_title),
                        subtitle = stringResource(R.string.settings_sidecar_subtitle),
                        checked = projectSidecars,
                        onCheckedChange = {
                            projectSidecars = it
                            prefs.edit().putBoolean("project_sidecars", it).apply()
                        }
                    )

                    var appCropProfiles by remember { mutableStateOf(prefs.getBoolean("app_crop_profiles", true)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_profiles_title),
                        subtitle = stringResource(R.string.settings_profiles_subtitle),
                        checked = appCropProfiles,
                        onCheckedChange = {
                            appCropProfiles = it
                            prefs.edit().putBoolean("app_crop_profiles", it).apply()
                        }
                    )

                    AppRulesPanel(
                        enabled = appCropProfiles,
                        builtIns = AppCropProfiles.builtInProfiles(),
                        profiles = userAppProfiles,
                        importText = appRuleImportText,
                        testStatus = appRuleTestStatus,
                        onSaveProfile = { profile ->
                            userAppProfiles = UserAppProfileStore.upsert(userAppProfiles, profile)
                            UserAppProfileStore.save(prefs, userAppProfiles)
                            Toast.makeText(this@SettingsActivity, getString(R.string.toast_app_rule_saved), Toast.LENGTH_SHORT).show()
                        },
                        onToggleProfile = { profile ->
                            userAppProfiles = UserAppProfileStore.upsert(
                                userAppProfiles,
                                profile.copy(enabled = !profile.enabled)
                            )
                            UserAppProfileStore.save(prefs, userAppProfiles)
                        },
                        onDeleteProfile = { profile ->
                            userAppProfiles = userAppProfiles.filterNot { it.id == profile.id }
                            UserAppProfileStore.save(prefs, userAppProfiles)
                            Toast.makeText(this@SettingsActivity, getString(R.string.toast_app_rule_removed), Toast.LENGTH_SHORT).show()
                        },
                        onImportTextChange = { appRuleImportText = it },
                        onCopyProfiles = {
                            copyAppProfilePack(userAppProfiles)
                        },
                        onImportProfiles = {
                            runCatching {
                                val incoming = UserAppProfileStore.decode(appRuleImportText)
                                require(incoming.isNotEmpty()) { "No profiles found" }
                                userAppProfiles = UserAppProfileStore.merge(userAppProfiles, incoming)
                                UserAppProfileStore.save(prefs, userAppProfiles)
                                appRuleImportText = ""
                                Toast.makeText(this@SettingsActivity, getString(R.string.toast_imported_rules, incoming.size), Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    this@SettingsActivity,
                                    getString(R.string.toast_import_failed, error.message ?: "invalid JSON"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onPickTestImage = { appRuleTestLauncher.launch("image/*") }
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(R.string.settings_section_intelligence), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    var screenshotIndexEnabled by remember {
                        mutableStateOf(prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false))
                    }
                    val indexCountStr = stringResource(R.string.settings_index_count, ScreenshotIndexStore(this@SettingsActivity).count())
                    val indexEnabledStr = stringResource(R.string.settings_index_enabled)
                    val indexDisabledStr = stringResource(R.string.settings_index_disabled)
                    var screenshotIndexStatus by remember { mutableStateOf(indexCountStr) }
                    SettingToggle(
                        title = stringResource(R.string.settings_index_title),
                        subtitle = stringResource(R.string.settings_index_subtitle),
                        checked = screenshotIndexEnabled,
                        onCheckedChange = {
                            screenshotIndexEnabled = it
                            prefs.edit().putBoolean(ScreenshotIndexStore.PREF_ENABLED, it).apply()
                            screenshotIndexStatus = if (it) indexEnabledStr else indexDisabledStr
                        }
                    )
                    if (screenshotIndexEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.settings_index_controls), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(
                                    screenshotIndexStatus,
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                val rebuildingStr = stringResource(R.string.settings_index_rebuilding)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            screenshotIndexStatus = rebuildingStr
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val count = ScreenshotIndexStore(this@SettingsActivity)
                                                    .rebuildFromMediaStore(
                                                        contentResolver,
                                                        screenW,
                                                        screenH,
                                                        FavoritesStore.getAllIds(this@SettingsActivity)
                                                    )
                                                withContext(Dispatchers.Main) {
                                                    screenshotIndexStatus = getString(R.string.settings_index_count, count)
                                                    android.widget.Toast.makeText(
                                                        this@SettingsActivity,
                                                        getString(R.string.toast_index_rebuilt),
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.settings_rebuild))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            ScreenshotIndexStore(this@SettingsActivity).purge()
                                            screenshotIndexStatus = getString(R.string.settings_index_count, 0)
                                            android.widget.Toast.makeText(
                                                this@SettingsActivity,
                                                getString(R.string.toast_index_purged),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.settings_purge), color = Tertiary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(R.string.settings_section_erase), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    var allowAdvancedErase by remember {
                        mutableStateOf(prefs.getBoolean(AdvancedEraseBackendRegistry.PREF_ALLOW_EXPERIMENTAL, false))
                    }
                    SettingToggle(
                        title = stringResource(R.string.settings_erase_title),
                        subtitle = AdvancedEraseBackendRegistry.statusSummary(prefs),
                        checked = allowAdvancedErase,
                        onCheckedChange = {
                            allowAdvancedErase = it
                            prefs.edit()
                                .putBoolean(AdvancedEraseBackendRegistry.PREF_ALLOW_EXPERIMENTAL, it)
                                .putString(AdvancedEraseBackendRegistry.PREF_SELECTED_BACKEND, EraseBackendId.LOCAL_SMART_ERASE.prefValue)
                                .apply()
                        }
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_erase_readiness), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            AdvancedEraseBackendRegistry.candidates.forEach { candidate ->
                                Text(candidate.id.label, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${candidate.runtime} • ${candidate.notes}",
                                    color = OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Image format selector
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stringResource(R.string.settings_section_format), color = OnSurface, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.settings_format_hint), color = OnSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(stringResource(R.string.format_png), false, false),
                                Triple(stringResource(R.string.format_jpeg), true, false),
                                Triple(stringResource(R.string.format_webp), false, true)
                            ).forEach { (label, isJpeg, isWebp) ->
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
                            Text(stringResource(R.string.settings_quality, jpegQuality), color = OnSurfaceVariant, fontSize = 13.sp,
                                modifier = Modifier.width(80.dp))
                            Slider(
                                value = jpegQuality.toFloat(),
                                onValueChange = {
                                    jpegQuality = it.toInt()
                                    prefs.edit().putInt("jpeg_quality", jpegQuality).apply()
                                },
                                valueRange = 50f..100f,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = getString(R.string.settings_quality_cd, jpegQuality) },
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
                        title = stringResource(R.string.settings_target_size_title),
                        subtitle = if (lossyFormat)
                            stringResource(R.string.settings_target_size_lossy)
                        else
                            stringResource(R.string.settings_target_size_unavailable),
                        checked = targetSizeEnabled && lossyFormat,
                        enabled = lossyFormat,
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
                            Text(stringResource(R.string.settings_target_kb, targetSizeKb), color = OnSurfaceVariant, fontSize = 13.sp,
                                modifier = Modifier.width(90.dp))
                            Slider(
                                value = targetSizeKb.toFloat(),
                                onValueChange = {
                                    targetSizeKb = it.toInt()
                                    prefs.edit().putInt("target_size_kb", targetSizeKb).apply()
                                },
                                valueRange = 50f..5000f,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = getString(R.string.settings_target_kb_cd, targetSizeKb) },
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    var stripExif by remember { mutableStateOf(prefs.getBoolean("strip_exif", false)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_strip_title),
                        subtitle = stringResource(R.string.settings_strip_subtitle),
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
                            Text(stringResource(R.string.settings_filename_title), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_filename_hint),
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

                    // Annotation presets section
                    val stylePresets = remember { mutableStateListOf<DrawStylePreset>().apply { addAll(DrawStylePresetStore.load(prefs)) } }
                    var styleDefault by remember { mutableStateOf(DrawStylePresetStore.defaultName(prefs)) }
                    Text(stringResource(R.string.settings_section_presets), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_presets_body), color = OnSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    if (stylePresets.isEmpty()) {
                        Text(stringResource(R.string.settings_presets_empty), color = OnSurfaceVariant, fontSize = 11.sp)
                    } else {
                        stylePresets.forEachIndexed { idx, preset ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {
                                contentDescription = "${preset.name} preset: ${preset.tool.label}, ${preset.strokeWidth.toInt()} pixels${if (preset.dashed) ", dashed" else ""}${if (styleDefault == preset.name) ", default" else ""}"
                            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(16.dp).background(androidx.compose.ui.graphics.Color(preset.color), RoundedCornerShape(3.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name, color = OnSurface, fontSize = 13.sp, fontWeight = if (styleDefault == preset.name) FontWeight.Bold else FontWeight.Normal)
                                    Text("${preset.tool.label}, ${preset.strokeWidth.toInt()}px${if (preset.dashed) ", dashed" else ""}", color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    styleDefault = if (styleDefault == preset.name) null else preset.name
                                    DrawStylePresetStore.setDefault(prefs, styleDefault)
                                }) { Text(if (styleDefault == preset.name) stringResource(R.string.settings_preset_default) else stringResource(R.string.settings_preset_set_default), color = if (styleDefault == preset.name) Secondary else OnSurfaceVariant, fontSize = 11.sp) }
                                IconButton(onClick = {
                                    stylePresets.removeAt(idx)
                                    DrawStylePresetStore.save(prefs, stylePresets.toList())
                                    if (styleDefault == preset.name) { styleDefault = null; DrawStylePresetStore.setDefault(prefs, null) }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete) + " ${preset.name}", tint = Tertiary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Network export section
                    var networkExportsEnabled by remember {
                        mutableStateOf(prefs.getBoolean(NetworkExportSettings.PREF_ENABLED, false))
                    }
                    var networkTarget by remember {
                        mutableStateOf(NetworkExportTarget.fromPref(prefs.getString(NetworkExportSettings.PREF_TARGET, NetworkExportTarget.HTTP.prefValue)))
                    }
                    var networkEndpoint by remember {
                        mutableStateOf(prefs.getString(NetworkExportSettings.PREF_ENDPOINT, "") ?: "")
                    }
                    var networkAuthorization by remember {
                        mutableStateOf(credPrefs.getString(NetworkExportSettings.PREF_AUTHORIZATION, "") ?: "")
                    }
                    var imgurClientId by remember {
                        mutableStateOf(credPrefs.getString(NetworkExportSettings.PREF_IMGUR_CLIENT_ID, "") ?: "")
                    }
                    Text(stringResource(R.string.settings_section_network), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    SettingToggle(
                        title = stringResource(R.string.settings_network_title),
                        subtitle = stringResource(R.string.settings_network_subtitle),
                        checked = networkExportsEnabled,
                        onCheckedChange = {
                            networkExportsEnabled = it
                            prefs.edit().putBoolean(NetworkExportSettings.PREF_ENABLED, it).apply()
                        }
                    )
                    if (networkExportsEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.settings_upload_target), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NetworkExportTarget.entries.forEach { target ->
                                        FilterChip(
                                            selected = networkTarget == target,
                                            onClick = {
                                                networkTarget = target
                                                prefs.edit().putString(NetworkExportSettings.PREF_TARGET, target.prefValue).apply()
                                            },
                                            label = { Text(target.label, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer,
                                                selectedLabelColor = Primary,
                                                containerColor = Surface,
                                                labelColor = OnSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                                if (networkTarget == NetworkExportTarget.IMGUR) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = imgurClientId,
                                        onValueChange = {
                                            imgurClientId = it.trim()
                                            credPrefs.edit().putString(NetworkExportSettings.PREF_IMGUR_CLIENT_ID, imgurClientId).apply()
                                        },
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.settings_imgur_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = Outline,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface,
                                            cursorColor = Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Text(
                                        stringResource(R.string.settings_imgur_hint),
                                        color = OnSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = networkEndpoint,
                                        onValueChange = {
                                            networkEndpoint = it.trim()
                                            prefs.edit().putString(NetworkExportSettings.PREF_ENDPOINT, networkEndpoint).apply()
                                        },
                                        singleLine = true,
                                        label = { Text(if (networkTarget == NetworkExportTarget.WEBDAV) stringResource(R.string.settings_webdav_label) else stringResource(R.string.settings_http_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = Outline,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface,
                                            cursorColor = Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = networkAuthorization,
                                        onValueChange = {
                                            networkAuthorization = it
                                            credPrefs.edit().putString(NetworkExportSettings.PREF_AUTHORIZATION, it).apply()
                                        },
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.settings_auth_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = Outline,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface,
                                            cursorColor = Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Text(
                                        stringResource(R.string.settings_auth_hint),
                                        color = OnSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Watermark section
                    Text(stringResource(R.string.settings_section_watermark), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    var watermarkEnabled by remember { mutableStateOf(prefs.getBoolean("watermark_enabled", false)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_watermark_title),
                        subtitle = stringResource(R.string.settings_watermark_subtitle),
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
                                Text(stringResource(R.string.settings_watermark_text), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
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
                            Text(stringResource(R.string.settings_section_border), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_border_hint), color = OnSurfaceVariant, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_border_size, borderSize), color = OnSurfaceVariant, fontSize = 11.sp,
                                    modifier = Modifier.width(56.dp))
                                Slider(
                                    value = borderSize.toFloat(), onValueChange = {
                                        borderSize = it.toInt()
                                        prefs.edit().putInt("border_size", borderSize).apply()
                                    },
                                    valueRange = 0f..100f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { contentDescription = getString(R.string.settings_border_size_cd, borderSize) },
                                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant)
                                )
                            }
                            if (borderSize > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.settings_border_color), color = OnSurfaceVariant, fontSize = 11.sp)
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
                            Text(stringResource(R.string.settings_section_location), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_location_hint), color = OnSurfaceVariant, fontSize = 11.sp)
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
                    Text(stringResource(R.string.settings_section_service), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    SettingToggle(
                        title = stringResource(R.string.settings_autostart_title),
                        subtitle = stringResource(R.string.settings_autostart_subtitle),
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            prefs.edit().putBoolean("auto_start", it).apply()
                        }
                    )

                    var conditionalAutoActions by remember {
                        mutableStateOf(prefs.getBoolean("conditional_auto_actions", false))
                    }
                    SettingToggle(
                        title = stringResource(R.string.settings_autoactions_title),
                        subtitle = stringResource(R.string.settings_autoactions_subtitle),
                        checked = conditionalAutoActions,
                        onCheckedChange = {
                            conditionalAutoActions = it
                            prefs.edit().putBoolean("conditional_auto_actions", it).apply()
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Storage section
                    Text(stringResource(R.string.settings_section_storage), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                cacheDir.deleteRecursively()
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        this@SettingsActivity,
                                        getString(R.string.toast_temp_cleared),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_clear_cache), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(stringResource(R.string.settings_clear_cache_subtitle), color = OnSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // About
                    Text(stringResource(R.string.settings_section_about), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_about_tagline),
                        color = OnSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_about_url),
                        color = Primary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    var evalResult by remember { mutableStateOf<String?>(null) }
                    var evalRunning by remember { mutableStateOf(false) }
                    FilledTonalButton(
                        onClick = {
                            if (!evalRunning) {
                                evalRunning = true
                                lifecycleScope.launch(Dispatchers.Default) {
                                    val report = EvalHarness.runAll()
                                    val text = report.summary()
                                    withContext(Dispatchers.Main) {
                                        evalResult = text
                                        evalRunning = false
                                    }
                                }
                            }
                        },
                        enabled = !evalRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceVariant,
                            contentColor = OnSurface
                        )
                    ) {
                        Text(
                            if (evalRunning) stringResource(R.string.eval_running)
                            else stringResource(R.string.eval_run),
                            fontSize = 13.sp
                        )
                    }
                    evalResult?.let { text ->
                        Spacer(Modifier.height(8.dp))
                        Text(text, color = OnSurfaceVariant, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 15.sp)
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    private fun copyAppProfilePack(profiles: List<UserAppCropProfile>) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("SnapCrop app rules", UserAppProfileStore.encode(profiles))
        )
        Toast.makeText(this, getString(R.string.toast_profile_copied), Toast.LENGTH_SHORT).show()
    }

    private suspend fun testAppRuleImage(uri: Uri, prefs: SharedPreferences): String =
        withContext(Dispatchers.IO) {
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext getString(R.string.rules_test_open_failed)
            try {
                val userProfiles = UserAppProfileStore.load(prefs)
                val sourceHints = CropSourceHints.normalize(
                    CropSourceHints.fromMedia(contentResolver, uri) + listOf(uri.toString())
                )
                val profileTextHints = if (UserAppProfileStore.needsOcr(userProfiles)) {
                    TextExtractor.extract(bitmap).map { it.text }
                } else {
                    emptyList()
                }
                val preview = AutoCrop.explainProfileMatch(
                    bitmap = bitmap,
                    statusBarPx = SystemBars.statusBarHeight(resources),
                    navBarPx = SystemBars.navigationBarHeight(resources),
                    sourceHints = sourceHints,
                    profileTextHints = profileTextHints,
                    userProfiles = userProfiles,
                    appProfilesEnabled = prefs.getBoolean("app_crop_profiles", true)
                )
                preview?.let {
                    getString(R.string.rules_test_matched, it.label, (it.confidence * 100).roundToInt(), it.reason)
                } ?: getString(R.string.rules_test_no_match)
            } finally {
                bitmap.recycle()
            }
        }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) SurfaceVariant else SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    color = if (enabled) OnSurface else OnSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                Text(subtitle, color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimary,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = OnSurfaceVariant,
                    uncheckedTrackColor = SurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun AppRulesPanel(
    enabled: Boolean,
    builtIns: List<BuiltInAppCropProfileInfo>,
    profiles: List<UserAppCropProfile>,
    importText: String,
    testStatus: String,
    onSaveProfile: (UserAppCropProfile) -> Unit,
    onToggleProfile: (UserAppCropProfile) -> Unit,
    onDeleteProfile: (UserAppCropProfile) -> Unit,
    onImportTextChange: (String) -> Unit,
    onCopyProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
    onPickTestImage: () -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var sourceHints by remember { mutableStateOf("") }
    var ocrKeywords by remember { mutableStateOf("") }
    var albumName by remember { mutableStateOf("") }
    var topCrop by remember { mutableFloatStateOf(0.07f) }
    var bottomCrop by remember { mutableFloatStateOf(0.07f) }
    var leftCrop by remember { mutableFloatStateOf(0f) }
    var rightCrop by remember { mutableFloatStateOf(0f) }
    var redactSensitiveText by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("default") }

    val sourceTokens = UserAppProfileStore.parseTokenList(sourceHints)
    val ocrTokens = UserAppProfileStore.parseTokenList(ocrKeywords)
    val canSave = ruleName.isNotBlank() && (sourceTokens.isNotEmpty() || ocrTokens.isNotEmpty())

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.rules_title), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                if (enabled) {
                    stringResource(R.string.rules_body_enabled)
                } else {
                    stringResource(R.string.rules_body_disabled)
                },
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rules_builtin), color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            builtIns.forEach { BuiltInRuleRow(it) }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rules_user_title), color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            if (profiles.isEmpty()) {
                Text(
                    stringResource(R.string.rules_user_empty),
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                profiles.forEach { profile ->
                    UserRuleRow(
                        profile = profile,
                        onToggle = { onToggleProfile(profile) },
                        onDelete = { onDeleteProfile(profile) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rules_create), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text(stringResource(R.string.rules_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = appRuleTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sourceHints,
                onValueChange = { sourceHints = it },
                label = { Text(stringResource(R.string.rules_source_label)) },
                placeholder = { Text(stringResource(R.string.rules_source_placeholder)) },
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = appRuleTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ocrKeywords,
                onValueChange = { ocrKeywords = it },
                label = { Text(stringResource(R.string.rules_ocr_label)) },
                placeholder = { Text(stringResource(R.string.rules_ocr_placeholder)) },
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = appRuleTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                label = { Text(stringResource(R.string.rules_album_label)) },
                placeholder = { Text(ruleName.ifBlank { "Custom" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = appRuleTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.rules_crop_preview, formatPercent(leftCrop), formatPercent(topCrop), formatPercent(rightCrop), formatPercent(bottomCrop)),
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            CropBandSlider("Top", topCrop) { topCrop = it }
            CropBandSlider("Bottom", bottomCrop) { bottomCrop = it }
            CropBandSlider("Left", leftCrop) { leftCrop = it }
            CropBandSlider("Right", rightCrop) { rightCrop = it }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.rules_redact_title), color = OnSurface, fontSize = 13.sp)
                    Text(
                        stringResource(R.string.rules_redact_subtitle),
                        color = OnSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = redactSensitiveText,
                    onCheckedChange = { redactSensitiveText = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnPrimary,
                        checkedTrackColor = Primary,
                        uncheckedThumbColor = OnSurfaceVariant,
                        uncheckedTrackColor = SurfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.rules_export_format), color = OnSurface, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormatChip(stringResource(R.string.format_default), "default", exportFormat) { exportFormat = it }
                    ExportFormatChip(stringResource(R.string.format_png), "png", exportFormat) { exportFormat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormatChip(stringResource(R.string.format_jpeg), "jpeg", exportFormat) { exportFormat = it }
                    ExportFormatChip(stringResource(R.string.format_webp), "webp", exportFormat) { exportFormat = it }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSaveProfile(
                        UserAppProfileStore.create(
                            label = ruleName,
                            sourceHints = sourceTokens,
                            ocrKeywords = ocrTokens,
                            cropLeftFraction = leftCrop,
                            cropTopFraction = topCrop,
                            cropRightFraction = rightCrop,
                            cropBottomFraction = bottomCrop,
                            albumName = albumName,
                            redactSensitiveText = redactSensitiveText,
                            exportFormat = exportFormat
                        )
                    )
                    ruleName = ""
                    sourceHints = ""
                    ocrKeywords = ""
                    albumName = ""
                    topCrop = 0.07f
                    bottomCrop = 0.07f
                    leftCrop = 0f
                    rightCrop = 0f
                    redactSensitiveText = false
                    exportFormat = "default"
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.rules_save))
            }
            if (!canSave) {
                Text(
                    stringResource(R.string.rules_save_hint),
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.rules_packs_title), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.rules_packs_body),
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = importText,
                onValueChange = onImportTextChange,
                label = { Text(stringResource(R.string.rules_packs_paste)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = appRuleTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyProfiles, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.rules_copy_pack), color = Primary)
                }
                Button(
                    onClick = onImportProfiles,
                    enabled = importText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.import_label))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.rules_test_title), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(testStatus, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickTestImage, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.rules_test_choose), color = Primary)
            }
        }
    }
}

@Composable
private fun BuiltInRuleRow(profile: BuiltInAppCropProfileInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(profile.label, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.rules_builtin_badge), color = Primary, fontSize = 11.sp)
        }
        Text(
            stringResource(R.string.rules_hints, profile.aliases.joinToString(", ")),
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(profile.cropPreview, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        Text(profile.confidencePreview, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        Text(profile.automationSummary, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun UserRuleRow(
    profile: UserAppCropProfile,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(profile.label, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.rules_crop, profile.cropSummary()),
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Switch(
                checked = profile.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimary,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = OnSurfaceVariant,
                    uncheckedTrackColor = SurfaceVariant
                )
            )
        }
        Text(
            stringResource(R.string.rules_sources, profile.sourceHints.ifEmpty { listOf(stringResource(R.string.rules_none)) }.joinToString(", ")),
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.rules_ocr, profile.ocrKeywords.ifEmpty { listOf(stringResource(R.string.rules_none)) }.joinToString(", ")),
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(
                R.string.rules_quick_crop,
                profile.albumName,
                if (profile.redactSensitiveText) stringResource(R.string.rules_redact) else stringResource(R.string.rules_no_redaction),
                profile.normalizedExportFormat()
            ),
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(8.dp)) {
            Text(stringResource(R.string.delete), color = Tertiary)
        }
    }
}

@Composable
private fun CropBandSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label ${formatPercent(value)}",
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.width(76.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..0.35f,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$label crop percentage, ${formatPercent(value)}" },
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = SurfaceContainer
            )
        )
    }
}

@Composable
private fun ExportFormatChip(
    label: String,
    value: String,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onSelected(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryContainer,
            selectedLabelColor = Primary,
            containerColor = SurfaceVariant,
            labelColor = OnSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun appRuleTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Primary,
        unfocusedBorderColor = Outline,
        focusedTextColor = OnSurface,
        unfocusedTextColor = OnSurface,
        cursorColor = Primary
    )

private fun formatPercent(value: Float): String =
    "${(value * 100).roundToInt()}%"
