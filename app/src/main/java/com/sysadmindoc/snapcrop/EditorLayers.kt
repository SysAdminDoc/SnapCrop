package com.sysadmindoc.snapcrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary

private fun DrawPath.layerTitle(): String = when {
    shapeType == "text" && !text.isNullOrBlank() -> "Text: ${text.take(22)}${if (text.length > 22) "..." else ""}"
    shapeType == "emoji" && !text.isNullOrBlank() -> "Emoji $text"
    shapeType == "callout" && !text.isNullOrBlank() -> "Callout $text"
    shapeType == "rect" -> "Rectangle"
    shapeType == "circle" -> "Circle"
    shapeType == "line" -> "Line"
    shapeType == "highlight" -> "Highlight"
    shapeType == "spotlight" -> "Spotlight"
    shapeType == "magnifier" -> "Magnifier"
    shapeType == "neon" -> "Neon stroke"
    shapeType == "blur" -> "Blur brush"
    shapeType == "eraser" -> "Eraser"
    shapeType == "fill" -> "Flood fill"
    shapeType == "smart_erase" || shapeType == "heal" -> "Smart erase"
    isArrow -> "Arrow"
    else -> "Pen stroke"
}

private fun DrawPath.layerSubtitle(indexFromBottom: Int): String {
    val order = "Layer ${indexFromBottom + 1}"
    val shape = if (points.size == 1) "1 point" else "${points.size} points"
    val state = if (visible) "Visible" else "Hidden"
    return "$order - $state - $shape"
}

@Composable
internal fun DrawLayerPanel(
    drawPaths: List<DrawPath>,
    onMoveLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleVisible: (index: Int) -> Unit,
    onDeleteLayer: (index: Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = SurfaceVariant.copy(alpha = 0.36f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Layers", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Top layers render last", color = OnSurfaceVariant, fontSize = 10.sp)
                }
                Text("${drawPaths.size}", color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            if (drawPaths.isEmpty()) {
                Text(
                    "Draw, place text, or add a callout to create a layer.",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                drawPaths.asReversed().forEachIndexed { visualIndex, layer ->
                    val actualIndex = drawPaths.lastIndex - visualIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (layer.visible) Color.Black.copy(alpha = 0.18f)
                                else Color.Black.copy(alpha = 0.08f),
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
                                layer.layerTitle(),
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
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                if (layer.visible) "Hide layer" else "Show layer",
                                tint = if (layer.visible) Secondary else OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        TextButton(
                            onClick = { onMoveLayer(actualIndex, actualIndex + 1) },
                            enabled = actualIndex < drawPaths.lastIndex,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Up",
                                color = if (actualIndex < drawPaths.lastIndex) Secondary else OnSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        TextButton(
                            onClick = { onMoveLayer(actualIndex, actualIndex - 1) },
                            enabled = actualIndex > 0,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Down",
                                color = if (actualIndex > 0) Secondary else OnSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = { onDeleteLayer(actualIndex) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete layer", tint = Tertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
