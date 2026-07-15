package com.sysadmindoc.snapcrop

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(
    name = "compact-light-empty",
    widthDp = 390,
    heightDp = 844,
    fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "compact-dark-loading-2x",
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "expanded-light-error-2x",
    widthDp = 1000,
    heightDp = 720,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "expanded-dark-destructive",
    widthDp = 1000,
    heightDp = 720,
    fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private annotation class HostStateConfigurations

@PreviewTest
@HostStateConfigurations
@Composable
fun HomeWorkflowStateReferences() = WorkflowReference(HostWorkflow.HOME) {
    HomeScreen(
        isRunning = false,
        mediaCapabilities = MediaCapabilities(MediaAccess.NONE, MediaAccess.NONE, false),
        recentCrops = emptyList(),
        cropCount = 0,
        recentWorkflows = emptyList(),
        onToggleService = {},
        permissionRecoveryStates = PermissionCapability.entries.associateWith {
            PermissionRecoveryState.REQUESTABLE
        },
        onRequestImageAccess = {},
        onPickImage = {},
        onWebCapture = {},
        onBatchCrop = {},
        onStitch = {},
        onCollage = {},
        onDeviceFrame = {},
        onVideoClip = {},
        longScreenshotReady = false,
        onLongScreenshot = {},
        onDelayedCapture = {},
        batchProgress = "",
        batchFraction = 0f,
        onBatchCancel = {},
        hasOverlayPermission = false,
        onRequestOverlay = {},
        onOpenSettings = {},
        onOpenHelp = {},
        onViewAll = {},
        onOpenCrop = {},
        onCopyCrop = {},
        onDeleteCrop = {},
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun LibraryWorkflowStateReferences() = WorkflowReference(HostWorkflow.LIBRARY) {
    Column(Modifier.fillMaxSize().background(Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.gallery_title),
                modifier = Modifier.weight(1f),
                color = OnSurface,
                style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = {}) {
                Icon(Icons.Default.PhotoLibrary, stringResource(R.string.gallery_title), tint = OnSurface)
            }
        }
        GalleryEmptyState(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.gallery_empty_title),
            subtitle = stringResource(R.string.gallery_empty_subtitle),
            actionLabel = stringResource(R.string.gallery_allow_images),
            onAction = {},
        )
    }
}

@PreviewTest
@HostStateConfigurations
@Composable
fun EditorWorkflowStateReferences() = WorkflowReference(HostWorkflow.EDITOR) {
    Column(Modifier.fillMaxSize().background(Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Close, stringResource(R.string.editor_close), tint = OnSurface)
            }
            IconButton(onClick = {}) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo), tint = OnSurface)
            }
            IconButton(onClick = {}) {
                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo), tint = OnSurface)
            }
            Text(
                stringResource(R.string.ui_matrix_editor_title),
                modifier = Modifier.weight(1f),
                color = OnSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                R.string.mode_crop,
                R.string.mode_cutout,
                R.string.mode_pixelate,
                R.string.mode_draw,
                R.string.mode_ocr,
                R.string.mode_adjust,
            ).forEach { label ->
                FilterChip(
                    selected = label == R.string.mode_ocr,
                    onClick = {},
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
        EditorModeBanner(
            editMode = EditMode.OCR,
            drawTool = DrawTool.PEN,
            ocrLoading = false,
            ocrBlockCount = 2,
            scannedCodeCount = 1,
            actions = EditorModeBannerActions({}, {}, {}),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .background(androidx.compose.ui.graphics.Color(0xFF212736))
                .border(2.dp, OnSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.editor_canvas_cd),
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Button(onClick = {}) { Text(stringResource(R.string.editor_share)) }
            Button(onClick = {}) { Text(stringResource(R.string.save)) }
        }
    }
}

@PreviewTest
@HostStateConfigurations
@Composable
fun SettingsWorkflowStateReferences() = WorkflowReference(HostWorkflow.SETTINGS) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            color = OnSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        SettingsSearchPanel(
            query = "",
            entries = emptyList(),
            onQueryChange = {},
            onOpen = {},
            onReset = {},
        )
        SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
        SettingToggle(
            title = stringResource(R.string.settings_recent_workflows_title),
            subtitle = stringResource(R.string.settings_recent_workflows_subtitle),
            checked = true,
            onCheckedChange = {},
        )
        SettingsSectionHeader(stringResource(R.string.settings_section_save))
        SettingToggle(
            title = stringResource(R.string.settings_replace_title),
            subtitle = stringResource(R.string.settings_replace_subtitle),
            checked = true,
            onCheckedChange = {},
        )
        SourceArchiveAccessCard(supported = true, granted = false, onGrant = {})
    }
}

@PreviewTest
@HostStateConfigurations
@Composable
fun StitchWorkflowStateReferences() = WorkflowReference(HostWorkflow.STITCH) {
    StitchScreen(
        uris = emptyList(),
        isVertical = true,
        isSaving = false,
        onToggleDirection = {},
        onAddImages = {},
        onMoveUp = {},
        onMoveDown = {},
        onRemoveImage = {},
        onSave = {},
        onClose = {},
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun CollageWorkflowStateReferences() = WorkflowReference(HostWorkflow.COLLAGE) {
    CollageScreen(
        uris = emptyList(),
        layout = collageLayouts[2],
        pendingLayout = null,
        undo = null,
        isSaving = false,
        spacing = 4,
        bgColorIdx = 0,
        onLayoutChange = {},
        onConfirmLayoutChange = {},
        onCancelLayoutChange = {},
        onUndoLayoutChange = {},
        onUndoDismissed = {},
        onSpacingChange = {},
        onBgColorChange = {},
        cellAspect = 1f,
        onCellAspectChange = {},
        onPickImages = {},
        onAddImage = {},
        onRemoveImage = {},
        onSave = {},
        onClose = {},
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun DeviceFrameWorkflowStateReferences() = WorkflowReference(HostWorkflow.DEVICE_FRAME) {
    FrameScreen(
        imageUri = null,
        frame = frames[0],
        isSaving = false,
        onFrameChange = {},
        onPickImage = {},
        onSave = {},
        onClose = {},
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun VideoClipWorkflowStateReferences() = WorkflowReference(HostWorkflow.VIDEO_CLIP) {
    VideoClipScreen(
        uri = Uri.EMPTY,
        isWorking = false,
        onClose = {},
        onOpenExternally = {},
        onChooseAnother = {},
        initialFramePositionMs = 15_000L,
        initialTrimStartMs = 5_000L,
        initialTrimEndMs = 45_000L,
        onTimelineChanged = { _, _, _ -> },
        onGrabFrame = {},
        onTrimClip = { _, _ -> },
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun WebCaptureWorkflowStateReferences() = WorkflowReference(HostWorkflow.WEB_CAPTURE) {
    WebCaptureContent(
        inputUrl = "https://example.com/article",
        status = stringResource(R.string.web_capture_privacy),
        isLoading = false,
        isCapturing = false,
        canCapture = false,
        onBack = {},
        onInputUrlChange = {},
        onLoad = {},
        onCancel = {},
        onCapture = {},
    ) {
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.web_capture_privacy),
                modifier = Modifier.padding(24.dp),
                color = androidx.compose.ui.graphics.Color.DarkGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PreviewTest
@HostStateConfigurations
@Composable
fun LongScreenshotWorkflowStateReferences() = WorkflowReference(HostWorkflow.LONG_SCREENSHOT_REVIEW) {
    LongScreenshotReviewScreen(
        uri = null,
        frameCount = 0,
        stopReason = stringResource(R.string.long_screenshot_no_content),
        isBusy = false,
        stitchPlan = null,
        previewRevision = 0,
        onPlanChanged = { _, _ -> },
        onRenderPlan = {},
        onSave = {},
        onRetry = {},
        onDiscard = {},
    )
}

@PreviewTest
@HostStateConfigurations
@Composable
fun CompareWorkflowStateReferences() = WorkflowReference(HostWorkflow.COMPARE) {
    CompareScreen(
        state = CompareUiState.Loading,
        mode = CompareMode.SWIPE,
        alignment = CompareAlignment.TOP_LEFT,
        divider = 0.5f,
        overlayOpacity = 0.5f,
        blinkRunning = false,
        onClose = {},
        onRetry = {},
        onModeChange = {},
        onAlignmentChange = {},
        onDividerChange = {},
        onOverlayOpacityChange = {},
        onBlinkRunningChange = {},
        onSwap = {},
    )
}

@Composable
private fun WorkflowReference(
    workflow: HostWorkflow,
    content: @Composable () -> Unit,
) {
    check(LocalInspectionMode.current) { "Host UI references must run only under Layoutlib inspection" }
    val configuration = LocalConfiguration.current
    val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val state = when {
        configuration.screenWidthDp < 600 && !dark -> HostUiState.EMPTY
        configuration.screenWidthDp < 600 -> HostUiState.LOADING
        !dark -> HostUiState.ERROR
        else -> HostUiState.DESTRUCTIVE_DIALOG
    }
    SnapCropTheme(darkOverride = dark) {
        HostUiStateOverlay(workflow = workflow, state = state, content = content)
    }
}
