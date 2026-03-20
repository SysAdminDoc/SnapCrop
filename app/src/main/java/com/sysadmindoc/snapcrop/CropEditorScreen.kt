package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.*
import kotlin.math.min
import kotlin.math.roundToInt

private enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER
}

@Composable
fun CropEditorScreen(
    bitmap: Bitmap,
    initialCropRect: Rect,
    cropMethod: String,
    onSave: (Rect) -> Unit,
    onShare: (Rect) -> Unit,
    onDiscard: () -> Unit,
    onAutoCrop: () -> Rect,
    onSmartCrop: () -> Unit
) {
    val imageBitmap = remember { bitmap.asImageBitmap() }

    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var cropLeft by remember { mutableIntStateOf(initialCropRect.left) }
    var cropTop by remember { mutableIntStateOf(initialCropRect.top) }
    var cropRight by remember { mutableIntStateOf(initialCropRect.right) }
    var cropBottom by remember { mutableIntStateOf(initialCropRect.bottom) }

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var previewMode by remember { mutableStateOf(false) }

    // Update crop when initialCropRect changes (from SmartCrop callback)
    LaunchedEffect(initialCropRect) {
        cropLeft = initialCropRect.left
        cropTop = initialCropRect.top
        cropRight = initialCropRect.right
        cropBottom = initialCropRect.bottom
    }

    val handleRadius = with(LocalDensity.current) { 14.dp.toPx() }
    val hitRadius = with(LocalDensity.current) { 28.dp.toPx() }

    fun bitmapToScreenX(bx: Int) = offsetX + bx * scaleX
    fun bitmapToScreenY(by: Int) = offsetY + by * scaleY

    fun findHandle(pos: Offset): DragHandle {
        val corners = listOf(
            DragHandle.TOP_LEFT to Offset(bitmapToScreenX(cropLeft), bitmapToScreenY(cropTop)),
            DragHandle.TOP_RIGHT to Offset(bitmapToScreenX(cropRight), bitmapToScreenY(cropTop)),
            DragHandle.BOTTOM_LEFT to Offset(bitmapToScreenX(cropLeft), bitmapToScreenY(cropBottom)),
            DragHandle.BOTTOM_RIGHT to Offset(bitmapToScreenX(cropRight), bitmapToScreenY(cropBottom)),
        )

        for ((handle, corner) in corners) {
            if ((pos - corner).getDistance() < hitRadius) return handle
        }

        val sl = bitmapToScreenX(cropLeft)
        val sr = bitmapToScreenX(cropRight)
        val st = bitmapToScreenY(cropTop)
        val sb = bitmapToScreenY(cropBottom)

        if (pos.x in sl..sr && kotlin.math.abs(pos.y - st) < hitRadius) return DragHandle.TOP
        if (pos.x in sl..sr && kotlin.math.abs(pos.y - sb) < hitRadius) return DragHandle.BOTTOM
        if (pos.y in st..sb && kotlin.math.abs(pos.x - sl) < hitRadius) return DragHandle.LEFT
        if (pos.y in st..sb && kotlin.math.abs(pos.x - sr) < hitRadius) return DragHandle.RIGHT

        if (pos.x in sl..sr && pos.y in st..sb) return DragHandle.CENTER

        return DragHandle.NONE
    }

    val cropW = cropRight - cropLeft
    val cropH = cropBottom - cropTop
    val cropInfo = "${cropW} x ${cropH}"

    val methodLabel = when (cropMethod) {
        "border" -> "Border"
        "statusbar" -> "System bars"
        "ai" -> "AI"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDiscard) {
                Icon(Icons.Default.Close, "Discard", tint = OnSurface)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (methodLabel.isNotEmpty()) {
                    Surface(
                        color = SurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            methodLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(cropInfo, color = OnSurfaceVariant, fontSize = 13.sp)
            }

            Row {
                IconButton(onClick = { previewMode = !previewMode }) {
                    Icon(
                        if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Preview",
                        tint = if (previewMode) Primary else OnSurface
                    )
                }
                IconButton(onClick = { onShare(Rect(cropLeft, cropTop, cropRight, cropBottom)) }) {
                    Icon(Icons.Default.Share, "Share", tint = OnSurface)
                }
            }
        }

        // Canvas area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Preview mode: show only the cropped portion
            AnimatedVisibility(
                visible = previewMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val croppedPreview = remember(cropLeft, cropTop, cropRight, cropBottom) {
                    try {
                        Bitmap.createBitmap(
                            bitmap,
                            cropLeft.coerceAtLeast(0),
                            cropTop.coerceAtLeast(0),
                            cropW.coerceAtMost(bitmap.width - cropLeft.coerceAtLeast(0)),
                            cropH.coerceAtMost(bitmap.height - cropTop.coerceAtLeast(0))
                        ).asImageBitmap()
                    } catch (_: Exception) {
                        imageBitmap
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val imgW = croppedPreview.width.toFloat()
                    val imgH = croppedPreview.height.toFloat()
                    val scale = min(canvasW / imgW, canvasH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val ox = (canvasW - drawW) / 2
                    val oy = (canvasH - drawH) / 2

                    drawImage(
                        image = croppedPreview,
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                    )
                }
            }

            // Edit mode: show full image with crop overlay
            AnimatedVisibility(
                visible = !previewMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectDragGestures(
                                onDragStart = { pos -> activeHandle = findHandle(pos) },
                                onDragEnd = { activeHandle = DragHandle.NONE },
                                onDragCancel = { activeHandle = DragHandle.NONE },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = (dragAmount.x / scaleX).roundToInt()
                                    val dy = (dragAmount.y / scaleY).roundToInt()
                                    val minSize = 50

                                    when (activeHandle) {
                                        DragHandle.TOP_LEFT -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize)
                                            cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize)
                                        }
                                        DragHandle.TOP_RIGHT -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width)
                                            cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize)
                                        }
                                        DragHandle.BOTTOM_LEFT -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height)
                                        }
                                        DragHandle.BOTTOM_RIGHT -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height)
                                        }
                                        DragHandle.TOP -> {
                                            cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize)
                                        }
                                        DragHandle.BOTTOM -> {
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height)
                                        }
                                        DragHandle.LEFT -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize)
                                        }
                                        DragHandle.RIGHT -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width)
                                        }
                                        DragHandle.CENTER -> {
                                            val w = cropRight - cropLeft
                                            val h = cropBottom - cropTop
                                            var newL = cropLeft + dx
                                            var newT = cropTop + dy
                                            newL = newL.coerceIn(0, bitmap.width - w)
                                            newT = newT.coerceIn(0, bitmap.height - h)
                                            cropLeft = newL
                                            cropTop = newT
                                            cropRight = newL + w
                                            cropBottom = newT + h
                                        }
                                        DragHandle.NONE -> {}
                                    }
                                }
                            )
                        }
                ) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()

                    val scale = min(canvasW / imgW, canvasH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val ox = (canvasW - drawW) / 2
                    val oy = (canvasH - drawH) / 2

                    scaleX = scale
                    scaleY = scale
                    offsetX = ox
                    offsetY = oy

                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                    )

                    val sl = ox + cropLeft * scale
                    val st = oy + cropTop * scale
                    val sr = ox + cropRight * scale
                    val sb = oy + cropBottom * scale

                    // Dim overlay outside crop
                    drawRect(DimOverlay, Offset(ox, oy), Size(drawW, st - oy))
                    drawRect(DimOverlay, Offset(ox, sb), Size(drawW, oy + drawH - sb))
                    drawRect(DimOverlay, Offset(ox, st), Size(sl - ox, sb - st))
                    drawRect(DimOverlay, Offset(sr, st), Size(ox + drawW - sr, sb - st))

                    // Crop border
                    drawRect(
                        CropBorder, Offset(sl, st), Size(sr - sl, sb - st),
                        style = Stroke(2.dp.toPx())
                    )

                    // Rule of thirds
                    val thirdW = (sr - sl) / 3
                    val thirdH = (sb - st) / 3
                    val gridColor = CropBorder.copy(alpha = 0.3f)
                    for (i in 1..2) {
                        drawLine(gridColor, Offset(sl + thirdW * i, st), Offset(sl + thirdW * i, sb), 1f)
                        drawLine(gridColor, Offset(sl, st + thirdH * i), Offset(sr, st + thirdH * i), 1f)
                    }

                    // Corner handles
                    drawCornerHandle(sl, st, handleRadius, false, false)
                    drawCornerHandle(sr, st, handleRadius, true, false)
                    drawCornerHandle(sl, sb, handleRadius, false, true)
                    drawCornerHandle(sr, sb, handleRadius, true, true)
                }
            }
        }

        // Bottom toolbar — two rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Crop method buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reset
                FilledTonalButton(
                    onClick = {
                        cropLeft = 0; cropTop = 0
                        cropRight = bitmap.width; cropBottom = bitmap.height
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CropFree, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", fontSize = 13.sp)
                }

                // Auto (border scan)
                FilledTonalButton(
                    onClick = {
                        val rect = onAutoCrop()
                        cropLeft = rect.left; cropTop = rect.top
                        cropRight = rect.right; cropBottom = rect.bottom
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto", fontSize = 13.sp)
                }

                // AI (ML Kit)
                FilledTonalButton(
                    onClick = { onSmartCrop() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Tertiary.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(16.dp), tint = Tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text("AI", fontSize = 13.sp, color = Tertiary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Save / Share row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share
                OutlinedButton(
                    onClick = { onShare(Rect(cropLeft, cropTop, cropRight, cropBottom)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }

                // Crop & Save
                Button(
                    onClick = { onSave(Rect(cropLeft, cropTop, cropRight, cropBottom)) },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Crop, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Crop & Save", color = Color.Black)
                }
            }
        }
    }
}

private fun DrawScope.drawCornerHandle(
    x: Float, y: Float, radius: Float,
    isRight: Boolean, isBottom: Boolean
) {
    val len = radius * 2
    val stroke = 4.dp.toPx()
    val hDir = if (isRight) -1f else 1f
    val vDir = if (isBottom) -1f else 1f

    drawLine(CropHandle, Offset(x, y), Offset(x + len * hDir, y), strokeWidth = stroke)
    drawLine(CropHandle, Offset(x, y), Offset(x, y + len * vDir), strokeWidth = stroke)
}
