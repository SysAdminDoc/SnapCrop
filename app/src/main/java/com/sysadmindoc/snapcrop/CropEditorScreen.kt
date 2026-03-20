package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
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
import androidx.compose.ui.Alignment
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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

data class DrawPath(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val isArrow: Boolean = false,
    val shapeType: String? = null, // "rect", "circle", or "text"
    val text: String? = null,
    val filled: Boolean = false
)

private enum class EditMode { CROP, PIXELATE, DRAW }
private enum class DrawTool(val label: String) {
    PEN("Pen"), ARROW("Arrow"), RECT("Rect"), CIRCLE("Circle"), TEXT("Text")
}

private val drawColors = listOf(
    0xFFFF0000.toInt() to "Red",
    0xFFFFFF00.toInt() to "Yellow",
    0xFF00FF00.toInt() to "Green",
    0xFF89B4FA.toInt() to "Blue",
    0xFFFFFFFF.toInt() to "White",
    0xFF000000.toInt() to "Black"
)

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
    onSave: (Rect, List<Rect>, List<DrawPath>) -> Unit,
    onSaveCopy: (Rect, List<Rect>, List<DrawPath>) -> Unit,
    onShare: (Rect, List<Rect>, List<DrawPath>) -> Unit,
    onCopyClipboard: (Rect, List<Rect>, List<DrawPath>) -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onAutoCrop: () -> Rect,
    onSmartCrop: () -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
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
    var aiLoading by remember { mutableStateOf(false) }

    // Edit modes
    var editMode by remember { mutableStateOf(EditMode.CROP) }
    val pixelateRects = remember { mutableStateListOf<Rect>() }
    var pixDragStart by remember { mutableStateOf<Offset?>(null) }
    var pixDragCurrent by remember { mutableStateOf<Offset?>(null) }

    // Draw mode
    val drawPaths = remember { mutableStateListOf<DrawPath>() }
    var currentDrawPoints by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var drawColor by remember { mutableIntStateOf(0xFFFF0000.toInt()) }
    var drawStrokeWidth by remember { mutableFloatStateOf(6f) }
    var drawTool by remember { mutableStateOf(DrawTool.PEN) }
    var shapeFilled by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogValue by remember { mutableStateOf("") }
    var textPlacePoint by remember { mutableStateOf<PointF?>(null) }

    val context = LocalContext.current
    fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

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
        haptic()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(Rect(cropLeft, cropTop, cropRight, cropBottom))
        val next = redoStack.removeLast()
        cropLeft = next.left; cropTop = next.top
        cropRight = next.right; cropBottom = next.bottom
        haptic()
    }

    LaunchedEffect(initialCropRect) {
        cropLeft = initialCropRect.left
        cropTop = initialCropRect.top
        cropRight = initialCropRect.right
        cropBottom = initialCropRect.bottom
        aiLoading = false
    }

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

    fun applyAspectRatio(ratio: AspectRatio) {
        val r = ratio.ratio ?: return
        val cw = cropRight - cropLeft
        val ch = cropBottom - cropTop
        val cx = cropLeft + cw / 2
        val cy = cropTop + ch / 2
        var newW: Int; var newH: Int
        if (cw.toFloat() / ch > r) { newH = ch; newW = (ch * r).toInt() }
        else { newW = cw; newH = (cw / r).toInt() }
        newW = newW.coerceAtLeast(50); newH = newH.coerceAtLeast(50)
        cropLeft = (cx - newW / 2).coerceAtLeast(0)
        cropTop = (cy - newH / 2).coerceAtLeast(0)
        cropRight = (cropLeft + newW).coerceAtMost(bitmap.width)
        cropBottom = (cropTop + newH).coerceAtMost(bitmap.height)
        cropLeft = (cropRight - newW).coerceAtLeast(0)
        cropTop = (cropBottom - newH).coerceAtLeast(0)
    }

    fun constrainToRatio(handle: DragHandle, dx: Int, dy: Int) {
        val ratio = selectedRatio.ratio
        val minSize = 50
        when (handle) {
            DragHandle.CENTER -> {
                val w = cropRight - cropLeft; val h = cropBottom - cropTop
                val newL = (cropLeft + dx).coerceIn(0, bitmap.width - w)
                val newT = (cropTop + dy).coerceIn(0, bitmap.height - h)
                cropLeft = newL; cropTop = newT; cropRight = newL + w; cropBottom = newT + h
            }
            else -> {
                when (handle) {
                    DragHandle.TOP_LEFT -> { cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize); cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize) }
                    DragHandle.TOP_RIGHT -> { cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width); cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize) }
                    DragHandle.BOTTOM_LEFT -> { cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize); cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height) }
                    DragHandle.BOTTOM_RIGHT -> { cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width); cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height) }
                    DragHandle.TOP -> cropTop = (cropTop + dy).coerceIn(0, cropBottom - minSize)
                    DragHandle.BOTTOM -> cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, bitmap.height)
                    DragHandle.LEFT -> cropLeft = (cropLeft + dx).coerceIn(0, cropRight - minSize)
                    DragHandle.RIGHT -> cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, bitmap.width)
                    else -> {}
                }
                if (ratio != null) {
                    val cw = cropRight - cropLeft; val ch = cropBottom - cropTop
                    if (cw.toFloat() / ch > ratio) {
                        val targetW = (ch * ratio).toInt().coerceAtLeast(minSize)
                        when (handle) {
                            DragHandle.TOP_LEFT, DragHandle.BOTTOM_LEFT, DragHandle.LEFT -> cropLeft = (cropRight - targetW).coerceAtLeast(0)
                            else -> cropRight = (cropLeft + targetW).coerceAtMost(bitmap.width)
                        }
                    } else {
                        val targetH = (cw / ratio).toInt().coerceAtLeast(minSize)
                        when (handle) {
                            DragHandle.TOP_LEFT, DragHandle.TOP_RIGHT, DragHandle.TOP -> cropTop = (cropBottom - targetH).coerceAtLeast(0)
                            else -> cropBottom = (cropTop + targetH).coerceAtMost(bitmap.height)
                        }
                    }
                }
            }
        }
    }

    val cropW = cropRight - cropLeft
    val cropH = cropBottom - cropTop
    val cropPct = if (bitmap.width > 0 && bitmap.height > 0) {
        val origArea = bitmap.width.toLong() * bitmap.height
        val cropArea = cropW.toLong() * cropH
        ((origArea - cropArea) * 100 / origArea).toInt()
    } else 0

    val methodLabel = when (cropMethod) {
        "border" -> "Border"; "statusbar" -> "Bars"; "ai" -> "AI"; else -> ""
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = onDiscard) { Icon(Icons.Default.Close, "Close", tint = OnSurface) }
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Undo, "Undo",
                        tint = if (undoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f))
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Redo, "Redo",
                        tint = if (redoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f))
                }
            }

            // Info: dimensions + method + crop %
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (methodLabel.isNotEmpty()) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(methodLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${cropW}x${cropH}", color = OnSurfaceVariant, fontSize = 12.sp)
                    if (cropPct > 0) {
                        Text("-${cropPct}%", color = Secondary, fontSize = 10.sp)
                    }
                }
            }

            Row {
                IconButton(onClick = onRotate) { Icon(@Suppress("DEPRECATION") Icons.Default.RotateRight, "Rotate", tint = OnSurface) }
                IconButton(onClick = onFlipH) { Icon(Icons.Default.Flip, "Flip", tint = OnSurface) }
                IconButton(onClick = { editMode = if (editMode == EditMode.PIXELATE) EditMode.CROP else EditMode.PIXELATE }) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.BlurOn, "Pixelate",
                        tint = if (editMode == EditMode.PIXELATE) Tertiary else OnSurface)
                }
                IconButton(onClick = { editMode = if (editMode == EditMode.DRAW) EditMode.CROP else EditMode.DRAW }) {
                    Icon(Icons.Default.Draw, "Draw",
                        tint = if (editMode == EditMode.DRAW) Secondary else OnSurface)
                }
                IconButton(onClick = { previewMode = !previewMode }) {
                    Icon(if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Preview", tint = if (previewMode) Primary else OnSurface)
                }
            }
        }

        // Mode indicator
        if (editMode != EditMode.CROP) {
            Row(Modifier.fillMaxWidth().background(
                if (editMode == EditMode.PIXELATE) Tertiary.copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f)
            ).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center) {
                Text(
                    when (editMode) {
                        EditMode.PIXELATE -> "PIXELATE MODE — draw rectangles to redact"
                        EditMode.DRAW -> "DRAW MODE — ${drawTool.label.lowercase()}"
                        else -> ""
                    },
                    color = if (editMode == EditMode.PIXELATE) Tertiary else Secondary,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium
                )
            }
        }

        // Aspect ratio chips (only in crop mode)
        if (editMode == EditMode.CROP) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = selectedRatio == ratio,
                    onClick = {
                        pushUndo()
                        selectedRatio = ratio
                        if (ratio.ratio != null) applyAspectRatio(ratio)
                    },
                    label = { Text(ratio.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Tool options row (pixelate/draw mode)
        if (editMode == EditMode.PIXELATE && pixelateRects.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { pixelateRects.removeLastOrNull() }) {
                    Text("Undo last", color = Tertiary, fontSize = 12.sp)
                }
                TextButton(onClick = { pixelateRects.clear() }) {
                    Text("Clear all", color = Tertiary, fontSize = 12.sp)
                }
            }
        }

        if (editMode == EditMode.DRAW) {
            // Tool + color row
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    DrawTool.entries.forEach { tool ->
                        FilterChip(selected = drawTool == tool,
                            onClick = { drawTool = tool },
                            label = { Text(tool.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                            shape = RoundedCornerShape(8.dp))
                    }
                    if (drawTool == DrawTool.RECT || drawTool == DrawTool.CIRCLE) {
                        FilterChip(selected = shapeFilled,
                            onClick = { shapeFilled = !shapeFilled },
                            label = { Text("Fill", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                            shape = RoundedCornerShape(8.dp))
                    }
                    drawColors.forEach { (color, _) ->
                        Box(Modifier
                            .size(if (drawColor == color) 24.dp else 18.dp)
                            .background(Color(color), RoundedCornerShape(3.dp))
                            .pointerInput(color) { detectTapGestures { drawColor = color } })
                    }
                }
                Row {
                    if (drawPaths.isNotEmpty()) {
                        TextButton(onClick = { drawPaths.removeLastOrNull() }) {
                            Text("Undo", color = Secondary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { drawPaths.clear() }) {
                            Text("Clear", color = Secondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            // Stroke width slider
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${drawStrokeWidth.toInt()}px", color = OnSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.width(32.dp))
                Slider(value = drawStrokeWidth, onValueChange = { drawStrokeWidth = it },
                    valueRange = 2f..20f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Secondary, activeTrackColor = Secondary,
                        inactiveTrackColor = SurfaceVariant))
            }
        }

        // Canvas area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (previewMode) {
                val croppedPreview = remember(cropLeft, cropTop, cropRight, cropBottom, bitmap) {
                    try {
                        Bitmap.createBitmap(bitmap, cropLeft.coerceAtLeast(0), cropTop.coerceAtLeast(0),
                            cropW.coerceAtMost(bitmap.width - cropLeft.coerceAtLeast(0)),
                            cropH.coerceAtMost(bitmap.height - cropTop.coerceAtLeast(0))
                        ).asImageBitmap()
                    } catch (_: Exception) { imageBitmap }
                }
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { previewMode = false }) }
                ) {
                    val s = min(size.width / croppedPreview.width, size.height / croppedPreview.height)
                    val dw = croppedPreview.width * s; val dh = croppedPreview.height * s
                    val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
                    drawImage(croppedPreview, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, editMode) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    when (editMode) {
                                        EditMode.PIXELATE -> { pixDragStart = pos; pixDragCurrent = pos }
                                        EditMode.DRAW -> {
                                            val bx = ((pos.x - offsetX) / scaleX).coerceIn(0f, bitmap.width.toFloat())
                                            val by = ((pos.y - offsetY) / scaleY).coerceIn(0f, bitmap.height.toFloat())
                                            if (drawTool == DrawTool.TEXT) {
                                                textPlacePoint = PointF(bx, by)
                                                textDialogValue = ""
                                                showTextDialog = true
                                            } else {
                                                currentDrawPoints = listOf(PointF(bx, by))
                                            }
                                        }
                                        EditMode.CROP -> {
                                            activeHandle = findHandle(pos)
                                            if (activeHandle != DragHandle.NONE) pushUndo()
                                        }
                                    }
                                },
                                onDragEnd = {
                                    when (editMode) {
                                        EditMode.PIXELATE -> {
                                            if (pixDragStart != null && pixDragCurrent != null) {
                                                val s = pixDragStart!!; val e = pixDragCurrent!!
                                                val bx1 = ((minOf(s.x, e.x) - offsetX) / scaleX).roundToInt().coerceIn(0, bitmap.width)
                                                val by1 = ((minOf(s.y, e.y) - offsetY) / scaleY).roundToInt().coerceIn(0, bitmap.height)
                                                val bx2 = ((maxOf(s.x, e.x) - offsetX) / scaleX).roundToInt().coerceIn(0, bitmap.width)
                                                val by2 = ((maxOf(s.y, e.y) - offsetY) / scaleY).roundToInt().coerceIn(0, bitmap.height)
                                                if (bx2 - bx1 > 10 && by2 - by1 > 10) {
                                                    pixelateRects.add(Rect(bx1, by1, bx2, by2))
                                                }
                                            }
                                            pixDragStart = null; pixDragCurrent = null
                                        }
                                        EditMode.DRAW -> {
                                            if (currentDrawPoints.size >= 2 && drawTool != DrawTool.TEXT) {
                                                val shape = when (drawTool) {
                                                    DrawTool.RECT -> "rect"
                                                    DrawTool.CIRCLE -> "circle"
                                                    else -> null
                                                }
                                                drawPaths.add(DrawPath(
                                                    points = if (shape != null) listOf(currentDrawPoints.first(), currentDrawPoints.last()) else currentDrawPoints.toList(),
                                                    color = drawColor,
                                                    strokeWidth = drawStrokeWidth,
                                                    isArrow = drawTool == DrawTool.ARROW,
                                                    shapeType = shape,
                                                    filled = shapeFilled && shape != null
                                                ))
                                            }
                                            currentDrawPoints = emptyList()
                                        }
                                        EditMode.CROP -> activeHandle = DragHandle.NONE
                                    }
                                },
                                onDragCancel = {
                                    activeHandle = DragHandle.NONE
                                    pixDragStart = null; pixDragCurrent = null
                                    currentDrawPoints = emptyList()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    when (editMode) {
                                        EditMode.PIXELATE -> pixDragCurrent = pixDragCurrent?.plus(Offset(dragAmount.x, dragAmount.y))
                                        EditMode.DRAW -> {
                                            val pos = change.position
                                            val bx = ((pos.x - offsetX) / scaleX).coerceIn(0f, bitmap.width.toFloat())
                                            val by = ((pos.y - offsetY) / scaleY).coerceIn(0f, bitmap.height.toFloat())
                                            currentDrawPoints = currentDrawPoints + PointF(bx, by)
                                        }
                                        EditMode.CROP -> constrainToRatio(activeHandle,
                                            (dragAmount.x / scaleX).roundToInt(),
                                            (dragAmount.y / scaleY).roundToInt())
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { previewMode = true }) }
                ) {
                    val imgW = bitmap.width.toFloat(); val imgH = bitmap.height.toFloat()
                    val scale = min(size.width / imgW, size.height / imgH)
                    val drawW = imgW * scale; val drawH = imgH * scale
                    val ox = (size.width - drawW) / 2; val oy = (size.height - drawH) / 2
                    scaleX = scale; scaleY = scale; offsetX = ox; offsetY = oy

                    drawImage(imageBitmap, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()))

                    val sl = ox + cropLeft * scale; val st = oy + cropTop * scale
                    val sr = ox + cropRight * scale; val sb = oy + cropBottom * scale

                    // Dim overlay
                    drawRect(DimOverlay, Offset(ox, oy), Size(drawW, st - oy))
                    drawRect(DimOverlay, Offset(ox, sb), Size(drawW, oy + drawH - sb))
                    drawRect(DimOverlay, Offset(ox, st), Size(sl - ox, sb - st))
                    drawRect(DimOverlay, Offset(sr, st), Size(ox + drawW - sr, sb - st))

                    drawRect(CropBorder, Offset(sl, st), Size(sr - sl, sb - st), style = Stroke(2.dp.toPx()))

                    // Rule of thirds
                    val gridColor = CropBorder.copy(alpha = 0.3f)
                    val tw = (sr - sl) / 3; val th = (sb - st) / 3
                    for (i in 1..2) {
                        drawLine(gridColor, Offset(sl + tw * i, st), Offset(sl + tw * i, sb), 1f)
                        drawLine(gridColor, Offset(sl, st + th * i), Offset(sr, st + th * i), 1f)
                    }

                    // Corner handles
                    drawCornerHandle(sl, st, handleRadius, false, false)
                    drawCornerHandle(sr, st, handleRadius, true, false)
                    drawCornerHandle(sl, sb, handleRadius, false, true)
                    drawCornerHandle(sr, sb, handleRadius, true, true)

                    // Edge midpoint dots
                    val midR = handleRadius * 0.5f
                    drawCircle(CropHandle, midR, Offset((sl + sr) / 2, st))
                    drawCircle(CropHandle, midR, Offset((sl + sr) / 2, sb))
                    drawCircle(CropHandle, midR, Offset(sl, (st + sb) / 2))
                    drawCircle(CropHandle, midR, Offset(sr, (st + sb) / 2))

                    // Pixelate region indicators (mosaic pattern)
                    val pixColor = Tertiary.copy(alpha = 0.35f)
                    val pixBorder = Tertiary.copy(alpha = 0.7f)
                    for (pr in pixelateRects) {
                        val px1 = ox + pr.left * scale; val py1 = oy + pr.top * scale
                        val px2 = ox + pr.right * scale; val py2 = oy + pr.bottom * scale
                        drawRect(pixColor, Offset(px1, py1), Size(px2 - px1, py2 - py1))
                        drawRect(pixBorder, Offset(px1, py1), Size(px2 - px1, py2 - py1), style = Stroke(1.5f.dp.toPx()))
                    }

                    // Draw paths + shapes
                    fun drawShapeOnCanvas(dp: DrawPath, pts: List<PointF>, color: Color, sw: Float) {
                        val shape = dp.shapeType

                        // Text rendering
                        if (shape == "text" && dp.text != null && pts.isNotEmpty()) {
                            val p = pts.first()
                            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color
                                textSize = dp.strokeWidth * scale * 3
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                dp.text, ox + p.x * scale, oy + p.y * scale, textPaint)
                            return
                        }

                        if (shape != null && pts.size >= 2) {
                            val p1 = pts.first(); val p2 = pts.last()
                            val sx1 = ox + p1.x * scale; val sy1 = oy + p1.y * scale
                            val sx2 = ox + p2.x * scale; val sy2 = oy + p2.y * scale
                            val style = if (dp.filled) null else Stroke(sw)
                            when (shape) {
                                "rect" -> {
                                    val off = Offset(minOf(sx1, sx2), minOf(sy1, sy2))
                                    val sz = Size(kotlin.math.abs(sx2 - sx1), kotlin.math.abs(sy2 - sy1))
                                    if (style != null) drawRect(color, off, sz, style = style)
                                    else drawRect(color, off, sz)
                                }
                                "circle" -> {
                                    val cx = (sx1 + sx2) / 2; val cy = (sy1 + sy2) / 2
                                    val rx = kotlin.math.abs(sx2 - sx1) / 2; val ry = kotlin.math.abs(sy2 - sy1) / 2
                                    val off = Offset(cx - rx, cy - ry); val sz = Size(rx * 2, ry * 2)
                                    if (style != null) drawOval(color, off, sz, style = style)
                                    else drawOval(color, off, sz)
                                }
                            }
                            return
                        }
                        if (pts.size < 2) return
                        for (i in 1 until pts.size) {
                            val a = pts[i - 1]; val b = pts[i]
                            drawLine(color, Offset(ox + a.x * scale, oy + a.y * scale),
                                Offset(ox + b.x * scale, oy + b.y * scale), strokeWidth = sw)
                        }
                        if (dp.isArrow && pts.size >= 2) {
                            val last = pts.last(); val prev = pts[pts.size - 2]
                            val dx = last.x - prev.x; val dy = last.y - prev.y
                            val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (len > 0) {
                                val ux = dx / len; val uy = dy / len
                                val hl = sw / scale * 4; val hw = sw / scale * 2.5f
                                val tip = Offset(ox + last.x * scale, oy + last.y * scale)
                                drawLine(color, tip, Offset(ox + (last.x - ux * hl + uy * hw) * scale, oy + (last.y - uy * hl - ux * hw) * scale), strokeWidth = sw)
                                drawLine(color, tip, Offset(ox + (last.x - ux * hl - uy * hw) * scale, oy + (last.y - uy * hl + ux * hw) * scale), strokeWidth = sw)
                            }
                        }
                    }

                    for (dp in drawPaths) {
                        drawShapeOnCanvas(dp, dp.points, Color(dp.color), dp.strokeWidth * scale)
                    }

                    // Current draw stroke
                    if (editMode == EditMode.DRAW && currentDrawPoints.size > 1) {
                        val curShape = when (drawTool) { DrawTool.RECT -> "rect"; DrawTool.CIRCLE -> "circle"; else -> null }
                        val curPts = if (curShape != null) listOf(currentDrawPoints.first(), currentDrawPoints.last()) else currentDrawPoints
                        drawShapeOnCanvas(DrawPath(curPts, drawColor, drawStrokeWidth, drawTool == DrawTool.ARROW, curShape),
                            curPts, Color(drawColor), drawStrokeWidth * scale)
                    }

                    // Current pixelate drag preview
                    if (editMode == EditMode.PIXELATE && pixDragStart != null && pixDragCurrent != null) {
                        val ds = pixDragStart!!; val dc = pixDragCurrent!!
                        val rx = minOf(ds.x, dc.x); val ry = minOf(ds.y, dc.y)
                        val rw = kotlin.math.abs(dc.x - ds.x); val rh = kotlin.math.abs(dc.y - ds.y)
                        drawRect(Tertiary.copy(alpha = 0.25f), Offset(rx, ry), Size(rw, rh))
                        drawRect(Tertiary, Offset(rx, ry), Size(rw, rh), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }

        // Bottom toolbar
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledTonalButton(
                    onClick = { pushUndo(); cropLeft = 0; cropTop = 0; cropRight = bitmap.width; cropBottom = bitmap.height; selectedRatio = AspectRatio.FREE },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Icon(Icons.Default.CropFree, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reset", fontSize = 13.sp) }

                FilledTonalButton(
                    onClick = { pushUndo(); val r = onAutoCrop(); cropLeft = r.left; cropTop = r.top; cropRight = r.right; cropBottom = r.bottom; selectedRatio = AspectRatio.FREE },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Auto", fontSize = 13.sp) }

                FilledTonalButton(
                    onClick = { if (!aiLoading) { pushUndo(); aiLoading = true; onSmartCrop() } },
                    enabled = !aiLoading,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (aiLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tertiary)
                    else Icon(Icons.Default.Psychology, null, Modifier.size(16.dp), tint = Tertiary)
                    Spacer(Modifier.width(4.dp)); Text("AI", fontSize = 13.sp, color = Tertiary)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Action icons row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Tertiary) }
                IconButton(onClick = { onShare(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList()) }) {
                    Icon(Icons.Default.Share, "Share", tint = OnSurface) }
                IconButton(onClick = { onCopyClipboard(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList()) }) {
                    Icon(Icons.Default.ContentCopy, "Clipboard", tint = OnSurface) }
                IconButton(onClick = { onSaveCopy(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList()) }) {
                    Icon(Icons.Default.Save, "Save Copy", tint = OnSurface) }
            }

            // Main save button — full width
            Button(onClick = { onSave(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Crop, null, Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Crop & Save", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // Text input dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text", color = OnSurface) },
            text = {
                OutlinedTextField(
                    value = textDialogValue,
                    onValueChange = { textDialogValue = it },
                    placeholder = { Text("Type here...") },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Outline,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textDialogValue.isNotBlank() && textPlacePoint != null) {
                        drawPaths.add(DrawPath(
                            points = listOf(textPlacePoint!!),
                            color = drawColor,
                            strokeWidth = drawStrokeWidth,
                            shapeType = "text",
                            text = textDialogValue
                        ))
                    }
                    showTextDialog = false
                }) { Text("Add", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }
}

private fun DrawScope.drawCornerHandle(x: Float, y: Float, radius: Float, isRight: Boolean, isBottom: Boolean) {
    val len = radius * 2; val stroke = 4.dp.toPx()
    val hDir = if (isRight) -1f else 1f; val vDir = if (isBottom) -1f else 1f
    drawLine(CropHandle, Offset(x, y), Offset(x + len * hDir, y), strokeWidth = stroke)
    drawLine(CropHandle, Offset(x, y), Offset(x, y + len * vDir), strokeWidth = stroke)
}
