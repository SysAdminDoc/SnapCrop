package com.sysadmindoc.snapcrop

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant

@get:StringRes
internal val ReverseSearchEngine.labelRes: Int
    get() = when (this) {
        ReverseSearchEngine.GOOGLE_LENS -> R.string.reverse_search_google_lens
        ReverseSearchEngine.BING -> R.string.reverse_search_bing
        ReverseSearchEngine.YANDEX -> R.string.reverse_search_yandex
    }

@get:StringRes
private val ReverseSearchEngine.detailRes: Int
    get() = when (this) {
        ReverseSearchEngine.GOOGLE_LENS -> R.string.reverse_search_google_lens_detail
        ReverseSearchEngine.BING, ReverseSearchEngine.YANDEX -> R.string.reverse_search_upload_detail
    }

/** Picks the engine for the editor's "Search image" action (issue #4). Picking one starts the search. */
@Composable
internal fun ReverseSearchDialog(
    engines: List<ReverseSearchEngine>,
    onPick: (ReverseSearchEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reverse_search_title), color = OnSurface) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reverse_search_body), color = OnSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                engines.forEach { engine ->
                    val name = stringResource(engine.labelRes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onPick(engine) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (engine == ReverseSearchEngine.GOOGLE_LENS) Icons.Default.ImageSearch else Icons.Default.TravelExplore,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.padding(end = 14.dp).size(22.dp),
                        )
                        Column {
                            Text(name, color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(engine.detailRes, name), color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
        },
        containerColor = SurfaceVariant,
    )
}
