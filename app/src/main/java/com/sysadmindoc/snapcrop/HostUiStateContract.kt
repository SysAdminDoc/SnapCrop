package com.sysadmindoc.snapcrop

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.sysadmindoc.snapcrop.ui.theme.Danger
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Outline
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceContainer

internal enum class HostWorkflow(@param:StringRes val titleRes: Int) {
    HOME(R.string.nav_home),
    LIBRARY(R.string.nav_gallery),
    EDITOR(R.string.ui_matrix_editor_title),
    SETTINGS(R.string.settings_title),
    STITCH(R.string.stitch_title),
    COLLAGE(R.string.collage_title),
    DEVICE_FRAME(R.string.device_frame_title),
    VIDEO_CLIP(R.string.video_title),
    WEB_CAPTURE(R.string.web_capture_title),
    LONG_SCREENSHOT_REVIEW(R.string.long_screenshot_review_title),
    COMPARE(R.string.compare_title),
}

internal enum class HostUiState(@param:StringRes val labelRes: Int) {
    EMPTY(R.string.ui_matrix_empty_label),
    LOADING(R.string.ui_matrix_loading_label),
    ERROR(R.string.ui_matrix_error_label),
    DESTRUCTIVE_DIALOG(R.string.ui_matrix_destructive_label),
}

internal enum class HostSemanticRole { STATUS, PROGRESS, BUTTON, DIALOG }

internal data class HostSemanticNode(
    val key: String,
    val role: HostSemanticRole,
    val traversalIndex: Float,
    val minimumTouchTargetDp: Int? = null,
)

internal object HostUiStateContract {
    const val MINIMUM_TOUCH_TARGET_DP = 48

    val workflows: List<HostWorkflow> = HostWorkflow.entries
    val states: List<HostUiState> = HostUiState.entries

    fun nodes(state: HostUiState): List<HostSemanticNode> = when (state) {
        HostUiState.EMPTY -> listOf(
            HostSemanticNode("status", HostSemanticRole.STATUS, 0f),
        )
        HostUiState.LOADING -> listOf(
            HostSemanticNode("progress", HostSemanticRole.PROGRESS, 0f),
        )
        HostUiState.ERROR -> listOf(
            HostSemanticNode("error", HostSemanticRole.STATUS, 0f),
            HostSemanticNode("retry", HostSemanticRole.BUTTON, 1f, MINIMUM_TOUCH_TARGET_DP),
        )
        HostUiState.DESTRUCTIVE_DIALOG -> listOf(
            HostSemanticNode("dialog", HostSemanticRole.DIALOG, 0f),
            HostSemanticNode("keep", HostSemanticRole.BUTTON, 1f, MINIMUM_TOUCH_TARGET_DP),
            HostSemanticNode("remove", HostSemanticRole.BUTTON, 2f, MINIMUM_TOUCH_TARGET_DP),
        )
    }
}

@Composable
internal fun HostUiStateOverlay(
    workflow: HostWorkflow,
    state: HostUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val workflowTitle = stringResource(workflow.titleRes)
    val stateLabel = stringResource(state.labelRes)
    val rootDescription = stringResource(
        R.string.ui_matrix_root_description,
        workflowTitle,
        stateLabel,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                testTag = "host-ui-${workflow.name.lowercase()}-${state.name.lowercase()}"
                contentDescription = rootDescription
                stateDescription = stateLabel
                isTraversalGroup = true
            }
    ) {
        content()
        when (state) {
            HostUiState.EMPTY -> StateLabel(
                text = stateLabel,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
            HostUiState.LOADING -> LoadingState(workflowTitle)
            HostUiState.ERROR -> ErrorState(workflowTitle)
            HostUiState.DESTRUCTIVE_DIALOG -> DestructiveState(workflowTitle)
        }
    }
}

@Composable
private fun StateLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { traversalIndex = 0f },
        color = SurfaceContainer.copy(alpha = 0.96f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = OnSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LoadingState(workflowTitle: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .semantics { traversalIndex = 0f },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Primary,
                    strokeWidth = 3.dp,
                )
                Text(
                    stringResource(R.string.ui_matrix_loading_body, workflowTitle),
                    color = OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(workflowTitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = 0.72f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .semantics { traversalIndex = 0f },
                ) {
                    Text(stringResource(R.string.ui_matrix_error_label), color = OnSurface)
                    Text(
                        stringResource(R.string.ui_matrix_error_body, workflowTitle),
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            role = Role.Button
                            traversalIndex = 1f
                        },
                ) {
                    Text(stringResource(R.string.ui_matrix_retry))
                }
            }
        }
    }
}

@Composable
private fun DestructiveState(workflowTitle: String) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(stringResource(R.string.ui_matrix_destructive_title, workflowTitle))
        },
        text = { Text(stringResource(R.string.ui_matrix_destructive_body)) },
        dismissButton = {
            TextButton(
                onClick = {},
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        traversalIndex = 1f
                    },
            ) {
                Text(stringResource(R.string.ui_matrix_keep))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {},
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        traversalIndex = 2f
                    },
            ) {
                Text(stringResource(R.string.ui_matrix_remove), color = Danger)
            }
        },
        containerColor = SurfaceContainer,
        modifier = Modifier.semantics { traversalIndex = 0f },
    )
}
