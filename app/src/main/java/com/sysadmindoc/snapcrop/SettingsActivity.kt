package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val initialCredentialStore = NetworkCredentialStore.open(this)
        val (screenW, screenH) = getScreenSize(this)

        setContent {
            SnapCropTheme {
                var credentialStore by remember { mutableStateOf(initialCredentialStore) }
                var deleteOriginal by remember { mutableStateOf(prefs.getBoolean("delete_original", true)) }
                var useJpeg by remember { mutableStateOf(prefs.getBoolean("use_jpeg", false)) }
                var useWebp by remember { mutableStateOf(prefs.getBoolean("use_webp", false)) }
                var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }
                var jpegQuality by remember { mutableIntStateOf(prefs.getInt("jpeg_quality", 95).coerceIn(50, 100)) }
                var exportPresets by remember { mutableStateOf(ExportPresetStore.load(prefs)) }
                var exportPresetName by remember { mutableStateOf("") }
                var editorPresetId by remember {
                    mutableStateOf(prefs.getString(ExportPresetStore.PREF_EDITOR_PRESET_ID, null))
                }
                var quickPresetId by remember {
                    mutableStateOf(prefs.getString(ExportPresetStore.PREF_QUICK_PRESET_ID, null))
                }
                val outputFormat = when {
                    useWebp -> stringResource(R.string.format_webp)
                    useJpeg -> stringResource(R.string.format_jpeg)
                    else -> stringResource(R.string.format_png)
                }
                val lossyFormat = useJpeg || useWebp
                var userAppProfiles by remember { mutableStateOf(UserAppProfileStore.load(prefs)) }
                val initialCustomPatterns = remember {
                    runCatching { CustomRedactionPatternStore.load(prefs) }
                }
                var customPatterns by remember {
                    mutableStateOf(initialCustomPatterns.getOrDefault(emptyList()))
                }
                var customPatternsCorrupt by remember {
                    mutableStateOf(initialCustomPatterns.isFailure)
                }
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
                val backupExportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri -> if (uri != null) exportSettings(uri, prefs) }
                val backupImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) importSettings(uri, prefs) }
                var pendingRuleDelete by remember { mutableStateOf<UserAppCropProfile?>(null) }
                var showHelp by remember { mutableStateOf(false) }
                val recentWorkflowPrefs = remember {
                    getSharedPreferences(RecentWorkflowStore.PREF_NAME, MODE_PRIVATE)
                }
                var recentWorkflowsEnabled by remember {
                    mutableStateOf(RecentWorkflowStore.isEnabled(recentWorkflowPrefs))
                }

                if (showHelp) {
                    LocalHelpDialog(
                        onOpenRoute = { route ->
                            if (route != HelpRoute.SETTINGS_PROJECTS) {
                                startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                            }
                        },
                        onDismiss = { showHelp = false }
                    )
                }

                pendingRuleDelete?.let { profile ->
                    AlertDialog(
                        onDismissRequest = { pendingRuleDelete = null },
                        title = { Text(stringResource(R.string.rules_delete_title), color = OnSurface) },
                        text = {
                            Text(
                                stringResource(R.string.rules_delete_body, profile.label),
                                color = OnSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                userAppProfiles = userAppProfiles.filterNot { it.id == profile.id }
                                UserAppProfileStore.save(prefs, userAppProfiles)
                                pendingRuleDelete = null
                                Toast.makeText(
                                    this@SettingsActivity,
                                    getString(R.string.toast_app_rule_removed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Text(stringResource(R.string.rules_delete_confirm), color = Danger)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingRuleDelete = null }) {
                                Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                            }
                        },
                        containerColor = SurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Black, Surface.copy(alpha = 0.72f), Black)
                            )
                        )
                        .safeDrawingPadding()
                        .imePadding()
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
                        Text(
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = OnSurface
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.help_content_description), tint = OnSurface)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.settings_export_defaults),
                                color = OnSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (deleteOriginal) stringResource(R.string.settings_export_summary_replace, outputFormat)
                                else stringResource(R.string.settings_export_summary_keep, outputFormat),
                                color = OnSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Appearance section
                    SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
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

                    SettingToggle(
                        title = stringResource(R.string.settings_recent_workflows_title),
                        subtitle = stringResource(R.string.settings_recent_workflows_subtitle),
                        checked = recentWorkflowsEnabled,
                        onCheckedChange = {
                            recentWorkflowsEnabled = it
                            RecentWorkflowStore.setEnabled(recentWorkflowPrefs, it)
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Save behavior section
                    SettingsSectionHeader(stringResource(R.string.settings_section_save))
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

                    var projectSidecars by remember { mutableStateOf(prefs.getBoolean("project_sidecars", false)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_sidecar_title),
                        subtitle = stringResource(R.string.settings_sidecar_subtitle),
                        checked = projectSidecars,
                        onCheckedChange = {
                            projectSidecars = it
                            prefs.edit().putBoolean("project_sidecars", it).apply()
                        }
                    )

                    var ocrTextSidecars by remember { mutableStateOf(prefs.getBoolean("ocr_text_sidecars", false)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_ocr_text_sidecar_title),
                        subtitle = stringResource(R.string.settings_ocr_text_sidecar_subtitle),
                        checked = ocrTextSidecars,
                        onCheckedChange = {
                            ocrTextSidecars = it
                            prefs.edit().putBoolean("ocr_text_sidecars", it).apply()
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
                            pendingRuleDelete = profile
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

                    SettingsSectionHeader(stringResource(R.string.settings_section_intelligence))
                    Spacer(Modifier.height(8.dp))

                    var screenshotIndexEnabled by remember {
                        mutableStateOf(prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false))
                    }
                    val screenshotIndexStore = remember { ScreenshotIndexStore(this@SettingsActivity) }
                    val indexEnabledStr = stringResource(R.string.settings_index_enabled)
                    val indexDisabledStr = stringResource(R.string.settings_index_disabled)
                    var screenshotIndexStatus by remember {
                        mutableStateOf(getString(R.string.settings_index_loading))
                    }
                    LaunchedEffect(screenshotIndexStore) {
                        screenshotIndexStatus = try {
                            getString(R.string.settings_index_count, screenshotIndexStore.count())
                        } catch (error: Exception) {
                            IndexHealthStore.markFailure(this@SettingsActivity)
                            OperationJournal.record(
                                this@SettingsActivity, DiagnosticOperation.INDEX,
                                DiagnosticStage.OBSERVE, DiagnosticResult.FAILED,
                                code = DiagnosticCode.INTERNAL, error = error,
                            )
                            getString(R.string.settings_index_failed)
                        }
                    }
                    SettingToggle(
                        title = stringResource(R.string.settings_index_title),
                        subtitle = stringResource(R.string.settings_index_subtitle),
                        checked = screenshotIndexEnabled,
                        onCheckedChange = {
                            screenshotIndexEnabled = it
                            prefs.edit().putBoolean(ScreenshotIndexStore.PREF_ENABLED, it).apply()
                            IndexWorker.sync(this@SettingsActivity, it)
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
                                            lifecycleScope.launch {
                                                val journalStarted = OperationJournal.start()
                                                IndexHealthStore.markStarted(this@SettingsActivity)
                                                try {
                                                    val count = screenshotIndexStore.rebuildFromMediaStore(
                                                        contentResolver,
                                                        screenW,
                                                        screenH,
                                                        FavoritesStore.getAllKeys(this@SettingsActivity)
                                                    )
                                                    IndexHealthStore.markSuccess(this@SettingsActivity, count)
                                                    screenshotIndexStatus = getString(R.string.settings_index_count, count)
                                                    OperationJournal.record(
                                                        this@SettingsActivity, DiagnosticOperation.INDEX,
                                                        DiagnosticStage.COMPLETE, DiagnosticResult.SUCCESS, journalStarted,
                                                    )
                                                    android.widget.Toast.makeText(
                                                        this@SettingsActivity,
                                                        getString(R.string.toast_index_rebuilt),
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                } catch (error: Exception) {
                                                    IndexHealthStore.markFailure(this@SettingsActivity)
                                                    screenshotIndexStatus = getString(R.string.settings_index_failed)
                                                    OperationJournal.record(
                                                        this@SettingsActivity, DiagnosticOperation.INDEX,
                                                        DiagnosticStage.OBSERVE, DiagnosticResult.FAILED, journalStarted,
                                                        if (error is SecurityException) DiagnosticCode.PERMISSION_DENIED else DiagnosticCode.INTERNAL,
                                                        error,
                                                    )
                                                    android.widget.Toast.makeText(
                                                        this@SettingsActivity,
                                                        getString(R.string.settings_index_failed),
                                                        android.widget.Toast.LENGTH_LONG,
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
                                            lifecycleScope.launch {
                                                try {
                                                    screenshotIndexStore.purge()
                                                    IndexHealthStore.clear(this@SettingsActivity)
                                                    screenshotIndexStatus = getString(R.string.settings_index_count, 0)
                                                    android.widget.Toast.makeText(
                                                        this@SettingsActivity,
                                                        getString(R.string.toast_index_purged),
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                } catch (error: Exception) {
                                                    IndexHealthStore.markFailure(this@SettingsActivity)
                                                    screenshotIndexStatus = getString(R.string.settings_index_failed)
                                                    OperationJournal.record(
                                                        this@SettingsActivity, DiagnosticOperation.INDEX,
                                                        DiagnosticStage.OBSERVE, DiagnosticResult.FAILED,
                                                        code = DiagnosticCode.INTERNAL, error = error,
                                                    )
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.settings_purge), color = Danger)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    SettingsSectionHeader(stringResource(R.string.settings_section_erase))
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
                    var targetSizeAllowResize by remember {
                        mutableStateOf(prefs.getBoolean("target_size_allow_resize", false))
                    }
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
                        SettingToggle(
                            title = stringResource(R.string.settings_target_resize_title),
                            subtitle = stringResource(R.string.settings_target_resize_subtitle),
                            checked = targetSizeAllowResize,
                            onCheckedChange = {
                                targetSizeAllowResize = it
                                prefs.edit().putBoolean("target_size_allow_resize", it).apply()
                            },
                        )
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

                    var ocrScript by remember { mutableStateOf(OcrScript.fromKey(prefs.getString(OcrScript.PREF_KEY, null))) }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_ocr_script_title), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_ocr_script_subtitle), color = OnSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                OcrScript.entries.forEach { script ->
                                    FilterChip(
                                        selected = ocrScript == script,
                                        onClick = {
                                            ocrScript = script
                                            prefs.edit().putString(OcrScript.PREF_KEY, script.key).apply()
                                        },
                                        label = { Text(script.label, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

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
                    SettingsSectionHeader(stringResource(R.string.settings_section_presets))
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
                                Box(Modifier.size(16.dp)
                                    .background(androidx.compose.ui.graphics.Color(preset.color), RoundedCornerShape(3.dp))
                                    .border(1.dp, Outline, RoundedCornerShape(3.dp)))
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
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete) + " ${preset.name}", tint = Danger, modifier = Modifier.size(16.dp))
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
                        mutableStateOf(credentialStore.getString(NetworkExportSettings.PREF_AUTHORIZATION))
                    }
                    var imgurClientId by remember {
                        mutableStateOf(credentialStore.getString(NetworkExportSettings.PREF_IMGUR_CLIENT_ID))
                    }
                    var credentialStatus by remember { mutableStateOf(credentialStore.status) }
                    var localNetworkPermissionGranted by remember {
                        mutableStateOf(
                            Build.VERSION.SDK_INT < LocalNetworkEndpointPolicy.ANDROID_17_API ||
                                checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        )
                    }
                    var localNetworkPermissionRequested by remember {
                        mutableStateOf(
                            prefs.getBoolean(AndroidLocalNetworkAccess.PREF_PERMISSION_REQUESTED, false)
                        )
                    }
                    DisposableEffect(Unit) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                localNetworkPermissionGranted =
                                    Build.VERSION.SDK_INT < LocalNetworkEndpointPolicy.ANDROID_17_API ||
                                    checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                localNetworkPermissionRequested = prefs.getBoolean(
                                    AndroidLocalNetworkAccess.PREF_PERMISSION_REQUESTED,
                                    false,
                                )
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose { lifecycle.removeObserver(observer) }
                    }
                    LaunchedEffect(credentialStore, networkAuthorization) {
                        if (!credentialStatus.isUsable) return@LaunchedEffect
                        delay(500)
                        val saved = withContext(Dispatchers.IO) {
                            credentialStore.putString(NetworkExportSettings.PREF_AUTHORIZATION, networkAuthorization)
                        }
                        credentialStatus = credentialStore.status
                        if (!saved && credentialStatus == NetworkCredentialStore.Status.RECOVERY_REQUIRED) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.settings_credentials_save_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                    LaunchedEffect(credentialStore, imgurClientId) {
                        if (!credentialStatus.isUsable) return@LaunchedEffect
                        delay(500)
                        val saved = withContext(Dispatchers.IO) {
                            credentialStore.putString(NetworkExportSettings.PREF_IMGUR_CLIENT_ID, imgurClientId)
                        }
                        credentialStatus = credentialStore.status
                        if (!saved && credentialStatus == NetworkCredentialStore.Status.RECOVERY_REQUIRED) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.settings_credentials_save_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                    SettingsSectionHeader(stringResource(R.string.settings_section_network))
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
                    if (credentialStatus == NetworkCredentialStore.Status.MIGRATED) {
                        Text(
                            stringResource(R.string.settings_credentials_migrated),
                            color = OnSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (credentialStatus == NetworkCredentialStore.Status.RECOVERY_REQUIRED) {
                        Text(
                            stringResource(R.string.settings_credentials_recovery),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
                                        },
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.settings_auth_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                                    if (Build.VERSION.SDK_INT >= LocalNetworkEndpointPolicy.ANDROID_17_API) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = Outline.copy(alpha = 0.45f),
                                        )
                                        Text(
                                            stringResource(R.string.settings_local_network_title),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            stringResource(R.string.settings_local_network_body),
                                            color = OnSurfaceVariant,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        when {
                                            localNetworkPermissionGranted -> Text(
                                                stringResource(R.string.settings_local_network_ready),
                                                color = Success,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 8.dp),
                                            )
                                            localNetworkPermissionRequested -> OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        startActivity(
                                                            Intent(
                                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                                Uri.parse("package:$packageName"),
                                                            )
                                                        )
                                                    }.onFailure {
                                                        Toast.makeText(
                                                            this@SettingsActivity,
                                                            getString(R.string.local_network_settings_open_failed),
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 8.dp),
                                            ) {
                                                Text(stringResource(R.string.settings_local_network_open_settings))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (credentialStatus == NetworkCredentialStore.Status.RECOVERY_REQUIRED ||
                        networkAuthorization.isNotBlank() || imgurClientId.isNotBlank()
                    ) {
                        OutlinedButton(
                            onClick = {
                                lifecycleScope.launch {
                                    val reopened = withContext(Dispatchers.IO) {
                                        NetworkCredentialStore.reset(this@SettingsActivity)
                                    }
                                    credentialStore = reopened
                                    credentialStatus = reopened.status
                                    networkAuthorization = ""
                                    imgurClientId = ""
                                    networkExportsEnabled = false
                                    prefs.edit().putBoolean(NetworkExportSettings.PREF_ENABLED, false).apply()
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        getString(R.string.settings_credentials_reset_done),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_credentials_reset))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Watermark section
                    SettingsSectionHeader(stringResource(R.string.settings_section_watermark))
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
                                    borderColors.forEachIndexed { i, (color, name) ->
                                        Box(
                                            Modifier.size(48.dp)
                                                .semantics {
                                                    contentDescription = "$name border color"
                                                    selected = i == borderColorIdx
                                                    role = Role.RadioButton
                                                }
                                                .clickable(role = Role.RadioButton) {
                                                    borderColorIdx = i
                                                    prefs.edit().putInt("border_color", i).apply()
                                                }
                                        ) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.Center)
                                                    .size(24.dp)
                                                    .background(androidx.compose.ui.graphics.Color(color), RoundedCornerShape(4.dp))
                                                    .then(if (i == borderColorIdx) Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp)) else Modifier)
                                            )
                                        }
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

                    Spacer(Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_export_presets_title), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_export_presets_hint), color = OnSurfaceVariant, fontSize = 11.sp)
                            OutlinedTextField(
                                value = exportPresetName,
                                onValueChange = { exportPresetName = it.take(40) },
                                label = { Text(stringResource(R.string.settings_export_preset_name)) },
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
                            Button(
                                onClick = {
                                    val updated = ExportPresetStore.upsertCurrent(prefs, exportPresetName)
                                    if (updated == null) {
                                        Toast.makeText(this@SettingsActivity, R.string.settings_export_preset_invalid, Toast.LENGTH_SHORT).show()
                                    } else {
                                        exportPresets = updated
                                        exportPresetName = ""
                                    }
                                },
                                enabled = exportPresetName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) { Text(stringResource(R.string.settings_export_preset_save), color = OnPrimary) }

                            exportPresets.forEach { preset ->
                                Surface(color = SurfaceElevated, shape = RoundedCornerShape(8.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(preset.name, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            stringResource(
                                                R.string.settings_export_preset_summary,
                                                preset.settings.format.key.uppercase(),
                                                preset.settings.quality,
                                                preset.settings.savePath,
                                                preset.settings.borderSize,
                                                if (preset.settings.watermarkEnabled) stringResource(R.string.settings_export_preset_on) else stringResource(R.string.settings_export_preset_off)
                                            ),
                                            color = OnSurfaceVariant,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            FilterChip(
                                                selected = editorPresetId == preset.id,
                                                onClick = {
                                                    editorPresetId = if (editorPresetId == preset.id) null else preset.id
                                                    prefs.edit().apply {
                                                        editorPresetId?.let { putString(ExportPresetStore.PREF_EDITOR_PRESET_ID, it) }
                                                            ?: remove(ExportPresetStore.PREF_EDITOR_PRESET_ID)
                                                    }.apply()
                                                },
                                                label = { Text(stringResource(R.string.settings_export_preset_editor), fontSize = 10.sp) }
                                            )
                                            FilterChip(
                                                selected = quickPresetId == preset.id,
                                                onClick = {
                                                    quickPresetId = if (quickPresetId == preset.id) null else preset.id
                                                    prefs.edit().apply {
                                                        quickPresetId?.let { putString(ExportPresetStore.PREF_QUICK_PRESET_ID, it) }
                                                            ?: remove(ExportPresetStore.PREF_QUICK_PRESET_ID)
                                                    }.apply()
                                                },
                                                label = { Text(stringResource(R.string.settings_export_preset_quick), fontSize = 10.sp) }
                                            )
                                            TextButton(onClick = {
                                                ExportPresetStore.applyToCurrent(prefs, preset)
                                                recreate()
                                            }) { Text(stringResource(R.string.settings_export_preset_apply), color = Primary) }
                                            TextButton(onClick = {
                                                exportPresets = ExportPresetStore.upsertCurrent(prefs, preset.name, preset.id) ?: exportPresets
                                            }) { Text(stringResource(R.string.settings_export_preset_update), color = Secondary) }
                                            TextButton(onClick = {
                                                exportPresets = ExportPresetStore.delete(prefs, preset.id)
                                                if (editorPresetId == preset.id) editorPresetId = null
                                                if (quickPresetId == preset.id) quickPresetId = null
                                            }) { Text(stringResource(R.string.delete), color = Danger) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Service section
                    SettingsSectionHeader(stringResource(R.string.settings_section_service))
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

                    var redactOnShare by remember {
                        mutableStateOf(prefs.getBoolean("redact_on_share", true))
                    }
                    SettingToggle(
                        title = stringResource(R.string.settings_redact_share_title),
                        subtitle = stringResource(R.string.settings_redact_share_subtitle),
                        checked = redactOnShare,
                        onCheckedChange = {
                            redactOnShare = it
                            prefs.edit().putBoolean("redact_on_share", it).apply()
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    CustomRedactionPatternsPanel(
                        patterns = customPatterns,
                        storageError = customPatternsCorrupt,
                        onSavePatterns = { updated ->
                            val saved = CustomRedactionPatternStore.save(prefs, updated)
                            if (saved) {
                                customPatterns = updated
                                customPatternsCorrupt = false
                            }
                            saved
                        },
                        onExport = { serialized ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    getString(R.string.settings_custom_patterns_title),
                                    serialized,
                                )
                            )
                            Toast.makeText(
                                this@SettingsActivity,
                                R.string.settings_custom_patterns_copied,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )

                    var redactionStyle by remember {
                        mutableStateOf(RedactionStyle.fromPreference(
                            prefs.getString(ImageRedactor.PREF_REDACTION_STYLE, RedactionStyle.SOLID.preferenceValue)
                        ))
                    }
                    Text(
                        stringResource(R.string.settings_redaction_style_title),
                        color = OnSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.settings_redaction_style_subtitle),
                        color = OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RedactionStyle.entries.forEach { style ->
                            FilterChip(
                                selected = redactionStyle == style,
                                onClick = {
                                    redactionStyle = style
                                    prefs.edit().putString(ImageRedactor.PREF_REDACTION_STYLE, style.preferenceValue).apply()
                                },
                                label = {
                                    Text(stringResource(when (style) {
                                        RedactionStyle.SOLID -> R.string.redaction_style_solid
                                        RedactionStyle.PIXELATE -> R.string.redaction_style_pixelate
                                        RedactionStyle.BLUR -> R.string.redaction_style_blur
                                    }))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.22f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }

                    var secureEditor by remember {
                        mutableStateOf(prefs.getBoolean(SecurePreviewPolicy.PREF_ENABLED, false))
                    }
                    SettingToggle(
                        title = stringResource(R.string.settings_secure_editor_title),
                        subtitle = stringResource(R.string.settings_secure_editor_subtitle),
                        checked = secureEditor,
                        onCheckedChange = {
                            secureEditor = it
                            prefs.edit().putBoolean(SecurePreviewPolicy.PREF_ENABLED, it).apply()
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Storage section
                    SettingsSectionHeader(stringResource(R.string.settings_section_storage))
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

                    // Settings backup / restore — survive reinstall (allowBackup=false).
                    SettingsSectionHeader(stringResource(R.string.settings_section_backup))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_backup_hint), color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilledTonalButton(
                            onClick = { backupExportLauncher.launch("snapcrop-settings.json") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer, contentColor = Primary)
                        ) { Text(stringResource(R.string.settings_backup_export), fontSize = 13.sp) }
                        FilledTonalButton(
                            onClick = { backupImportLauncher.launch(arrayOf("application/json")) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant, contentColor = OnSurface)
                        ) { Text(stringResource(R.string.settings_backup_import), fontSize = 13.sp) }
                    }

                    Spacer(Modifier.height(20.dp))

                    // About
                    SettingsSectionHeader(stringResource(R.string.settings_section_about))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_about_tagline),
                        color = OnSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_about_url),
                        color = Primary, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/SnapCrop"))) } catch (_: Exception) {}
                        })
                    Spacer(Modifier.height(12.dp))

                    // Update check — anonymous, opt-in, nothing sent off-device but a version query.
                    var updateChecking by remember { mutableStateOf(false) }
                    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                    var updateOutcome by remember { mutableStateOf<UpdateChecker.Result?>(null) }
                    val checkingLabel = stringResource(R.string.settings_update_checking)
                    FilledTonalButton(
                        onClick = {
                            if (!updateChecking) {
                                updateChecking = true
                                updateOutcome = null
                                lifecycleScope.launch {
                                    val r = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                    updateInfo = (r as? UpdateChecker.Result.Available)?.info
                                    updateOutcome = r
                                    updateChecking = false
                                }
                            }
                        },
                        enabled = !updateChecking,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer, contentColor = Primary)
                    ) {
                        Text(if (updateChecking) checkingLabel else stringResource(R.string.settings_update_check), fontSize = 13.sp)
                    }
                    when (updateOutcome) {
                        is UpdateChecker.Result.UpToDate -> {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.settings_update_current), color = Secondary, fontSize = 12.sp)
                        }
                        is UpdateChecker.Result.Failed -> {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.settings_update_failed), color = Danger, fontSize = 12.sp)
                        }
                        else -> {}
                    }
                    var autoUpdate by remember { mutableStateOf(prefs.getBoolean(UpdateChecker.PREF_AUTO, false)) }
                    SettingToggle(
                        title = stringResource(R.string.settings_update_auto_title),
                        subtitle = stringResource(R.string.settings_update_auto_subtitle),
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            prefs.edit().putBoolean(UpdateChecker.PREF_AUTO, it).apply()
                        }
                    )
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
                                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                    Text(
                                        info.notes.ifBlank { stringResource(R.string.settings_update_body, info.versionName) },
                                        color = OnSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                    info.apkUrl?.let {
                                        Spacer(Modifier.height(8.dp))
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

                    var journalEnabled by remember { mutableStateOf(OperationJournal.isEnabled(this@SettingsActivity)) }
                    var journalEvents by remember { mutableStateOf(OperationJournal.events(this@SettingsActivity)) }
                    var journalViewText by remember { mutableStateOf<String?>(null) }
                    Spacer(Modifier.height(12.dp))
                    SettingToggle(
                        title = stringResource(R.string.settings_journal_title),
                        subtitle = stringResource(R.string.settings_journal_subtitle),
                        checked = journalEnabled,
                        onCheckedChange = { enabled ->
                            OperationJournal.setEnabled(this@SettingsActivity, enabled)
                            journalEnabled = OperationJournal.isEnabled(this@SettingsActivity)
                            journalEvents = OperationJournal.events(this@SettingsActivity)
                            journalViewText = null
                        }
                    )
                    if (journalEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.settings_journal_count, journalEvents.size),
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        enabled = journalEvents.isNotEmpty(),
                                        onClick = { journalViewText = OperationJournal.format(journalEvents) }
                                    ) { Text(stringResource(R.string.settings_journal_view), color = Primary, fontSize = 13.sp) }
                                    TextButton(
                                        enabled = journalEvents.isNotEmpty(),
                                        onClick = {
                                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("SnapCrop operation journal", OperationJournal.format(journalEvents))
                                            )
                                            Toast.makeText(this@SettingsActivity, R.string.settings_journal_copied, Toast.LENGTH_SHORT).show()
                                        }
                                    ) { Text(stringResource(R.string.settings_journal_copy), color = Primary, fontSize = 13.sp) }
                                    TextButton(
                                        enabled = journalEvents.isNotEmpty(),
                                        onClick = ::shareOperationJournal
                                    ) { Text(stringResource(R.string.settings_journal_share), color = Primary, fontSize = 13.sp) }
                                    TextButton(
                                        enabled = journalEvents.isNotEmpty(),
                                        onClick = {
                                            OperationJournal.clear(this@SettingsActivity)
                                            journalEvents = emptyList()
                                            journalViewText = null
                                        }
                                    ) { Text(stringResource(R.string.settings_journal_clear), color = Danger, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                    journalViewText?.let { text ->
                        AlertDialog(
                            onDismissRequest = { journalViewText = null },
                            confirmButton = {
                                TextButton(onClick = { journalViewText = null }) { Text(stringResource(R.string.close), color = Primary) }
                            },
                            title = { Text(stringResource(R.string.settings_journal_title), color = OnSurface) },
                            text = {
                                Text(
                                    text,
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 13.sp,
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            },
                            containerColor = SurfaceVariant
                        )
                    }

                    // Crash diagnostics — local only, nothing leaves the device unless shared.
                    var crashFiles by remember { mutableStateOf(CrashReporter.crashLogs(this@SettingsActivity)) }
                    var crashViewText by remember { mutableStateOf<String?>(null) }
                    if (crashFiles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.settings_crash_title), color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(R.string.settings_crash_subtitle, crashFiles.size), color = OnSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        crashViewText = crashFiles.firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }
                                    }) { Text(stringResource(R.string.settings_crash_view), color = Primary, fontSize = 13.sp) }
                                    TextButton(onClick = { shareCrashLog(crashFiles.firstOrNull()) }) {
                                        Text(stringResource(R.string.settings_crash_share), color = Primary, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = {
                                        CrashReporter.clear(this@SettingsActivity)
                                        crashFiles = emptyList()
                                        crashViewText = null
                                    }) { Text(stringResource(R.string.settings_crash_clear), color = Danger, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                    crashViewText?.let { text ->
                        AlertDialog(
                            onDismissRequest = { crashViewText = null },
                            confirmButton = {
                                TextButton(onClick = { crashViewText = null }) { Text(stringResource(R.string.close), color = Primary) }
                            },
                            title = { Text(stringResource(R.string.settings_crash_title), color = OnSurface) },
                            text = {
                                Text(text, color = OnSurfaceVariant, fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 13.sp,
                                    modifier = Modifier.verticalScroll(rememberScrollState()))
                            },
                            containerColor = SurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    private fun exportSettings(uri: Uri, prefs: SharedPreferences) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(SettingsBackup.export(prefs).toString(2).toByteArray()) }
                ?: throw IllegalStateException("no stream")
            Toast.makeText(this, getString(R.string.settings_backup_done), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.settings_backup_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings(uri: Uri, prefs: SharedPreferences) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("no stream")
            val report = SettingsBackup.importWithReport(prefs, org.json.JSONObject(text))
            if (report == null) {
                Toast.makeText(this, getString(R.string.settings_backup_invalid), Toast.LENGTH_SHORT).show()
            } else {
                RecentWorkflowStore.clear(
                    getSharedPreferences(RecentWorkflowStore.PREF_NAME, MODE_PRIVATE)
                )
                Toast.makeText(
                    this,
                    getString(
                        R.string.settings_backup_restored_report,
                        report.restoredCount,
                        report.migratedCount,
                        report.ignoredUnknownCount,
                        report.ignoredInvalidCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
                recreate()
            }
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.settings_backup_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCrashLog(file: File?) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, getString(R.string.settings_crash_none), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = File(cacheDir, "shared_crops").apply { mkdirs() }
            val out = File(dir, file.name)
            file.copyTo(out, overwrite = true)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", out)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SnapCrop crash log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareOperationJournal() {
        val file = OperationJournal.snapshotFile(this)
        if (file == null) {
            Toast.makeText(this, R.string.settings_journal_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SnapCrop operation journal")
                clipData = ClipData.newRawUri("SnapCrop operation journal", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        } catch (_: Exception) {
            file.delete()
            Toast.makeText(this, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
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
                val ocrScript = OcrScript.fromContext(this@SettingsActivity)
                val profileTextHints = if (UserAppProfileStore.needsOcr(userProfiles)) {
                    TextExtractor.extract(bitmap, ocrScript).map { it.text }
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
private fun SettingsSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp, 20.dp)
                .background(Primary, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(title, color = OnSurface, style = MaterialTheme.typography.titleMedium)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        colors = CardDefaults.cardColors(containerColor = if (enabled) SurfaceContainer else Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = if (enabled) 0.72f else 0.42f)),
        shape = RoundedCornerShape(10.dp)
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
                    style = MaterialTheme.typography.titleSmall
                )
                Text(subtitle, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
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
            Text(stringResource(R.string.delete), color = Danger)
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
