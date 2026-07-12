package com.sysadmindoc.snapcrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun CustomRedactionPatternsPanel(
    patterns: List<CustomRedactionPattern>,
    storageError: Boolean,
    onSavePatterns: (List<CustomRedactionPattern>) -> Boolean,
    onExport: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<CustomRedactionPattern?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val removedMessage = stringResource(R.string.settings_custom_patterns_removed)
    val importedMessage = stringResource(R.string.settings_custom_patterns_imported)

    Text(
        stringResource(R.string.settings_custom_patterns_title),
        color = OnSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    if (storageError) {
        Text(
            stringResource(R.string.settings_custom_patterns_storage_error),
            color = Tertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Text(
        stringResource(R.string.settings_custom_patterns_subtitle),
        color = OnSurfaceVariant,
        fontSize = 12.sp,
    )
    Text(
        stringResource(R.string.settings_custom_patterns_warning),
        color = Tertiary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            enabled = patterns.size < CustomRedactionPatternStore.MAX_PATTERNS,
        ) { Text(stringResource(R.string.settings_custom_patterns_add), fontSize = 12.sp) }
        TextButton(onClick = { showImport = true }) {
            Text(stringResource(R.string.settings_custom_patterns_import), fontSize = 12.sp)
        }
        TextButton(
            onClick = { onExport(CustomRedactionPatternStore.export(patterns)) },
            enabled = patterns.isNotEmpty(),
        ) { Text(stringResource(R.string.settings_custom_patterns_export), fontSize = 12.sp) }
    }
    status?.let { Text(it, color = OnSurfaceVariant, fontSize = 11.sp) }
    if (patterns.isEmpty()) {
        Text(
            stringResource(R.string.settings_custom_patterns_empty),
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    patterns.forEach { pattern ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(pattern.name, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            pattern.expression,
                            color = OnSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = pattern.enabled,
                        onCheckedChange = { enabled ->
                            val updated = patterns.map {
                                if (it.id == pattern.id) it.copy(enabled = enabled) else it
                            }
                            if (onSavePatterns(updated)) status = null
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        editing = pattern
                        showEditor = true
                    }) { Text(stringResource(R.string.settings_custom_patterns_edit), fontSize = 11.sp) }
                    TextButton(onClick = {
                        if (onSavePatterns(patterns.filterNot { it.id == pattern.id })) {
                            status = removedMessage
                        }
                    }) { Text(stringResource(R.string.delete), color = Tertiary, fontSize = 11.sp) }
                }
            }
        }
    }

    if (showEditor) {
        CustomPatternEditorDialog(
            existing = editing,
            existingPatterns = patterns,
            onDismiss = { showEditor = false },
            onSave = { pattern ->
                val updated = if (editing == null) {
                    patterns + pattern
                } else {
                    patterns.map { if (it.id == pattern.id) pattern else it }
                }
                if (onSavePatterns(updated)) {
                    status = null
                    showEditor = false
                }
            },
        )
    }
    if (showImport) {
        CustomPatternImportDialog(
            onDismiss = { showImport = false },
            onImport = { imported ->
                if (onSavePatterns(imported)) {
                    status = importedMessage
                    showImport = false
                    true
                } else {
                    false
                }
            },
        )
    }
}

@Composable
private fun CustomPatternEditorDialog(
    existing: CustomRedactionPattern?,
    existingPatterns: List<CustomRedactionPattern>,
    onDismiss: () -> Unit,
    onSave: (CustomRedactionPattern) -> Unit,
) {
    val id = remember(existing?.id) { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var expression by remember(existing?.id) { mutableStateOf(existing?.expression.orEmpty()) }
    var caseSensitive by remember(existing?.id) { mutableStateOf(existing?.caseSensitive ?: true) }
    var testText by remember(existing?.id) { mutableStateOf("") }
    var result by remember(existing?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val matchResult = stringResource(R.string.settings_custom_patterns_test_matches)
    val noMatchResult = stringResource(R.string.settings_custom_patterns_test_no_match)
    val invalidResult = stringResource(R.string.settings_custom_patterns_test_invalid)
    val timedOutResult = stringResource(R.string.settings_custom_patterns_test_timed_out)
    val candidate = CustomRedactionPattern(id, name.trim(), expression.trim(), caseSensitive, existing?.enabled ?: true)
    val duplicateName = existingPatterns.any { it.id != id && it.name.equals(candidate.name, true) }
    val validation = CustomRedactionPatternStore.validate(candidate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.settings_custom_patterns_add else R.string.settings_custom_patterns_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CustomRedactionPatternStore.MAX_NAME_LENGTH); result = null },
                    label = { Text(stringResource(R.string.settings_custom_patterns_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it.take(CustomRedactionPatternStore.MAX_EXPRESSION_LENGTH); result = null },
                    label = { Text(stringResource(R.string.settings_custom_patterns_expression)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_custom_patterns_case), color = OnSurfaceVariant, fontSize = 12.sp)
                    Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it; result = null })
                }
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it.take(CustomRedactionPatternStore.MAX_TEST_TEXT_LENGTH); result = null },
                    label = { Text(stringResource(R.string.settings_custom_patterns_test_text)) },
                    supportingText = { Text(stringResource(R.string.settings_custom_patterns_test_private)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = validation == null && !duplicateName && testText.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            val test = withContext(Dispatchers.Default) {
                                CustomRedactionPatternStore.test(candidate, testText)
                            }
                            result = when (test.status) {
                                CustomPatternTestStatus.MATCH -> matchResult.format(test.matchCount)
                                CustomPatternTestStatus.NO_MATCH -> noMatchResult
                                CustomPatternTestStatus.INVALID -> test.validationMessage ?: invalidResult
                                CustomPatternTestStatus.TIMED_OUT -> timedOutResult
                            }
                        }
                    },
                ) { Text(stringResource(R.string.settings_custom_patterns_test)) }
                val issue = when {
                    duplicateName -> stringResource(R.string.settings_custom_patterns_duplicate)
                    validation != null -> validation
                    else -> result
                }
                issue?.let { Text(it, color = if (validation != null || duplicateName) Tertiary else OnSurfaceVariant, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && !duplicateName,
                onClick = { onSave(candidate) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CustomPatternImportDialog(
    onDismiss: () -> Unit,
    onImport: (List<CustomRedactionPattern>) -> Boolean,
) {
    var serialized by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_patterns_import)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_custom_patterns_import_hint), color = OnSurfaceVariant, fontSize = 12.sp)
                OutlinedTextField(
                    value = serialized,
                    onValueChange = { serialized = it.take(16_384); error = false },
                    label = { Text(stringResource(R.string.settings_custom_patterns_json)) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error,
                )
                if (error) Text(stringResource(R.string.settings_custom_patterns_import_error), color = Tertiary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val imported = CustomRedactionPatternStore.import(serialized)
                error = imported == null || !onImport(imported)
            }) { Text(stringResource(R.string.settings_custom_patterns_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
