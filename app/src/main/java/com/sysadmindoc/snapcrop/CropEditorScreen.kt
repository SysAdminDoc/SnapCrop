package com.sysadmindoc.snapcrop

import android.content.Context
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private enum class OcrEntityType { PHONE, EMAIL, URL }

private data class OcrEntity(val type: OcrEntityType, val value: String, val display: String)

private fun extractEntities(text: String): List<OcrEntity> {
    val entities = mutableListOf<OcrEntity>()
    val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    val phoneRegex = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)")
    val urlRegex = Regex("https?://[^\\s)\"'>]+|www\\.[^\\s)\"'>]+")
    val seen = mutableSetOf<String>()
    for (match in emailRegex.findAll(text)) {
        val v = match.value
        if (seen.add(v.lowercase())) {
            entities += OcrEntity(OcrEntityType.EMAIL, v, v)
        }
    }
    for (match in phoneRegex.findAll(text)) {
        val raw = match.value.trim()
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7 && seen.add(digits)) {
            entities += OcrEntity(OcrEntityType.PHONE, digits, raw)
        }
    }
    for (match in urlRegex.findAll(text)) {
        val v = match.value
        if (seen.add(v.lowercase())) {
            val url = if (v.startsWith("www.")) "https://$v" else v
            entities += OcrEntity(OcrEntityType.URL, url, v)
        }
    }
    return entities
}

@Composable
fun CropEditorScreen(
    bitmap: Bitmap,
    initialCropRect: Rect,
    cropMethod: String,
    initialRedactions: List<RedactionRegion> = emptyList(),
    initialDrawPaths: List<DrawPath> = emptyList(),
    initialAdjustments: FloatArray? = null,
    initialCutout: CutoutEditState = CutoutEditState(),
    initialOcrBlocks: List<TextBlock> = emptyList(),
    initialOcrReviewed: Boolean = false,
    initialExportPresetId: String? = null,
    onSave: (Rect, List<RedactionRegion>, List<DrawPath>, FloatArray, CutoutEditState) -> Unit,
    onSaveCopy: (Rect, List<RedactionRegion>, List<DrawPath>, FloatArray, CutoutEditState) -> Unit,
    onShare: (Rect, List<RedactionRegion>, List<DrawPath>, FloatArray, CutoutEditState) -> Unit,
    onCopyClipboard: (Rect, List<RedactionRegion>, List<DrawPath>, FloatArray, CutoutEditState) -> Unit,
    hasSourceContext: Boolean = false,
    onEditSourceContext: () -> Unit = {},
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onAutoCrop: () -> Rect,
    onSmartCrop: () -> Unit,
    onRemoveBg: ((String?) -> Unit) -> Unit,
    onResize: (Int) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onOcrIndexed: (text: String, codes: List<String>) -> Unit = { _, _ -> },
    onOcrChanged: (List<TextBlock>) -> Unit = {},
    onOcrReviewedChanged: (Boolean) -> Unit = {},
    onExportPresetChanged: (String?) -> Unit = {},
    registerStateProvider: ((() -> EditorDraft)?) -> Unit = {},
    replaceOriginalOnSave: Boolean
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val scope = rememberCoroutineScope()

    var showDiscardDialog by remember { mutableStateOf(false) }

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
    var selectedRatio by remember { mutableStateOf(aspectRatioForShapeCrop(initialAdjustments.adjustValue(3, 0f))) }
    var aiLoading by remember { mutableStateOf(false) }
    var reframeLoading by remember { mutableStateOf(false) }

    // Edit modes
    var editMode by remember { mutableStateOf(EditMode.CROP) }
    var cutBands by remember(bitmap) { mutableStateOf(initialCutout.bands) }
    var cutSeparatorStyle by remember(bitmap) { mutableStateOf(initialCutout.separatorStyle) }
    var selectedCutBand by remember { mutableIntStateOf(-1) }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) {
        LocalHelpDialog(
            onOpenRoute = { route ->
                if (route == HelpRoute.EDITOR_REDACTION) editMode = EditMode.PIXELATE
                if (route == HelpRoute.EDITOR_CUTOUT) editMode = EditMode.CUTOUT
            },
            onDismiss = { showHelp = false }
        )
    }
    val redactions = remember { mutableStateListOf<RedactionRegion>().apply { addAll(initialRedactions.map { it.copy() }) } }
    var selectedRedactionIndex by remember { mutableIntStateOf(-1) }
    var pixDragStart by remember { mutableStateOf<Offset?>(null) }
    var pixDragCurrent by remember { mutableStateOf<Offset?>(null) }

    // Draw mode
    val drawPaths = remember { mutableStateListOf<DrawPath>().apply { addAll(initialDrawPaths) } }
    val drawRedoStack = remember { mutableStateListOf<DrawPath>() }
    val currentDrawPoints = remember { mutableStateListOf<PointF>() }
    var drawColor by remember { mutableIntStateOf(0xFFFF0000.toInt()) }
    val recentColors = remember { mutableStateListOf<Int>() }
    var drawStrokeWidth by remember { mutableFloatStateOf(6f) }
    var drawTool by remember { mutableStateOf(DrawTool.PEN) }
    var shapeFilled by remember { mutableStateOf(false) }
    var dashedStroke by remember { mutableStateOf(false) }
    var calloutCounter by remember { mutableIntStateOf(1) }
    // Index of the committed layer currently in transform mode (move/resize/rotate), or -1.
    var selectedLayerIndex by remember { mutableIntStateOf(-1) }
    var selectedLayerIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var eyedropperActive by remember { mutableStateOf(false) }
    var samplerColor1 by remember { mutableStateOf<Int?>(null) }
    var samplerColor2 by remember { mutableStateOf<Int?>(null) }
    var showSamplerDialog by remember { mutableStateOf(false) }
    var showLayerPanel by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val drawPrefs = remember { context.getSharedPreferences("snapcrop", android.content.Context.MODE_PRIVATE) }
    val exportPresets = remember { ExportPresetStore.load(drawPrefs) }
    var selectedExportPresetId by remember {
        mutableStateOf(initialExportPresetId?.takeIf { id -> exportPresets.any { it.id == id } })
    }
    val selectedExportSettings = ExportPresetStore.resolve(drawPrefs, selectedExportPresetId)
    val drawPresets = remember { mutableStateListOf<DrawStylePreset>().apply { addAll(DrawStylePresetStore.load(drawPrefs)) } }
    val defaultPresetName = remember { DrawStylePresetStore.defaultName(drawPrefs) }

    LaunchedEffect(Unit) {
        val def = defaultPresetName?.let { n -> drawPresets.firstOrNull { it.name == n } }
        if (def != null) {
            drawColor = def.color
            drawStrokeWidth = def.strokeWidth
            dashedStroke = def.dashed
            drawTool = def.tool
        }
    }

    LaunchedEffect(drawPaths.size) {
        selectedLayerIndices = selectedLayerIndices.filterTo(linkedSetOf()) { it in drawPaths.indices }
        if (selectedLayerIndex !in selectedLayerIndices) {
            selectedLayerIndex = selectedLayerIndices.maxOrNull() ?: -1
        }
    }

    var bgRemoving by remember { mutableStateOf(false) }
    var bgRemovalStatus by remember { mutableStateOf<String?>(null) }
    var paletteColors by remember { mutableStateOf<List<ColorPaletteExtractor.PaletteColor>>(emptyList()) }

    // Reset bgRemoving and stale palette when bitmap changes (BG removal, rotation, resize)
    LaunchedEffect(bitmap) { bgRemoving = false; bgRemovalStatus = null; paletteColors = emptyList() }

    var showPalette by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var ocrBlocks by remember(bitmap) { mutableStateOf(initialOcrBlocks.map(TextBlock::deepCopy)) }
    var ocrLoading by remember { mutableStateOf(false) }
    var scannedCodes by remember { mutableStateOf<List<ScannedCode>>(emptyList()) }
    var selectedOcrText by remember { mutableStateOf<String?>(null) }
    var showOcrReview by remember { mutableStateOf(false) }
    val selectedOcrBlocks = remember { mutableStateListOf<Int>() }
    val ocrDraftTexts = remember { mutableStateMapOf<Int, String>() }
    var ocrScanCompleted by remember(bitmap) {
        mutableStateOf(initialOcrReviewed || initialOcrBlocks.isNotEmpty())
    }
    var translateTarget by remember { mutableStateOf(TextTranslator.defaultTarget) }
    var translation by remember { mutableStateOf<TextTranslation?>(null) }
    var translationError by remember { mutableStateOf<String?>(null) }
    var translationStatus by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var faceRedacting by remember { mutableStateOf(false) }
    var lastFaceCount by remember { mutableIntStateOf(-1) } // -1 = not scanned yet
    var textRedacting by remember { mutableStateOf(false) }
    var lastTextRedactionCount by remember { mutableIntStateOf(-1) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogValue by remember { mutableStateOf("") }
    var textPlacePoint by remember { mutableStateOf<PointF?>(null) }

    // Emoji tool
    var selectedEmoji by remember { mutableStateOf(commonEmojis[0]) }

    // Adjust mode (brightness/contrast/saturation)
    var brightness by remember { mutableFloatStateOf(initialAdjustments.adjustValue(0, 0f)) }    // -100 to 100
    var contrast by remember { mutableFloatStateOf(initialAdjustments.adjustValue(1, 1f)) }      // 0.5 to 2.0
    var saturation by remember { mutableFloatStateOf(initialAdjustments.adjustValue(2, 1f)) }    // 0.0 to 2.0
    var warmth by remember { mutableFloatStateOf(initialAdjustments.adjustValue(4, 0f)) }        // -50 to 50 (red/blue shift)
    var vignette by remember { mutableFloatStateOf(initialAdjustments.adjustValue(5, 0f)) }     // 0 to 1 (edge darkening)
    var sharpen by remember { mutableFloatStateOf(initialAdjustments.adjustValue(7, 0f)) }      // 0 to 2 (convolution kernel strength)
    var highlights by remember { mutableFloatStateOf(initialAdjustments.adjustValue(9, 0f)) }   // -100 to 100 (bright area adjustment)
    var shadows by remember { mutableFloatStateOf(initialAdjustments.adjustValue(10, 0f)) }      // -100 to 100 (dark area adjustment)
    var tiltShift by remember { mutableFloatStateOf(initialAdjustments.adjustValue(11, 0f)) }    // 0 to 1 (linear blur top/bottom)
    var denoise by remember { mutableFloatStateOf(initialAdjustments.adjustValue(12, 0f)) }      // 0 to 1 (noise reduction strength)
    // Curves: per-channel midpoint adjustments (-100 to 100)
    var curveR by remember { mutableFloatStateOf(initialAdjustments.adjustValue(14, 0f)) }
    var curveG by remember { mutableFloatStateOf(initialAdjustments.adjustValue(15, 0f)) }
    var curveB by remember { mutableFloatStateOf(initialAdjustments.adjustValue(16, 0f)) }
    var selectedFilter by remember { mutableStateOf(filterFromOrdinal(initialAdjustments.adjustValue(6, 0f))) }
    var showCropInputDialog by remember { mutableStateOf(false) }
    var gradientBg by remember { mutableIntStateOf(initialAdjustments.adjustValue(13, 0f).roundToInt().coerceIn(0, 6)) } // 0=none, 1-6=gradient presets
    var showUndoHistory by remember { mutableStateOf(false) }
    var gridMode by remember { mutableIntStateOf(0) } // 0=thirds, 1=golden, 2=none
    var rotationAngle by remember { mutableFloatStateOf(initialAdjustments.adjustValue(8, 0f)) } // -45 to 45 degrees for straightening

    // Perspective crop mode
    var perspectiveMode by remember { mutableStateOf(false) }
    var quadTL by remember { mutableStateOf(PointF(initialCropRect.left.toFloat(), initialCropRect.top.toFloat())) }
    var quadTR by remember { mutableStateOf(PointF(initialCropRect.right.toFloat(), initialCropRect.top.toFloat())) }
    var quadBR by remember { mutableStateOf(PointF(initialCropRect.right.toFloat(), initialCropRect.bottom.toFloat())) }
    var quadBL by remember { mutableStateOf(PointF(initialCropRect.left.toFloat(), initialCropRect.bottom.toFloat())) }

    // Pre-allocate Paint for vignette to avoid allocation in DrawScope
    val vigPaint = remember { android.graphics.Paint() }

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

    fun openOcrText(text: String) {
        selectedOcrText = text
        translation = null
        translationError = null
        translationStatus = null
        translating = false
    }

    fun copyText(label: String, text: String, message: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        haptic()
    }

    fun translateText(text: String) {
        if (translating) return
        translating = true
        translation = null
        translationError = null
        translationStatus = context.getString(MlKitStatus.TRANSLATION_IDENTIFYING)
        scope.launch {
            try {
                translation = withContext(Dispatchers.IO) {
                    TextTranslator.translate(
                        text = text,
                        target = translateTarget,
                        context = context.applicationContext,
                        onProgress = { message -> scope.launch { translationStatus = message } }
                    )
                }
            } catch (error: Throwable) {
                translationError = TextTranslator.userMessage(context, error)
            } finally {
                translationStatus = null
                translating = false
            }
        }
    }

    fun captureSnapshot() = EditorSnapshot(
        Rect(cropLeft, cropTop, cropRight, cropBottom),
        brightness, contrast, saturation, warmth, vignette, sharpen, rotationAngle,
        highlights, shadows, tiltShift, denoise, gradientBg,
        selectedFilter,
        redactions.map { it.copy() },
        drawPaths.toList(),
        ocrBlocks.map(TextBlock::deepCopy),
        ocrScanCompleted,
        curveR, curveG, curveB,
        if (perspectiveMode) listOf(PointF(quadTL.x, quadTL.y), PointF(quadTR.x, quadTR.y), PointF(quadBR.x, quadBR.y), PointF(quadBL.x, quadBL.y)) else null,
        CutoutEditState(cutBands, cutSeparatorStyle),
    )

    fun restoreSnapshot(s: EditorSnapshot) {
        cropLeft = s.crop.left; cropTop = s.crop.top; cropRight = s.crop.right; cropBottom = s.crop.bottom
        brightness = s.bright; contrast = s.contr; saturation = s.sat; warmth = s.warm; vignette = s.vig; sharpen = s.sharp; rotationAngle = s.rotAngle
        highlights = s.hi; shadows = s.sh; tiltShift = s.tilt; denoise = s.dn; gradientBg = s.gradBg
        selectedFilter = s.filter
        redactions.clear(); redactions.addAll(s.redactions.map { it.copy() })
        selectedRedactionIndex = -1
        drawPaths.clear(); drawPaths.addAll(s.draws)
        selectedLayerIndex = -1
        selectedLayerIndices = emptySet()
        ocrBlocks = s.ocrBlocks.map(TextBlock::deepCopy)
        ocrScanCompleted = s.ocrReviewed
        onOcrChanged(ocrBlocks)
        onOcrReviewedChanged(ocrScanCompleted)
        curveR = s.cR; curveG = s.cG; curveB = s.cB
        if (s.perspectiveQuad != null && s.perspectiveQuad.size == 4) {
            perspectiveMode = true
            quadTL = s.perspectiveQuad[0]; quadTR = s.perspectiveQuad[1]
            quadBR = s.perspectiveQuad[2]; quadBL = s.perspectiveQuad[3]
        } else {
            perspectiveMode = false
        }
        cutBands = s.cutout.bands
        cutSeparatorStyle = s.cutout.separatorStyle
        selectedCutBand = -1
    }

    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    val hasUnsavedChanges by remember { derivedStateOf { undoStack.isNotEmpty() } }

    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

    fun pushUndo() {
        undoStack.add(captureSnapshot())
        redoStack.clear()
        if (undoStack.size > 30) undoStack.removeAt(0)
    }

    fun commitCutBands(updated: List<CutBand>) {
        val canonical = runCatching {
            val plan = CutoutSqueeze.create(bitmap.width, bitmap.height, updated, cutSeparatorStyle)
            CutoutSqueeze.createForCrop(
                bitmap.width,
                bitmap.height,
                cropLeft,
                cropTop,
                cropRight,
                cropBottom,
                plan.bands,
                cutSeparatorStyle,
            )
            plan.bands
        }.getOrNull() ?: return
        if (canonical == cutBands) return
        pushUndo()
        cutBands = canonical
        selectedCutBand = selectedCutBand.coerceIn(-1, canonical.lastIndex)
    }

    fun commitCutSeparatorStyle(style: CutSeparatorStyle) {
        if (style == cutSeparatorStyle) return
        pushUndo()
        cutSeparatorStyle = style
    }

    fun replaceOcrBlocks(updated: List<TextBlock>) {
        if (updated == ocrBlocks) return
        pushUndo()
        ocrBlocks = updated.map(TextBlock::deepCopy)
        selectedOcrBlocks.removeAll { it !in ocrBlocks.indices }
        onOcrChanged(ocrBlocks)
        ocrScanCompleted = true
        onOcrReviewedChanged(true)
    }

    fun commitOcrDrafts(): List<TextBlock> {
        val committed = ocrBlocks.mapIndexedNotNull { index, block ->
            val text = (ocrDraftTexts[index] ?: block.text).trim()
            text.takeIf(String::isNotEmpty)?.let { block.copy(text = it) }
        }
        replaceOcrBlocks(committed)
        ocrDraftTexts.clear()
        return committed
    }

    fun addDrawLayer(path: DrawPath) {
        drawPaths.add(path)
        drawRedoStack.clear()
    }

    fun moveDrawLayer(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in drawPaths.indices || toIndex !in drawPaths.indices || fromIndex == toIndex) return
        pushUndo()
        val layer = drawPaths.removeAt(fromIndex)
        drawPaths.add(toIndex, layer)
        drawRedoStack.clear()
        selectedLayerIndex = -1
        selectedLayerIndices = emptySet()
        haptic()
    }

    fun updateDrawLayer(index: Int, transform: (DrawPath) -> DrawPath) {
        if (index !in drawPaths.indices) return
        pushUndo()
        drawPaths[index] = transform(drawPaths[index])
        drawRedoStack.clear()
        haptic()
    }

    fun transformDrawLayer(index: Int, dx: Float, dy: Float, scaleMul: Float, dRotation: Float): Boolean {
        if (index !in drawPaths.indices) return false
        val step = maxOf(bitmap.width, bitmap.height) * 0.02f
        updateDrawLayer(index) {
            it.copy(
                transOffsetX = it.transOffsetX + dx * step,
                transOffsetY = it.transOffsetY + dy * step,
                transScale = (it.transScale * scaleMul).coerceIn(0.2f, 5f),
                transRotation = it.transRotation + dRotation
            )
        }
        return true
    }

    fun toggleDrawLayerSelection(index: Int) {
        if (index !in drawPaths.indices) return
        selectedLayerIndices = if (index in selectedLayerIndices) {
            selectedLayerIndices - index
        } else {
            selectedLayerIndices + index
        }
        selectedLayerIndex = if (index in selectedLayerIndices) index else selectedLayerIndices.maxOrNull() ?: -1
    }

    fun arrangeDrawLayers(updated: List<DrawPath>) {
        if (updated == drawPaths.toList()) return
        pushUndo()
        drawPaths.clear()
        drawPaths.addAll(updated)
        drawRedoStack.clear()
        haptic()
    }

    fun alignSelectedLayers(alignment: LayerAlignment) {
        arrangeDrawLayers(
            DrawLayerArrangement.align(
                drawPaths.toList(),
                selectedLayerIndices,
                Rect(cropLeft, cropTop, cropRight, cropBottom),
                alignment,
            )
        )
    }

    fun distributeSelectedLayers(distribution: LayerDistribution) {
        arrangeDrawLayers(
            DrawLayerArrangement.distribute(
                drawPaths.toList(),
                selectedLayerIndices,
                Rect(cropLeft, cropTop, cropRight, cropBottom),
                distribution,
            )
        )
    }

    fun duplicateSelectedLayers() {
        val duplicated = DrawLayerArrangement.duplicate(
            drawPaths.toList(),
            selectedLayerIndices,
            Rect(cropLeft, cropTop, cropRight, cropBottom),
        )
        if (duplicated.layers == drawPaths.toList()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.layer_duplicate_limit),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        pushUndo()
        drawPaths.clear()
        drawPaths.addAll(duplicated.layers)
        drawRedoStack.clear()
        selectedLayerIndices = duplicated.selectedIndices
        selectedLayerIndex = duplicated.selectedIndices.maxOrNull() ?: -1
        showLayerPanel = true
        haptic()
    }

    fun deleteDrawLayer(index: Int) {
        if (index !in drawPaths.indices) return
        pushUndo()
        drawPaths.removeAt(index)
        drawRedoStack.clear()
        selectedLayerIndex = -1
        selectedLayerIndices = emptySet()
        haptic()
    }

    fun replaceRedactions(updated: List<RedactionRegion>) {
        pushUndo()
        redactions.clear()
        redactions.addAll(updated.map { it.copy() })
        if (selectedRedactionIndex !in redactions.indices) selectedRedactionIndex = -1
        haptic()
    }

    fun updateRedaction(index: Int, transform: (RedactionRegion) -> RedactionRegion) {
        val region = redactions.getOrNull(index) ?: return
        pushUndo()
        redactions[index] = transform(region)
        haptic()
    }

    fun moveRedaction(index: Int, dx: Int, dy: Int) {
        val step = (maxOf(bitmap.width, bitmap.height) * 0.02f).roundToInt().coerceAtLeast(1)
        updateRedaction(index) {
            RedactionRegions.move(it, dx * step, dy * step, bitmap.width, bitmap.height)
        }
    }

    fun resizeRedaction(index: Int, dw: Int, dh: Int) {
        val region = redactions.getOrNull(index) ?: return
        val step = (maxOf(bitmap.width, bitmap.height) * 0.02f).roundToInt().coerceAtLeast(1)
        val bounds = region.bounds
        updateRedaction(index) {
            RedactionRegions.resize(
                it,
                Rect(bounds.left, bounds.top, bounds.right + dw * step, bounds.bottom + dh * step),
                bitmap.width,
                bitmap.height
            )
        }
    }

    fun defaultRedactionStyle(): RedactionStyle = RedactionStyle.fromPreference(
        drawPrefs.getString(ImageRedactor.PREF_REDACTION_STYLE, RedactionStyle.SOLID.preferenceValue)
    )

    fun addRedactionAtCropCenter(): Boolean {
        val width = (cropRight - cropLeft).coerceAtLeast(RedactionRegions.MIN_REGION_SIZE)
        val height = (cropBottom - cropTop).coerceAtLeast(RedactionRegions.MIN_REGION_SIZE)
        val regionWidth = (width / 3).coerceAtLeast(RedactionRegions.MIN_REGION_SIZE)
        val regionHeight = (height / 8).coerceAtLeast(RedactionRegions.MIN_REGION_SIZE)
        val centerX = (cropLeft + cropRight) / 2
        val centerY = (cropTop + cropBottom) / 2
        val bounds = Rect(
            (centerX - regionWidth / 2).coerceAtLeast(0),
            (centerY - regionHeight / 2).coerceAtLeast(0),
            (centerX + (regionWidth + 1) / 2).coerceAtMost(bitmap.width),
            (centerY + (regionHeight + 1) / 2).coerceAtMost(bitmap.height)
        )
        val incoming = RedactionRegions.manual(bounds, defaultRedactionStyle())
        val merged = RedactionRegions.merge(redactions, listOf(incoming))
        if (merged == redactions.toList()) return false
        replaceRedactions(merged)
        selectedRedactionIndex = redactions.lastIndex
        editMode = EditMode.PIXELATE
        return true
    }

    fun addAnnotationAtCropCenter(): Boolean {
        val cx = (cropLeft + cropRight) / 2f
        val cy = (cropTop + cropBottom) / 2f
        val extent = (minOf(cropRight - cropLeft, cropBottom - cropTop) * 0.12f).coerceAtLeast(12f)
        if (drawTool == DrawTool.TEXT) {
            textPlacePoint = PointF(cx, cy)
            textDialogValue = ""
            showTextDialog = true
            editMode = EditMode.DRAW
            return true
        }
        val points = when (drawTool) {
            DrawTool.CALLOUT, DrawTool.MAGNIFIER, DrawTool.EMOJI, DrawTool.FILL -> listOf(PointF(cx, cy))
            DrawTool.RECT, DrawTool.CIRCLE -> listOf(
                PointF(cx - extent, cy - extent * 0.65f),
                PointF(cx + extent, cy + extent * 0.65f)
            )
            else -> listOf(PointF(cx - extent, cy), PointF(cx + extent, cy))
        }
        val shape = when (drawTool) {
            DrawTool.LINE -> "line"; DrawTool.MEASURE -> "measure"; DrawTool.RECT -> "rect"
            DrawTool.CIRCLE -> "circle"; DrawTool.HIGHLIGHT -> "highlight"; DrawTool.CALLOUT -> "callout"
            DrawTool.SPOTLIGHT -> "spotlight"; DrawTool.MAGNIFIER -> "magnifier"; DrawTool.EMOJI -> "emoji"
            DrawTool.NEON -> "neon"; DrawTool.BLUR -> "blur"; DrawTool.ERASER -> "eraser"
            DrawTool.FILL -> "fill"; DrawTool.HEAL -> "smart_erase"; else -> null
        }
        val text = when (drawTool) {
            DrawTool.CALLOUT -> "${calloutCounter++}"
            DrawTool.EMOJI -> selectedEmoji
            else -> null
        }
        val width = when (drawTool) {
            DrawTool.HIGHLIGHT, DrawTool.ERASER -> drawStrokeWidth * 3f
            DrawTool.BLUR, DrawTool.HEAL -> drawStrokeWidth * 4f
            else -> drawStrokeWidth
        }
        pushUndo()
        addDrawLayer(
            DrawPath(
                points = points,
                color = drawColor,
                strokeWidth = width,
                isArrow = drawTool == DrawTool.ARROW || drawTool == DrawTool.CURVED_ARROW,
                shapeType = shape,
                text = text,
                filled = shapeFilled && (shape == "rect" || shape == "circle"),
                dashed = dashedStroke
            )
        )
        selectedLayerIndex = drawPaths.lastIndex
        selectedLayerIndices = setOf(selectedLayerIndex)
        editMode = EditMode.DRAW
        haptic()
        return true
    }

    fun addDetectedRedactions(incoming: List<RedactionRegion>): Int {
        if (incoming.isEmpty()) return 0
        val merged = RedactionRegions.merge(redactions.toList(), incoming)
        val added = merged.size - redactions.size
        if (merged != redactions.toList()) replaceRedactions(merged)
        return added.coerceAtLeast(0)
    }

    fun scanFacesForRedaction() {
        if (faceRedacting) return
        faceRedacting = true
        scope.launch {
            try {
                val faces = FaceDetector.detect(bitmap)
                val detected = RedactionRegions.fromFaces(faces, defaultRedactionStyle())
                addDetectedRedactions(detected)
                lastFaceCount = detected.size
                android.widget.Toast.makeText(
                    context,
                    if (detected.isEmpty()) context.getString(R.string.toast_no_faces)
                    else context.getString(R.string.toast_redacted_faces, detected.size),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                faceRedacting = false
            }
        }
    }

    fun scanTextForRedaction() {
        if (textRedacting) return
        textRedacting = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SensitiveTextDetector.detect(
                        bitmap,
                        OcrScript.fromContext(context),
                        failOnOcrError = true,
                        customPatterns = CustomRedactionPatternStore.load(
                            context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
                        ),
                    )
                }
                val detected = RedactionRegions.fromSensitiveDetections(
                    result.detections,
                    defaultRedactionStyle()
                )
                addDetectedRedactions(detected)
                lastTextRedactionCount = detected.size
                android.widget.Toast.makeText(
                    context,
                    if (detected.isEmpty()) context.getString(R.string.toast_no_text)
                    else context.getString(R.string.toast_redacted_text_blocks, detected.size),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.toast_redaction_scan_failed),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                textRedacting = false
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(captureSnapshot())
        restoreSnapshot(undoStack.removeAt(undoStack.lastIndex))
        haptic()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(captureSnapshot())
        restoreSnapshot(redoStack.removeAt(redoStack.lastIndex))
        haptic()
    }

    LaunchedEffect(initialCropRect) {
        cropLeft = initialCropRect.left
        cropTop = initialCropRect.top
        cropRight = initialCropRect.right
        cropBottom = initialCropRect.bottom
        aiLoading = false
        reframeLoading = false
    }

    LaunchedEffect(bitmap.width, bitmap.height) {
        if (cropRight > bitmap.width) cropRight = bitmap.width
        if (cropBottom > bitmap.height) cropBottom = bitmap.height
        cropLeft = cropLeft.coerceIn(0, (cropRight - 50).coerceAtLeast(0))
        cropTop = cropTop.coerceIn(0, (cropBottom - 50).coerceAtLeast(0))
    }

    val handleRadius = with(LocalDensity.current) { 14.dp.toPx() }
    val hitRadius = with(LocalDensity.current) { 28.dp.toPx() }

    fun bitmapToScreenX(bx: Int) = offsetX + bx * scaleX
    fun bitmapToScreenY(by: Int) = offsetY + by * scaleY

    fun bitmapToScreenXf(bx: Float) = offsetX + bx * scaleX
    fun bitmapToScreenYf(by: Float) = offsetY + by * scaleY

    fun findHandle(pos: Offset): DragHandle {
        if (perspectiveMode) {
            val corners = listOf(
                DragHandle.TOP_LEFT to Offset(bitmapToScreenXf(quadTL.x), bitmapToScreenYf(quadTL.y)),
                DragHandle.TOP_RIGHT to Offset(bitmapToScreenXf(quadTR.x), bitmapToScreenYf(quadTR.y)),
                DragHandle.BOTTOM_RIGHT to Offset(bitmapToScreenXf(quadBR.x), bitmapToScreenYf(quadBR.y)),
                DragHandle.BOTTOM_LEFT to Offset(bitmapToScreenXf(quadBL.x), bitmapToScreenYf(quadBL.y)),
            )
            for ((handle, corner) in corners) {
                if ((pos - corner).getDistance() < hitRadius) return handle
            }
            return DragHandle.NONE
        }
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

    fun dragPerspectiveCorner(handle: DragHandle, dx: Float, dy: Float) {
        val bitmapDx = dx / scaleX
        val bitmapDy = dy / scaleY
        when (handle) {
            DragHandle.TOP_LEFT -> quadTL = PointF(
                (quadTL.x + bitmapDx).coerceIn(0f, bitmap.width.toFloat()),
                (quadTL.y + bitmapDy).coerceIn(0f, bitmap.height.toFloat())
            )
            DragHandle.TOP_RIGHT -> quadTR = PointF(
                (quadTR.x + bitmapDx).coerceIn(0f, bitmap.width.toFloat()),
                (quadTR.y + bitmapDy).coerceIn(0f, bitmap.height.toFloat())
            )
            DragHandle.BOTTOM_RIGHT -> quadBR = PointF(
                (quadBR.x + bitmapDx).coerceIn(0f, bitmap.width.toFloat()),
                (quadBR.y + bitmapDy).coerceIn(0f, bitmap.height.toFloat())
            )
            DragHandle.BOTTOM_LEFT -> quadBL = PointF(
                (quadBL.x + bitmapDx).coerceIn(0f, bitmap.width.toFloat()),
                (quadBL.y + bitmapDy).coerceIn(0f, bitmap.height.toFloat())
            )
            else -> {}
        }
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
    val activeCutPlan = remember(
        bitmap.width,
        bitmap.height,
        cropLeft,
        cropTop,
        cropRight,
        cropBottom,
        cutBands,
        cutSeparatorStyle,
    ) {
        runCatching {
            CutoutSqueeze.createForCrop(
                bitmap.width,
                bitmap.height,
                cropLeft,
                cropTop,
                cropRight,
                cropBottom,
                cutBands,
                cutSeparatorStyle,
            )
        }.getOrNull()
    }
    val squeezedWidth = activeCutPlan?.outputWidth ?: cropW
    val squeezedHeight = activeCutPlan?.outputHeight ?: cropH
    val cutoutControlActions = CutoutControlActions(
        onSelectedIndexChange = { selectedCutBand = it },
        onAddBand = { band -> commitCutBands(cutBands + band) },
        onUpdateBand = { index, band ->
            commitCutBands(cutBands.toMutableList().apply { this[index] = band })
        },
        onRemoveBand = { index -> commitCutBands(cutBands.filterIndexed { i, _ -> i != index }) },
        onClearBands = { commitCutBands(emptyList()) },
        onSeparatorStyleChange = ::commitCutSeparatorStyle,
    )
    val modeBannerActions = EditorModeBannerActions(
        onReviewOcr = {
            ocrDraftTexts.clear()
            ocrBlocks.forEachIndexed { index, block -> ocrDraftTexts[index] = block.text }
            showOcrReview = true
        },
        onCopyOcr = {
            copyText(
                "SnapCrop OCR",
                ocrBlocks.joinToString("\n") { it.text },
                context.getString(R.string.toast_copied),
            )
        },
        onTranslateOcr = { openOcrText(ocrBlocks.joinToString("\n") { it.text }) },
    )
    val cropPct = if (bitmap.width > 0 && bitmap.height > 0) {
        val origArea = bitmap.width.toLong() * bitmap.height
        val cropArea = cropW.toLong() * cropH
        ((origArea - cropArea) * 100 / origArea).toInt()
    } else 0

    val methodLabel = when (cropMethod) {
        "border" -> stringResource(R.string.crop_method_border)
        "border+bars" -> stringResource(R.string.crop_method_bars_border)
        "statusbar" -> stringResource(R.string.crop_method_bars)
        "ai" -> stringResource(R.string.crop_method_ai)
        else -> if (cropMethod.startsWith("profile:")) cropMethod.substringAfter(":") else ""
    }

    val modeOptions = listOf(
        Triple(stringResource(R.string.mode_crop), EditMode.CROP, Primary),
        Triple(stringResource(R.string.mode_cutout), EditMode.CUTOUT, Primary),
        Triple(stringResource(R.string.mode_pixelate), EditMode.PIXELATE, Tertiary),
        Triple(stringResource(R.string.mode_draw), EditMode.DRAW, Secondary),
        Triple(stringResource(R.string.mode_ocr), EditMode.OCR, OcrAccent),
        Triple(stringResource(R.string.mode_adjust), EditMode.ADJUST, AdjustAccent)
    )

    fun exportAdjustments(): FloatArray {
        val shapeCrop = when (selectedRatio) {
            AspectRatio.CIRCLE -> 1f
            AspectRatio.ROUNDED -> 2f
            AspectRatio.STAR -> 3f
            AspectRatio.HEART -> 4f
            AspectRatio.TRIANGLE -> 5f
            AspectRatio.HEXAGON -> 6f
            AspectRatio.DIAMOND -> 7f
            else -> 0f
        }
        return floatArrayOf(
            brightness, contrast, saturation, shapeCrop, warmth, vignette,
            selectedFilter.ordinal.toFloat(), sharpen, rotationAngle,
            highlights, shadows, tiltShift, denoise, gradientBg.toFloat(),
            curveR, curveG, curveB,
            if (perspectiveMode) quadTL.x else 0f, if (perspectiveMode) quadTL.y else 0f,
            if (perspectiveMode) quadTR.x else 0f, if (perspectiveMode) quadTR.y else 0f,
            if (perspectiveMode) quadBR.x else 0f, if (perspectiveMode) quadBR.y else 0f,
            if (perspectiveMode) quadBL.x else 0f, if (perspectiveMode) quadBL.y else 0f
        )
    }

    fun currentCropRect() = Rect(cropLeft, cropTop, cropRight, cropBottom)

    // Let the host pull the live editor state to checkpoint a draft across process death.
    DisposableEffect(Unit) {
        registerStateProvider {
            EditorDraft(
                currentCropRect(),
                redactions.map { it.copy() },
                drawPaths.toList(),
                exportAdjustments(),
                ocrBlocks.map(TextBlock::deepCopy),
                ocrScanCompleted,
                CutoutEditState(cutBands, cutSeparatorStyle),
            )
        }
        onDispose { registerStateProvider(null) }
    }

    fun resetCrop() {
        pushUndo()
        cropLeft = 0
        cropTop = 0
        cropRight = bitmap.width
        cropBottom = bitmap.height
        selectedRatio = AspectRatio.FREE
    }

    fun runAutoCrop() {
        pushUndo()
        val r = onAutoCrop()
        cropLeft = r.left
        cropTop = r.top
        cropRight = r.right
        cropBottom = r.bottom
        selectedRatio = AspectRatio.FREE
    }

    fun startBackgroundRemoval() {
        if (bgRemoving) return
        bgRemoving = true
        bgRemovalStatus = context.getString(MlKitStatus.SUBJECT_SEGMENTATION_STARTING)
        onRemoveBg { message ->
            bgRemoving = false
            bgRemovalStatus = message
            if (!message.isNullOrBlank()) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun selectEditMode(mode: EditMode) {
        if (editMode == mode && mode != EditMode.CROP) {
            editMode = EditMode.CROP
            return
        }
        editMode = mode
        if (mode == EditMode.OCR && !ocrScanCompleted && !ocrLoading) {
            ocrLoading = true
            scope.launch {
                val ocrScript = OcrScript.fromContext(context)
                val textDeferred = async(Dispatchers.IO) { TextExtractor.extract(bitmap, ocrScript) }
                val codeDeferred = async(Dispatchers.IO) { BarcodeScanner.scan(bitmap) }
                pushUndo()
                ocrBlocks = textDeferred.await().map(TextBlock::deepCopy)
                scannedCodes = codeDeferred.await()
                ocrScanCompleted = true
                onOcrChanged(ocrBlocks)
                onOcrReviewedChanged(true)
                if (ocrBlocks.isNotEmpty() || scannedCodes.isNotEmpty()) {
                    onOcrIndexed(
                        ocrBlocks.joinToString("\n") { it.text },
                        scannedCodes.map { it.rawValue }
                    )
                }
                ocrLoading = false
            }
        }
    }

    fun nudgeCrop(dx: Int, dy: Int) {
        val width = cropRight - cropLeft
        val height = cropBottom - cropTop
        cropLeft = (cropLeft + dx).coerceIn(0, bitmap.width - width)
        cropTop = (cropTop + dy).coerceIn(0, bitmap.height - height)
        cropRight = cropLeft + width
        cropBottom = cropTop + height
    }

    fun zoomBy(factor: Float) {
        val next = (zoomLevel * factor).coerceIn(1f, 5f)
        if (next <= 1.01f) {
            zoomLevel = 1f
            panX = 0f
            panY = 0f
        } else {
            zoomLevel = next
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .safeDrawingPadding()
            .imePadding()
    ) {
        val isWideLayout = editorLayoutClass(maxWidth.value, maxHeight.value) == EditorLayoutClass.Wide
        val sidePanelWidth = editorSidePanelWidthDp(maxWidth.value).dp

        @Composable
        fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                content()
            }
        }

        @Composable
        fun PanelSlider(
            label: String,
            value: Float,
            range: ClosedFloatingPointRange<Float>,
            color: Color,
            valueLabel: String,
            onChange: (Float) -> Unit
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(76.dp))
                Slider(
                    value = value,
                    onValueChange = onChange,
                    valueRange = range,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "$label, $valueLabel" },
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = SurfaceVariant
                    )
                )
                Text(valueLabel, color = OnSurfaceVariant, fontSize = 10.sp, modifier = Modifier.width(34.dp))
            }
        }

        @Composable
        fun WideEditorSidePanel() {
            Column(
                modifier = Modifier
                    .width(sidePanelWidth)
                    .fillMaxHeight()
                    .background(SurfaceContainer.copy(alpha = 0.78f))
                    .border(1.dp, SurfaceVariant.copy(alpha = 0.65f))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(stringResource(R.string.editor_inspector), color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))

                PanelSection(stringResource(R.string.editor_section_mode)) {
                    modeOptions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, mode, color) ->
                                FilterChip(
                                    selected = editMode == mode,
                                    onClick = { selectEditMode(mode) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.25f),
                                        selectedLabelColor = color,
                                        containerColor = SurfaceVariant,
                                        labelColor = OnSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                PanelSection(stringResource(R.string.mode_crop)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { resetCrop() },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(stringResource(R.string.adjust_reset), fontSize = 11.sp) }
                        FilledTonalButton(
                            onClick = { runAutoCrop() },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(stringResource(R.string.crop_auto), fontSize = 11.sp) }
                        FilledTonalButton(
                            onClick = { if (!aiLoading) { pushUndo(); aiLoading = true; onSmartCrop() } },
                            enabled = !aiLoading,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(stringResource(R.string.crop_ai), fontSize = 11.sp, color = Tertiary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { startBackgroundRemoval() },
                            enabled = !bgRemoving,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Secondary.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(stringResource(R.string.adjust_remove_bg), fontSize = 11.sp, color = Secondary) }
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
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AdjustAccent.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(stringResource(R.string.adjust_palette), fontSize = 11.sp, color = AdjustAccent) }
                    }
                    bgRemovalStatus?.let { status ->
                        Text(status, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    if (showPalette && paletteColors.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val paletteCd = stringResource(R.string.palette_tap_copy)
                            paletteColors.take(5).forEach { pc ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .sizeIn(minWidth = 40.dp, minHeight = 44.dp)
                                        .semantics { contentDescription = paletteCd }
                                        .clickable {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Color", pc.hex))
                                        android.widget.Toast.makeText(context, context.getString(R.string.toast_copied), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Box(
                                        Modifier.size(24.dp)
                                            .background(Color(pc.color), RoundedCornerShape(4.dp))
                                            .border(1.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                    Text(pc.hex, fontSize = 7.sp, color = OnSurfaceVariant)
                                }
                            }
                        }
                    }
                    AspectRatio.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { ratio ->
                                FilterChip(
                                    selected = selectedRatio == ratio,
                                    onClick = {
                                        pushUndo()
                                        selectedRatio = ratio
                                        if (ratio.ratio != null) applyAspectRatio(ratio)
                                    },
                                    label = { Text(ratio.label, fontSize = 10.sp) },
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
                    }
                    PanelSlider(
                        label = stringResource(R.string.crop_straighten),
                        value = rotationAngle,
                        range = -45f..45f,
                        color = Primary,
                        valueLabel = stringResource(R.string.crop_straighten_angle, rotationAngle),
                        onChange = { if (cutBands.isEmpty()) rotationAngle = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = gridMode < 2,
                            onClick = { gridMode = (gridMode + 1) % 3 },
                            label = { Text(when (gridMode) { 0 -> stringResource(R.string.crop_grid_thirds); 1 -> stringResource(R.string.crop_grid_golden); else -> stringResource(R.string.crop_grid_off) }, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer,
                                selectedLabelColor = Primary,
                                containerColor = SurfaceVariant,
                                labelColor = OnSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        IconButton(onClick = onFlipV, enabled = cutBands.isEmpty(), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Flip, stringResource(R.string.editor_flip_v), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 90f))
                        }
                    }
                }

                if (editMode == EditMode.PIXELATE) {
                    PanelSection(stringResource(R.string.mode_pixelate)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { scanFacesForRedaction() },
                                enabled = !faceRedacting,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(stringResource(R.string.pixelate_blur_faces), color = Tertiary, fontSize = 11.sp) }
                            FilledTonalButton(
                                onClick = { scanTextForRedaction() },
                                enabled = !textRedacting,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(stringResource(R.string.pixelate_auto_text), color = Primary, fontSize = 11.sp) }
                        }
                        if (redactions.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { replaceRedactions(redactions.dropLast(1)) }) {
                                    Text(stringResource(R.string.editor_undo), color = Tertiary, fontSize = 11.sp)
                                }
                                TextButton(onClick = { replaceRedactions(emptyList()) }) {
                                    Text(stringResource(R.string.editor_clear), color = Tertiary, fontSize = 11.sp)
                                }
                            }
                            RedactionLayerPanel(
                                regions = redactions,
                                selectedIndex = selectedRedactionIndex,
                                onSelectRegion = { index -> selectedRedactionIndex = if (selectedRedactionIndex == index) -1 else index },
                                onToggleRegion = { index -> updateRedaction(index) { it.copy(enabled = !it.enabled) } },
                                onDeleteRegion = { index -> replaceRedactions(redactions.filterIndexed { i, _ -> i != index }) },
                                onStyleRegion = { index, style -> updateRedaction(index) { it.copy(style = style) } },
                                onMoveRegion = ::moveRedaction,
                                onResizeRegion = ::resizeRedaction,
                                onToggleCategory = { category, enabled ->
                                    replaceRedactions(RedactionRegions.setCategoryEnabled(redactions, category, enabled))
                                }
                            )
                        }
                    }
                }

                if (editMode == EditMode.DRAW || drawPaths.isNotEmpty()) {
                    if (drawPresets.isNotEmpty()) {
                        PanelSection(stringResource(R.string.editor_section_presets)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                drawPresets.forEach { preset ->
                                    FilterChip(selected = false,
                                        onClick = { drawColor = preset.color; drawStrokeWidth = preset.strokeWidth; dashedStroke = preset.dashed; drawTool = preset.tool; editMode = EditMode.DRAW },
                                        label = { Text(preset.name, fontSize = 9.sp) },
                                        leadingIcon = { Box(Modifier.size(8.dp).background(Color(preset.color), RoundedCornerShape(2.dp))) },
                                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp))
                                }
                                FilterChip(selected = false,
                                    onClick = { showSavePresetDialog = true },
                                    label = { Text("+", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceVariant, labelColor = Secondary),
                                    shape = RoundedCornerShape(8.dp))
                            }
                        }
                    }
                    PanelSection(stringResource(R.string.mode_draw)) {
                        DrawTool.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEach { tool ->
                                    FilterChip(
                                        selected = drawTool == tool,
                                        onClick = { drawTool = tool; editMode = EditMode.DRAW },
                                        label = { Text(tool.label, fontSize = 9.sp) },
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
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (drawTool == DrawTool.RECT || drawTool == DrawTool.CIRCLE) {
                                FilterChip(
                                    selected = shapeFilled,
                                    onClick = { shapeFilled = !shapeFilled },
                                    label = { Text(stringResource(R.string.draw_filled), fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer,
                                        selectedLabelColor = Primary,
                                        containerColor = SurfaceVariant,
                                        labelColor = OnSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            if (drawTool in listOf(DrawTool.PEN, DrawTool.ARROW, DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE)) {
                                FilterChip(
                                    selected = dashedStroke,
                                    onClick = { dashedStroke = !dashedStroke },
                                    label = { Text(stringResource(R.string.draw_dashed), fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer,
                                        selectedLabelColor = Primary,
                                        containerColor = SurfaceVariant,
                                        labelColor = OnSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            IconButton(onClick = { eyedropperActive = !eyedropperActive }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Colorize, stringResource(R.string.draw_eyedropper), tint = if (eyedropperActive) Primary else OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val selectedSuffix = stringResource(R.string.selected_suffix)
                            drawColors.take(7).forEach { (color, name) ->
                                val selected = drawColor == color
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .semantics { contentDescription = "$name color${if (selected) selectedSuffix else ""}" }
                                        .clickable { drawColor = color; eyedropperActive = false }
                                ) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(if (selected) 24.dp else 18.dp)
                                            .background(Color(color), RoundedCornerShape(3.dp))
                                            .border(0.5f.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                        PanelSlider(
                            label = stringResource(R.string.draw_width_label),
                            value = drawStrokeWidth,
                            range = 2f..20f,
                            color = Secondary,
                            valueLabel = "${drawStrokeWidth.toInt()}px",
                            onChange = { drawStrokeWidth = it }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (drawPaths.isNotEmpty()) {
                                TextButton(onClick = { pushUndo(); drawPaths.removeLastOrNull()?.let { drawRedoStack.add(it) } }) {
                                    Text(stringResource(R.string.editor_undo), color = Secondary, fontSize = 11.sp)
                                }
                            }
                            if (drawRedoStack.isNotEmpty()) {
                                TextButton(onClick = { pushUndo(); drawRedoStack.removeLastOrNull()?.let { drawPaths.add(it) } }) {
                                    Text(stringResource(R.string.editor_redo), color = Secondary, fontSize = 11.sp)
                                }
                            }
                            if (drawPaths.isNotEmpty()) {
                                TextButton(onClick = { pushUndo(); drawPaths.clear(); drawRedoStack.clear() }) {
                                    Text(stringResource(R.string.editor_clear), color = Secondary, fontSize = 11.sp)
                                }
                            }
                        }
                        DrawLayerPanel(
                            drawPaths = drawPaths,
                            onMoveLayer = { from, to -> moveDrawLayer(from, to); selectedLayerIndex = -1 },
                            onToggleVisible = { index -> updateDrawLayer(index) { it.copy(visible = !it.visible) } },
                            onDeleteLayer = { index -> deleteDrawLayer(index); selectedLayerIndex = -1 },
                            selectedIndex = selectedLayerIndex,
                            selectedIndices = selectedLayerIndices,
                            onSelectLayer = ::toggleDrawLayerSelection,
                            onTransformLayer = { index, dx, dy, sMul, dRot ->
                                transformDrawLayer(index, dx, dy, sMul, dRot)
                            },
                            onResetTransform = { index ->
                                updateDrawLayer(index) {
                                    it.copy(transOffsetX = 0f, transOffsetY = 0f, transScale = 1f, transRotation = 0f)
                                }
                            },
                            onAlign = ::alignSelectedLayers,
                            onDistribute = ::distributeSelectedLayers,
                            onDuplicate = ::duplicateSelectedLayers,
                        )
                    }
                }

                if (editMode == EditMode.ADJUST) {
                    val adjustColor = AdjustAccent
                    PanelSection(stringResource(R.string.mode_adjust)) {
                        ImageFilter.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter.label, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = adjustColor.copy(alpha = 0.3f),
                                            selectedLabelColor = adjustColor,
                                            containerColor = SurfaceVariant,
                                            labelColor = OnSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                        PanelSlider(stringResource(R.string.adjust_brightness), brightness, -100f..100f, adjustColor, "${brightness.toInt()}") { brightness = it }
                        PanelSlider(stringResource(R.string.adjust_contrast), contrast, 0.5f..2f, adjustColor, String.format("%.1f", contrast)) { contrast = it }
                        PanelSlider(stringResource(R.string.adjust_saturation), saturation, 0f..2f, adjustColor, String.format("%.1f", saturation)) { saturation = it }
                        PanelSlider(stringResource(R.string.adjust_warmth), warmth, -50f..50f, adjustColor, "${warmth.toInt()}") { warmth = it }
                        PanelSlider(stringResource(R.string.adjust_vignette), vignette, 0f..1f, adjustColor, "${(vignette * 100).toInt()}%") { vignette = it }
                        PanelSlider(stringResource(R.string.adjust_sharpen), sharpen, 0f..2f, adjustColor, String.format("%.1f", sharpen)) { sharpen = it }
                        PanelSlider(stringResource(R.string.adjust_highlights), highlights, -100f..100f, adjustColor, "${highlights.toInt()}") { highlights = it }
                        PanelSlider(stringResource(R.string.adjust_shadows), shadows, -100f..100f, adjustColor, "${shadows.toInt()}") { shadows = it }
                        PanelSlider(stringResource(R.string.adjust_tilt_shift), tiltShift, 0f..1f, adjustColor, "${(tiltShift * 100).toInt()}%") { tiltShift = it }
                        PanelSlider(stringResource(R.string.adjust_denoise), denoise, 0f..1f, adjustColor, "${(denoise * 100).toInt()}%") { denoise = it }
                        PanelSlider(stringResource(R.string.adjust_curve_r), curveR, -100f..100f, ChannelRed, "${curveR.toInt()}") { curveR = it }
                        PanelSlider(stringResource(R.string.adjust_curve_g), curveG, -100f..100f, ChannelGreen, "${curveG.toInt()}") { curveG = it }
                        PanelSlider(stringResource(R.string.adjust_curve_b), curveB, -100f..100f, ChannelBlue, "${curveB.toInt()}") { curveB = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
                                vignette = 0f; sharpen = 0f; highlights = 0f; shadows = 0f
                                tiltShift = 0f; denoise = 0f; curveR = 0f; curveG = 0f; curveB = 0f
                                selectedFilter = ImageFilter.NONE
                            }) { Text(stringResource(R.string.adjust_reset), color = adjustColor, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

    Column(
        modifier = Modifier.fillMaxSize()
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
                IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onDiscard() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.editor_close), tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Undo, stringResource(R.string.editor_undo),
                        tint = if (undoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Redo, stringResource(R.string.editor_redo),
                        tint = if (redoStack.isNotEmpty()) OnSurface else OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                if (undoStack.isNotEmpty()) {
                    IconButton(onClick = { showUndoHistory = !showUndoHistory }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.History, stringResource(R.string.editor_history),
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
                    Text("${squeezedWidth}x${squeezedHeight}", color = OnSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                    if (cropPct > 0) {
                        Text("-${cropPct}%", color = Secondary, fontSize = 11.sp)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.help_content_description), tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRotate, enabled = cutBands.isEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.RotateRight, stringResource(R.string.editor_rotate), tint = OnSurface, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onFlipH, enabled = cutBands.isEmpty(), modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Flip, stringResource(R.string.editor_flip_h), tint = OnSurface, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { previewMode = !previewMode }, modifier = Modifier.size(40.dp)) {
                    Icon(if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(R.string.crop_preview), tint = if (previewMode) Primary else OnSurface, modifier = Modifier.size(20.dp))
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
                Text(stringResource(R.string.editor_history) + ":", color = OnSurfaceVariant, fontSize = 10.sp)
                undoStack.forEachIndexed { idx, snap ->
                    val label = buildString {
                        if (snap.draws.isNotEmpty()) append("D${snap.draws.size} ")
                        if (snap.redactions.isNotEmpty()) append("R${snap.redactions.size} ")
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

        // Top bar — Row 2: mode tabs (scrollable on phones, side panel on wide layouts)
        if (!isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                modeOptions.forEach { (label, mode, color) ->
                    FilterChip(
                        selected = editMode == mode,
                        onClick = { selectEditMode(mode) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.25f), selectedLabelColor = color,
                            containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                // Transform tools
                IconButton(onClick = onFlipV, enabled = cutBands.isEmpty(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Flip, stringResource(R.string.editor_flip_v), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 90f)) }
            }
        }

        EditorModeBanner(
            editMode = editMode,
            drawTool = drawTool,
            ocrLoading = ocrLoading,
            ocrBlockCount = ocrBlocks.size,
            scannedCodeCount = scannedCodes.size,
            actions = modeBannerActions,
        )

        // Aspect ratio chips (only in crop mode)
        if (!isWideLayout && editMode == EditMode.CROP) Row(
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
            if (selectedRatio.ratio != null) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val target = selectedRatio.ratio
                        if (target != null && !reframeLoading) {
                            pushUndo()
                            reframeLoading = true
                            scope.launch {
                                val rect = withContext(Dispatchers.IO) {
                                    SmartReframeEngine.reframe(bitmap, target)
                                }
                                cropLeft = rect.left
                                cropTop = rect.top
                                cropRight = rect.right
                                cropBottom = rect.bottom
                                reframeLoading = false
                                android.widget.Toast.makeText(context, context.getString(R.string.crop_reframe), android.widget.Toast.LENGTH_SHORT).show()
                                haptic()
                            }
                        }
                    },
                    enabled = !reframeLoading,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (reframeLoading) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Secondary)
                            } else {
                                Icon(Icons.Default.Psychology, null, tint = Secondary, modifier = Modifier.size(13.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.crop_reframe), fontSize = 12.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Secondary.copy(alpha = 0.25f),
                        selectedLabelColor = Secondary,
                        containerColor = Secondary.copy(alpha = 0.12f),
                        labelColor = Secondary,
                        disabledContainerColor = SurfaceVariant,
                        disabledLabelColor = OnSurfaceVariant
                    ),
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
            // Perspective crop toggle
            FilterChip(
                selected = perspectiveMode,
                enabled = cutBands.isEmpty(),
                onClick = {
                    pushUndo()
                    perspectiveMode = !perspectiveMode
                    if (perspectiveMode) {
                        selectedRatio = AspectRatio.FREE
                        quadTL = PointF(cropLeft.toFloat(), cropTop.toFloat())
                        quadTR = PointF(cropRight.toFloat(), cropTop.toFloat())
                        quadBR = PointF(cropRight.toFloat(), cropBottom.toFloat())
                        quadBL = PointF(cropLeft.toFloat(), cropBottom.toFloat())
                    }
                },
                label = { Text(stringResource(R.string.crop_perspective), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Secondary.copy(alpha = 0.25f),
                    selectedLabelColor = Secondary,
                    containerColor = SurfaceVariant,
                    labelColor = OnSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        // Gradient background picker (shows when shape crop is selected)
        if (!isWideLayout && editMode == EditMode.CROP && selectedRatio in listOf(AspectRatio.CIRCLE, AspectRatio.ROUNDED, AspectRatio.STAR, AspectRatio.HEART, AspectRatio.TRIANGLE, AspectRatio.HEXAGON, AspectRatio.DIAMOND)) {
            val gradLabels = listOf(
                stringResource(R.string.gradient_none),
                stringResource(R.string.gradient_sunset),
                stringResource(R.string.gradient_ocean),
                stringResource(R.string.gradient_purple),
                stringResource(R.string.gradient_dark),
                stringResource(R.string.gradient_mint),
                stringResource(R.string.gradient_fire)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.draw_background_label), color = OnSurfaceVariant, fontSize = 11.sp)
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
        if (!isWideLayout && editMode == EditMode.CROP) {
            val straightenLabel = stringResource(R.string.crop_straighten)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(straightenLabel, color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                Slider(value = rotationAngle, onValueChange = { if (cutBands.isEmpty()) rotationAngle = it }, enabled = cutBands.isEmpty(),
                    valueRange = -45f..45f, modifier = Modifier.weight(1f).semantics { contentDescription = straightenLabel + ", ${String.format("%.1f", rotationAngle)} degrees" },
                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant))
                Text(stringResource(R.string.crop_straighten_angle, rotationAngle), color = OnSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.width(36.dp))
                if (rotationAngle != 0f) {
                    TextButton(onClick = { rotationAngle = 0f },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(stringResource(R.string.adjust_reset), color = Primary, fontSize = 10.sp)
                    }
                }
            }
        }

        // Tool options row (pixelate/draw mode)
        if (!isWideLayout && editMode == EditMode.PIXELATE) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { scanFacesForRedaction() },
                        enabled = !faceRedacting,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (faceRedacting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Tertiary)
                        else {
                            Text(stringResource(R.string.pixelate_blur_faces), fontSize = 11.sp, color = Tertiary)
                            if (lastFaceCount >= 0) {
                                Spacer(Modifier.width(4.dp))
                                Surface(color = Tertiary, shape = RoundedCornerShape(8.dp)) {
                                    Text("$lastFaceCount", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = OnPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { scanTextForRedaction() },
                        enabled = !textRedacting,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (textRedacting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Primary)
                        else {
                            Text(stringResource(R.string.pixelate_auto_text), fontSize = 11.sp, color = Primary)
                            if (lastTextRedactionCount >= 0) {
                                Spacer(Modifier.width(4.dp))
                                Surface(color = Primary, shape = RoundedCornerShape(8.dp)) {
                                    Text("$lastTextRedactionCount", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = OnPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Row {
                    if (redactions.isNotEmpty()) {
                        TextButton(onClick = { replaceRedactions(redactions.dropLast(1)) }) {
                            Text(stringResource(R.string.editor_undo), color = Tertiary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { replaceRedactions(emptyList()) }) {
                            Text(stringResource(R.string.editor_clear), color = Tertiary, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (redactions.isNotEmpty()) {
                RedactionLayerPanel(
                    regions = redactions,
                    selectedIndex = selectedRedactionIndex,
                    onSelectRegion = { index -> selectedRedactionIndex = if (selectedRedactionIndex == index) -1 else index },
                    onToggleRegion = { index -> updateRedaction(index) { it.copy(enabled = !it.enabled) } },
                    onDeleteRegion = { index -> replaceRedactions(redactions.filterIndexed { i, _ -> i != index }) },
                    onStyleRegion = { index, style -> updateRedaction(index) { it.copy(style = style) } },
                    onMoveRegion = ::moveRedaction,
                    onResizeRegion = ::resizeRedaction,
                    onToggleCategory = { category, enabled ->
                        replaceRedactions(RedactionRegions.setCategoryEnabled(redactions, category, enabled))
                    }
                )
            }
        }

        if (!isWideLayout && editMode == EditMode.DRAW && drawPresets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                drawPresets.forEach { preset ->
                    FilterChip(selected = false,
                        onClick = { drawColor = preset.color; drawStrokeWidth = preset.strokeWidth; dashedStroke = preset.dashed; drawTool = preset.tool },
                        label = { Text(preset.name, fontSize = 10.sp) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(preset.color), RoundedCornerShape(2.dp))) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                        shape = RoundedCornerShape(8.dp))
                }
                FilterChip(selected = false,
                    onClick = { showSavePresetDialog = true },
                    label = { Text("+", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceVariant, labelColor = Secondary),
                    shape = RoundedCornerShape(8.dp))
            }
        }

        if (!isWideLayout && editMode == EditMode.DRAW) {
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
                            label = { Text(stringResource(R.string.draw_filled), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                            shape = RoundedCornerShape(8.dp))
                    }
                    // Dashed toggle (for pen, arrow, rect, circle)
                    if (drawTool in listOf(DrawTool.PEN, DrawTool.ARROW, DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE)) {
                        FilterChip(selected = dashedStroke,
                            onClick = { dashedStroke = !dashedStroke },
                            label = { Text(stringResource(R.string.draw_dashed), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                                containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                            shape = RoundedCornerShape(8.dp))
                    }
                    // Eyedropper
                    IconButton(onClick = { eyedropperActive = !eyedropperActive },
                        modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Colorize, stringResource(R.string.draw_eyedropper),
                            tint = if (eyedropperActive) Primary else OnSurfaceVariant,
                            modifier = Modifier.size(16.dp))
                    }
                    drawColors.forEach { (color, name) ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .semantics { contentDescription = "$name color${if (drawColor == color) ", selected" else ""}" }
                                .pointerInput(color) { detectTapGestures { drawColor = color; eyedropperActive = false } }
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(if (drawColor == color) 24.dp else 18.dp)
                                    .background(Color(color), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    // Recent custom colors
                    recentColors.forEachIndexed { index, color ->
                        val hex = String.format("#%06X", color and 0xFFFFFF)
                        val selectedSuffix = stringResource(R.string.selected_suffix)
                        val recentColorCd = stringResource(
                            R.string.draw_recent_color_cd,
                            index + 1,
                            hex,
                            if (drawColor == color) selectedSuffix else ""
                        )
                        Box(
                            Modifier
                                .size(36.dp)
                                .semantics { contentDescription = recentColorCd }
                                .pointerInput(color) { detectTapGestures { drawColor = color; eyedropperActive = false } }
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(if (drawColor == color) 24.dp else 18.dp)
                                    .background(Color(color), RoundedCornerShape(3.dp))
                                    .border(0.5f.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    // Current color preview (tap to open color picker)
                    var showColorPicker by remember { mutableStateOf(false) }
                    val currentDrawColorCd = stringResource(R.string.draw_current_color_cd)
                    Box(
                        Modifier
                            .size(36.dp)
                            .semantics { contentDescription = currentDrawColorCd }
                            .clickable { showColorPicker = true }
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .background(Color(drawColor), RoundedCornerShape(3.dp))
                                .border(1.dp, OnSurfaceVariant, RoundedCornerShape(3.dp))
                        )
                    }
                    if (showColorPicker) {
                        val redLabel = stringResource(R.string.draw_red)
                        val greenLabel = stringResource(R.string.draw_green)
                        val blueLabel = stringResource(R.string.draw_blue)
                        var pickerR by remember { mutableFloatStateOf(((drawColor shr 16) and 0xFF) / 255f) }
                        var pickerG by remember { mutableFloatStateOf(((drawColor shr 8) and 0xFF) / 255f) }
                        var pickerB by remember { mutableFloatStateOf((drawColor and 0xFF) / 255f) }
                        AlertDialog(
                            onDismissRequest = { showColorPicker = false },
                            title = { Text(stringResource(R.string.draw_color_picker_title), color = OnSurface) },
                            text = {
                                Column {
                                    Box(Modifier.fillMaxWidth().height(40.dp)
                                        .background(Color(pickerR, pickerG, pickerB), RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.draw_red), color = ChannelRed, fontSize = 11.sp)
                                    Slider(value = pickerR, onValueChange = { pickerR = it },
                                        modifier = Modifier.semantics { contentDescription = "$redLabel, ${(pickerR * 255).roundToInt()}" },
                                        colors = SliderDefaults.colors(
                                        thumbColor = Color.Red, activeTrackColor = Color.Red, inactiveTrackColor = SurfaceVariant))
                                    Text(stringResource(R.string.draw_green), color = ChannelGreen, fontSize = 11.sp)
                                    Slider(value = pickerG, onValueChange = { pickerG = it },
                                        modifier = Modifier.semantics { contentDescription = "$greenLabel, ${(pickerG * 255).roundToInt()}" },
                                        colors = SliderDefaults.colors(
                                        thumbColor = Color.Green, activeTrackColor = Color.Green, inactiveTrackColor = SurfaceVariant))
                                    Text(stringResource(R.string.draw_blue), color = ChannelBlue, fontSize = 11.sp)
                                    Slider(value = pickerB, onValueChange = { pickerB = it },
                                        modifier = Modifier.semantics { contentDescription = "$blueLabel, ${(pickerB * 255).roundToInt()}" },
                                        colors = SliderDefaults.colors(
                                        thumbColor = Color.Blue, activeTrackColor = Color.Blue, inactiveTrackColor = SurfaceVariant))
                                    val hex = String.format("%02X%02X%02X", (pickerR * 255).roundToInt(), (pickerG * 255).roundToInt(), (pickerB * 255).roundToInt())
                                    Text(stringResource(R.string.draw_hex, hex), color = OnSurfaceVariant, fontSize = 12.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val newColor = (0xFF000000.toInt() or ((pickerR * 255).roundToInt() shl 16) or ((pickerG * 255).roundToInt() shl 8) or (pickerB * 255).roundToInt())
                                    drawColor = newColor
                                    if (!recentColors.contains(newColor)) {
                                        recentColors.add(0, newColor)
                                        if (recentColors.size > 4) recentColors.removeAt(recentColors.lastIndex)
                                    }
                                    eyedropperActive = false
                                    showColorPicker = false
                                }) { Text(stringResource(R.string.save), color = Primary) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showColorPicker = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
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
                        }) { Text(stringResource(R.string.editor_undo), color = Secondary, fontSize = 11.sp) }
                    }
                    if (drawRedoStack.isNotEmpty()) {
                        TextButton(onClick = {
                            pushUndo()
                            drawRedoStack.removeLastOrNull()?.let { drawPaths.add(it) }
                        }) { Text(stringResource(R.string.editor_redo), color = Secondary, fontSize = 11.sp) }
                    }
                    if (drawPaths.isNotEmpty()) {
                        TextButton(onClick = { pushUndo(); drawPaths.clear(); drawRedoStack.clear() }) {
                            Text(stringResource(R.string.editor_clear), color = Secondary, fontSize = 11.sp)
                        }
                    }
                    TextButton(
                        onClick = { showLayerPanel = !showLayerPanel },
                        enabled = drawPaths.isNotEmpty() || showLayerPanel
                    ) {
                        Text(
                            if (showLayerPanel) "Hide ${stringResource(R.string.draw_layers)}" else "${stringResource(R.string.draw_layers)} ${drawPaths.size}",
                            color = if (drawPaths.isNotEmpty() || showLayerPanel) Secondary else OnSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            // Stroke width slider
            val widthLabel = stringResource(R.string.draw_width_label)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${drawStrokeWidth.toInt()}px", color = OnSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.width(32.dp))
                Slider(value = drawStrokeWidth, onValueChange = { drawStrokeWidth = it },
                    valueRange = 2f..20f, modifier = Modifier.weight(1f).semantics { contentDescription = "$widthLabel, ${drawStrokeWidth.toInt()} pixels" },
                    colors = SliderDefaults.colors(thumbColor = Secondary, activeTrackColor = Secondary,
                        inactiveTrackColor = SurfaceVariant))
            }
            if (showLayerPanel) {
                DrawLayerPanel(
                    drawPaths = drawPaths,
                    onMoveLayer = { from, to -> moveDrawLayer(from, to); selectedLayerIndex = -1 },
                    onToggleVisible = { index -> updateDrawLayer(index) { it.copy(visible = !it.visible) } },
                    onDeleteLayer = { index -> deleteDrawLayer(index); selectedLayerIndex = -1 },
                    selectedIndex = selectedLayerIndex,
                    selectedIndices = selectedLayerIndices,
                    onSelectLayer = ::toggleDrawLayerSelection,
                    onTransformLayer = { index, dx, dy, sMul, dRot ->
                        transformDrawLayer(index, dx, dy, sMul, dRot)
                    },
                    onResetTransform = { index ->
                        updateDrawLayer(index) {
                            it.copy(transOffsetX = 0f, transOffsetY = 0f, transScale = 1f, transRotation = 0f)
                        }
                    },
                    onAlign = ::alignSelectedLayers,
                    onDistribute = ::distributeSelectedLayers,
                    onDuplicate = ::duplicateSelectedLayers,
                )
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
                            modifier = Modifier.size(36.dp)
                                .semantics { contentDescription = "Emoji $emoji${if (selectedEmoji == emoji) ", selected" else ""}" }
                                .clickable { selectedEmoji = emoji },
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
        if (!isWideLayout && editMode == EditMode.ADJUST) {
            val adjustColor = AdjustAccent
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
            val brightnessLabel = stringResource(R.string.adjust_brightness)
            val contrastLabel = stringResource(R.string.adjust_contrast)
            val saturationLabel = stringResource(R.string.adjust_saturation)
            val warmthLabel = stringResource(R.string.adjust_warmth)
            val vignetteLabel = stringResource(R.string.adjust_vignette)
            val sharpenLabel = stringResource(R.string.adjust_sharpen)
            val highlightsLabel = stringResource(R.string.adjust_highlights)
            val shadowsLabel = stringResource(R.string.adjust_shadows)
            val tiltShiftLabel = stringResource(R.string.adjust_tilt_shift)
            val denoiseLabel = stringResource(R.string.adjust_denoise)
            val curveRLabel = stringResource(R.string.adjust_curve_r)
            val curveGLabel = stringResource(R.string.adjust_curve_g)
            val curveBLabel = stringResource(R.string.adjust_curve_b)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(brightnessLabel, color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = brightness, onValueChange = { brightness = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$brightnessLabel, ${brightness.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${brightness.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_contrast), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = contrast, onValueChange = { contrast = it },
                        valueRange = 0.5f..2f, modifier = Modifier.weight(1f).semantics { contentDescription = "$contrastLabel, ${String.format("%.1f", contrast)}x" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", contrast)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_saturation), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = saturation, onValueChange = { saturation = it },
                        valueRange = 0f..2f, modifier = Modifier.weight(1f).semantics { contentDescription = "$saturationLabel, ${String.format("%.1f", saturation)}x" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", saturation)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_warmth), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = warmth, onValueChange = { warmth = it },
                        valueRange = -50f..50f, modifier = Modifier.weight(1f).semantics { contentDescription = "$warmthLabel, ${warmth.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${warmth.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_vignette), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = vignette, onValueChange = { vignette = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "$vignetteLabel, ${(vignette * 100).toInt()} percent" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(vignette * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_sharpen), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = sharpen, onValueChange = { sharpen = it },
                        valueRange = 0f..2f, modifier = Modifier.weight(1f).semantics { contentDescription = "$sharpenLabel, ${String.format("%.1f", sharpen)}x" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${String.format("%.1f", sharpen)}x", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_highlights), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = highlights, onValueChange = { highlights = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$highlightsLabel, ${highlights.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${highlights.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_shadows), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = shadows, onValueChange = { shadows = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$shadowsLabel, ${shadows.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${shadows.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_tilt_shift), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = tiltShift, onValueChange = { tiltShift = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "$tiltShiftLabel, ${(tiltShift * 100).toInt()} percent" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(tiltShift * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_denoise), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = denoise, onValueChange = { denoise = it },
                        valueRange = 0f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "$denoiseLabel, ${(denoise * 100).toInt()} percent" },
                        colors = SliderDefaults.colors(thumbColor = adjustColor, activeTrackColor = adjustColor, inactiveTrackColor = SurfaceVariant))
                    Text("${(denoise * 100).toInt()}%", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                // Curves (per-channel RGB)
                val curvesColor = OcrAccent // Lavender
                Text(stringResource(R.string.adjust_curves), color = curvesColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_curve_r), color = ChannelRed, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveR, onValueChange = { curveR = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$curveRLabel, ${curveR.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = ChannelRed, activeTrackColor = ChannelRed, inactiveTrackColor = SurfaceVariant))
                    Text("${curveR.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_curve_g), color = ChannelGreen, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveG, onValueChange = { curveG = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$curveGLabel, ${curveG.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = ChannelGreen, activeTrackColor = ChannelGreen, inactiveTrackColor = SurfaceVariant))
                    Text("${curveG.toInt()}", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.adjust_curve_b), color = ChannelBlue, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    Slider(value = curveB, onValueChange = { curveB = it },
                        valueRange = -100f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = "$curveBLabel, ${curveB.toInt()}" },
                        colors = SliderDefaults.colors(thumbColor = ChannelBlue, activeTrackColor = ChannelBlue, inactiveTrackColor = SurfaceVariant))
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
                        Text(stringResource(R.string.adjust_auto_enhance), color = adjustColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; vignette = 0f; sharpen = 0f; highlights = 0f; shadows = 0f; tiltShift = 0f; denoise = 0f; curveR = 0f; curveG = 0f; curveB = 0f; selectedFilter = ImageFilter.NONE }) {
                        Text(stringResource(R.string.adjust_reset), color = adjustColor, fontSize = 11.sp)
                    }
                }
            }
        }

        // Canvas area
        val selectedLayer = drawPaths.getOrNull(selectedLayerIndex)
        val selectedLayerTitle = selectedLayer?.let { layerTitle(it) }
        val selectedLayerState = selectedLayer?.let { layer ->
            stringResource(
                R.string.editor_canvas_selected_layer,
                selectedLayerTitle ?: stringResource(R.string.draw_layers),
                if (layer.visible) stringResource(R.string.layer_state_visible) else stringResource(R.string.layer_state_hidden)
            )
        } ?: stringResource(R.string.editor_canvas_no_selected_layer)
        val selectedRedaction = redactions.getOrNull(selectedRedactionIndex)
        val selectedRedactionState = selectedRedaction?.let { region ->
            stringResource(
                R.string.editor_canvas_selected_redaction,
                selectedRedactionIndex + 1,
                region.style.preferenceValue,
                if (region.enabled) stringResource(R.string.redactions_enabled) else stringResource(R.string.redactions_disabled)
            )
        } ?: stringResource(R.string.editor_canvas_no_selected_redaction)
        val editorCanvasState = stringResource(
            R.string.editor_canvas_state,
            modeOptions.firstOrNull { it.second == editMode }?.first ?: stringResource(R.string.mode_crop),
            cropW,
            cropH,
            cropLeft,
            cropTop,
            bitmap.width,
            bitmap.height,
            redactions.size,
            drawPaths.size,
            selectedLayerState
        ) + ", " + selectedRedactionState
        val nudgeCropLeftLabel = stringResource(R.string.editor_a11y_nudge_crop_left)
        val nudgeCropRightLabel = stringResource(R.string.editor_a11y_nudge_crop_right)
        val nudgeCropUpLabel = stringResource(R.string.editor_a11y_nudge_crop_up)
        val nudgeCropDownLabel = stringResource(R.string.editor_a11y_nudge_crop_down)
        val zoomInLabel = stringResource(R.string.editor_a11y_zoom_in)
        val zoomOutLabel = stringResource(R.string.editor_a11y_zoom_out)
        val previewLabel = stringResource(R.string.editor_a11y_toggle_preview)
        val removeRedactionLabel = stringResource(R.string.editor_a11y_remove_last_redaction)
        val addRedactionLabel = stringResource(R.string.editor_a11y_add_redaction_center)
        val addAnnotationLabel = stringResource(R.string.editor_a11y_add_annotation_center)
        val redactionMoveLeftLabel = stringResource(R.string.editor_a11y_redaction_move_left)
        val redactionMoveRightLabel = stringResource(R.string.editor_a11y_redaction_move_right)
        val redactionMoveUpLabel = stringResource(R.string.editor_a11y_redaction_move_up)
        val redactionMoveDownLabel = stringResource(R.string.editor_a11y_redaction_move_down)
        val redactionGrowLabel = stringResource(R.string.editor_a11y_redaction_grow)
        val redactionShrinkLabel = stringResource(R.string.editor_a11y_redaction_shrink)
        val redactionToggleLabel = stringResource(R.string.editor_a11y_redaction_toggle)
        val redactionDeleteLabel = stringResource(R.string.editor_a11y_redaction_delete)
        val redactionBarLabel = stringResource(R.string.editor_a11y_redaction_bar)
        val redactionPixelateLabel = stringResource(R.string.editor_a11y_redaction_pixelate)
        val redactionBlurLabel = stringResource(R.string.editor_a11y_redaction_blur)
        val layerMoveLeftLabel = stringResource(R.string.layer_move_left)
        val layerMoveRightLabel = stringResource(R.string.layer_move_right)
        val layerMoveUpLabel = stringResource(R.string.layer_move_up_dir)
        val layerMoveDownLabel = stringResource(R.string.layer_move_down_dir)
        val layerGrowLabel = stringResource(R.string.layer_grow)
        val layerShrinkLabel = stringResource(R.string.layer_shrink)
        val layerRotateLeftLabel = stringResource(R.string.layer_rotate_left)
        val layerRotateRightLabel = stringResource(R.string.layer_rotate_right)
        val layerDeleteLabel = selectedLayerTitle?.let { stringResource(R.string.layer_delete_cd, it) }
            ?: stringResource(R.string.editor_delete)
        val layerToggleLabel = selectedLayer?.let { layer ->
            val title = selectedLayerTitle ?: stringResource(R.string.draw_layers)
            if (layer.visible) stringResource(R.string.layer_hide_cd, title)
            else stringResource(R.string.layer_show_cd, title)
        } ?: stringResource(R.string.draw_layers)
        val canvasActions = buildList {
            add(CustomAccessibilityAction(nudgeCropLeftLabel) { pushUndo(); nudgeCrop(-1, 0); true })
            add(CustomAccessibilityAction(nudgeCropRightLabel) { pushUndo(); nudgeCrop(1, 0); true })
            add(CustomAccessibilityAction(nudgeCropUpLabel) { pushUndo(); nudgeCrop(0, -1); true })
            add(CustomAccessibilityAction(nudgeCropDownLabel) { pushUndo(); nudgeCrop(0, 1); true })
            add(CustomAccessibilityAction(zoomInLabel) { zoomBy(1.1f); true })
            add(CustomAccessibilityAction(zoomOutLabel) { zoomBy(0.9f); true })
            add(CustomAccessibilityAction(previewLabel) { previewMode = !previewMode; true })
            add(CustomAccessibilityAction(addRedactionLabel) { addRedactionAtCropCenter() })
            add(CustomAccessibilityAction(addAnnotationLabel) { addAnnotationAtCropCenter() })
            if (redactions.isNotEmpty()) {
                add(CustomAccessibilityAction(removeRedactionLabel) {
                    pushUndo()
                    redactions.removeLastOrNull()
                    true
                })
            }
            if (selectedLayer != null) {
                add(CustomAccessibilityAction(layerMoveLeftLabel) { transformDrawLayer(selectedLayerIndex, -1f, 0f, 1f, 0f) })
                add(CustomAccessibilityAction(layerMoveRightLabel) { transformDrawLayer(selectedLayerIndex, 1f, 0f, 1f, 0f) })
                add(CustomAccessibilityAction(layerMoveUpLabel) { transformDrawLayer(selectedLayerIndex, 0f, -1f, 1f, 0f) })
                add(CustomAccessibilityAction(layerMoveDownLabel) { transformDrawLayer(selectedLayerIndex, 0f, 1f, 1f, 0f) })
                add(CustomAccessibilityAction(layerGrowLabel) { transformDrawLayer(selectedLayerIndex, 0f, 0f, 1.18f, 0f) })
                add(CustomAccessibilityAction(layerShrinkLabel) { transformDrawLayer(selectedLayerIndex, 0f, 0f, 0.85f, 0f) })
                add(CustomAccessibilityAction(layerRotateLeftLabel) { transformDrawLayer(selectedLayerIndex, 0f, 0f, 1f, -15f) })
                add(CustomAccessibilityAction(layerRotateRightLabel) { transformDrawLayer(selectedLayerIndex, 0f, 0f, 1f, 15f) })
                add(CustomAccessibilityAction(layerToggleLabel) {
                    updateDrawLayer(selectedLayerIndex) { it.copy(visible = !it.visible) }
                    true
                })
                add(CustomAccessibilityAction(layerDeleteLabel) {
                    deleteDrawLayer(selectedLayerIndex)
                    selectedLayerIndex = -1
                    true
                })
            }
            if (selectedRedaction != null) {
                add(CustomAccessibilityAction(redactionMoveLeftLabel) { moveRedaction(selectedRedactionIndex, -1, 0); true })
                add(CustomAccessibilityAction(redactionMoveRightLabel) { moveRedaction(selectedRedactionIndex, 1, 0); true })
                add(CustomAccessibilityAction(redactionMoveUpLabel) { moveRedaction(selectedRedactionIndex, 0, -1); true })
                add(CustomAccessibilityAction(redactionMoveDownLabel) { moveRedaction(selectedRedactionIndex, 0, 1); true })
                add(CustomAccessibilityAction(redactionGrowLabel) { resizeRedaction(selectedRedactionIndex, 1, 1); true })
                add(CustomAccessibilityAction(redactionShrinkLabel) { resizeRedaction(selectedRedactionIndex, -1, -1); true })
                add(CustomAccessibilityAction(redactionToggleLabel) {
                    updateRedaction(selectedRedactionIndex) { it.copy(enabled = !it.enabled) }
                    true
                })
                add(CustomAccessibilityAction(redactionBarLabel) {
                    updateRedaction(selectedRedactionIndex) { it.copy(style = RedactionStyle.SOLID) }
                    true
                })
                add(CustomAccessibilityAction(redactionPixelateLabel) {
                    updateRedaction(selectedRedactionIndex) { it.copy(style = RedactionStyle.PIXELATE) }
                    true
                })
                add(CustomAccessibilityAction(redactionBlurLabel) {
                    updateRedaction(selectedRedactionIndex) { it.copy(style = RedactionStyle.BLUR) }
                    true
                })
                add(CustomAccessibilityAction(redactionDeleteLabel) {
                    replaceRedactions(redactions.filterIndexed { index, _ -> index != selectedRedactionIndex })
                    selectedRedactionIndex = -1
                    true
                })
            }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scrollY != 0f) {
                                    zoomBy(if (scrollY > 0f) 0.9f else 1.1f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
        ) {
            // Zoom indicator
            if (zoomLevel > 1.05f) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("${String.format("%.1f", zoomLevel)}x",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = OnMediaSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (previewMode) {
                BeforeAfterPreview(
                    bitmap = bitmap,
                    imageBitmap = imageBitmap,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    cutBands = cutBands,
                    cutSeparatorStyle = cutSeparatorStyle,
                    onDismiss = { previewMode = false }
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            role = Role.Image
                            contentDescription = context.getString(R.string.editor_canvas_cd)
                            stateDescription = editorCanvasState
                            customActions = canvasActions
                        }
                        .pointerInput(bitmap, editMode, ocrBlocks, scannedCodes) {
                            var lastTapTime = 0L
                            var lastTapPos = Offset.Zero

                            awaitEachGesture {
                                val firstDown = awaitFirstDown()
                                firstDown.consume()
                                val downPos = firstDown.position
                                val stylusActive = firstDown.type == PointerType.Stylus || firstDown.type == PointerType.Eraser

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
                                    val pressed = if (stylusActive) {
                                        event.changes.filter { it.type == PointerType.Touch }.forEach { it.consume() }
                                        event.changes.filter { it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }
                                    } else {
                                        event.changes.filter { it.pressed }
                                    }

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
                                                        android.widget.Toast.makeText(context, context.getString(R.string.toast_cropped_to_text_block), android.widget.Toast.LENGTH_SHORT).show()
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
                                                    val pixel = bitmap.getPixel(bx, by)
                                                    drawColor = pixel
                                                    if (samplerColor1 == null) { samplerColor1 = pixel } else { samplerColor2 = pixel }
                                                    showSamplerDialog = true
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
                                                            addDrawLayer(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "callout", text = "${calloutCounter++}"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.MAGNIFIER -> {
                                                            pushUndo()
                                                            addDrawLayer(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "magnifier"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.EMOJI -> {
                                                            pushUndo()
                                                            addDrawLayer(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = drawStrokeWidth,
                                                                shapeType = "emoji", text = selectedEmoji
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.FILL -> {
                                                            pushUndo()
                                                            addDrawLayer(DrawPath(
                                                                points = listOf(PointF(bx, by)),
                                                                color = drawColor, strokeWidth = 0f,
                                                                shapeType = "fill"
                                                            ))
                                                            haptic()
                                                        }
                                                        DrawTool.HEAL -> {
                                                            // Smart erase is stroke-based; drag gestures commit the removal mask.
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
                                                        android.widget.Toast.makeText(context, context.getString(R.string.toast_copied), android.widget.Toast.LENGTH_SHORT).show()
                                                        haptic()
                                                    } else {
                                                        val tappedText = ocrBlocks.find { it.bounds.contains(bx, by) }
                                                        if (tappedText != null) {
                                                            openOcrText(tappedText.text)
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
                                                            val manual = RedactionRegions.manual(
                                                                Rect(bx1, by1, bx2, by2),
                                                                defaultRedactionStyle()
                                                            )
                                                            val merged = RedactionRegions.merge(redactions, listOf(manual))
                                                            if (merged != redactions.toList()) replaceRedactions(merged)
                                                        }
                                                    }
                                                    pixDragStart = null; pixDragCurrent = null
                                                }
                                                EditMode.DRAW -> {
                                                    if (currentDrawPoints.size >= 2 && drawTool != DrawTool.TEXT && drawTool != DrawTool.CALLOUT && drawTool != DrawTool.FILL) {
                                                        val shape = when (drawTool) {
                                                            DrawTool.RECT -> "rect"; DrawTool.CIRCLE -> "circle"
                                                            DrawTool.HIGHLIGHT -> "highlight"; DrawTool.SPOTLIGHT -> "spotlight"
                                                            DrawTool.NEON -> "neon"; DrawTool.BLUR -> "blur"; DrawTool.LINE -> "line"; DrawTool.MEASURE -> "measure"; DrawTool.ERASER -> "eraser"; DrawTool.HEAL -> "smart_erase"; else -> null
                                                        }
                                                        // Velocity-based stroke modulation for freehand tools
                                                        val velFactor = if (drawTool == DrawTool.PEN || drawTool == DrawTool.NEON) {
                                                            val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1)
                                                            val velocity = drawPathLength / elapsed // px/ms
                                                            (1.5f - velocity * 0.8f).coerceIn(0.6f, 1.5f) // slow=thick, fast=thin
                                                        } else 1f
                                                        val baseWidth = when (drawTool) { DrawTool.HIGHLIGHT -> drawStrokeWidth * 3; DrawTool.BLUR -> drawStrokeWidth * 4; DrawTool.ERASER -> drawStrokeWidth * 3; DrawTool.HEAL -> drawStrokeWidth * 4; else -> drawStrokeWidth }
                                                        val curvedCtrl = if (drawTool == DrawTool.CURVED_ARROW && currentDrawPoints.size >= 3) {
                                                            val mid = currentDrawPoints[currentDrawPoints.size / 2]
                                                            PointF(mid.x, mid.y)
                                                        } else null
                                                        // Capture state so committed strokes integrate with the global undo
                                                        // stack, consistent with pixelate and the tap-placed draw tools.
                                                        pushUndo()
                                                        addDrawLayer(DrawPath(
                                                            points = when {
                                                                shape == "rect" || shape == "circle" || shape == "spotlight" || shape == "line" || shape == "measure" -> listOf(currentDrawPoints.first(), currentDrawPoints.last())
                                                                drawTool == DrawTool.CURVED_ARROW -> listOf(currentDrawPoints.first(), currentDrawPoints.last())
                                                                drawTool == DrawTool.PEN || drawTool == DrawTool.HIGHLIGHT || drawTool == DrawTool.NEON || drawTool == DrawTool.BLUR || drawTool == DrawTool.ERASER || drawTool == DrawTool.HEAL -> smoothPath(currentDrawPoints)
                                                                else -> currentDrawPoints.toList()
                                                            },
                                                            color = drawColor,
                                                            strokeWidth = baseWidth * velFactor,
                                                            isArrow = drawTool == DrawTool.ARROW || drawTool == DrawTool.CURVED_ARROW,
                                                            shapeType = shape,
                                                            filled = shapeFilled && (shape == "rect" || shape == "circle"),
                                                            dashed = dashedStroke,
                                                            controlPoint = curvedCtrl
                                                        ))
                                                        haptic()
                                                    }
                                                    currentDrawPoints.clear()
                                                }
                                                EditMode.CROP -> {}
                                                EditMode.CUTOUT, EditMode.OCR, EditMode.ADJUST -> {}
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
                                            cropHandle != DragHandle.NONE && perspectiveMode -> {
                                                val precisionScale = if (System.currentTimeMillis() - dragStartTime > 800) 0.25f else 1f
                                                dragPerspectiveCorner(cropHandle, dragDelta.x * precisionScale, dragDelta.y * precisionScale)
                                            }
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

                    if (editMode == EditMode.CUTOUT) {
                        drawCutBandOverlays(
                            cutBands,
                            selectedCutBand,
                            ox,
                            oy,
                            drawW,
                            drawH,
                            scale,
                            Primary,
                            Tertiary,
                        )
                    }

                    val sl = ox + cropLeft * scale; val st = oy + cropTop * scale
                    val sr = ox + cropRight * scale; val sb = oy + cropBottom * scale

                    if (perspectiveMode) {
                        // Perspective quad: dim outside, draw quad outline + corner handles
                        val pTL = Offset(ox + quadTL.x * scale, oy + quadTL.y * scale)
                        val pTR = Offset(ox + quadTR.x * scale, oy + quadTR.y * scale)
                        val pBR = Offset(ox + quadBR.x * scale, oy + quadBR.y * scale)
                        val pBL = Offset(ox + quadBL.x * scale, oy + quadBL.y * scale)

                        // Semi-transparent overlay over entire image
                        drawRect(DimOverlay, Offset(ox, oy), Size(drawW, drawH))

                        // Cut out the quad region by drawing it brighter
                        val quadPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(pTL.x, pTL.y)
                            lineTo(pTR.x, pTR.y)
                            lineTo(pBR.x, pBR.y)
                            lineTo(pBL.x, pBL.y)
                            close()
                        }
                        drawPath(quadPath, Color.Black.copy(alpha = 0.5f), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)

                        // Quad outline
                        drawLine(CropBorder, pTL, pTR, 2.dp.toPx())
                        drawLine(CropBorder, pTR, pBR, 2.dp.toPx())
                        drawLine(CropBorder, pBR, pBL, 2.dp.toPx())
                        drawLine(CropBorder, pBL, pTL, 2.dp.toPx())

                        // Grid lines inside quad (interpolated thirds)
                        if (gridMode < 2) {
                            val gridColor = CropBorder.copy(alpha = 0.3f)
                            for (i in 1..2) {
                                val t = i / 3f
                                val lineStart = Offset(pTL.x + (pBL.x - pTL.x) * t, pTL.y + (pBL.y - pTL.y) * t)
                                val lineEnd = Offset(pTR.x + (pBR.x - pTR.x) * t, pTR.y + (pBR.y - pTR.y) * t)
                                drawLine(gridColor, lineStart, lineEnd, 1f)
                                val colStart = Offset(pTL.x + (pTR.x - pTL.x) * t, pTL.y + (pTR.y - pTL.y) * t)
                                val colEnd = Offset(pBL.x + (pBR.x - pBL.x) * t, pBL.y + (pBR.y - pBL.y) * t)
                                drawLine(gridColor, colStart, colEnd, 1f)
                            }
                        }

                        // Corner handles
                        drawCircle(CropHandle, handleRadius, pTL)
                        drawCircle(CropHandle, handleRadius, pTR)
                        drawCircle(CropHandle, handleRadius, pBR)
                        drawCircle(CropHandle, handleRadius, pBL)
                    } else {
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
                    } // end else (non-perspective)

                    // Editable redaction objects remain visible until export, including disabled
                    // regions so an exclusion cannot become invisible during review.
                    for ((index, region) in redactions.withIndex()) {
                        val pr = region.bounds
                        val px1 = ox + pr.left * scale; val py1 = oy + pr.top * scale
                        val px2 = ox + pr.right * scale; val py2 = oy + pr.bottom * scale
                        val fill = when (region.style) {
                            RedactionStyle.SOLID -> Color.Black.copy(alpha = if (region.enabled) 1f else 0.12f)
                            RedactionStyle.PIXELATE -> Tertiary.copy(alpha = if (region.enabled) 0.42f else 0.10f)
                            RedactionStyle.BLUR -> Color.White.copy(alpha = if (region.enabled) 0.28f else 0.08f)
                        }
                        val border = if (index == selectedRedactionIndex) Secondary else Tertiary
                        drawRect(fill, Offset(px1, py1), Size(px2 - px1, py2 - py1))
                        drawRect(
                            border.copy(alpha = if (region.enabled) 0.9f else 0.45f),
                            Offset(px1, py1),
                            Size(px2 - px1, py2 - py1),
                            style = Stroke(if (index == selectedRedactionIndex) 2.5f.dp.toPx() else 1.5f.dp.toPx())
                        )
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
                            // Compact label backdrop for readable text annotations.
                            if (dp.filled) {
                                val bounds = android.graphics.Rect()
                                textPaint.getTextBounds(dp.text, 0, dp.text.length, bounds)
                                val pad = textPaint.textSize * 0.3f
                                val radius = min(pad, 8.dp.toPx())
                                val bgColor = 0xCC000000.toInt()
                                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                                bgPaint.color = bgColor; bgPaint.style = android.graphics.Paint.Style.FILL
                                drawContext.canvas.nativeCanvas.drawRoundRect(
                                    tx - pad, ty + bounds.top - pad, tx + bounds.width() + pad, ty + bounds.bottom + pad,
                                    radius, radius, bgPaint)
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

                        // Measurement/ruler — line with end ticks and a pixel-distance label
                        if (shape == "measure" && pts.size >= 2) {
                            val p1 = pts.first(); val p2 = pts.last()
                            val sx1 = ox + p1.x * scale; val sy1 = oy + p1.y * scale
                            val sx2 = ox + p2.x * scale; val sy2 = oy + p2.y * scale
                            // Distance is reported in source-image pixels (pts are image coords).
                            val distPx = kotlin.math.hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble())
                            val angle = kotlin.math.atan2((sy2 - sy1).toDouble(), (sx2 - sx1).toDouble())
                            val tick = sw * 2.5f
                            val nx = (-kotlin.math.sin(angle)).toFloat() * tick
                            val ny = (kotlin.math.cos(angle)).toFloat() * tick
                            drawLine(color, Offset(sx1, sy1), Offset(sx2, sy2), strokeWidth = sw)
                            drawLine(color, Offset(sx1 - nx, sy1 - ny), Offset(sx1 + nx, sy1 + ny), strokeWidth = sw)
                            drawLine(color, Offset(sx2 - nx, sy2 - ny), Offset(sx2 + nx, sy2 + ny), strokeWidth = sw)
                            val label = "${distPx.roundToInt()} px"
                            val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color
                                textSize = (sw * 5f).coerceAtLeast(11.sp.toPx())
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            val mx = (sx1 + sx2) / 2f; val my = (sy1 + sy2) / 2f - tick - sw
                            val bounds = android.graphics.Rect()
                            labelPaint.getTextBounds(label, 0, label.length, bounds)
                            val pad = labelPaint.textSize * 0.3f
                            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0xCC000000.toInt(); style = android.graphics.Paint.Style.FILL
                            }
                            drawContext.canvas.nativeCanvas.drawRoundRect(
                                mx - bounds.width() / 2f - pad, my + bounds.top - pad,
                                mx + bounds.width() / 2f + pad, my + bounds.bottom + pad,
                                min(pad, 8.dp.toPx()), min(pad, 8.dp.toPx()), bgPaint)
                            drawContext.canvas.nativeCanvas.drawText(label, mx, my, labelPaint)
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

                        // Smart erase — translucent removal mask preview
                        if ((shape == "smart_erase" || shape == "heal") && pts.size >= 2) {
                            val erasePath = android.graphics.Path()
                            erasePath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            for (i in 1 until pts.size) erasePath.lineTo(ox + pts[i].x * scale, oy + pts[i].y * scale)
                            val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0x5589B4FA
                                strokeWidth = sw
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                            }
                            val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = 0xAA89B4FA.toInt()
                                strokeWidth = sw * 0.38f
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                            }
                            drawContext.canvas.nativeCanvas.drawPath(erasePath, haloPaint)
                            drawContext.canvas.nativeCanvas.drawPath(erasePath, corePaint)
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
                        if (dp.controlPoint != null && pts.size >= 2) {
                            val cp = dp.controlPoint
                            val bezierPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = dp.color; strokeWidth = sw; style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                if (dp.dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(sw * 3, sw * 2), 0f)
                            }
                            val bezierPath = android.graphics.Path()
                            bezierPath.moveTo(ox + pts[0].x * scale, oy + pts[0].y * scale)
                            bezierPath.quadTo(ox + cp.x * scale, oy + cp.y * scale, ox + pts.last().x * scale, oy + pts.last().y * scale)
                            drawContext.canvas.nativeCanvas.drawPath(bezierPath, bezierPaint)
                        } else if (dp.dashed) {
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
                            val arrowEnd = pts.last()
                            val arrowPrev = if (dp.controlPoint != null) {
                                val cp = dp.controlPoint; val t = 0.95f
                                PointF((1-t)*(1-t)*pts[0].x + 2*(1-t)*t*cp.x + t*t*arrowEnd.x,
                                       (1-t)*(1-t)*pts[0].y + 2*(1-t)*t*cp.y + t*t*arrowEnd.y)
                            } else pts[pts.size - 2]
                            val last = arrowEnd; val prev = arrowPrev
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
                        if (!dp.visible) continue
                        // Apply the layer's image-space transform, conjugated into screen space so the
                        // preview matches the export. Identity transforms skip the matrix entirely.
                        val layerM = dp.transformMatrix()
                        if (layerM != null) {
                            val imgToScreen = android.graphics.Matrix().apply { setScale(scale, scale); postTranslate(ox, oy) }
                            val inv = android.graphics.Matrix()
                            if (imgToScreen.invert(inv)) {
                                val screenM = android.graphics.Matrix(imgToScreen).apply { preConcat(layerM); preConcat(inv) }
                                drawContext.canvas.nativeCanvas.save()
                                drawContext.canvas.nativeCanvas.concat(screenM)
                                drawShapeOnCanvas(dp, dp.points, Color(dp.color), dp.strokeWidth * scale)
                                drawContext.canvas.nativeCanvas.restore()
                            } else {
                                drawShapeOnCanvas(dp, dp.points, Color(dp.color), dp.strokeWidth * scale)
                            }
                        } else {
                            drawShapeOnCanvas(dp, dp.points, Color(dp.color), dp.strokeWidth * scale)
                        }
                    }

                    // Current draw stroke
                    if (editMode == EditMode.DRAW && currentDrawPoints.size > 1) {
                        val curShape = when (drawTool) {
                            DrawTool.RECT -> "rect"; DrawTool.CIRCLE -> "circle"
                            DrawTool.HIGHLIGHT -> "highlight"; DrawTool.SPOTLIGHT -> "spotlight"
                            DrawTool.NEON -> "neon"; DrawTool.BLUR -> "blur"; DrawTool.LINE -> "line"; DrawTool.MEASURE -> "measure"; DrawTool.ERASER -> "eraser"; DrawTool.HEAL -> "smart_erase"; else -> null
                        }
                        val curSw = when (drawTool) { DrawTool.HIGHLIGHT -> drawStrokeWidth * 3; DrawTool.BLUR -> drawStrokeWidth * 4; DrawTool.ERASER -> drawStrokeWidth * 3; DrawTool.HEAL -> drawStrokeWidth * 4; else -> drawStrokeWidth }
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
                        val ocrColor = OcrAccent
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
            if (isWideLayout) {
                WideEditorSidePanel()
            }
        }

        // Bottom toolbar
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (!isWideLayout) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalButton(
                        onClick = { resetCrop() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) { Icon(Icons.Default.CropFree, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.adjust_reset), fontSize = 13.sp) }

                    FilledTonalButton(
                        onClick = { runAutoCrop() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.crop_auto), fontSize = 13.sp) }

                    FilledTonalButton(
                        onClick = { if (!aiLoading) { pushUndo(); aiLoading = true; onSmartCrop() } },
                        enabled = !aiLoading,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tertiary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (aiLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Tertiary)
                        else Icon(Icons.Default.Psychology, null, Modifier.size(16.dp), tint = Tertiary)
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.crop_ai), fontSize = 13.sp, color = Tertiary)
                    }

                    FilledTonalButton(
                        onClick = { startBackgroundRemoval() },
                        enabled = !bgRemoving,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Secondary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (bgRemoving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Secondary)
                        else Text(stringResource(R.string.adjust_remove_bg), fontSize = 13.sp, color = Secondary)
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
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AdjustAccent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) { Text(stringResource(R.string.adjust_palette), fontSize = 13.sp, color = AdjustAccent) }
                }
                bgRemovalStatus?.let { status ->
                    Text(
                        status,
                        color = OnSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }

            // Color palette display
            if (!isWideLayout && showPalette && paletteColors.isNotEmpty()) {
                val paletteCd = stringResource(R.string.palette_tap_copy)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paletteColors.forEach { pc ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .sizeIn(minWidth = 40.dp, minHeight = 48.dp)
                                .semantics { contentDescription = paletteCd }
                                .clickable {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Color", pc.hex))
                                    android.widget.Toast.makeText(context, context.getString(R.string.toast_copied), android.widget.Toast.LENGTH_SHORT).show()
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

            if (exportPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedExportPresetId == null,
                        onClick = {
                            selectedExportPresetId = null
                            onExportPresetChanged(null)
                        },
                        label = { Text(stringResource(R.string.export_preset_current), fontSize = 10.sp) }
                    )
                    exportPresets.forEach { preset ->
                        FilterChip(
                            selected = selectedExportPresetId == preset.id,
                            onClick = {
                                selectedExportPresetId = preset.id
                                onExportPresetChanged(preset.id)
                            },
                            label = { Text(preset.name, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }

            // Action icons row
            val adj = exportAdjustments()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.editor_delete), tint = Tertiary) }
                IconButton(onClick = { showResizeDialog = true }) {
                    Icon(Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.adjust_resize_button), tint = OnSurface) }
                IconButton(onClick = { onShare(currentCropRect(), redactions.map { it.copy() }, drawPaths.toList(), adj, CutoutEditState(cutBands, cutSeparatorStyle)) }) {
                    Icon(Icons.Default.Share, stringResource(R.string.editor_share), tint = OnSurface) }
                IconButton(onClick = { onCopyClipboard(currentCropRect(), redactions.map { it.copy() }, drawPaths.toList(), adj, CutoutEditState(cutBands, cutSeparatorStyle)) }) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.editor_copy), tint = OnSurface) }
                IconButton(onClick = onEditSourceContext) {
                    Icon(
                        Icons.Default.Link,
                        stringResource(if (hasSourceContext) R.string.source_context_action_edit else R.string.source_context_action_add),
                        tint = if (hasSourceContext) Primary else OnSurface
                    )
                }
                IconButton(onClick = { onSaveCopy(currentCropRect(), redactions.map { it.copy() }, drawPaths.toList(), adj, CutoutEditState(cutBands, cutSeparatorStyle)) }) {
                    Icon(Icons.Default.Save, stringResource(R.string.crop_save_copy), tint = OnSurface) }
            }

            // Main save button — full width
            Button(onClick = { onSave(currentCropRect(), redactions.map { it.copy() }, drawPaths.toList(), adj, CutoutEditState(cutBands, cutSeparatorStyle)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Crop, null, Modifier.size(18.dp), tint = OnPrimary)
                Spacer(Modifier.width(8.dp))
                // Estimated file size (read prefs once, not every recomposition)
                val pixels = squeezedWidth.toLong() * squeezedHeight
                val estKb = when (selectedExportSettings.format) {
                    ExportImageFormat.WEBP -> pixels * 0.5f / 1024
                    ExportImageFormat.JPEG -> pixels * 0.8f / 1024
                    ExportImageFormat.PNG -> pixels * 3f / 1024
                }
                val estLabel = if (estKb > 1024) String.format("~%.1f MB", estKb / 1024) else String.format("~%.0f KB", estKb)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (replaceOriginalOnSave) stringResource(R.string.crop_save_replace) else stringResource(R.string.crop_save_copy),
                    color = OnPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Text(estLabel, color = OnPrimary.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
    }

    if (editMode == EditMode.CUTOUT) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 104.dp, start = 8.dp, end = 8.dp),
            color = SurfaceContainer,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                CutoutControls(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    bands = cutBands,
                    separatorStyle = cutSeparatorStyle,
                    enabled = rotationAngle == 0f && !perspectiveMode,
                    selectedIndex = selectedCutBand,
                    actions = cutoutControlActions,
                )
                Text(
                    stringResource(R.string.cutout_preview_dimensions, squeezedWidth, squeezedHeight),
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (showOcrReview) {
        AlertDialog(
            onDismissRequest = {
                commitOcrDrafts()
                showOcrReview = false
                selectedOcrBlocks.clear()
            },
            title = { Text(stringResource(R.string.ocr_review_title), color = OnSurface) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.ocr_review_help),
                        color = OnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    ocrBlocks.forEachIndexed { index, block ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainer, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = index in selectedOcrBlocks,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (index !in selectedOcrBlocks) selectedOcrBlocks.add(index)
                                    } else {
                                        selectedOcrBlocks.remove(index)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = OcrAccent)
                            )
                            OutlinedTextField(
                                value = ocrDraftTexts[index] ?: block.text,
                                onValueChange = { value ->
                                    ocrDraftTexts[index] = value
                                },
                                modifier = Modifier.weight(1f),
                                minLines = 1,
                                maxLines = 4,
                                label = { Text(stringResource(R.string.ocr_block_number, index + 1)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OcrAccent,
                                    unfocusedBorderColor = Outline,
                                    focusedTextColor = OnSurface,
                                    unfocusedTextColor = OnSurface,
                                    cursorColor = OcrAccent
                                )
                            )
                            IconButton(
                                onClick = {
                                    val drafted = ocrBlocks.mapIndexed { draftIndex, current ->
                                        current.copy(text = ocrDraftTexts[draftIndex] ?: current.text)
                                    }
                                    replaceOcrBlocks(
                                        drafted.filterIndexed { draftIndex, current ->
                                            draftIndex != index && current.text.isNotBlank()
                                        }
                                    )
                                    ocrDraftTexts.clear()
                                    ocrBlocks.forEachIndexed { draftIndex, current ->
                                        ocrDraftTexts[draftIndex] = current.text
                                    }
                                    selectedOcrBlocks.clear()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.ocr_delete_block, index + 1),
                                    tint = Tertiary
                                )
                            }
                        }
                    }
                    if (ocrBlocks.isEmpty()) {
                        Text(stringResource(R.string.ocr_no_text), color = OnSurfaceVariant, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val drafted = ocrBlocks.mapIndexed { index, block ->
                                block.copy(text = ocrDraftTexts[index] ?: block.text)
                            }
                            replaceOcrBlocks(
                                OcrBlockEdits.merge(drafted, selectedOcrBlocks.toSet())
                                    .filterNot { it.text.isBlank() }
                            )
                            ocrDraftTexts.clear()
                            ocrBlocks.forEachIndexed { index, block -> ocrDraftTexts[index] = block.text }
                            selectedOcrBlocks.clear()
                        },
                        enabled = selectedOcrBlocks.size >= 2
                    ) { Text(stringResource(R.string.ocr_merge_selected), color = if (selectedOcrBlocks.size >= 2) OcrAccent else Outline) }
                    Button(
                        onClick = {
                            commitOcrDrafts()
                            showOcrReview = false
                            selectedOcrBlocks.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OcrAccent)
                    ) { Text(stringResource(R.string.done), color = OnPrimary) }
                }
            },
            containerColor = SurfaceVariant
        )
    }

    if (selectedOcrText != null) {
        val ocrText = selectedOcrText.orEmpty()
        AlertDialog(
            onDismissRequest = { selectedOcrText = null },
            title = { Text(stringResource(R.string.mode_ocr), color = OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        ocrText,
                        color = OnSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .background(SurfaceContainer, RoundedCornerShape(8.dp))
                            .border(1.dp, Outline, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    val entities = remember(ocrText) { extractEntities(ocrText) }
                    if (entities.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.ocr_entity_actions), color = OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                entities.forEach { entity ->
                                    val chipLabel = when (entity.type) {
                                        OcrEntityType.PHONE -> stringResource(R.string.ocr_entity_call, entity.display)
                                        OcrEntityType.EMAIL -> stringResource(R.string.ocr_entity_email, entity.display)
                                        OcrEntityType.URL -> stringResource(R.string.ocr_entity_open_url)
                                    }
                                    val chipIcon = when (entity.type) {
                                        OcrEntityType.PHONE -> Icons.Default.Call
                                        OcrEntityType.EMAIL -> Icons.Default.Email
                                        OcrEntityType.URL -> Icons.AutoMirrored.Filled.OpenInNew
                                    }
                                    AssistChip(
                                        onClick = {
                                            val intent = when (entity.type) {
                                                OcrEntityType.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.value}"))
                                                OcrEntityType.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entity.value}"))
                                                OcrEntityType.URL -> Intent(Intent.ACTION_VIEW, Uri.parse(entity.value))
                                            }
                                            context.startActivity(intent)
                                        },
                                        label = { Text(chipLabel, fontSize = 11.sp, maxLines = 1) },
                                        leadingIcon = { Icon(chipIcon, null, modifier = Modifier.size(16.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = SurfaceElevated,
                                            labelColor = Primary,
                                            leadingIconContentColor = Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.ocr_target_language), color = OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextTranslator.targetLanguages.forEach { target ->
                                FilterChip(
                                    selected = translateTarget == target,
                                    onClick = {
                                        translateTarget = target
                                        translation = null
                                        translationError = null
                                    },
                                    label = { Text(target.label, fontSize = 11.sp) },
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
                    }

                    when {
                        translating -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                translationStatus ?: "Preparing on-device translation...",
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        translationError != null -> Text(
                            translationError.orEmpty(),
                            color = Tertiary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        translation != null -> {
                            val result = translation!!
                            val summary = if (result.alreadyTargetLanguage) {
                                "Already ${result.targetLabel}"
                            } else {
                                "${result.sourceLabel} -> ${result.targetLabel}"
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(summary, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    result.translatedText,
                                    color = OnSurface,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                        .border(1.dp, Outline, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                        else -> Text(
                            "Language models download over Wi-Fi once per language pair. If Play Services reports storage or model errors, free space and retry.",
                            color = OnSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val copy = translation?.translatedText ?: ocrText
                        val label = if (translation != null) "SnapCrop Translation" else "SnapCrop OCR"
                        val message = if (translation != null) "Copied translation" else "Copied text"
                        copyText(label, copy, message)
                    }) { Text(stringResource(R.string.ocr_copy), color = OnSurfaceVariant) }
                    Button(
                        onClick = { translateText(ocrText) },
                        enabled = !translating,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (translating) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = OnPrimary)
                        } else {
                            Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp), tint = OnPrimary)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ocr_translate), color = OnPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedOcrText = null }) { Text(stringResource(R.string.close), color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    // Resize dialog
    if (showResizeDialog) {
        val sizes = listOf(480, 720, 1080, 1440, 2160)
        var selectedSize by remember { mutableIntStateOf(1080) }
        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            title = { Text(stringResource(R.string.resize_dialog_editor_title), color = OnSurface) },
            text = {
                Column {
                    Text(stringResource(R.string.resize_current_dimensions, bitmap.width, bitmap.height), color = OnSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.resize_max_dimension_px), color = OnSurfaceVariant, fontSize = 12.sp)
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
                TextButton(onClick = { showResizeDialog = false; onResize(selectedSize) }, enabled = cutBands.isEmpty()) {
                    Text(stringResource(R.string.resize), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResizeDialog = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    // Text input dialog
    if (showTextDialog) {
        var textBg by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(stringResource(R.string.draw_text_dialog_title), color = OnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = textDialogValue,
                        onValueChange = { textDialogValue = it },
                        placeholder = { Text(stringResource(R.string.draw_text_hint)) },
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
                            label = { Text(stringResource(R.string.draw_backdrop), fontSize = 11.sp) },
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
                        addDrawLayer(DrawPath(
                            points = listOf(textPlacePoint!!),
                            color = drawColor,
                            strokeWidth = drawStrokeWidth,
                            shapeType = "text",
                            text = textDialogValue,
                            filled = textBg
                        ))
                    }
                    showTextDialog = false
                }) { Text(stringResource(R.string.draw_add_text), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.draw_save_preset_title), color = OnSurface) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(20.dp).background(Color(drawColor), RoundedCornerShape(4.dp)))
                        Text("${drawTool.label}, ${drawStrokeWidth.toInt()}px${if (dashedStroke) ", dashed" else ""}", color = OnSurfaceVariant, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(20) },
                        placeholder = { Text(stringResource(R.string.draw_preset_name_label)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                            focusedTextColor = OnSurface, unfocusedTextColor = OnSurface)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        val preset = DrawStylePreset(presetName.trim(), drawColor, drawStrokeWidth, dashedStroke, drawTool)
                        drawPresets.removeAll { it.name == preset.name }
                        drawPresets.add(preset)
                        DrawStylePresetStore.save(drawPrefs, drawPresets.toList())
                        showSavePresetDialog = false
                    }
                }) { Text(stringResource(R.string.save), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title), color = OnSurface) },
            text = { Text(stringResource(R.string.editor_discard_body), color = OnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDiscard() }) {
                    Text(stringResource(R.string.editor_discard), color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.editor_keep_editing), color = Primary) }
            },
            containerColor = SurfaceVariant
        )
    }

    if (showSamplerDialog) {
        ColorSamplerDialog(
            color1 = samplerColor1,
            color2 = samplerColor2,
            onDismiss = { showSamplerDialog = false },
            onClear = { samplerColor1 = null; samplerColor2 = null; showSamplerDialog = false },
            context = context
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
            title = { Text(stringResource(R.string.crop_precise_input_title), color = OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.crop_image_dimensions, bitmap.width, bitmap.height), color = OnSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputX, onValueChange = { inputX = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crop_x_label), fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                        OutlinedTextField(
                            value = inputY, onValueChange = { inputY = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crop_y_label), fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputW, onValueChange = { inputW = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crop_w_label), fontSize = 11.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, focusedLabelColor = Primary)
                        )
                        OutlinedTextField(
                            value = inputH, onValueChange = { inputH = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crop_h_label), fontSize = 11.sp) }, singleLine = true,
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
                    val minCropW = minOf(50, bitmap.width).coerceAtLeast(1)
                    val minCropH = minOf(50, bitmap.height).coerceAtLeast(1)
                    val maxLeft = (bitmap.width - minCropW).coerceAtLeast(0)
                    val maxTop = (bitmap.height - minCropH).coerceAtLeast(0)
                    val newLeft = x.coerceIn(0, maxLeft)
                    val newTop = y.coerceIn(0, maxTop)
                    val newRight = (newLeft + w.coerceAtLeast(minCropW)).coerceIn(newLeft + minCropW, bitmap.width)
                    val newBottom = (newTop + h.coerceAtLeast(minCropH)).coerceIn(newTop + minCropH, bitmap.height)
                    pushUndo()
                    cropLeft = newLeft; cropTop = newTop; cropRight = newRight; cropBottom = newBottom
                    selectedRatio = AspectRatio.FREE
                    showCropInputDialog = false
                }) { Text(stringResource(R.string.apply), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCropInputDialog = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }
}

@Composable
private fun ColorSamplerDialog(
    color1: Int?,
    color2: Int?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    context: android.content.Context
) {
    fun copyText(text: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Color", text))
        android.widget.Toast.makeText(context, context.getString(R.string.toast_copied), android.widget.Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sampler_title), color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                color1?.let { c ->
                    ColorSample(c, stringResource(R.string.sampler_color_1)) { copyText(it) }
                }
                color2?.let { c ->
                    ColorSample(c, stringResource(R.string.sampler_color_2)) { copyText(it) }
                }
                if (color1 != null && color2 != null) {
                    val ratio = wcagContrastRatio(color1, color2)
                    val apca = apcaContrast(color1, color2)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.sampler_contrast), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("WCAG 2.x: %.2f:1".format(ratio), color = OnSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.clickable { copyText("%.2f:1".format(ratio)) })
                    val wcagAA = if (ratio >= 4.5) "Pass" else "Fail"
                    val wcagAAA = if (ratio >= 7.0) "Pass" else "Fail"
                    Text("AA (4.5:1): $wcagAA  |  AAA (7:1): $wcagAAA", color = OnSurfaceVariant, fontSize = 11.sp)
                    Text("APCA Lc: %.1f".format(apca), color = OnSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.clickable { copyText("Lc %.1f".format(apca)) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Primary) }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text(stringResource(R.string.sampler_clear), color = OnSurfaceVariant) }
        },
        containerColor = SurfaceVariant
    )
}

@Composable
private fun ColorSample(color: Int, label: String, onCopy: (String) -> Unit) {
    val r = (color ushr 16) and 0xFF
    val g = (color ushr 8) and 0xFF
    val b = color and 0xFF
    val hex = "#%02X%02X%02X".format(r, g, b)
    val rgb = "rgb($r, $g, $b)"
    val hsl = toHsl(r, g, b)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(32.dp)
                .background(Color(color), RoundedCornerShape(6.dp))
                .border(1.dp, OnSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
        )
        Column {
            Text(label, color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            ColorCopyButton(hex, onCopy)
            ColorCopyButton(rgb, onCopy)
            ColorCopyButton(hsl, onCopy)
        }
    }
}

@Composable
private fun ColorCopyButton(value: String, onCopy: (String) -> Unit) {
    TextButton(
        onClick = { onCopy(value) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Text(value, color = OnSurfaceVariant, fontSize = 11.sp)
    }
}

private fun toHsl(r: Int, g: Int, b: Int): String {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val l = (max + min) / 2f
    if (max == min) return "hsl(0, 0%%, %.0f%%)".format(l * 100)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        rf -> ((gf - bf) / d + (if (gf < bf) 6 else 0)) * 60
        gf -> ((bf - rf) / d + 2) * 60
        else -> ((rf - gf) / d + 4) * 60
    }
    return "hsl(%.0f, %.0f%%, %.0f%%)".format(h, s * 100, l * 100)
}

private fun relativeLuminance(color: Int): Double {
    fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel((color ushr 16) and 0xFF) +
        0.7152 * channel((color ushr 8) and 0xFF) +
        0.0722 * channel(color and 0xFF)
}

private fun wcagContrastRatio(c1: Int, c2: Int): Double {
    val l1 = relativeLuminance(c1)
    val l2 = relativeLuminance(c2)
    return if (l1 > l2) (l1 + 0.05) / (l2 + 0.05) else (l2 + 0.05) / (l1 + 0.05)
}

private fun apcaContrast(text: Int, bg: Int): Double {
    fun sRGBtoY(c: Int): Double {
        fun f(v: Int): Double { val s = v / 255.0; return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126729 * f((c ushr 16) and 0xFF) + 0.7151522 * f((c ushr 8) and 0xFF) + 0.0721750 * f(c and 0xFF)
    }
    val txtY = sRGBtoY(text)
    val bgY = sRGBtoY(bg)
    val sapc = if (bgY >= txtY) {
        (Math.pow(bgY, 0.56) - Math.pow(txtY, 0.57)) * 1.14
    } else {
        (Math.pow(bgY, 0.65) - Math.pow(txtY, 0.62)) * 1.14
    }
    return if (Math.abs(sapc) < 0.1) 0.0 else if (sapc > 0) (sapc - 0.027) * 100 else (sapc + 0.027) * 100
}
