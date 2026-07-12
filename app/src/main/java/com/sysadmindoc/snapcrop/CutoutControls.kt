package com.sysadmindoc.snapcrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import kotlin.math.roundToInt

internal data class CutoutControlActions(
    val onSelectedIndexChange: (Int) -> Unit,
    val onAddBand: (CutBand) -> Unit,
    val onUpdateBand: (Int, CutBand) -> Unit,
    val onRemoveBand: (Int) -> Unit,
    val onClearBands: () -> Unit,
    val onSeparatorStyleChange: (CutSeparatorStyle) -> Unit,
)

@Composable
internal fun CutoutControls(
    sourceWidth: Int,
    sourceHeight: Int,
    cropLeft: Int,
    cropTop: Int,
    cropRight: Int,
    cropBottom: Int,
    bands: List<CutBand>,
    separatorStyle: CutSeparatorStyle,
    enabled: Boolean,
    selectedIndex: Int,
    actions: CutoutControlActions,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.cutout_help),
            color = OnSurfaceVariant,
            fontSize = 11.sp,
        )
        if (!enabled) {
            Text(stringResource(R.string.cutout_incompatible), color = OnSurfaceVariant, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    val span = ((cropBottom - cropTop) / 8).coerceAtLeast(1)
                    val center = (cropTop + cropBottom) / 2
                    actions.onAddBand(CutBand(CutAxis.HORIZONTAL, center - span / 2, center + (span + 1) / 2))
                },
                enabled = enabled && bands.size < CutoutSqueeze.MAX_INPUT_BANDS,
            ) { Text(stringResource(R.string.cutout_add_horizontal), fontSize = 11.sp) }
            FilledTonalButton(
                onClick = {
                    val span = ((cropRight - cropLeft) / 8).coerceAtLeast(1)
                    val center = (cropLeft + cropRight) / 2
                    actions.onAddBand(CutBand(CutAxis.VERTICAL, center - span / 2, center + (span + 1) / 2))
                },
                enabled = enabled && bands.size < CutoutSqueeze.MAX_INPUT_BANDS,
            ) { Text(stringResource(R.string.cutout_add_vertical), fontSize = 11.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CutSeparatorStyle.entries.forEach { style ->
                FilterChip(
                    selected = separatorStyle == style,
                    onClick = { actions.onSeparatorStyleChange(style) },
                    enabled = enabled,
                    label = { Text(stringResource(style.labelRes()), fontSize = 10.sp) },
                )
            }
        }
        if (bands.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                bands.forEachIndexed { index, band ->
                    FilterChip(
                        selected = selectedIndex == index,
                        onClick = { actions.onSelectedIndexChange(index) },
                        label = {
                            Text(
                                stringResource(
                                    if (band.axis == CutAxis.HORIZONTAL) R.string.cutout_horizontal_band_short
                                    else R.string.cutout_vertical_band_short,
                                    index + 1,
                                ),
                                fontSize = 10.sp,
                            )
                        },
                    )
                }
            }
        }
        bands.getOrNull(selectedIndex.coerceAtLeast(0))?.let { band ->
            val index = selectedIndex.coerceAtLeast(0)
            val limit = if (band.axis == CutAxis.HORIZONTAL) sourceHeight else sourceWidth
            var draftRange by remember(band) {
                mutableStateOf(band.start.toFloat()..band.endExclusive.toFloat())
            }
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(
                            if (band.axis == CutAxis.HORIZONTAL) R.string.cutout_horizontal_band
                            else R.string.cutout_vertical_band,
                            index + 1,
                            band.length,
                        ),
                        color = if (selectedIndex == index) Primary else OnSurface,
                        fontSize = 11.sp,
                    )
                    TextButton(onClick = { actions.onRemoveBand(index) }, enabled = enabled) {
                        Text(stringResource(R.string.cutout_remove), fontSize = 11.sp)
                    }
                }
                RangeSlider(
                    value = draftRange,
                    onValueChange = { range ->
                        val start = range.start.roundToInt().coerceIn(0, limit - 1)
                        val end = range.endInclusive.roundToInt().coerceIn(start + 1, limit)
                        draftRange = start.toFloat()..end.toFloat()
                    },
                    onValueChangeFinished = {
                        actions.onUpdateBand(
                            index,
                            band.copy(
                                start = draftRange.start.roundToInt(),
                                endExclusive = draftRange.endInclusive.roundToInt(),
                            ),
                        )
                    },
                    valueRange = 0f..limit.toFloat(),
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "${band.axis.name.lowercase()} cut band ${index + 1}, ${band.start} to ${band.endExclusive}"
                    },
                )
            }
        }
        if (bands.isNotEmpty()) {
            TextButton(onClick = actions.onClearBands, enabled = enabled) {
                Text(stringResource(R.string.cutout_clear_all), fontSize = 11.sp)
            }
        }
    }
}

private fun CutSeparatorStyle.labelRes(): Int = when (this) {
    CutSeparatorStyle.STRAIGHT -> R.string.cutout_style_straight
    CutSeparatorStyle.DASHED -> R.string.cutout_style_dashed
    CutSeparatorStyle.TORN -> R.string.cutout_style_torn
}

internal fun DrawScope.drawCutBandOverlays(
    bands: List<CutBand>,
    selectedIndex: Int,
    imageLeft: Float,
    imageTop: Float,
    imageWidth: Float,
    imageHeight: Float,
    imageScale: Float,
    selectedColor: androidx.compose.ui.graphics.Color,
    normalColor: androidx.compose.ui.graphics.Color,
) {
    bands.forEachIndexed { index, band ->
        val selected = index == selectedIndex || (selectedIndex < 0 && index == 0)
        val left = if (band.axis == CutAxis.VERTICAL) imageLeft + band.start * imageScale else imageLeft
        val top = if (band.axis == CutAxis.HORIZONTAL) imageTop + band.start * imageScale else imageTop
        val width = if (band.axis == CutAxis.VERTICAL) band.length * imageScale else imageWidth
        val height = if (band.axis == CutAxis.HORIZONTAL) band.length * imageScale else imageHeight
        val color = if (selected) selectedColor else normalColor
        drawRect(color.copy(alpha = if (selected) 0.28f else 0.18f), Offset(left, top), Size(width, height))
        drawRect(color, Offset(left, top), Size(width, height), style = Stroke(if (selected) 3.dp.toPx() else 1.5f.dp.toPx()))
    }
}
