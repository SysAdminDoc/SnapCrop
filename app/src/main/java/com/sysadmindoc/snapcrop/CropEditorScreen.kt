package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

private enum class EditMode { CROP, PIXELATE, DRAW, OCR, ADJUST }

/** Simplifies a path by removing points that are too close together, then
 *  applies Catmull-Rom interpolation for smooth curves. */
private fun smoothPath(points: List<PointF>): List<PointF> {
    if (points.size < 4) return points
    // Step 1: Reduce — skip points within 2px of previous
    val reduced = mutableListOf(points.first())
    for (i in 1 until points.size) {
        val prev = reduced.last(); val cur = points[i]
        val dist = kotlin.math.sqrt(((cur.x - prev.x) * (cur.x - prev.x) + (cur.y - prev.y) * (cur.y - prev.y)).toDouble())
        if (dist > 2.0) reduced.add(cur)
    }
    if (reduced.size < 4) return reduced

    // Step 2: Catmull-Rom interpolation
    val smooth = mutableListOf<PointF>()
    for (i in 0 until reduced.size - 1) {
        val p0 = reduced[(i - 1).coerceAtLeast(0)]
        val p1 = reduced[i]
        val p2 = reduced[(i + 1).coerceAtMost(reduced.size - 1)]
        val p3 = reduced[(i + 2).coerceAtMost(reduced.size - 1)]
        val steps = 4
        for (s in 0 until steps) {
            val t = s.toFloat() / steps
            val t2 = t * t; val t3 = t2 * t
            val x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)
            val y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)
            smooth.add(PointF(x, y))
        }
    }
    smooth.add(reduced.last())
    return smooth
}
private enum class DrawTool(val label: String) {
    PEN("Pen"), ARROW("Arrow"), RECT("Rect"), CIRCLE("Circle"), TEXT("Text"),
    HIGHLIGHT("Mark"), CALLOUT("#"), SPOTLIGHT("Focus"), MAGNIFIER("Zoom"), EMOJI("Emoji")
}

private val commonEmojis = listOf(
    "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD14", "\uD83D\uDE31",
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDD25", "\u2764\uFE0F", "\u2B50",
    "\u2705", "\u274C", "\u26A0\uFE0F", "\uD83D\uDCA1", "\uD83D\uDCCC",
    "\uD83D\uDCF7", "\uD83C\uDFAF", "\uD83D\uDE80", "\uD83D\uDC40", "\uD83C\uDF89"
)

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
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_2_1("2:1", 2f / 1f),
    CIRCLE("Circle", 1f)
}

@Composable
fun CropEditorScreen(
    bitmap: Bitmap,
    initialCropRect: Rect,
    cropMethod: String,
    onSave: (Rect, List<Rect>, List<DrawPath>, FloatArray) -> Unit,
    onSaveCopy: (Rect, List<Rect>, List<DrawPath>, FloatArray) -> Unit,
    onShare: (Rect, List<Rect>, List<DrawPath>, FloatArray) -> Unit,
    onCopyClipboard: (Rect, List<Rect>, List<DrawPath>, FloatArray) -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onAutoCrop: () -> Rect,
    onSmartCrop: () -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // Base scale/offset (fit image to canvas)
    var baseScale by remember { mutableFloatStateOf(1f) }
    var baseOx by remember { mutableFloatStateOf(0f) }
    var baseOy by remember { mutableFloatStateOf(0f) }

    // Zoom/pan (user-controlled, multiplicative on top of base)
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Effective scale/offset used by all coordinate math
    val scaleX = baseScale * zoomLevel
    val scaleY = baseScale * zoomLevel
    val offsetX = baseOx * zoomLevel + panX
    val offsetY = baseOy * zoomLevel + panY

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
    var calloutCounter by remember { mutableIntStateOf(1) }
    var eyedropperActive by remember { mutableStateOf(false) }
    var ocrBlocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var ocrLoading by remember { mutableStateOf(false) }
    var scannedCodes by remember { mutableStateOf<List<ScannedCode>>(emptyList()) }
    var faceRedacting by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogValue by remember { mutableStateOf("") }
    var textPlacePoint by remember { mutableStateOf<PointF?>(null) }

    // Emoji tool
    var selectedEmoji by remember { mutableStateOf(commonEmojis[0]) }

    // Adjust mode (brightness/contrast/saturation)
    var brightness by remember { mutableFloatStateOf(0f) }    // -100 to 100
    var contrast by remember { mutableFloatStateOf(1f) }      // 0.5 to 2.0
    var saturation by remember { mutableFloatStateOf(1f) }    // 0.0 to 2.0

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
                IconButton(onClick = {
                    if (editMode == EditMode.OCR) { editMode = EditMode.CROP; ocrBlocks = emptyList(); scannedCodes = emptyList() }
                    else {
                        editMode = EditMode.OCR
                        if (ocrBlocks.isEmpty() && scannedCodes.isEmpty() && !ocrLoading) {
                            ocrLoading = true
                            CoroutineScope(Dispatchers.Main).launch {
                                val textDeferred = async(Dispatchers.IO) { TextExtractor.extract(bitmap) }
                                val codeDeferred = async(Dispatchers.IO) { BarcodeScanner.scan(bitmap) }
                                ocrBlocks = textDeferred.await()
                                scannedCodes = codeDeferred.await()
                                ocrLoading = false
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.TextFields, "OCR",
                        tint = if (editMode == EditMode.OCR) Color(0xFFCBA6F7) else OnSurface)
                }
                IconButton(onClick = {
                    editMode = if (editMode == EditMode.ADJUST) EditMode.CROP else EditMode.ADJUST
                }) {
                    Icon(Icons.Default.Tune, "Adjust",
                        tint = if (editMode == EditMode.ADJUST) Color(0xFFFAB387) else OnSurface)
                }
                IconButton(onClick = { previewMode = !previewMode }) {
                    Icon(if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Preview", tint = if (previewMode) Primary else OnSurface)
                }
            }
        }

        // Mode indicator
        if (editMode != EditMode.CROP) {
            val (bannerBg, bannerColor, bannerText) = when (editMode) {
                EditMode.PIXELATE -> Triple(Tertiary.copy(alpha = 0.15f), Tertiary, "PIXELATE — draw rectangles to redact")
                EditMode.DRAW -> Triple(Secondary.copy(alpha = 0.15f), Secondary, "DRAW — ${drawTool.label.lowercase()}")
                EditMode.OCR -> {
                    val info = if (ocrLoading) "SCANNING..." else buildString {
                        append("OCR — tap to copy")
                        if (ocrBlocks.isNotEmpty()) append(" | ${ocrBlocks.size} text")
                        if (scannedCodes.isNotEmpty()) append(" | ${scannedCodes.size} code")
                    }
                    Triple(Color(0xFFCBA6F7).copy(alpha = 0.15f), Color(0xFFCBA6F7), info)
                }
                EditMode.ADJUST -> Triple(Color(0xFFFAB387).copy(alpha = 0.15f), Color(0xFFFAB387), "ADJUST — brightness, contrast, saturation")
                else -> Triple(Color.Transparent, Color.Transparent, "")
            }
            Row(Modifier.fillMaxWidth().background(bannerBg).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center) {
                if (ocrLoading && editMode == EditMode.OCR) {
                    CircularProgressIndicator(Modifier.size(12.dp).padding(end = 4.dp), strokeWidth = 1.5.dp, color = bannerColor)
                    Spacer(Modifier.width(6.dp))
                }
                Text(bannerText, color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Aspect ratio chips (only in crop mode)
        if (editMode == EditMode.CROP) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
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
        if (editMode == EditMode.PIXELATE) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Smart redact faces
                FilledTonalButton(
                    onClick = {
                        if (!faceRedacting) {
                            faceRedacting = true
                            CoroutineScope(Dispatchers.Main).launch {
                                val faces = FaceDetector.detect(bitmap)
                                pixelateRects.addAll(faces)
                                faceRedacting = false
                                if (faces.isEmpty()) {
                                    android.widget.Toast.makeText(context, "No faces found", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Redacted ${faces.size} face(s)", android.widget.Toast.LENGTH_SHORT).show()
                                    haptic()
                                }
                            }
                        }
                    },
                    enabled = !faceRedacting,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (faceRedacting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Tertiary)
                    else Text("Blur Faces", fontSize = 11.sp, color = Tertiary)
                }

                Row {
                    if (pixelateRects.isNotEmpty()) {
                        TextButton(onClick = { pixelateRects.removeLastOrNull() }) {
                            Text("Undo", color = Tertiary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { pixelateRects.clear() }) {
                            Text("Clear", color = Tertiary, fontSize = 11.sp)
                        }
                    }
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
                    // Eyedropper
                    IconButton(onClick = { eyedropperActive = !eyedropperActive },
                        modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Colorize, "Pick color",
                            tint = if (eyedropperActive) Primary else OnSurfaceVariant,
                            modifier = Modifier.size(16.dp))
                    }
                    drawColors.forEach { (color, _) ->
                        Box(Modifier
                            .size(if (drawColor == color) 24.dp else 18.dp)
                            .background(Color(color), RoundedCornerShape(3.dp))
                            .pointerInput(color) { detectTapGestures { drawColor = color; eyedropperActive = false } })
                    }
                    // Current color preview (shows sampled color)
                    Box(Modifier.size(24.dp)
                        .background(Color(drawColor), RoundedCornerShape(3.dp))
                        .border(1.dp, OnSurfaceVariant, RoundedCornerShape(3.dp)))
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
            // Emoji picker row
            if (drawTool == DrawTool.EMOJI) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    commonEmojis.forEach { emoji ->
                        Surface(
                            modifier = Modifier.size(36.dp).clickable { selectedEmoji = emoji },
                            color = if (selectedEmoji == emoji) PrimaryContainer else SurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }

        // Adjust mode sliders
        if (editMode == EditMode.ADJUST) {
            val adjustColor = Color(0xFFFAB387)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Brightness", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = brightness, onValueChange = { brightness = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${brightness.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contrast", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = contrast, onValueChange = { contrast = it },
                        valueRange = 0.5f..2f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", contrast)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saturation", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = saturation, onValueChange = { saturation = it },
                        valueRange = 0f..2f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", saturation)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { brightness = 0f; contrast = 1f; saturation = 1f }) {
                        Text("Reset", color = adjustColor, fontSize = 11.sp)
                    }
                }
            }
        }

        // Canvas area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Zoom indicator
            if (zoomLevel > 1.05f) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("${String.format("%.1f", zoomLevel)}x",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

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
                                            } else if (drawTool == DrawTool.CALLOUT) {
                                                // Place numbered circle at tap point
                                                drawPaths.add(DrawPath(
                                                    points = listOf(PointF(bx, by)),
                                                    color = drawColor,
                                                    strokeWidth = drawStrokeWidth,
                                                    shapeType = "callout",
                                                    text = "${calloutCounter++}"
                                                ))
                                                haptic()
                                            } else if (drawTool == DrawTool.MAGNIFIER) {
                                                // Place magnifier loupe at tap point
                                                drawPaths.add(DrawPath(
                                                    points = listOf(PointF(bx, by)),
                                                    color = drawColor,
                                                    strokeWidth = drawStrokeWidth,
                                                    shapeType = "magnifier"
                                                ))
                                                haptic()
                                            } else if (drawTool == DrawTool.EMOJI) {
                                                // Place emoji at tap point
                                                drawPaths.add(DrawPath(
                                                    points = listOf(PointF(bx, by)),
                                                    color = drawColor,
                                                    strokeWidth = drawStrokeWidth,
                                                    shapeType = "emoji",
                                                    text = selectedEmoji
                                                ))
                                                haptic()
                                            } else {
                                                currentDrawPoints = listOf(PointF(bx, by))
                                            }
                                        }
                                        EditMode.CROP -> {
                                            activeHandle = findHandle(pos)
                                            if (activeHandle != DragHandle.NONE) pushUndo()
                                        }
                                        EditMode.OCR, EditMode.ADJUST -> {}
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
                                            if (currentDrawPoints.size >= 2 && drawTool != DrawTool.TEXT && drawTool != DrawTool.CALLOUT) {
                                                val shape = when (drawTool) {
                                                    DrawTool.RECT -> "rect"
                                                    DrawTool.CIRCLE -> "circle"
                                                    DrawTool.HIGHLIGHT -> "highlight"
                                                    DrawTool.SPOTLIGHT -> "spotlight"
                                                    else -> null
                                                }
                                                drawPaths.add(DrawPath(
                                                    points = when {
                                                        shape == "rect" || shape == "circle" || shape == "spotlight" -> listOf(currentDrawPoints.first(), currentDrawPoints.last())
                                                        drawTool == DrawTool.PEN || drawTool == DrawTool.HIGHLIGHT -> smoothPath(currentDrawPoints)
                                                        else -> currentDrawPoints.toList()
                                                    },
                                                    color = drawColor,
                                                    strokeWidth = if (drawTool == DrawTool.HIGHLIGHT) drawStrokeWidth * 3 else drawStrokeWidth,
                                                    isArrow = drawTool == DrawTool.ARROW,
                                                    shapeType = shape,
                                                    filled = shapeFilled && (shape == "rect" || shape == "circle")
                                                ))
                                            }
                                            currentDrawPoints = emptyList()
                                        }
                                        EditMode.CROP -> activeHandle = DragHandle.NONE
                                        EditMode.OCR, EditMode.ADJUST -> {}
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
                                        EditMode.OCR, EditMode.ADJUST -> {}
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                                if (zoom != 1f || zoomLevel > 1.05f) {
                                    zoomLevel = (zoomLevel * zoom).coerceIn(1f, 5f)
                                    panX += pan.x
                                    panY += pan.y
                                }
                            }
                        }
                        .pointerInput(editMode, ocrBlocks, scannedCodes) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomLevel > 1.05f) { zoomLevel = 1f; panX = 0f; panY = 0f }
                                    else previewMode = true
                                },
                                onTap = { pos ->
                                    // Eyedropper: sample pixel color from bitmap
                                    if (eyedropperActive && editMode == EditMode.DRAW) {
                                        val bx = ((pos.x - offsetX) / scaleX).toInt().coerceIn(0, bitmap.width - 1)
                                        val by = ((pos.y - offsetY) / scaleY).toInt().coerceIn(0, bitmap.height - 1)
                                        drawColor = bitmap.getPixel(bx, by)
                                        eyedropperActive = false
                                        haptic()
                                        return@detectTapGestures
                                    }

                                    if (editMode == EditMode.OCR) {
                                        val bx = ((pos.x - offsetX) / scaleX).toInt()
                                        val by = ((pos.y - offsetY) / scaleY).toInt()

                                        // Check barcodes first (higher priority)
                                        val tappedCode = scannedCodes.find { it.bounds.contains(bx, by) }
                                        if (tappedCode != null) {
                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("SnapCrop QR", tappedCode.rawValue))
                                            android.widget.Toast.makeText(context, "Copied: ${tappedCode.displayValue.take(80)}", android.widget.Toast.LENGTH_SHORT).show()
                                            haptic()
                                            return@detectTapGestures
                                        }

                                        // Then check text blocks
                                        val tappedText = ocrBlocks.find { it.bounds.contains(bx, by) }
                                        if (tappedText != null) {
                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("SnapCrop OCR", tappedText.text))
                                            android.widget.Toast.makeText(context, "Copied: ${tappedText.text.take(50)}", android.widget.Toast.LENGTH_SHORT).show()
                                            haptic()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val imgW = bitmap.width.toFloat(); val imgH = bitmap.height.toFloat()
                    val fitScale = min(size.width / imgW, size.height / imgH)
                    val fitW = imgW * fitScale; val fitH = imgH * fitScale
                    val fitOx = (size.width - fitW) / 2; val fitOy = (size.height - fitH) / 2
                    baseScale = fitScale; baseOx = fitOx; baseOy = fitOy

                    // Effective (zoomed) image position
                    val ox = offsetX; val oy = offsetY
                    val scale = scaleX
                    val drawW = imgW * scale; val drawH = imgH * scale

                    // Build color adjustment matrix
                    val adjustFilter = if (brightness != 0f || contrast != 1f || saturation != 1f) {
                        val cm = ColorMatrix()
                        // Saturation
                        if (saturation != 1f) cm.timesAssign(ColorMatrix().apply { setToSaturation(saturation) })
                        // Contrast: scale around 0.5
                        if (contrast != 1f) {
                            val t = (1f - contrast) / 2f * 255f
                            cm.timesAssign(ColorMatrix(floatArrayOf(
                                contrast, 0f, 0f, 0f, t,
                                0f, contrast, 0f, 0f, t,
                                0f, 0f, contrast, 0f, t,
                                0f, 0f, 0f, 1f, 0f
                            )))
                        }
                        // Brightness: additive offset
                        if (brightness != 0f) {
                            cm.timesAssign(ColorMatrix(floatArrayOf(
                                1f, 0f, 0f, 0f, brightness,
                                0f, 1f, 0f, 0f, brightness,
                                0f, 0f, 1f, 0f, brightness,
                                0f, 0f, 0f, 1f, 0f
                            )))
                        }
                        ColorFilter.colorMatrix(cm)
                    } else null

                    drawImage(imageBitmap, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                        colorFilter = adjustFilter)

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

                    // Circle crop preview overlay
                    if (selectedRatio == AspectRatio.CIRCLE) {
                        val cx = (sl + sr) / 2; val cy = (st + sb) / 2
                        val radius = minOf(sr - sl, sb - st) / 2
                        drawCircle(CropBorder.copy(alpha = 0.5f), radius, Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }

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

                        // Emoji overlay
                        if (shape == "emoji" && dp.text != null && pts.isNotEmpty()) {
                            val p = pts.first()
                            val emojiPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = dp.strokeWidth * scale * 5
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                dp.text, ox + p.x * scale, oy + p.y * scale, emojiPaint)
                            return
                        }

                        // Callout (numbered circle)
                        if (shape == "callout" && dp.text != null && pts.isNotEmpty()) {
                            val p = pts.first()
                            val cx = ox + p.x * scale; val cy = oy + p.y * scale
                            val radius = dp.strokeWidth * scale * 2
                            drawCircle(color, radius, Offset(cx, cy))
                            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = if (dp.color == 0xFFFFFFFF.toInt() || dp.color == 0xFFFFFF00.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                                textSize = radius * 1.2f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                dp.text, cx, cy + radius * 0.4f, textPaint)
                            return
                        }

                        // Magnifier loupe — zoomed circular inset
                        if (shape == "magnifier" && pts.isNotEmpty()) {
                            val p = pts.first()
                            val cx = ox + p.x * scale; val cy = oy + p.y * scale
                            val loupeRadius = 60.dp.toPx()
                            val zoomFactor = 2f
                            // Draw border circle
                            drawCircle(Color.White, loupeRadius + 3.dp.toPx(), Offset(cx, cy - loupeRadius - 10.dp.toPx()))
                            drawCircle(color, loupeRadius + 2.dp.toPx(), Offset(cx, cy - loupeRadius - 10.dp.toPx()), style = Stroke(2.dp.toPx()))
                            // Clip and draw zoomed content using nativeCanvas
                            drawContext.canvas.nativeCanvas.apply {
                                val loupeCx = cx; val loupeCy = cy - loupeRadius - 10.dp.toPx()
                                save()
                                val clipPath = android.graphics.Path()
                                clipPath.addCircle(loupeCx, loupeCy, loupeRadius, android.graphics.Path.Direction.CW)
                                clipPath(clipPath)
                                // Translate so the tapped point is at center of loupe, then scale
                                val srcX = ox + p.x * scale
                                val srcY = oy + p.y * scale
                                translate(loupeCx - srcX * zoomFactor, loupeCy - srcY * zoomFactor)
                                scale(zoomFactor, zoomFactor)
                                drawBitmap(bitmap, ox / zoomFactor, oy / zoomFactor, null)
                                restore()
                            }
                            // Crosshair
                            val lCy = cy - loupeRadius - 10.dp.toPx()
                            drawLine(color, Offset(cx - 8.dp.toPx(), lCy), Offset(cx + 8.dp.toPx(), lCy), 1.dp.toPx())
                            drawLine(color, Offset(cx, lCy - 8.dp.toPx()), Offset(cx, lCy + 8.dp.toPx()), 1.dp.toPx())
                            return
                        }

                        // Highlighter (semi-transparent wide stroke)
                        if (shape == "highlight" && pts.size >= 2) {
                            val highlightColor = color.copy(alpha = 0.4f)
                            for (i in 1 until pts.size) {
                                val a = pts[i - 1]; val b = pts[i]
                                drawLine(highlightColor,
                                    Offset(ox + a.x * scale, oy + a.y * scale),
                                    Offset(ox + b.x * scale, oy + b.y * scale),
                                    strokeWidth = sw)
                            }
                            return
                        }

                        // Spotlight — dim entire image except the selected rectangle
                        if (shape == "spotlight" && pts.size >= 2) {
                            val p1 = pts.first(); val p2 = pts.last()
                            val sx1 = ox + minOf(p1.x, p2.x) * scale
                            val sy1 = oy + minOf(p1.y, p2.y) * scale
                            val sx2 = ox + maxOf(p1.x, p2.x) * scale
                            val sy2 = oy + maxOf(p1.y, p2.y) * scale
                            val spotDim = Color.Black.copy(alpha = 0.6f)
                            // Top strip
                            drawRect(spotDim, Offset(ox, oy), Size(drawW, sy1 - oy))
                            // Bottom strip
                            drawRect(spotDim, Offset(ox, sy2), Size(drawW, oy + drawH - sy2))
                            // Left strip
                            drawRect(spotDim, Offset(ox, sy1), Size(sx1 - ox, sy2 - sy1))
                            // Right strip
                            drawRect(spotDim, Offset(sx2, sy1), Size(ox + drawW - sx2, sy2 - sy1))
                            // Border around spotlight area
                            drawRect(Color.White.copy(alpha = 0.8f), Offset(sx1, sy1), Size(sx2 - sx1, sy2 - sy1), style = Stroke(2.dp.toPx()))
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
                        val curShape = when (drawTool) {
                            DrawTool.RECT -> "rect"; DrawTool.CIRCLE -> "circle"
                            DrawTool.HIGHLIGHT -> "highlight"; DrawTool.SPOTLIGHT -> "spotlight"; else -> null
                        }
                        val curSw = if (drawTool == DrawTool.HIGHLIGHT) drawStrokeWidth * 3 else drawStrokeWidth
                        val curPts = if (curShape == "rect" || curShape == "circle") listOf(currentDrawPoints.first(), currentDrawPoints.last()) else currentDrawPoints
                        drawShapeOnCanvas(DrawPath(curPts, drawColor, curSw, drawTool == DrawTool.ARROW, curShape),
                            curPts, Color(drawColor), curSw * scale)
                    }

                    // Current pixelate drag preview
                    if (editMode == EditMode.PIXELATE && pixDragStart != null && pixDragCurrent != null) {
                        val ds = pixDragStart!!; val dc = pixDragCurrent!!
                        val rx = minOf(ds.x, dc.x); val ry = minOf(ds.y, dc.y)
                        val rw = kotlin.math.abs(dc.x - ds.x); val rh = kotlin.math.abs(dc.y - ds.y)
                        drawRect(Tertiary.copy(alpha = 0.25f), Offset(rx, ry), Size(rw, rh))
                        drawRect(Tertiary, Offset(rx, ry), Size(rw, rh), style = Stroke(2.dp.toPx()))
                    }

                    // OCR text block + barcode overlays
                    if (editMode == EditMode.OCR) {
                        val ocrColor = Color(0xFFCBA6F7)
                        for (block in ocrBlocks) {
                            val bl = ox + block.bounds.left * scale
                            val bt = oy + block.bounds.top * scale
                            val bw = block.bounds.width() * scale
                            val bh = block.bounds.height() * scale
                            drawRect(ocrColor.copy(alpha = 0.15f), Offset(bl, bt), Size(bw, bh))
                            drawRect(ocrColor.copy(alpha = 0.6f), Offset(bl, bt), Size(bw, bh), style = Stroke(1.5f.dp.toPx()))
                        }
                        // Barcodes in green
                        val codeColor = Secondary
                        for (code in scannedCodes) {
                            val cl = ox + code.bounds.left * scale
                            val ct = oy + code.bounds.top * scale
                            val cw2 = code.bounds.width() * scale
                            val ch2 = code.bounds.height() * scale
                            drawRect(codeColor.copy(alpha = 0.2f), Offset(cl, ct), Size(cw2, ch2))
                            drawRect(codeColor, Offset(cl, ct), Size(cw2, ch2), style = Stroke(2.dp.toPx()))
                        }
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
                val circleCrop = if (selectedRatio == AspectRatio.CIRCLE) 1f else 0f
                val adj = floatArrayOf(brightness, contrast, saturation, circleCrop)
                IconButton(onClick = { onShare(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.Share, "Share", tint = OnSurface) }
                IconButton(onClick = { onCopyClipboard(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.ContentCopy, "Clipboard", tint = OnSurface) }
                IconButton(onClick = { onSaveCopy(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.Save, "Save Copy", tint = OnSurface) }
            }

            // Main save button — full width
            Button(onClick = { onSave(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), floatArrayOf(brightness, contrast, saturation, if (selectedRatio == AspectRatio.CIRCLE) 1f else 0f)) },
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
