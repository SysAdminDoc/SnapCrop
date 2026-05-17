package com.sysadmindoc.snapcrop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.sysadmindoc.snapcrop.ui.theme.CropHandle

internal fun getGradientBrush(
    gradIdx: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Brush? {
    val (startColor, endColor) = when (gradIdx) {
        1 -> Color(0xFFFF6B35) to Color(0xFFF7C948) // Sunset
        2 -> Color(0xFF0077B6) to Color(0xFF00B4D8) // Ocean
        3 -> Color(0xFF7B2FBE) to Color(0xFFE040FB) // Purple
        4 -> Color(0xFF1A1A2E) to Color(0xFF16213E) // Dark
        5 -> Color(0xFF00B09B) to Color(0xFF96C93D) // Mint
        6 -> Color(0xFFFF416C) to Color(0xFFFF4B2B) // Fire
        else -> return null
    }
    return Brush.linearGradient(
        colors = listOf(startColor, endColor),
        start = Offset(left, top),
        end = Offset(right, bottom)
    )
}

internal fun DrawScope.drawCornerHandle(
    x: Float,
    y: Float,
    radius: Float,
    isRight: Boolean,
    isBottom: Boolean
) {
    val len = radius * 2
    val stroke = 4.dp.toPx()
    val hDir = if (isRight) -1f else 1f
    val vDir = if (isBottom) -1f else 1f
    drawLine(CropHandle, Offset(x, y), Offset(x + len * hDir, y), strokeWidth = stroke)
    drawLine(CropHandle, Offset(x, y), Offset(x, y + len * vDir), strokeWidth = stroke)
}
