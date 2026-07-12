package com.sysadmindoc.snapcrop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.SurfaceContainer
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant

@Composable
internal fun LocalHelpDialog(
    onOpenRoute: (HelpRoute) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { LocalHelpCatalog.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.help_title),
                color = OnSurface,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.help_search)) },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        .padding(top = 8.dp)
                ) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.help_no_results),
                                color = OnSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                    items(results, key = HelpEntry::id) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable {
                                    onDismiss()
                                    onOpenRoute(entry.route)
                                },
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(entry.title, color = OnSurface, fontSize = 14.sp)
                                Text(entry.summary, color = OnSurfaceVariant, fontSize = 12.sp)
                                Text(stringResource(R.string.help_open), color = com.sysadmindoc.snapcrop.ui.theme.Primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        containerColor = SurfaceVariant
    )
}
