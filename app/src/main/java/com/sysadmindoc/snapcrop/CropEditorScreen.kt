package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
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

private enum class AspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f)
}

@Composable
fun CropEditorScreen(
    bitmap: Bitmap,
    initialCropRect: Rect,
    cropMethod: String,
    onSave: (Rect) -> Unit,
    onSaveCopy: (Rect) -> Unit,
    onShare: (Rect) -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onAutoCrop: () -> Rect,
    onSmartCrop: () -> Unit,
    onRotate: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

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
    var selectedRatio by remember { mutableStateOf(AspectRatio.FREE) }

    // Undo/Redo stacks
    val undoStack = remember { mutableStateListOf<Rect>() }
    val redoStack = remember { mutableStateListOf<Rect>() }

    fun pushUndo() {
        undoStack.add(Rect(cropLeft, cropTop, cropRight, cropBottom))
        redoStack.clear()
        if (undoStack.size > 30) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(Rect(cropLeft, cropTop, cropRight, cropBottom))
        val prev = undoStack.removeLast()
        cropLeft = prev.left; cropTop = prev.top
        cropRight = prev.right; cropBottom = prev.bottom
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(Rect(cropLeft, cropTop, cropRight, cropBottom))
        val next = redoStack.removeLast()
        cropLeft = next.left; cropTop = next.top
        cropRight = next.right; cropBottom = next.bottom
    }

    LaunchedEffect(initialCropRect) {
        cropLeft = initialCropRect.left
        cropTop = initialCropRect.top
        cropRight = initialCropRect.right
        cropBottom = initialCropRect.bottom
    }

    // Re-key on bitmap identity change (rotation)
    LaunchedEffect(bitmap.width, bitmap.height) {
        if (cropRight > bitmap.width) cropRight = bitmap.width
        if (cropBottom > bitmap.height) cropBottom = bitmap.height
        cropLeft = cropLeft.coerceIn(0, cropRight - 50)
        cropTop = cropTop.coerceIn(0, cropBottom - 50)
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

    fun applyAspectRatio(ratio: AspectRatio, anchorCenter: Boolean = true) {
        val r = ratio.ratio ?: return
        val cw = cropRight - cropLeft
        val ch = cropBottom - cropTop
        val cx = cropLeft + cw / 2
        val cy = cropTop + ch / 2

        // Fit the ratio within current bounds
        var newW: Int
        var newH: Int
        if (cw.toFloat() / ch > r) {
            newH = ch
            newW = (ch * r).toInt()
        } else {
            newW = cw
            newH = (cw / r).toInt()
        }

        newW = newW.coerceAtLeast(50)
        newH = newH.coerceAtLeast(50)

        if (anchorCenter) {
            cropLeft = (cx - newW / 2).coerceAtLeast(0)
            cropTop = (cy - newH / 2).coerceAtLeast(0)
            cropRight = (cropLeft + newW).coerceAtMost(bitmap.width)
            cropBottom = (cropTop + newH).coerceAtMost(bitmap.height)
            // Re-adjust if clamped
            cropLeft = (cropRight - newW).coerceAtLeast(0)
            cropTop = (cropBottom - newH).coerceAtLeast(0)
        }
    }

    fun constrainToRatio(handle: DragHandle, dx: Int, dy: Int) {
        val ratio = selectedRatio.ratio
        val minSize = 50

        when (handle) {
            DragHandle.CENTER -> {
                val w = cropRight - cropLeft
                val h = cropBottom - cropTop
                var newL = (cropLeft + dx).coerceIn(0, bitmap.width - w)
                var newT = (cropTop + dy).coerceIn(0, bitmap.height - h)
                cropLeft = newL; cropTop = newT
                cropRight = newL + w; cropBottom = newT + h
            }
            else -> {
                // Apply unconstrained first
                when (handle) {
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
                    DragHandle.TOP -> cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize)
                    DragHandle.BOTTOM -> cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height)
                    DragHandle.LEFT -> cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize)
                    DragHandle.RIGHT -> cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width)
                    else -> {}
                }

                // Re-apply aspect ratio constraint if locked
                if (ratio != null) {
                    val cw = cropRight - cropLeft
                    val ch = cropBottom - cropTop
                    val currentRatio = cw.toFloat() / ch
                    if (currentRatio > ratio) {
                        // Too wide — shrink width
                        val targetW = (ch * ratio).toInt().coerceAtLeast(minSize)
                        when (handle) {
                            DragHandle.TOP_LEFT, DragHandle.BOTTOM_LEFT, DragHandle.LEFT ->
                                cropLeft = (cropRight - targetW).coerceAtLeast(0)
                            else -> cropRight = (cropLeft + targetW).coerceAtMost(bitmap.width)
                        }
                    } else {
                        // Too tall — shrink height
                        val targetH = (cw / ratio).toInt().coerceAtLeast(minSize)
                        when (handle) {
                            DragHandle.TOP_LEFT, DragHandle.TOP_RIGHT, DragHandle.TOP ->
                                cropTop = (cropBottom - targetH).coerceAtLeast(0)
                            else -> cropBottom = (cropTop + targetH).coerceAtMost(bitmap.height)
                        }
                    }
                }
            }
        }
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
            Row {
                IconButton(onClick = onDiscard) {
                    Icon(Icons.Default.Close, "Discard", tint = OnSurface)
                }
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Undo, "Undo",
                        tint = if (undoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f))
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Redo, "Redo",
                        tint = if (redoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f))
                }
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
                IconButton(onClick = onRotate) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.RotateRight, "Rotate", tint = OnSurface)
                }
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

        // Aspect ratio chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatio.entries.forEach { ratio ->
                val selected = selectedRatio == ratio
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedRatio = ratio
                        if (ratio.ratio != null) applyAspectRatio(ratio)
                    },
                    label = { Text(ratio.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer,
                        selectedLabelColor = Primary,
                        containerColor = SurfaceVariant,
                        labelColor = OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Canvas area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (previewMode) {
                val croppedPreview = remember(cropLeft, cropTop, cropRight, cropBottom, bitmap) {
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
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    activeHandle = findHandle(pos)
                                    if (activeHandle != DragHandle.NONE) pushUndo()
                                },
                                onDragEnd = { activeHandle = DragHandle.NONE },
                                onDragCancel = { activeHandle = DragHandle.NONE },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = (dragAmount.x / scaleX).roundToInt()
                                    val dy = (dragAmount.y / scaleY).roundToInt()
                                    constrainToRatio(activeHandle, dx, dy)
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

                    // Edge midpoint dots
                    val midR = handleRadius * 0.5f
                    val midX = (sl + sr) / 2
                    val midY = (st + sb) / 2
                    drawCircle(CropHandle, midR, Offset(midX, st))  // top
                    drawCircle(CropHandle, midR, Offset(midX, sb))  // bottom
                    drawCircle(CropHandle, midR, Offset(sl, midY))  // left
                    drawCircle(CropHandle, midR, Offset(sr, midY))  // right
                }
            }
        }

        // Bottom toolbar
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
                FilledTonalButton(
                    onClick = {
                        cropLeft = 0; cropTop = 0
                        cropRight = bitmap.width; cropBottom = bitmap.height
                        selectedRatio = AspectRatio.FREE
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CropFree, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = {
                        val rect = onAutoCrop()
                        cropLeft = rect.left; cropTop = rect.top
                        cropRight = rect.right; cropBottom = rect.bottom
                        selectedRatio = AspectRatio.FREE
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto", fontSize = 13.sp)
                }

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

            // Save / Delete row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete original
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                }

                // Save Copy (keep original)
                OutlinedButton(
                    onClick = { onSaveCopy(Rect(cropLeft, cropTop, cropRight, cropBottom)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", fontSize = 13.sp)
                }

                // Crop & Replace
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
