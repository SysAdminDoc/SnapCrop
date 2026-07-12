package com.sysadmindoc.snapcrop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant

@Composable
internal fun RecentWorkflowRow(
    workflows: List<WorkflowId>,
    onRun: (WorkflowId) -> Unit
) {
    val executable = workflows.filter(WorkflowId::isHomeExecutable)
    if (executable.isEmpty()) return
    Column {
        Text(stringResource(R.string.home_recent_workflows), color = OnSurface, fontSize = 15.sp)
        Text(stringResource(R.string.home_recent_workflows_private), color = OnSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            executable.forEach { workflow ->
                AssistChip(onClick = { onRun(workflow) }, label = { Text(workflow.homeLabel()) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

internal fun WorkflowId.isHomeExecutable(): Boolean = when (this) {
    WorkflowId.EDIT_IMAGE,
    WorkflowId.WEB_CAPTURE,
    WorkflowId.BATCH_CROP,
    WorkflowId.DELAYED_CAPTURE,
    WorkflowId.LONG_SCREENSHOT,
    WorkflowId.STITCH,
    WorkflowId.COLLAGE,
    WorkflowId.DEVICE_FRAME,
    WorkflowId.VIDEO_CLIP,
    WorkflowId.GALLERY -> true
    else -> false
}

@Composable
private fun WorkflowId.homeLabel(): String = when (this) {
    WorkflowId.EDIT_IMAGE -> stringResource(R.string.home_crop_one_title)
    WorkflowId.WEB_CAPTURE -> stringResource(R.string.home_web_capture_title)
    WorkflowId.BATCH_CROP -> stringResource(R.string.home_batch_title)
    WorkflowId.DELAYED_CAPTURE -> stringResource(R.string.home_delay_title)
    WorkflowId.LONG_SCREENSHOT -> stringResource(R.string.home_long_title)
    WorkflowId.STITCH -> stringResource(R.string.home_stitch_title)
    WorkflowId.COLLAGE -> stringResource(R.string.home_collage_title)
    WorkflowId.DEVICE_FRAME -> stringResource(R.string.home_mockup_title)
    WorkflowId.VIDEO_CLIP -> stringResource(R.string.home_video_title)
    WorkflowId.GALLERY -> stringResource(R.string.nav_gallery)
    else -> name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
}
