package com.sysadmindoc.snapcrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary

@Composable
internal fun layerTitle(layer: DrawPath): String = when {
    layer.shapeType == "text" && !layer.text.isNullOrBlank() -> "${stringResource(R.string.tool_text)}: ${layer.text.take(22)}${if (layer.text.length > 22) "..." else ""}"
    layer.shapeType == "emoji" && !layer.text.isNullOrBlank() -> "${stringResource(R.string.tool_emoji)} ${layer.text}"
    layer.shapeType == "callout" && !layer.text.isNullOrBlank() -> "${stringResource(R.string.tool_callout)} ${layer.text}"
    layer.shapeType == "rect" -> stringResource(R.string.tool_rect)
    layer.shapeType == "circle" -> stringResource(R.string.tool_circle)
    layer.shapeType == "line" -> stringResource(R.string.tool_line)
    layer.shapeType == "measure" -> stringResource(R.string.tool_measure)
    layer.shapeType == "highlight" -> stringResource(R.string.tool_highlight)
    layer.shapeType == "spotlight" -> stringResource(R.string.tool_spotlight)
    layer.shapeType == "magnifier" -> stringResource(R.string.tool_magnifier)
    layer.shapeType == "neon" -> stringResource(R.string.tool_neon)
    layer.shapeType == "blur" -> stringResource(R.string.tool_blur)
    layer.shapeType == "eraser" -> stringResource(R.string.tool_eraser)
    layer.shapeType == "fill" -> stringResource(R.string.tool_fill)
    layer.shapeType == "smart_erase" || layer.shapeType == "heal" -> stringResource(R.string.tool_smart_erase)
    layer.isArrow -> stringResource(R.string.tool_arrow)
    else -> stringResource(R.string.tool_pen)
}

@Composable
private fun LayerXfBtn(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(label, color = Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun DrawPath.layerSubtitle(indexFromBottom: Int): String {
    val shape = if (points.size == 1) stringResource(R.string.layer_points_one)
    else stringResource(R.string.layer_points_other, points.size)
    val state = if (visible) stringResource(R.string.layer_state_visible)
    else stringResource(R.string.layer_state_hidden)
    return stringResource(R.string.layer_subtitle, indexFromBottom + 1, state, shape)
}

@Composable
internal fun DrawLayerPanel(
    drawPaths: List<DrawPath>,
    onMoveLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleVisible: (index: Int) -> Unit,
    onDeleteLayer: (index: Int) -> Unit,
    selectedIndex: Int = -1,
    selectedIndices: Set<Int> = if (selectedIndex >= 0) setOf(selectedIndex) else emptySet(),
    onSelectLayer: (index: Int) -> Unit = {},
    onTransformLayer: (index: Int, dx: Float, dy: Float, scaleMul: Float, dRotation: Float) -> Unit = { _, _, _, _, _ -> },
    onResetTransform: (index: Int) -> Unit = {},
    onAlign: (LayerAlignment) -> Unit = {},
    onDistribute: (LayerDistribution) -> Unit = {},
    onDuplicate: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = SurfaceVariant.copy(alpha = 0.36f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.draw_layers), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.draw_layers_subtitle), color = OnSurfaceVariant, fontSize = 10.sp)
                }
                Text("${drawPaths.size}", color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            if (selectedIndices.isNotEmpty()) {
                Text(
                    stringResource(R.string.layer_selected_count, selectedIndices.size),
                    color = Secondary,
                    fontSize = 10.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    LayerXfBtn(stringResource(R.string.layer_align_left_short), stringResource(R.string.layer_align_left)) { onAlign(LayerAlignment.LEFT) }
                    LayerXfBtn(stringResource(R.string.layer_align_center_short), stringResource(R.string.layer_align_center)) { onAlign(LayerAlignment.CENTER) }
                    LayerXfBtn(stringResource(R.string.layer_align_right_short), stringResource(R.string.layer_align_right)) { onAlign(LayerAlignment.RIGHT) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    LayerXfBtn(stringResource(R.string.layer_align_top_short), stringResource(R.string.layer_align_top)) { onAlign(LayerAlignment.TOP) }
                    LayerXfBtn(stringResource(R.string.layer_align_middle_short), stringResource(R.string.layer_align_middle)) { onAlign(LayerAlignment.MIDDLE) }
                    LayerXfBtn(stringResource(R.string.layer_align_bottom_short), stringResource(R.string.layer_align_bottom)) { onAlign(LayerAlignment.BOTTOM) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (selectedIndices.size >= 3) {
                        LayerXfBtn(stringResource(R.string.layer_distribute_horizontal_short), stringResource(R.string.layer_distribute_horizontal)) {
                            onDistribute(LayerDistribution.HORIZONTAL)
                        }
                        LayerXfBtn(stringResource(R.string.layer_distribute_vertical_short), stringResource(R.string.layer_distribute_vertical)) {
                            onDistribute(LayerDistribution.VERTICAL)
                        }
                    }
                    LayerXfBtn(stringResource(R.string.layer_duplicate_short), stringResource(R.string.layer_duplicate)) { onDuplicate() }
                }
            }

            if (drawPaths.isEmpty()) {
                Text(
                    stringResource(R.string.draw_layers_empty),
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                drawPaths.asReversed().forEachIndexed { visualIndex, layer ->
                    val actualIndex = drawPaths.lastIndex - visualIndex
                    val title = layerTitle(layer)
                    val isSelected = actualIndex in selectedIndices
                    // Resolve a11y descriptions outside the non-composable semantics lambdas.
                    val visibilityCd = if (layer.visible) stringResource(R.string.layer_hide_cd, title)
                    else stringResource(R.string.layer_show_cd, title)
                    val moveUpCd = stringResource(R.string.layer_move_up_cd, title)
                    val moveDownCd = stringResource(R.string.layer_move_down_cd, title)
                    val deleteCd = stringResource(R.string.layer_delete_cd, title)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayer(actualIndex) }
                            .semantics { selected = isSelected }
                            .background(
                                when {
                                    isSelected -> Secondary.copy(alpha = 0.22f)
                                    layer.visible -> OnSurface.copy(alpha = 0.10f)
                                    else -> OnSurface.copy(alpha = 0.05f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(18.dp)
                                .background(Color(layer.color), RoundedCornerShape(4.dp))
                                .border(1.dp, OnSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                color = if (layer.visible) OnSurface else OnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                layer.layerSubtitle(actualIndex),
                                color = OnSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleVisible(actualIndex) },
                            modifier = Modifier.size(36.dp).semantics {
                                contentDescription = visibilityCd
                            }
                        ) {
                            Icon(
                                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null,
                                tint = if (layer.visible) Secondary else OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        TextButton(
                            onClick = { onMoveLayer(actualIndex, actualIndex + 1) },
                            enabled = actualIndex < drawPaths.lastIndex,
                            modifier = Modifier.semantics {
                                contentDescription = moveUpCd
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                stringResource(R.string.layer_move_up),
                                color = if (actualIndex < drawPaths.lastIndex) Secondary else OnSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        TextButton(
                            onClick = { onMoveLayer(actualIndex, actualIndex - 1) },
                            enabled = actualIndex > 0,
                            modifier = Modifier.semantics {
                                contentDescription = moveDownCd
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                stringResource(R.string.layer_move_down),
                                color = if (actualIndex > 0) Secondary else OnSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = { onDeleteLayer(actualIndex) },
                            modifier = Modifier.size(36.dp).semantics {
                                contentDescription = deleteCd
                            }
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Tertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (isSelected) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(R.string.layer_transform_hint),
                                color = Secondary,
                                fontSize = 10.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                LayerXfBtn("↺", stringResource(R.string.layer_rotate_left)) { onTransformLayer(actualIndex, 0f, 0f, 1f, -15f) }
                                LayerXfBtn("↻", stringResource(R.string.layer_rotate_right)) { onTransformLayer(actualIndex, 0f, 0f, 1f, 15f) }
                                LayerXfBtn("−", stringResource(R.string.layer_shrink)) { onTransformLayer(actualIndex, 0f, 0f, 0.85f, 0f) }
                                LayerXfBtn("+", stringResource(R.string.layer_grow)) { onTransformLayer(actualIndex, 0f, 0f, 1.18f, 0f) }
                                LayerXfBtn(stringResource(R.string.layer_reset), stringResource(R.string.layer_reset_cd)) { onResetTransform(actualIndex) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                LayerXfBtn("←", stringResource(R.string.layer_move_left)) { onTransformLayer(actualIndex, -1f, 0f, 1f, 0f) }
                                LayerXfBtn("→", stringResource(R.string.layer_move_right)) { onTransformLayer(actualIndex, 1f, 0f, 1f, 0f) }
                                LayerXfBtn("↑", stringResource(R.string.layer_move_up_dir)) { onTransformLayer(actualIndex, 0f, -1f, 1f, 0f) }
                                LayerXfBtn("↓", stringResource(R.string.layer_move_down_dir)) { onTransformLayer(actualIndex, 0f, 1f, 1f, 0f) }
                            }
                        }
                    }
                }
            }
        }
    }
}
