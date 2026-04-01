package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.compose.runtime.rememberCoroutineScope
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
    val shapeType: String? = null,
    val text: String? = null,
    val filled: Boolean = false,
    val dashed: Boolean = false
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
    PEN("Pen"), ARROW("Arrow"), LINE("Line"), RECT("Rect"), CIRCLE("Circle"), TEXT("Text"),
    HIGHLIGHT("Mark"), CALLOUT("#"), SPOTLIGHT("Focus"), MAGNIFIER("Zoom"), EMOJI("Emoji"),
    NEON("Neon"), BLUR("Blur"), ERASER("Erase"), FILL("Fill"), HEAL("Heal")
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
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_5_4("5:4", 5f / 4f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_2_1("2:1", 2f / 1f),
    RATIO_3_1("3:1", 3f / 1f),
    RATIO_21_9("21:9", 21f / 9f),
    CIRCLE("Circle", 1f),
    ROUNDED("Rounded", null),
    STAR("Star", 1f),
    HEART("Heart", 1f),
    TRIANGLE("Triangle", 1f),
    HEXAGON("Hexagon", 1f),
    DIAMOND("Diamond", 1f)
}

private data class EditorSnapshot(
    val crop: Rect,
    val bright: Float, val contr: Float, val sat: Float, val warm: Float, val vig: Float,
    val sharp: Float, val rotAngle: Float,
    val hi: Float, val sh: Float, val tilt: Float, val dn: Float,
    val gradBg: Int,
    val filter: ImageFilter,
    val pixRects: List<Rect>,
    val draws: List<DrawPath>,
    val cR: Float = 0f, val cG: Float = 0f, val cB: Float = 0f
)

private enum class ImageFilter(val label: String) {
    NONE("None"), MONO("Mono"), SEPIA("Sepia"), COOL("Cool"), WARM("Warm"),
    VIVID("Vivid"), MUTED("Muted"), VINTAGE("Vintage"), NOIR("Noir"), FADE("Fade"),
    INVERT("Invert"), POLAROID("Polaroid"), GRAIN("Grain"),
    RED_POP("Red"), BLUE_POP("Blue"), GREEN_POP("Green"),
    GLITCH("Glitch")
}

private fun getFilterMatrix(filter: ImageFilter): android.graphics.ColorMatrix? {
    return when (filter) {
        ImageFilter.NONE -> null
        ImageFilter.MONO -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
        ImageFilter.SEPIA -> android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 40f,
                0f, 1f, 0f, 0f, 20f,
                0f, 0f, 1f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.COOL -> android.graphics.ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.WARM -> android.graphics.ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.VIVID -> android.graphics.ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.MUTED -> android.graphics.ColorMatrix().apply {
            setSaturation(0.4f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 15f,
                0f, 1f, 0f, 0f, 15f,
                0f, 0f, 1f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.VINTAGE -> android.graphics.ColorMatrix().apply {
            setSaturation(0.5f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.05f, 0.05f, 0f, 0f, 20f,
                0f, 1f, 0.05f, 0f, 10f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.NOIR -> android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.4f, 0f, 0f, 0f, -30f,
                0f, 1.4f, 0f, 0f, -30f,
                0f, 0f, 1.4f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.FADE -> android.graphics.ColorMatrix().apply {
            setSaturation(0.6f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 30f,
                0f, 0f, 1f, 0f, 30f,
                0f, 0f, 0f, 0.9f, 0f
            )))
        }
        ImageFilter.INVERT -> android.graphics.ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.POLAROID -> android.graphics.ColorMatrix(floatArrayOf(
            1.438f, -0.062f, -0.062f, 0f, 0f,
            -0.122f, 1.378f, -0.122f, 0f, 0f,
            -0.016f, -0.016f, 1.483f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.GRAIN -> android.graphics.ColorMatrix().apply {
            setSaturation(0.8f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.05f, 0.02f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 0.95f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // Per-pixel filters — handled in CropActivity, no ColorMatrix
        ImageFilter.RED_POP, ImageFilter.BLUE_POP, ImageFilter.GREEN_POP, ImageFilter.GLITCH -> null
    }
}

private fun getGradientBrush(gradIdx: Int, left: Float, top: Float, right: Float, bottom: Float): androidx.compose.ui.graphics.Brush? {
    val (startColor, endColor) = when (gradIdx) {
        1 -> Color(0xFFFF6B35) to Color(0xFFF7C948) // Sunset
        2 -> Color(0xFF0077B6) to Color(0xFF00B4D8) // Ocean
        3 -> Color(0xFF7B2FBE) to Color(0xFFE040FB) // Purple
        4 -> Color(0xFF1A1A2E) to Color(0xFF16213E) // Dark
        5 -> Color(0xFF00B09B) to Color(0xFF96C93D) // Mint
        6 -> Color(0xFFFF416C) to Color(0xFFFF4B2B) // Fire
        else -> return null
    }
    return androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(startColor, endColor),
        start = Offset(left, top),
        end = Offset(right, bottom)
    )
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
    onRemoveBg: () -> Unit,
    onResize: (Int) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val scope = rememberCoroutineScope()

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
    val drawRedoStack = remember { mutableStateListOf<DrawPath>() }
    val currentDrawPoints = remember { mutableStateListOf<PointF>() }
    var drawColor by remember { mutableIntStateOf(0xFFFF0000.toInt()) }
    val recentColors = remember { mutableStateListOf<Int>() }
    var drawStrokeWidth by remember { mutableFloatStateOf(6f) }
    var drawTool by remember { mutableStateOf(DrawTool.PEN) }
    var shapeFilled by remember { mutableStateOf(false) }
    var dashedStroke by remember { mutableStateOf(false) }
    var calloutCounter by remember { mutableIntStateOf(1) }
    var eyedropperActive by remember { mutableStateOf(false) }
    var bgRemoving by remember { mutableStateOf(false) }
    var paletteColors by remember { mutableStateOf<List<ColorPaletteExtractor.PaletteColor>>(emptyList()) }

    // Reset bgRemoving and stale palette when bitmap changes (BG removal, rotation, resize)
    LaunchedEffect(bitmap) { bgRemoving = false; paletteColors = emptyList() }

    var showPalette by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var ocrBlocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var ocrLoading by remember { mutableStateOf(false) }
    var scannedCodes by remember { mutableStateOf<List<ScannedCode>>(emptyList()) }
    var faceRedacting by remember { mutableStateOf(false) }
    var lastFaceCount by remember { mutableIntStateOf(-1) } // -1 = not scanned yet
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogValue by remember { mutableStateOf("") }
    var textPlacePoint by remember { mutableStateOf<PointF?>(null) }

    // Emoji tool
    var selectedEmoji by remember { mutableStateOf(commonEmojis[0]) }

    // Adjust mode (brightness/contrast/saturation)
    var brightness by remember { mutableFloatStateOf(0f) }    // -100 to 100
    var contrast by remember { mutableFloatStateOf(1f) }      // 0.5 to 2.0
    var saturation by remember { mutableFloatStateOf(1f) }    // 0.0 to 2.0
    var warmth by remember { mutableFloatStateOf(0f) }        // -50 to 50 (red/blue shift)
    var vignette by remember { mutableFloatStateOf(0f) }     // 0 to 1 (edge darkening)
    var sharpen by remember { mutableFloatStateOf(0f) }      // 0 to 2 (convolution kernel strength)
    var highlights by remember { mutableFloatStateOf(0f) }   // -100 to 100 (bright area adjustment)
    var shadows by remember { mutableFloatStateOf(0f) }      // -100 to 100 (dark area adjustment)
    var tiltShift by remember { mutableFloatStateOf(0f) }    // 0 to 1 (linear blur top/bottom)
    var denoise by remember { mutableFloatStateOf(0f) }      // 0 to 1 (noise reduction strength)
    // Curves: per-channel midpoint adjustments (-100 to 100)
    var curveR by remember { mutableFloatStateOf(0f) }
    var curveG by remember { mutableFloatStateOf(0f) }
    var curveB by remember { mutableFloatStateOf(0f) }
    var selectedFilter by remember { mutableStateOf(ImageFilter.NONE) }
    var showCropInputDialog by remember { mutableStateOf(false) }
    var gradientBg by remember { mutableIntStateOf(0) } // 0=none, 1-6=gradient presets
    var showUndoHistory by remember { mutableStateOf(false) }
    var gridMode by remember { mutableIntStateOf(0) } // 0=thirds, 1=golden, 2=none
    var rotationAngle by remember { mutableFloatStateOf(0f) } // -45 to 45 degrees for straightening

    // Pre-allocate Paint for vignette to avoid allocation in DrawScope
    val vigPaint = remember { android.graphics.Paint() }

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

    fun captureSnapshot() = EditorSnapshot(
        Rect(cropLeft, cropTop, cropRight, cropBottom),
        brightness, contrast, saturation, warmth, vignette, sharpen, rotationAngle,
        highlights, shadows, tiltShift, denoise, gradientBg,
        selectedFilter,
        pixelateRects.toList(),
        drawPaths.toList(),
        curveR, curveG, curveB
    )

    fun restoreSnapshot(s: EditorSnapshot) {
        cropLeft = s.crop.left; cropTop = s.crop.top; cropRight = s.crop.right; cropBottom = s.crop.bottom
        brightness = s.bright; contrast = s.contr; saturation = s.sat; warmth = s.warm; vignette = s.vig; sharpen = s.sharp; rotationAngle = s.rotAngle
        highlights = s.hi; shadows = s.sh; tiltShift = s.tilt; denoise = s.dn; gradientBg = s.gradBg
        selectedFilter = s.filter
        pixelateRects.clear(); pixelateRects.addAll(s.pixRects)
        drawPaths.clear(); drawPaths.addAll(s.draws)
        curveR = s.cR; curveG = s.cG; curveB = s.cB
    }

    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    fun pushUndo() {
        undoStack.add(captureSnapshot())
        redoStack.clear()
        if (undoStack.size > 30) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(captureSnapshot())
        restoreSnapshot(undoStack.removeLast())
        haptic()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(captureSnapshot())
        restoreSnapshot(redoStack.removeLast())
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

    // Snap edge values to nearby guide positions (image edges, thirds, center)
    fun snapX(v: Int): Int {
        val snapDist = (bitmap.width * 0.015f).toInt().coerceAtLeast(4) // ~1.5% of image width
        val guides = intArrayOf(0, bitmap.width / 4, bitmap.width / 3, bitmap.width / 2,
            bitmap.width * 2 / 3, bitmap.width * 3 / 4, bitmap.width)
        for (g in guides) { if (kotlin.math.abs(v - g) <= snapDist) return g }
        return v
    }
    fun snapY(v: Int): Int {
        val snapDist = (bitmap.height * 0.015f).toInt().coerceAtLeast(4)
        val guides = intArrayOf(0, bitmap.height / 4, bitmap.height / 3, bitmap.height / 2,
            bitmap.height * 2 / 3, bitmap.height * 3 / 4, bitmap.height)
        for (g in guides) { if (kotlin.math.abs(v - g) <= snapDist) return g }
        return v
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
                } else {
                    // Edge magnetism: snap to guide positions when no ratio lock
                    when (handle) {
                        DragHandle.TOP_LEFT -> { cropLeft = snapX(cropLeft); cropTop = snapY(cropTop) }
                        DragHandle.TOP_RIGHT -> { cropRight = snapX(cropRight); cropTop = snapY(cropTop) }
                        DragHandle.BOTTOM_LEFT -> { cropLeft = snapX(cropLeft); cropBottom = snapY(cropBottom) }
                        DragHandle.BOTTOM_RIGHT -> { cropRight = snapX(cropRight); cropBottom = snapY(cropBottom) }
                        DragHandle.TOP -> cropTop = snapY(cropTop)
                        DragHandle.BOTTOM -> cropBottom = snapY(cropBottom)
                        DragHandle.LEFT -> cropLeft = snapX(cropLeft)
                        DragHandle.RIGHT -> cropRight = snapX(cropRight)
                        else -> {}
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
        // Top bar — Row 1: navigation + info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDiscard, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Undo, "Undo (${undoStack.size})",
                        tint = if (undoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Redo, "Redo (${redoStack.size})",
                        tint = if (redoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                if (undoStack.isNotEmpty()) {
                    IconButton(onClick = { showUndoHistory = !showUndoHistory }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.History, "History",
                            tint = if (showUndoHistory) Primary else OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Info: dimensions + method + crop %
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showCropInputDialog = true }) {
                if (methodLabel.isNotEmpty()) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(methodLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${cropW}x${cropH}", color = OnSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                    if (cropPct > 0) {
                        Text("-${cropPct}%", color = Secondary, fontSize = 11.sp)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRotate, modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.RotateRight, "Rotate", tint = OnSurface, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onFlipH, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Flip, "Flip H", tint = OnSurface, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { previewMode = !previewMode }, modifier = Modifier.size(40.dp)) {
                    Icon(if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Preview", tint = if (previewMode) Primary else OnSurface, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Undo history panel (collapsible)
        if (showUndoHistory && undoStack.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(SurfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History:", color = OnSurfaceVariant, fontSize = 10.sp)
                undoStack.forEachIndexed { idx, snap ->
                    val label = buildString {
                        if (snap.draws.isNotEmpty()) append("D${snap.draws.size} ")
                        if (snap.pixRects.isNotEmpty()) append("P${snap.pixRects.size} ")
                        if (snap.filter != ImageFilter.NONE) append(snap.filter.label + " ")
                        if (snap.bright != 0f || snap.contr != 1f || snap.sat != 1f) append("Adj ")
                        val cropDesc = "${snap.crop.width()}x${snap.crop.height()}"
                        if (isEmpty()) append(cropDesc) else append(cropDesc)
                    }.trim()
                    FilterChip(
                        selected = false,
                        onClick = {
                            // Jump to this snapshot: push current state onto redo, restore clicked
                            redoStack.add(captureSnapshot())
                            // Move everything after idx back to redo
                            for (i in undoStack.size - 1 downTo idx + 1) {
                                redoStack.add(undoStack.removeAt(i))
                            }
                            restoreSnapshot(undoStack.removeAt(idx))
                            haptic()
                        },
                        label = { Text("${idx + 1}: $label", fontSize = 9.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            }
        }

        // Top bar — Row 2: mode tabs (scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeChip = @Composable { label: String, mode: EditMode, color: Color ->
                FilterChip(
                    selected = editMode == mode,
                    onClick = {
                        if (editMode == mode && mode != EditMode.CROP) editMode = EditMode.CROP
                        else {
                            editMode = mode
                            if (mode == EditMode.OCR && ocrBlocks.isEmpty() && scannedCodes.isEmpty() && !ocrLoading) {
                                ocrLoading = true
                                scope.launch {
                                    val textDeferred = async(Dispatchers.IO) { TextExtractor.extract(bitmap) }
                                    val codeDeferred = async(Dispatchers.IO) { BarcodeScanner.scan(bitmap) }
                                    ocrBlocks = textDeferred.await()
                                    scannedCodes = codeDeferred.await()
                                    ocrLoading = false
                                }
                            }
                        }
                    },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.25f), selectedLabelColor = color,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            modeChip("Crop", EditMode.CROP, Primary)
            modeChip("Pixelate", EditMode.PIXELATE, Tertiary)
            modeChip("Draw", EditMode.DRAW, Secondary)
            modeChip("OCR", EditMode.OCR, Color(0xFFCBA6F7))
            modeChip("Adjust", EditMode.ADJUST, Color(0xFFFAB387))
            // Transform tools
            IconButton(onClick = onFlipV, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Flip, "Flip V", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 90f)) }
        }

        // Mode indicator
        if (editMode != EditMode.CROP) {
            val (bannerBg, bannerColor, bannerText) = when (editMode) {
                EditMode.PIXELATE -> Triple(Tertiary.copy(alpha = 0.15f), Tertiary, "PIXELATE — draw rectangles to redact")
                EditMode.DRAW -> Triple(Secondary.copy(alpha = 0.15f), Secondary, "DRAW — ${drawTool.label.lowercase()}")
                EditMode.OCR -> {
                    val info = if (ocrLoading) "SCANNING..." else buildString {
                        append("OCR — tap block to copy")
                        if (ocrBlocks.isNotEmpty()) append(" | ${ocrBlocks.size} text")
                        if (scannedCodes.isNotEmpty()) append(" | ${scannedCodes.size} code")
                    }
                    Triple(Color(0xFFCBA6F7).copy(alpha = 0.15f), Color(0xFFCBA6F7), info)
                }
                EditMode.ADJUST -> Triple(Color(0xFFFAB387).copy(alpha = 0.15f), Color(0xFFFAB387), "ADJUST — brightness, contrast, saturation")
                else -> Triple(Color.Transparent, Color.Transparent, "")
            }
            Row(Modifier.fillMaxWidth().background(bannerBg).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ocrLoading && editMode == EditMode.OCR) {
                        CircularProgressIndicator(Modifier.size(12.dp).padding(end = 4.dp), strokeWidth = 1.5.dp, color = bannerColor)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(bannerText, color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                if (editMode == EditMode.OCR && ocrBlocks.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val allText = ocrBlocks.joinToString("\n") { it.text }
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SnapCrop OCR", allText))
                            android.widget.Toast.makeText(context, "Copied all text (${ocrBlocks.size} blocks)", android.widget.Toast.LENGTH_SHORT).show()
                            haptic()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("Copy All", color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Aspect ratio chips (only in crop mode)
        if (editMode == EditMode.CROP) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
            // Lock indicator (tap to unlock ratio)
            if (selectedRatio != AspectRatio.FREE) {
                FilterChip(
                    selected = true,
                    onClick = { selectedRatio = AspectRatio.FREE },
                    label = { Text("\uD83D\uDD12", fontSize = 12.sp) }, // 🔒
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Tertiary.copy(alpha = 0.3f), selectedLabelColor = Tertiary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            // Grid mode toggle
            FilterChip(
                selected = gridMode < 2,
                onClick = { gridMode = (gridMode + 1) % 3 },
                label = { Text(when (gridMode) { 0 -> "⅓"; 1 -> "φ"; else -> "Grid" }, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                    containerColor = SurfaceVariant, labelColor = OnSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        // Gradient background picker (shows when shape crop is selected)
        if (editMode == EditMode.CROP && selectedRatio in listOf(AspectRatio.CIRCLE, AspectRatio.ROUNDED, AspectRatio.STAR, AspectRatio.HEART, AspectRatio.TRIANGLE, AspectRatio.HEXAGON, AspectRatio.DIAMOND)) {
            val gradLabels = listOf("None", "Sunset", "Ocean", "Purple", "Dark", "Mint", "Fire")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BG:", color = OnSurfaceVariant, fontSize = 11.sp)
                gradLabels.forEachIndexed { i, label ->
                    FilterChip(
                        selected = gradientBg == i,
                        onClick = { gradientBg = i },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                            containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Straighten angle slider (crop mode only, when angle != 0 or user taps)
        if (editMode == EditMode.CROP) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Straighten", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                Slider(value = rotationAngle, onValueChange = { rotationAngle = it },
                    valueRange = -45f..45f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant))
                Text("${String.format("%.1f", rotationAngle)}°", color = OnSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.width(36.dp))
                if (rotationAngle != 0f) {
                    TextButton(onClick = { rotationAngle = 0f },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text("Reset", color = Primary, fontSize = 10.sp)
                    }
                }
            }
        }

        // Tool options row (pixelate/draw mode)
        if (editMode == EditMode.PIXELATE) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Smart redact faces
                FilledTonalButton(
                    onClick = {
                        if (!faceRedacting) {
                            faceRedacting = true
                            scope.launch {
                                val faces = FaceDetector.detect(bitmap)
                                if (faces.isNotEmpty()) pushUndo()
                                pixelateRects.addAll(faces)
                                lastFaceCount = faces.size
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
                    else {
                        Text("Blur Faces", fontSize = 11.sp, color = Tertiary)
                        if (lastFaceCount >= 0) {
                            Spacer(Modifier.width(4.dp))
                            Surface(color = Tertiary, shape = RoundedCornerShape(8.dp)) {
                                Text("$lastFaceCount", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row {
                    if (pixelateRects.isNotEmpty()) {
                        TextButton(onClick = { pushUndo(); pixelateRects.removeLastOrNull() }) {
                            Text("Undo", color = Tertiary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { pushUndo(); pixelateRects.clear() }) {
                            Text("Clear", color = Tertiary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (editMode == EditMode.DRAW) {
            // Tool + color row
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
                    // Dashed toggle (for pen, arrow, rect, circle)
                    if (drawTool in listOf(DrawTool.PEN, DrawTool.ARROW, DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE)) {
                        FilterChip(selected = dashedStroke,
                            onClick = { dashedStroke = !dashedStroke },
                            label = { Text("---", fontSize = 10.sp) },
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
                    // Recent custom colors
                    recentColors.forEach { color ->
                        Box(Modifier
                            .size(if (drawColor == color) 24.dp else 18.dp)
                            .background(Color(color), RoundedCornerShape(3.dp))
                            .border(0.5f.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .pointerInput(color) { detectTapGestures { drawColor = color; eyedropperActive = false } })
                    }
                    // Current color preview (tap to open color picker)
                    var showColorPicker by remember { mutableStateOf(false) }
                    Box(Modifier.size(24.dp)
                        .background(Color(drawColor), RoundedCornerShape(3.dp))
                        .border(1.dp, OnSurfaceVariant, RoundedCornerShape(3.dp))
                        .clickable { showColorPicker = true })
                    if (showColorPicker) {
                        var pickerR by remember { mutableFloatStateOf(((drawColor shr 16) and 0xFF) / 255f) }
                        var pickerG by remember { mutableFloatStateOf(((drawColor shr 8) and 0xFF) / 255f) }
                        var pickerB by remember { mutableFloatStateOf((drawColor and 0xFF) / 255f) }
                        AlertDialog(
                            onDismissRequest = { showColorPicker = false },
                            title = { Text("Pick Color", color = OnSurface) },
                            text = {
                                Column {
                                    Box(Modifier.fillMaxWidth().height(40.dp)
                                        .background(Color(pickerR, pickerG, pickerB), RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.height(8.dp))
                                    Text("R", color = Color(0xFFFF6666), fontSize = 11.sp)
                                    Slider(value = pickerR, onValueChange = { pickerR = it }, colors = SliderDefaults.colors(
                                        thumbColor = Color.Red, activeTrackColor = Color.Red, inactiveTrackColor = SurfaceVariant))
                                    Text("G", color = Color(0xFF66FF66), fontSize = 11.sp)
                                    Slider(value = pickerG, onValueChange = { pickerG = it }, colors = SliderDefaults.colors(
                                        thumbColor = Color.Green, activeTrackColor = Color.Green, inactiveTrackColor = SurfaceVariant))
                                    Text("B", color = Color(0xFF6666FF), fontSize = 11.sp)
                                    Slider(value = pickerB, onValueChange = { pickerB = it }, colors = SliderDefaults.colors(
                                        thumbColor = Color.Blue, activeTrackColor = Color.Blue, inactiveTrackColor = SurfaceVariant))
                                    val hex = String.format("#%02X%02X%02X", (pickerR * 255).toInt(), (pickerG * 255).toInt(), (pickerB * 255).toInt())
                                    Text(hex, color = OnSurfaceVariant, fontSize = 12.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val newColor = (0xFF000000.toInt() or ((pickerR * 255).toInt() shl 16) or ((pickerG * 255).toInt() shl 8) or (pickerB * 255).toInt())
                                    drawColor = newColor
                                    if (!recentColors.contains(newColor)) {
                                        recentColors.add(0, newColor)
                                        if (recentColors.size > 4) recentColors.removeLast()
                                    }
                                    eyedropperActive = false
                                    showColorPicker = false
                                }) { Text("Apply", color = Primary) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showColorPicker = false }) { Text("Cancel", color = OnSurfaceVariant) }
                            },
                            containerColor = SurfaceVariant
                        )
                    }
                }
                Row {
                    if (drawPaths.isNotEmpty()) {
                        TextButton(onClick = {
                            pushUndo()
                            drawPaths.removeLastOrNull()?.let { drawRedoStack.add(it) }
                        }) { Text("Undo", color = Secondary, fontSize = 11.sp) }
                    }
                    if (drawRedoStack.isNotEmpty()) {
                        TextButton(onClick = {
                            pushUndo()
                            drawRedoStack.removeLastOrNull()?.let { drawPaths.add(it) }
                        }) { Text("Redo", color = Secondary, fontSize = 11.sp) }
                    }
                    if (drawPaths.isNotEmpty()) {
                        TextButton(onClick = { pushUndo(); drawPaths.clear(); drawRedoStack.clear() }) {
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
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
            // Filter presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ImageFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = adjustColor.copy(alpha = 0.3f), selectedLabelColor = adjustColor,
                            containerColor = SurfaceVariant, labelColor = OnSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Warmth", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = warmth, onValueChange = { warmth = it },
                        valueRange = -50f..50f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${warmth.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vignette", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = vignette, onValueChange = { vignette = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(vignette * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sharpen", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = sharpen, onValueChange = { sharpen = it },
                        valueRange = 0f..2f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", sharpen)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Highlights", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = highlights, onValueChange = { highlights = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${highlights.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shadows", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = shadows, onValueChange = { shadows = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${shadows.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tilt-Shift", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = tiltShift, onValueChange = { tiltShift = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(tiltShift * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Denoise", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = denoise, onValueChange = { denoise = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(denoise * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                // Curves (per-channel RGB)
                val curvesColor = Color(0xFFCBA6F7) // Lavender
                Text("Curves", color = curvesColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Red", color = Color(0xFFFF6B6B), fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveR, onValueChange = { curveR = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6B6B), activeTrackColor = Color(0xFFFF6B6B), inactiveTrackColor = SurfaceVariant))
                    Text("${curveR.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Green", color = Color(0xFF51CF66), fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveG, onValueChange = { curveG = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF51CF66), activeTrackColor = Color(0xFF51CF66), inactiveTrackColor = SurfaceVariant))
                    Text("${curveG.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blue", color = Color(0xFF339AF0), fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveB, onValueChange = { curveB = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF339AF0), activeTrackColor = Color(0xFF339AF0), inactiveTrackColor = SurfaceVariant))
                    Text("${curveB.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        // Auto-enhance: analyze bitmap histogram and set optimal values
                        pushUndo()
                        val sampleSize = 4
                        val sw = (bitmap.width / sampleSize).coerceAtLeast(1); val sh = (bitmap.height / sampleSize).coerceAtLeast(1)
                        val sampled = android.graphics.Bitmap.createScaledBitmap(bitmap, sw, sh, false)
                        var totalLum = 0f; var minLum = 255f; var maxLum = 0f; var totalSat = 0f
                        val pixels = IntArray(sw * sh)
                        sampled.getPixels(pixels, 0, sw, 0, 0, sw, sh)
                        for (px in pixels) {
                            val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                            val lum = 0.299f * r + 0.587f * g + 0.114f * b
                            totalLum += lum; minLum = minOf(minLum, lum); maxLum = maxOf(maxLum, lum)
                            val cMax = maxOf(r, g, b).toFloat(); val cMin = minOf(r, g, b).toFloat()
                            totalSat += if (cMax > 0) (cMax - cMin) / cMax else 0f
                        }
                        sampled.recycle()
                        val n = pixels.size.toFloat()
                        val avgLum = totalLum / n
                        val avgSat = totalSat / n
                        // Target: mid-tone brightness, decent contrast, natural saturation
                        brightness = ((128f - avgLum) * 0.4f).coerceIn(-40f, 40f)
                        contrast = if (maxLum - minLum < 150) 1.2f else if (maxLum - minLum > 230) 0.95f else 1.05f
                        saturation = if (avgSat < 0.15f) 1.3f else if (avgSat > 0.5f) 0.9f else 1.1f
                        warmth = 0f; vignette = 0f
                        haptic()
                    }) {
                        Text("Auto", color = adjustColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; vignette = 0f; sharpen = 0f; highlights = 0f; shadows = 0f; tiltShift = 0f; denoise = 0f; curveR = 0f; curveG = 0f; curveB = 0f; selectedFilter = ImageFilter.NONE }) {
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
                // Before/after swipe comparison
                var dividerX by remember { mutableFloatStateOf(0.5f) }
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { previewMode = false }) }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                dividerX = (down.position.x / size.width).coerceIn(0f, 1f)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    dividerX = (change.position.x / size.width).coerceIn(0f, 1f)
                                    change.consume()
                                }
                            }
                        }
                    ) {
                        // Right side: cropped preview (full)
                        val s = min(size.width / croppedPreview.width, size.height / croppedPreview.height)
                        val dw = croppedPreview.width * s; val dh = croppedPreview.height * s
                        val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
                        drawImage(croppedPreview, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))

                        // Left side: original image (clipped to divider)
                        val divPx = size.width * dividerX
                        clipRect(left = 0f, top = 0f, right = divPx, bottom = size.height) {
                            val os = min(size.width / imageBitmap.width, size.height / imageBitmap.height)
                            val odw = imageBitmap.width * os; val odh = imageBitmap.height * os
                            val oox = (size.width - odw) / 2; val ooy = (size.height - odh) / 2
                            drawImage(imageBitmap, dstOffset = IntOffset(oox.roundToInt(), ooy.roundToInt()),
                                dstSize = IntSize(odw.roundToInt(), odh.roundToInt()))
                        }

                        // Divider line
                        drawLine(Color.White, Offset(divPx, 0f), Offset(divPx, size.height), strokeWidth = 3f)
                        drawCircle(Color.White, 12f, Offset(divPx, size.height / 2))
                    }
                    // Labels
                    Text("BEFORE", Modifier.align(Alignment.TopStart).padding(12.dp),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("AFTER", Modifier.align(Alignment.TopEnd).padding(12.dp),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, editMode, ocrBlocks, scannedCodes) {
                            var lastTapTime = 0L
                            var lastTapPos = Offset.Zero

                            awaitEachGesture {
                                val firstDown = awaitFirstDown()
                                firstDown.consume()
                                val downPos = firstDown.position

                                val cropHandle = if (editMode == EditMode.CROP) findHandle(downPos) else DragHandle.NONE

                                var totalDrag = Offset.Zero
                                var moved = false
                                var multiTouch = false
                                var dragStarted = false
                                var dragStartTime = 0L
                                var drawPathLength = 0f
                                var prevSpread = 0f
                                var prevCentroid = downPos
                                var prevCount = 1

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.isEmpty()) {
                                        // All fingers up
                                        if (!moved && !multiTouch) {
                                            // TAP
                                            val now = event.changes.first().uptimeMillis
                                            if (now - lastTapTime < 300L && (downPos - lastTapPos).getDistance() < viewConfiguration.touchSlop * 3) {
                                                // Double tap
                                                if (editMode == EditMode.OCR && ocrBlocks.isNotEmpty()) {
                                                    // Double-tap in OCR: crop to tapped text block
                                                    val bx = ((downPos.x - offsetX) / scaleX).toInt()
                                                    val by = ((downPos.y - offsetY) / scaleY).toInt()
                                                    val tapped = ocrBlocks.find { it.bounds.contains(bx, by) }
                                                    if (tapped != null) {
                                                        pushUndo()
                                                        val pad = 10
                                                        cropLeft = (tapped.bounds.left - pad).coerceAtLeast(0)
                                                        cropTop = (tapped.bounds.top - pad).coerceAtLeast(0)
                                                        cropRight = (tapped.bounds.right + pad).coerceAtMost(bitmap.width)
                                                        cropBottom = (tapped.bounds.bottom + pad).coerceAtMost(bitmap.height)
                                                        selectedRatio = AspectRatio.FREE
                                                        editMode = EditMode.CROP
                                                        android.widget.Toast.makeText(context, "Cropped to text block", android.widget.Toast.LENGTH_SHORT).show()
                                                        haptic()
                                                    }
                                                } else if (zoomLevel > 1.05f) { zoomLevel = 1f; panX = 0f; panY = 0f }
                                                else previewMode = true
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = now
                                                lastTapPos = downPos
                                                // Single tap actions
                                                if (eyedropperActive && editMode == EditMode.DRAW && !bitmap.isRecycled) {
                                                    val bx = ((downPos.x - offsetX) / scaleX).toInt().coerceIn(0, bitmap.width - 1)
                                                    val by = ((downPos.y - offsetY) / scaleY).toInt().coerceIn(0, bitmap.height - 1)
                                                    drawColor = bitmap.getPixel(bx, by)
                                                    eyedropperActive = false
                                                    haptic()
                                                } else if (editMode == EditMode.DRAW) {
                                                    val bx = ((downPos.x - offsetX) / scaleX).coerceIn(0f, (bitmap.width - 1).toFloat())
                                                    val by = ((downPos.y - offsetY) / scaleY).coerceIn(0f, (bitmap.height - 1).toFloat())
                                                    when (drawTool) {
                                                        DrawTool.TEXT -> {
                                                            textPlacePoint = PointF(bx, by)
                                                            textDialogValue = ""
                                                            showTextDialog = true
                                                        }
                                                        DrawTool.CALLOUT -> {
                                                            pushUndo()
                                                            drawPaths.add(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "callout", text = "${calloutCounter++}"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.MAGNIFIER -> {
                                                            pushUndo()
                                                            drawPaths.add(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "magnifier"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.EMOJI -> {
                                                            pushUndo()
                                                            drawPaths.add(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "emoji", text = selectedEmoji
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.FILL -> {
                                                            pushUndo()
                                                            drawPaths.add(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = 0f,
                                                                shapeType = "fill"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.HEAL -> {
                                                            // Heal = content-aware fill from surrounding pixels (stored as special draw path)
                                                            // First tap sets source point, drag paints from source
                                                        }
                                                        else -> {}
                                                    }
                                                } else if (editMode == EditMode.OCR) {
                                                    val bx = ((downPos.x - offsetX) / scaleX).toInt()
                                                    val by = ((downPos.y - offsetY) / scaleY).toInt()
                                                    val tappedCode = scannedCodes.find { it.bounds.contains(bx, by) }
                                                    if (tappedCode != null) {
                                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        cm.setPrimaryClip(ClipData.newPlainText("SnapCrop QR", tappedCode.rawValue))
                                                        android.widget.Toast.makeText(context, "Copied: ${tappedCode.displayValue.take(80)}", android.widget.Toast.LENGTH_SHORT).show()
                                                        haptic()
                                                    } else {
                                                        val tappedText = ocrBlocks.find { it.bounds.contains(bx, by) }
                                                        if (tappedText != null) {
                                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("SnapCrop OCR", tappedText.text))
                                                            android.widget.Toast.makeText(context, "Copied: ${tappedText.text.take(50)}", android.widget.Toast.LENGTH_SHORT).show()
                                                            haptic()
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Drag end cleanup
                                        if (dragStarted) {
                                            when (editMode) {
                                                EditMode.PIXELATE -> {
                                                    if (pixDragStart != null && pixDragCurrent != null) {
                                                        val s = pixDragStart!!; val e = pixDragCurrent!!
                                                        val bx1 = ((minOf(s.x, e.x) - offsetX) / scaleX).roundToInt().coerceIn(0, bitmap.width)
                                                        val by1 = ((minOf(s.y, e.y) - offsetY) / scaleY).roundToInt().coerceIn(0, bitmap.height)
                                                        val bx2 = ((maxOf(s.x, e.x) - offsetX) / scaleX).roundToInt().coerceIn(0, bitmap.width)
                                                        val by2 = ((maxOf(s.y, e.y) - offsetY) / scaleY).roundToInt().coerceIn(0, bitmap.height)
                                                        if (bx2 - bx1 > 10 && by2 - by1 > 10) {
                                                            pushUndo()
                                                            pixelateRects.add(Rect(bx1, by1, bx2, by2))
                                                            haptic()
                                                        }
                                                    }
                                                    pixDragStart = null; pixDragCurrent = null
                                                }
                                                EditMode.DRAW -> {
                                                    if (currentDrawPoints.size >= 2 && drawTool != DrawTool.TEXT && drawTool != DrawTool.CALLOUT && drawTool != DrawTool.FILL) {
                                                        val shape = when (drawTool) {
                                                            DrawTool.RECT -> "rect"; DrawTool.CIRCLE -> "circle"
                                                            DrawTool.HIGHLIGHT -> "highlight"; DrawTool.SPOTLIGHT -> "spotlight"
                                                            DrawTool.NEON -> "neon"; DrawTool.BLUR -> "blur"; DrawTool.LINE -> "line"; DrawTool.ERASER -> "eraser"; DrawTool.HEAL -> "heal"; else -> null
                                                        }
                                                        // Velocity-based stroke modulation for freehand tools
                                                        val velFactor = if (drawTool == DrawTool.PEN || drawTool == DrawTool.NEON) {
                                                            val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1)
                                                            val velocity = drawPathLength / elapsed // px/ms
                                                            (1.5f - velocity * 0.8f).coerceIn(0.6f, 1.5f) // slow=thick, fast=thin
                                                        } else 1f
                                                        val baseWidth = when (drawTool) { DrawTool.HIGHLIGHT -> drawStrokeWidth * 3; DrawTool.BLUR -> drawStrokeWidth * 4; DrawTool.ERASER -> drawStrokeWidth * 3; DrawTool.HEAL -> drawStrokeWidth * 3; else -> drawStrokeWidth }
                                                        drawPaths.add(DrawPath(
                                                            points = when {
                                                                shape == "rect" || shape == "circle" || shape == "spotlight" || shape == "line" -> listOf(currentDrawPoints.first(), currentDrawPoints.last())
                                                                drawTool == DrawTool.PEN || drawTool == DrawTool.HIGHLIGHT || drawTool == DrawTool.NEON || drawTool == DrawTool.BLUR || drawTool == DrawTool.ERASER || drawTool == DrawTool.HEAL -> smoothPath(currentDrawPoints)
                                                                else -> currentDrawPoints.toList()
                                                            },
                                                            color = drawColor,
                                                            strokeWidth = baseWidth * velFactor,
                                                            isArrow = drawTool == DrawTool.ARROW,
                                                            shapeType = shape,
                                                            filled = shapeFilled && (shape == "rect" || shape == "circle"),
                                                            dashed = dashedStroke
                                                        ))
                                                        haptic()
                                                    }
                                                    currentDrawPoints.clear()
                                                }
                                                EditMode.CROP -> {}
                                                EditMode.OCR, EditMode.ADJUST -> {}
                                            }
                                        }
                                        activeHandle = DragHandle.NONE
                                        pixDragStart = null; pixDragCurrent = null
                                        currentDrawPoints.clear()
                                        break
                                    }

                                    // Multi-touch: pinch-to-zoom
                                    if (pressed.size >= 2) {
                                        multiTouch = true
                                        val centroid = Offset(
                                            pressed.map { it.position.x }.average().toFloat(),
                                            pressed.map { it.position.y }.average().toFloat()
                                        )
                                        val spread = pressed.map { (it.position - centroid).getDistance() }.average().toFloat()
                                        if (prevCount >= 2 && prevSpread > 1f) {
                                            val zoom = spread / prevSpread
                                            val pan = centroid - prevCentroid
                                            if (zoom != 1f || zoomLevel > 1.05f) {
                                                zoomLevel = (zoomLevel * zoom).coerceIn(1f, 5f)
                                                panX += pan.x; panY += pan.y
                                            }
                                        }
                                        prevCentroid = centroid
                                        prevSpread = spread
                                        prevCount = pressed.size
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }

                                    // Single finger drag
                                    prevCount = 1
                                    val change = pressed.first()
                                    val dragDelta = change.positionChange()
                                    totalDrag += dragDelta

                                    if (!moved && totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                        moved = true
                                    }

                                    if (moved && !multiTouch) {
                                        if (!dragStarted) {
                                            dragStarted = true
                                            dragStartTime = System.currentTimeMillis()
                                            when {
                                                cropHandle != DragHandle.NONE -> {
                                                    activeHandle = cropHandle
                                                    pushUndo()
                                                }
                                                editMode == EditMode.PIXELATE -> {
                                                    pixDragStart = downPos; pixDragCurrent = downPos
                                                }
                                                editMode == EditMode.DRAW -> {
                                                    drawRedoStack.clear()
                                                    val bx = ((downPos.x - offsetX) / scaleX).coerceIn(0f, (bitmap.width - 1).toFloat())
                                                    val by = ((downPos.y - offsetY) / scaleY).coerceIn(0f, (bitmap.height - 1).toFloat())
                                                    currentDrawPoints.clear(); currentDrawPoints.add(PointF(bx, by))
                                                }
                                            }
                                        }

                                        when {
                                            cropHandle != DragHandle.NONE -> {
                                                // Precision mode: after 800ms of dragging, slow down by 4x for fine-tuning
                                                val precisionScale = if (System.currentTimeMillis() - dragStartTime > 800) 0.25f else 1f
                                                constrainToRatio(cropHandle,
                                                    (dragDelta.x / scaleX * precisionScale).roundToInt(),
                                                    (dragDelta.y / scaleY * precisionScale).roundToInt())
                                            }
                                            editMode == EditMode.PIXELATE -> {
                                                pixDragCurrent = pixDragCurrent?.plus(dragDelta)
                                            }
                                            editMode == EditMode.DRAW -> {
                                                val pos = change.position
                                                val bx = ((pos.x - offsetX) / scaleX).coerceIn(0f, (bitmap.width - 1).toFloat())
                                                val by = ((pos.y - offsetY) / scaleY).coerceIn(0f, (bitmap.height - 1).toFloat())
                                                if (currentDrawPoints.isNotEmpty()) {
                                                    val prev = currentDrawPoints.last()
                                                    drawPathLength += kotlin.math.sqrt(((bx - prev.x) * (bx - prev.x) + (by - prev.y) * (by - prev.y)).toDouble()).toFloat()
                                                }
                                                currentDrawPoints.add(PointF(bx, by))
                                            }
                                            editMode == EditMode.CROP && zoomLevel > 1.05f -> {
                                                panX += dragDelta.x; panY += dragDelta.y
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        }
                ) {
                    val imgW = bitmap.width.toFloat(); val imgH = bitmap.height.toFloat()
                    val fitScale = min(size.width / imgW, size.height / imgH)
                    val fitW = imgW * fitScale; val fitH = imgH * fitScale
                    val fitOx = (size.width - fitW) / 2; val fitOy = (size.height - fitH) / 2
                    // Avoid state writes inside DrawScope to prevent recomposition loops
                    if (baseScale != fitScale || baseOx != fitOx || baseOy != fitOy) {
                        baseScale = fitScale; baseOx = fitOx; baseOy = fitOy
                    }

                    // Effective (zoomed) image position
                    val ox = offsetX; val oy = offsetY
                    val scale = scaleX
                    val drawW = imgW * scale; val drawH = imgH * scale

                    // Build color adjustment matrix
                    val hasAdjustments = brightness != 0f || contrast != 1f || saturation != 1f || warmth != 0f || selectedFilter != ImageFilter.NONE
                    val adjustFilter = if (hasAdjustments) {
                        val cm = ColorMatrix()
                        // Image filter preset (applied first)
                        val filterMat = getFilterMatrix(selectedFilter)
                        if (filterMat != null) {
                            cm.timesAssign(ColorMatrix(filterMat.getArray()))
                        }
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
                        // Warmth: shift red up / blue down (or vice versa)
                        if (warmth != 0f) {
                            cm.timesAssign(ColorMatrix(floatArrayOf(
                                1f, 0f, 0f, 0f, warmth,
                                0f, 1f, 0f, 0f, 0f,
                                0f, 0f, 1f, 0f, -warmth,
                                0f, 0f, 0f, 1f, 0f
                            )))
                        }
                        ColorFilter.colorMatrix(cm)
                    } else null

                    // Apply straighten rotation
                    if (rotationAngle != 0f) {
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.rotate(rotationAngle, ox + drawW / 2, oy + drawH / 2)
                    }

                    drawImage(imageBitmap, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                        colorFilter = adjustFilter)

                    // Vignette overlay (radial gradient from transparent to black)
                    if (vignette > 0.01f) {
                        drawContext.canvas.nativeCanvas.apply {
                            vigPaint.shader = android.graphics.RadialGradient(
                                ox + drawW / 2, oy + drawH / 2,
                                maxOf(drawW, drawH) * 0.7f,
                                intArrayOf(0x00000000, (vignette * 200).toInt().coerceAtMost(200) shl 24),
                                floatArrayOf(0.4f, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            drawRect(ox, oy, ox + drawW, oy + drawH, vigPaint)
                        }
                    }

                    if (rotationAngle != 0f) {
                        drawContext.canvas.nativeCanvas.restore()
                    }

                    val sl = ox + cropLeft * scale; val st = oy + cropTop * scale
                    val sr = ox + cropRight * scale; val sb = oy + cropBottom * scale

                    // Dim overlay
                    drawRect(DimOverlay, Offset(ox, oy), Size(drawW, st - oy))
                    drawRect(DimOverlay, Offset(ox, sb), Size(drawW, oy + drawH - sb))
                    drawRect(DimOverlay, Offset(ox, st), Size(sl - ox, sb - st))
                    drawRect(DimOverlay, Offset(sr, st), Size(ox + drawW - sr, sb - st))

                    drawRect(CropBorder, Offset(sl, st), Size(sr - sl, sb - st), style = Stroke(2.dp.toPx()))

                    // Snap guide lines (show when crop edge aligns with a guide)
                    if (activeHandle != DragHandle.NONE && activeHandle != DragHandle.CENTER && selectedRatio.ratio == null) {
                        val snapGuideColor = Primary.copy(alpha = 0.35f)
                        val xGuides = listOf(0, bitmap.width / 4, bitmap.width / 3, bitmap.width / 2,
                            bitmap.width * 2 / 3, bitmap.width * 3 / 4, bitmap.width)
                        val yGuides = listOf(0, bitmap.height / 4, bitmap.height / 3, bitmap.height / 2,
                            bitmap.height * 2 / 3, bitmap.height * 3 / 4, bitmap.height)
                        for (g in xGuides) {
                            if (cropLeft == g || cropRight == g) {
                                val gx = ox + g * scale
                                drawLine(snapGuideColor, Offset(gx, oy), Offset(gx, oy + drawH), 1.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                            }
                        }
                        for (g in yGuides) {
                            if (cropTop == g || cropBottom == g) {
                                val gy = oy + g * scale
                                drawLine(snapGuideColor, Offset(ox, gy), Offset(ox + drawW, gy), 1.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                            }
                        }
                    }

                    // Grid overlay (tap crop border text to cycle: thirds → golden → off)
                    val gridColor = CropBorder.copy(alpha = 0.3f)
                    val cw = sr - sl; val ch = sb - st
                    when (gridMode) {
                        0 -> { // Rule of thirds
                            val tw = cw / 3; val th = ch / 3
                            for (i in 1..2) {
                                drawLine(gridColor, Offset(sl + tw * i, st), Offset(sl + tw * i, sb), 1f)
                                drawLine(gridColor, Offset(sl, st + th * i), Offset(sr, st + th * i), 1f)
                            }
                        }
                        1 -> { // Golden ratio (φ ≈ 0.618)
                            val phi = 0.618f
                            val gx1 = cw * phi; val gx2 = cw * (1f - phi)
                            val gy1 = ch * phi; val gy2 = ch * (1f - phi)
                            drawLine(gridColor, Offset(sl + gx1, st), Offset(sl + gx1, sb), 1f)
                            drawLine(gridColor, Offset(sl + gx2, st), Offset(sl + gx2, sb), 1f)
                            drawLine(gridColor, Offset(sl, st + gy1), Offset(sr, st + gy1), 1f)
                            drawLine(gridColor, Offset(sl, st + gy2), Offset(sr, st + gy2), 1f)
                        }
                        // 2 = no grid
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

                    // Shape crop preview overlay
                    if (selectedRatio == AspectRatio.CIRCLE) {
                        val cx = (sl + sr) / 2; val cy = (st + sb) / 2
                        val radius = minOf(sr - sl, sb - st) / 2
                        // Gradient fill preview
                        if (gradientBg > 0) {
                            val gradBrush = getGradientBrush(gradientBg, sl, st, sr, sb)
                            if (gradBrush != null) drawCircle(brush = gradBrush, radius = radius, center = Offset(cx, cy), alpha = 0.4f)
                        }
                        drawCircle(CropBorder.copy(alpha = 0.5f), radius, Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    } else if (selectedRatio == AspectRatio.ROUNDED) {
                        if (gradientBg > 0) {
                            val gradBrush = getGradientBrush(gradientBg, sl, st, sr, sb)
                            if (gradBrush != null) drawRoundRect(brush = gradBrush, topLeft = Offset(sl, st), size = Size(sr - sl, sb - st),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()), alpha = 0.4f)
                        }
                        drawRoundRect(CropBorder.copy(alpha = 0.5f), Offset(sl, st), Size(sr - sl, sb - st),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()), style = Stroke(2.dp.toPx()))
                    } else if (selectedRatio in listOf(AspectRatio.STAR, AspectRatio.HEART, AspectRatio.TRIANGLE, AspectRatio.HEXAGON, AspectRatio.DIAMOND)) {
                        val shapeSize = minOf(sr - sl, sb - st)
                        val scx = (sl + sr) / 2; val scy = (st + sb) / 2
                        val shapeLeft = scx - shapeSize / 2; val shapeTop = scy - shapeSize / 2
                        val shapePath = androidx.compose.ui.graphics.Path()
                        when (selectedRatio) {
                            AspectRatio.STAR -> {
                                val outerR = shapeSize / 2; val innerR = outerR * 0.38f
                                for (i in 0 until 10) {
                                    val r = if (i % 2 == 0) outerR else innerR
                                    val angle = Math.toRadians((i * 36.0 - 90.0)).toFloat()
                                    val px = scx + r * kotlin.math.cos(angle); val py = scy + r * kotlin.math.sin(angle)
                                    if (i == 0) shapePath.moveTo(px, py) else shapePath.lineTo(px, py)
                                }
                            }
                            AspectRatio.HEART -> {
                                val w = shapeSize; val h = shapeSize
                                shapePath.moveTo(scx, shapeTop + h * 0.25f)
                                shapePath.cubicTo(shapeLeft + w * 0.15f, shapeTop + h * -0.05f, shapeLeft - w * 0.1f, shapeTop + h * 0.45f, scx, shapeTop + h * 0.95f)
                                shapePath.lineTo(scx, shapeTop + h * 0.25f)
                                shapePath.cubicTo(shapeLeft + w * 0.85f, shapeTop + h * -0.05f, shapeLeft + w * 1.1f, shapeTop + h * 0.45f, scx, shapeTop + h * 0.95f)
                            }
                            AspectRatio.TRIANGLE -> {
                                shapePath.moveTo(scx, shapeTop + shapeSize * 0.05f)
                                shapePath.lineTo(shapeLeft + shapeSize * 0.95f, shapeTop + shapeSize * 0.95f)
                                shapePath.lineTo(shapeLeft + shapeSize * 0.05f, shapeTop + shapeSize * 0.95f)
                            }
                            AspectRatio.HEXAGON -> {
                                val r = shapeSize / 2 * 0.95f
                                for (i in 0 until 6) {
                                    val angle = Math.toRadians((i * 60.0 - 30.0)).toFloat()
                                    val px = scx + r * kotlin.math.cos(angle); val py = scy + r * kotlin.math.sin(angle)
                                    if (i == 0) shapePath.moveTo(px, py) else shapePath.lineTo(px, py)
                                }
                            }
                            AspectRatio.DIAMOND -> {
                                val half = shapeSize / 2 * 0.95f
                                shapePath.moveTo(scx, scy - half)        // top
                                shapePath.lineTo(scx + half, scy)        // right
                                shapePath.lineTo(scx, scy + half)        // bottom
                                shapePath.lineTo(scx - half, scy)        // left
                            }
                            else -> {}
                        }
                        shapePath.close()
                        // Gradient fill preview
                        if (gradientBg > 0) {
                            val gradBrush = getGradientBrush(gradientBg, sl, st, sr, sb)
                            if (gradBrush != null) drawPath(shapePath, brush = gradBrush, alpha = 0.4f)
                        }
                        drawPath(shapePath, CropBorder.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
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
                            val tx = ox + p.x * scale; val ty = oy + p.y * scale
                            // Background pill
                            if (dp.filled) {
                                val bounds = android.graphics.Rect()
                                textPaint.getTextBounds(dp.text, 0, dp.text.length, bounds)
                                val pad = textPaint.textSize * 0.3f
                                val bgColor = 0xCC000000.toInt()
                                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                                bgPaint.color = bgColor; bgPaint.style = android.graphics.Paint.Style.FILL
                                drawContext.canvas.nativeCanvas.drawRoundRect(
                                    tx - pad, ty + bounds.top - pad, tx + bounds.width() + pad, ty + bounds.bottom + pad,
                                    pad, pad, bgPaint)
                            }
                            drawContext.canvas.nativeCanvas.drawText(dp.text, tx, ty, textPaint)
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

                        // Neon glow pen — thick blurred glow + thin bright core
                        if (shape == "neon" && pts.size >= 2) {
                            // Outer glow (wide, semi-transparent)
                            val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color; strokeWidth = sw * 3; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND; alpha = 80
                                maskFilter = android.graphics.BlurMaskFilter(sw * 2, android.graphics.BlurMaskFilter.Blur.NORMAL)
                            }
                            val glowPath = android.graphics.Path()
                            glowPath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            for (i in 1 until pts.size) glowPath.lineTo(ox + pts[i].x * scale, oy + pts[i].y * scale)
                            drawContext.canvas.nativeCanvas.drawPath(glowPath, glowPaint)
                            // Inner core (bright, thin)
                            val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0xFFFFFFFF.toInt(); strokeWidth = sw * 0.6f; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            drawContext.canvas.nativeCanvas.drawPath(glowPath, corePaint)
                            // Mid layer (colored, medium)
                            val midPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color; strokeWidth = sw; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND; alpha = 200
                            }
                            drawContext.canvas.nativeCanvas.drawPath(glowPath, midPaint)
                            return
                        }

                        // Blur brush — semi-transparent white wide stroke as visual indicator
                        if (shape == "blur" && pts.size >= 2) {
                            val blurPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0xFFFFFFFF.toInt(); strokeWidth = sw; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND; alpha = 40
                                maskFilter = android.graphics.BlurMaskFilter(sw * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                            }
                            val blurPath = android.graphics.Path()
                            blurPath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            for (i in 1 until pts.size) blurPath.lineTo(ox + pts[i].x * scale, oy + pts[i].y * scale)
                            drawContext.canvas.nativeCanvas.drawPath(blurPath, blurPaint)
                            return
                        }

                        // Line tool — straight line between first and last points
                        if (shape == "line" && pts.size >= 2) {
                            val p1 = pts.first(); val p2 = pts.last()
                            val dashEffect = if (dp.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 3, sw * 2), 0f) else null
                            drawLine(color,
                                Offset(ox + p1.x * scale, oy + p1.y * scale),
                                Offset(ox + p2.x * scale, oy + p2.y * scale),
                                strokeWidth = sw,
                                pathEffect = dashEffect)
                            return
                        }

                        // Eraser — semi-transparent checkerboard-style indicator
                        if (shape == "eraser" && pts.size >= 2) {
                            val eraserPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0xFFFF6666.toInt(); strokeWidth = sw; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND; alpha = 60
                            }
                            val eraserPath = android.graphics.Path()
                            eraserPath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            for (i in 1 until pts.size) eraserPath.lineTo(ox + pts[i].x * scale, oy + pts[i].y * scale)
                            drawContext.canvas.nativeCanvas.drawPath(eraserPath, eraserPaint)
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
                            val dashEffect = if (dp.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 3, sw * 2), 0f) else null
                            val style = if (dp.filled) null else Stroke(sw, pathEffect = dashEffect)
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
                        if (dp.dashed) {
                            // Dashed lines via nativeCanvas
                            val dashPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color; strokeWidth = sw; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                pathEffect = android.graphics.DashPathEffect(floatArrayOf(sw * 3, sw * 2), 0f)
                            }
                            val dashPath = android.graphics.Path()
                            dashPath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            for (i in 1 until pts.size) dashPath.lineTo(ox + pts[i].x * scale, oy + pts[i].y * scale)
                            drawContext.canvas.nativeCanvas.drawPath(dashPath, dashPaint)
                        } else {
                            for (i in 1 until pts.size) {
                                val a = pts[i - 1]; val b = pts[i]
                                drawLine(color, Offset(ox + a.x * scale, oy + a.y * scale),
                                    Offset(ox + b.x * scale, oy + b.y * scale), strokeWidth = sw)
                            }
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
                            DrawTool.HIGHLIGHT -> "highlight"; DrawTool.SPOTLIGHT -> "spotlight"
                            DrawTool.NEON -> "neon"; DrawTool.BLUR -> "blur"; DrawTool.LINE -> "line"; DrawTool.ERASER -> "eraser"; else -> null
                        }
                        val curSw = when (drawTool) { DrawTool.HIGHLIGHT -> drawStrokeWidth * 3; DrawTool.BLUR -> drawStrokeWidth * 4; DrawTool.ERASER -> drawStrokeWidth * 3; else -> drawStrokeWidth }
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

                FilledTonalButton(
                    onClick = {
                        if (!bgRemoving) {
                            bgRemoving = true
                            onRemoveBg()
                        }
                    },
                    enabled = !bgRemoving,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Secondary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (bgRemoving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Secondary)
                    else Text("BG", fontSize = 13.sp, color = Secondary)
                }

                FilledTonalButton(
                    onClick = {
                        if (paletteColors.isEmpty()) {
                            scope.launch(Dispatchers.Default) {
                                val colors = ColorPaletteExtractor.extract(bitmap)
                                paletteColors = colors
                            }
                        }
                        showPalette = !showPalette
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFAB387).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Colors", fontSize = 13.sp, color = Color(0xFFFAB387)) }
            }

            // Color palette display
            if (showPalette && paletteColors.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paletteColors.forEach { pc ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Color", pc.hex))
                                android.widget.Toast.makeText(context, "Copied ${pc.hex}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Box(Modifier.size(28.dp).background(Color(pc.color), RoundedCornerShape(4.dp))
                                .border(1.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                            Text(pc.hex, fontSize = 8.sp, color = OnSurfaceVariant)
                            Text("${pc.percentage.toInt()}%", fontSize = 7.sp, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Action icons row
            val shapeCrop = when (selectedRatio) { AspectRatio.CIRCLE -> 1f; AspectRatio.ROUNDED -> 2f; AspectRatio.STAR -> 3f; AspectRatio.HEART -> 4f; AspectRatio.TRIANGLE -> 5f; AspectRatio.HEXAGON -> 6f; AspectRatio.DIAMOND -> 7f; else -> 0f }
            val adj = floatArrayOf(brightness, contrast, saturation, shapeCrop, warmth, vignette, selectedFilter.ordinal.toFloat(), sharpen, rotationAngle, highlights, shadows, tiltShift, denoise, gradientBg.toFloat(), curveR, curveG, curveB)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Tertiary) }
                IconButton(onClick = { showResizeDialog = true }) {
                    Icon(Icons.Default.PhotoSizeSelectLarge, "Resize", tint = OnSurface) }
                IconButton(onClick = { onShare(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.Share, "Share", tint = OnSurface) }
                IconButton(onClick = { onCopyClipboard(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.ContentCopy, "Clipboard", tint = OnSurface) }
                IconButton(onClick = { onSaveCopy(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) }) {
                    Icon(Icons.Default.Save, "Save Copy", tint = OnSurface) }
            }

            // Main save button — full width
            Button(onClick = { onSave(Rect(cropLeft, cropTop, cropRight, cropBottom), pixelateRects.toList(), drawPaths.toList(), adj) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Crop, null, Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Crop & Save", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                // Estimated file size (read prefs once, not every recomposition)
                val (isJpeg, isWebp) = remember {
                    val prefs = context.getSharedPreferences("snapcrop", android.content.Context.MODE_PRIVATE)
                    prefs.getBoolean("use_jpeg", false) to prefs.getBoolean("use_webp", false)
                }
                val pixels = cropW.toLong() * cropH
                val estKb = when {
                    isWebp -> pixels * 0.5f / 1024 // ~0.5 bytes/px for WebP
                    isJpeg -> pixels * 0.8f / 1024  // ~0.8 bytes/px for JPEG
                    else -> pixels * 3f / 1024       // ~3 bytes/px for PNG
                }
                val estLabel = if (estKb > 1024) String.format("~%.1f MB", estKb / 1024) else String.format("~%.0f KB", estKb)
                Spacer(Modifier.width(6.dp))
                Text(estLabel, color = Color.Black.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }

    // Resize dialog
    if (showResizeDialog) {
        val sizes = listOf(480, 720, 1080, 1440, 2160)
        var selectedSize by remember { mutableIntStateOf(1080) }
        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            title = { Text("Resize Image", color = OnSurface) },
            text = {
                Column {
                    Text("Current: ${bitmap.width}x${bitmap.height}", color = OnSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Max dimension:", color = OnSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sizes.forEach { size ->
                            FilterChip(selected = selectedSize == size,
                                onClick = { selectedSize = size },
                                label = { Text("$size", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                    containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                                shape = RoundedCornerShape(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResizeDialog = false; onResize(selectedSize) }) {
                    Text("Resize", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResizeDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    // Text input dialog
    if (showTextDialog) {
        var textBg by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text", color = OnSurface) },
            text = {
                Column {
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
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = textBg,
                            onClick = { textBg = !textBg },
                            label = { Text("Background", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Size: ${(drawStrokeWidth * 3).toInt()}px", color = OnSurfaceVariant, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textDialogValue.isNotBlank() && textPlacePoint != null) {
                        pushUndo()
                        drawPaths.add(DrawPath(
                            points = listOf(textPlacePoint!!),
                            color = drawColor,
                            strokeWidth = drawStrokeWidth,
                            shapeType = "text",
                            text = textDialogValue,
                            filled = textBg
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

    // Crop input dialog — type exact pixel values
    if (showCropInputDialog) {
        var inputX by remember { mutableStateOf(cropLeft.toString()) }
        var inputY by remember { mutableStateOf(cropTop.toString()) }
        var inputW by remember { mutableStateOf(cropW.toString()) }
        var inputH by remember { mutableStateOf(cropH.toString()) }
        AlertDialog(
            onDismissRequest = { showCropInputDialog = false },
            title = { Text("Crop Dimensions", color = OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Image: ${bitmap.width}x${bitmap.height}", color = OnSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputX, onValueChange = { inputX = it.filter { c -> c.isDigit() } },
                            label = { Text("X", fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                        OutlinedTextField(
                            value = inputY, onValueChange = { inputY = it.filter { c -> c.isDigit() } },
                            label = { Text("Y", fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputW, onValueChange = { inputW = it.filter { c -> c.isDigit() } },
                            label = { Text("Width", fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                        OutlinedTextField(
                            value = inputH, onValueChange = { inputH = it.filter { c -> c.isDigit() } },
                            label = { Text("Height", fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val x = inputX.toIntOrNull() ?: 0
                    val y = inputY.toIntOrNull() ?: 0
                    val w = inputW.toIntOrNull() ?: cropW
                    val h = inputH.toIntOrNull() ?: cropH
                    val newLeft = x.coerceIn(0, bitmap.width - 50)
                    val newTop = y.coerceIn(0, bitmap.height - 50)
                    val newRight = (newLeft + w).coerceIn(newLeft + 50, bitmap.width)
                    val newBottom = (newTop + h).coerceIn(newTop + 50, bitmap.height)
                    pushUndo()
                    cropLeft = newLeft; cropTop = newTop; cropRight = newRight; cropBottom = newBottom
                    selectedRatio = AspectRatio.FREE
                    showCropInputDialog = false
                }) { Text("Apply", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCropInputDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
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
