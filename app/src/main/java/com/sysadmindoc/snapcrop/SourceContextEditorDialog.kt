package com.sysadmindoc.snapcrop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant

@Composable
internal fun SourceContextEditorDialog(
    initial: ExplicitSourceContext?,
    onSave: (ExplicitSourceContext?) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    val candidate = ExplicitSourceContext(
        url = url,
        label = label,
        packageName = initial?.packageName
    ).normalizedOrNull()
    val invalidUrl = url.isNotBlank() && candidate?.url == null
    val invalidLabel = label.isNotBlank() && candidate?.label == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_context_dialog_title), color = OnSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(2_048) },
                    label = { Text(stringResource(R.string.source_context_url)) },
                    isError = invalidUrl,
                    supportingText = {
                        if (invalidUrl) Text(stringResource(R.string.source_context_invalid_url))
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(160) },
                    label = { Text(stringResource(R.string.source_context_label)) },
                    isError = invalidLabel,
                    singleLine = true
                )
                initial?.packageName?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.source_context_package, it), color = OnSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !invalidUrl && !invalidLabel, onClick = { onSave(candidate) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (initial != null) {
                TextButton(onClick = { onSave(null) }) {
                    Text(stringResource(R.string.source_context_clear))
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = SurfaceVariant
    )
}
