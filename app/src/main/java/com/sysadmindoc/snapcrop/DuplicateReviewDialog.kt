package com.sysadmindoc.snapcrop

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DuplicateScanDialog(
    sensitivity: DuplicateSensitivity,
    running: Boolean,
    scanned: Int,
    total: Int,
    onSensitivityChange: (DuplicateSensitivity) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(stringResource(R.string.duplicate_scan_title), color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.duplicate_scan_body), color = OnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuplicateSensitivity.entries.forEach { choice ->
                        FilterChip(
                            selected = sensitivity == choice,
                            onClick = { if (!running) onSensitivityChange(choice) },
                            label = { Text(stringResource(choice.labelResource())) }
                        )
                    }
                }
                if (running) {
                    val progress = if (total > 0) "$scanned / $total" else stringResource(R.string.duplicate_scan_preparing)
                    Text(stringResource(R.string.duplicate_scan_progress, progress), color = Primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onAnalyze, enabled = !running) { Text(stringResource(R.string.duplicate_scan_action)) }
        },
        dismissButton = {
            TextButton(onClick = if (running) onCancel else onDismiss) {
                Text(stringResource(if (running) R.string.cancel else R.string.close))
            }
        },
        containerColor = SurfaceVariant
    )
}

@Composable
internal fun DuplicateReviewDialog(
    groups: List<DuplicateGroup>,
    sensitivity: DuplicateSensitivity,
    onSensitivityChange: (DuplicateSensitivity) -> Unit,
    onNotSimilar: (DuplicateGroup) -> Unit,
    onTrash: (List<android.net.Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    if (groups.isEmpty()) return
    val context = LocalContext.current
    var groupIndex by rememberSaveable(groups.map(DuplicateGroup::id)) { mutableIntStateOf(0) }
    if (groupIndex > groups.lastIndex) groupIndex = groups.lastIndex
    val group = groups[groupIndex]
    var activeIdentity by remember(group.id) { mutableStateOf(group.candidates.first().identity) }
    var removals by remember(group.id) { mutableStateOf(emptySet<String>()) }
    val active = group.candidates.firstOrNull { it.identity == activeIdentity } ?: group.candidates.first()
    val removableUris = group.candidates.filter { it.identity in removals }.map(DuplicateCandidate::uri)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.96f),
            shape = RoundedCornerShape(20.dp),
            color = Black
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.duplicate_review_title), color = OnSurface, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(
                                R.string.duplicate_group_position,
                                groupIndex + 1,
                                groups.size,
                                stringResource(if (group.kind == DuplicateMatchKind.EXACT) R.string.duplicate_exact else R.string.duplicate_similar)
                            ),
                            color = OnSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuplicateSensitivity.entries.forEach { choice ->
                        FilterChip(
                            selected = sensitivity == choice,
                            onClick = { onSensitivityChange(choice) },
                            label = { Text(stringResource(choice.labelResource())) }
                        )
                    }
                }

                Box(
                    Modifier.fillMaxWidth().weight(1f).background(SurfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(active.uri)
                            .size(Size.ORIGINAL)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build(),
                        contentDescription = stringResource(R.string.duplicate_full_resolution_preview, active.displayName),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }

                Column(
                    Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.candidates.forEach { candidate ->
                        val removing = candidate.identity in removals
                        Row(
                            Modifier.fillMaxWidth().clickable { activeIdentity = candidate.identity }
                                .background(if (candidate.identity == active.identity) SurfaceVariant else Black, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = candidate.identity == active.identity, onClick = { activeIdentity = candidate.identity })
                            Column(Modifier.weight(1f)) {
                                Text(candidate.displayName, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${candidate.width} × ${candidate.height} · ${Formatter.formatFileSize(context, candidate.sizeBytes)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(candidate.dateAdded * 1000))}",
                                    color = OnSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(stringResource(R.string.duplicate_remove), color = if (removing) Tertiary else OnSurfaceVariant)
                            Checkbox(
                                checked = removing,
                                onCheckedChange = { removals = DuplicateReviewSelection.toggle(group, removals, candidate.identity) }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { removals = DuplicateReviewSelection.keepOldest(group) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.duplicate_keep_oldest))
                    }
                    OutlinedButton(onClick = { removals = DuplicateReviewSelection.keepNewest(group) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.duplicate_keep_newest))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (group.kind == DuplicateMatchKind.SIMILAR) {
                        TextButton(onClick = { onNotSimilar(group) }) { Text(stringResource(R.string.duplicate_not_similar)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { groupIndex = (groupIndex + 1) % groups.size },
                        enabled = groups.size > 1
                    ) { Text(stringResource(R.string.duplicate_skip)) }
                    Button(
                        onClick = { onTrash(removableUris) },
                        enabled = removableUris.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Tertiary)
                    ) { Text(stringResource(R.string.duplicate_trash_count, removableUris.size)) }
                }
            }
        }
    }
}

private fun DuplicateSensitivity.labelResource(): Int = when (this) {
    DuplicateSensitivity.STRICT -> R.string.duplicate_strict
    DuplicateSensitivity.BALANCED -> R.string.duplicate_balanced
    DuplicateSensitivity.LOOSE -> R.string.duplicate_loose
}
