package com.sysadmindoc.snapcrop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant

internal data class GallerySourceOption(val key: String, val label: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryFilterDialog(
    state: GalleryFilterState,
    sourceOptions: List<GallerySourceOption>,
    categoryOptions: List<String>,
    formatOptions: List<GalleryFormat>,
    indexEnabled: Boolean,
    resultCount: Int,
    eligibleCollectionCount: Int,
    skippedCollectionCount: Int,
    onChange: (GalleryFilterState) -> Unit,
    onSeedCollection: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.gallery_filters_title),
                color = OnSurface,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.gallery_filter_result_count, resultCount),
                    color = OnSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                FilterSection(stringResource(R.string.gallery_filter_media_type)) {
                    GalleryMediaType.entries.forEach { value ->
                        FilterChip(
                            selected = state.mediaType == value,
                            onClick = { onChange(state.copy(mediaType = value)) },
                            label = { Text(mediaTypeLabel(value)) }
                        )
                    }
                }
                FilterSection(stringResource(R.string.gallery_filter_source)) {
                    FilterChip(
                        selected = state.sourceOrAlbum.isBlank(),
                        onClick = { onChange(state.copy(sourceOrAlbum = "")) },
                        label = { Text(stringResource(R.string.gallery_filter_any)) }
                    )
                    sourceOptions.forEach { option ->
                        FilterChip(
                            selected = state.sourceOrAlbum == option.key,
                            onClick = {
                                onChange(state.copy(sourceOrAlbum = if (state.sourceOrAlbum == option.key) "" else option.key))
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                FilterSection(stringResource(R.string.gallery_filter_category)) {
                    if (!indexEnabled) {
                        Text(stringResource(R.string.gallery_filter_index_disabled), color = OnSurfaceVariant)
                    } else if (categoryOptions.isEmpty()) {
                        Text(stringResource(R.string.gallery_filter_no_categories), color = OnSurfaceVariant)
                    }
                    categoryOptions.forEach { category ->
                        FilterChip(
                            selected = category in state.categories,
                            onClick = {
                                onChange(state.copy(categories = state.categories.toggle(category)))
                            },
                            label = { Text(category.replaceFirstChar { it.titlecase() }) }
                        )
                    }
                }
                FilterSection(stringResource(R.string.gallery_filter_date)) {
                    GalleryDateRange.entries.forEach { value ->
                        FilterChip(
                            selected = state.dateRange == value,
                            onClick = { onChange(state.copy(dateRange = value)) },
                            label = { Text(dateRangeLabel(value)) }
                        )
                    }
                }
                FilterSection(stringResource(R.string.gallery_filter_orientation)) {
                    GalleryOrientation.entries.forEach { value ->
                        FilterChip(
                            selected = state.orientation == value,
                            onClick = { onChange(state.copy(orientation = value)) },
                            label = { Text(orientationLabel(value)) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.minWidth.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { value ->
                            onChange(state.copy(minWidth = (value.filter(Char::isDigit).toIntOrNull() ?: 0).coerceAtMost(1_000_000)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.gallery_filter_min_width)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.minHeight.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { value ->
                            onChange(state.copy(minHeight = (value.filter(Char::isDigit).toIntOrNull() ?: 0).coerceAtMost(1_000_000)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.gallery_filter_min_height)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                FilterSection(stringResource(R.string.gallery_filter_favorite)) {
                    GalleryFavoriteMode.entries.forEach { value ->
                        FilterChip(
                            selected = state.favoriteMode == value,
                            onClick = { onChange(state.copy(favoriteMode = value)) },
                            label = { Text(favoriteLabel(value)) }
                        )
                    }
                }
                FilterSection(stringResource(R.string.gallery_filter_format)) {
                    formatOptions.forEach { format ->
                        FilterChip(
                            selected = format in state.formats,
                            onClick = { onChange(state.copy(formats = state.formats.toggle(format))) },
                            label = { Text(format.name.replace('_', ' ')) }
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.gallery_filter_collection_summary,
                        eligibleCollectionCount,
                        skippedCollectionCount
                    ),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(enabled = eligibleCollectionCount > 0, onClick = onSeedCollection) {
                Text(stringResource(R.string.gallery_filter_add_collection))
            }
        },
        dismissButton = {
            Row {
                if (state.activeCount > 0) {
                    TextButton(onClick = { onChange(GalleryFilterState()) }) {
                        Text(stringResource(R.string.gallery_filter_clear_all))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
        containerColor = SurfaceVariant
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = OnSurface, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

@Composable
private fun mediaTypeLabel(value: GalleryMediaType): String = stringResource(
    when (value) {
        GalleryMediaType.ALL -> R.string.gallery_filter_any
        GalleryMediaType.IMAGES -> R.string.gallery_filter_images
        GalleryMediaType.VIDEOS -> R.string.gallery_filter_videos
        GalleryMediaType.SCREENSHOTS -> R.string.gallery_filter_screenshots
    }
)

@Composable
private fun dateRangeLabel(value: GalleryDateRange): String = stringResource(
    when (value) {
        GalleryDateRange.ALL -> R.string.gallery_filter_any
        GalleryDateRange.LAST_24_HOURS -> R.string.gallery_filter_last_day
        GalleryDateRange.LAST_7_DAYS -> R.string.gallery_filter_last_7_days
        GalleryDateRange.LAST_30_DAYS -> R.string.gallery_filter_last_30_days
        GalleryDateRange.LAST_90_DAYS -> R.string.gallery_filter_last_90_days
        GalleryDateRange.LAST_365_DAYS -> R.string.gallery_filter_last_year
    }
)

@Composable
private fun orientationLabel(value: GalleryOrientation): String = stringResource(
    when (value) {
        GalleryOrientation.ALL -> R.string.gallery_filter_any
        GalleryOrientation.PORTRAIT -> R.string.gallery_filter_portrait
        GalleryOrientation.LANDSCAPE -> R.string.gallery_filter_landscape
        GalleryOrientation.SQUARE -> R.string.gallery_filter_square
    }
)

@Composable
private fun favoriteLabel(value: GalleryFavoriteMode): String = stringResource(
    when (value) {
        GalleryFavoriteMode.ALL -> R.string.gallery_filter_any
        GalleryFavoriteMode.FAVORITES -> R.string.gallery_filter_favorites_only
        GalleryFavoriteMode.NOT_FAVORITES -> R.string.gallery_filter_not_favorite
    }
)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
