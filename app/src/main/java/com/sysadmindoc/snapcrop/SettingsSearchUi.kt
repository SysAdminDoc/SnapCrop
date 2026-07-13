package com.sysadmindoc.snapcrop

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Outline
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceContainer

internal fun Modifier.settingsAnchor(
    destination: SettingsDestination,
    requesters: Map<SettingsDestination, BringIntoViewRequester>,
    highlightedDestination: SettingsDestination?,
): Modifier = this
    .bringIntoViewRequester(requesters.getValue(destination))
    .testTag("settings-anchor-${destination.wireValue}")
    .then(
        if (highlightedDestination == destination) {
            Modifier
                .border(2.dp, Primary, RoundedCornerShape(10.dp))
                .padding(2.dp)
        } else {
            Modifier
        }
    )

@Composable
internal fun SettingsSearchPanel(
    query: String,
    entries: List<SettingsSearchEntry>,
    onQueryChange: (String) -> Unit,
    onOpen: (SettingsDestination) -> Unit,
    onReset: (SettingsSearchEntry) -> Unit,
) {
    val results = remember(entries, query) { SettingsRegistry.search(entries, query) }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.settings_search_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("settings-search"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Outline,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
            cursorColor = Primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
    if (query.isBlank()) return

    Spacer(Modifier.height(8.dp))
    if (results.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("settings-search-empty"),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.72f)),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.settings_search_no_results, query),
                    color = OnSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.settings_search_no_results_hint),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onQueryChange("") }) {
                    Text(stringResource(R.string.settings_search_clear), color = Primary)
                }
            }
        }
        return
    }

    results.forEach { entry ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .testTag("settings-result-${entry.destination.wireValue}"),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.72f)),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(entry.destination) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, color = OnSurface, style = MaterialTheme.typography.titleSmall)
                    Text(entry.summary, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entry.resetKeys.isNotEmpty()) {
                HorizontalDivider(color = Outline.copy(alpha = 0.45f))
                TextButton(
                    onClick = { onReset(entry) },
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text(stringResource(R.string.settings_search_reset_section), color = OnSurfaceVariant)
                }
            }
        }
    }
}
