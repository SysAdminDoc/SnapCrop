package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sysadmindoc.snapcrop.ui.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CollageLayout(
    val name: String,
    val cols: Int,
    val rows: Int,
    val slots: Int = cols * rows
)

private data class CollageColor(
    val color: Int,
    @param:StringRes val nameRes: Int
)

private val collageBgColors = listOf(
    CollageColor(0xFF1A1A1A.toInt(), R.string.collage_color_dark),
    CollageColor(0xFF000000.toInt(), R.string.color_black),
    CollageColor(0xFFFFFFFF.toInt(), R.string.color_white),
    CollageColor(0xFF89B4FA.toInt(), R.string.color_blue),
    CollageColor(0xFFA6E3A1.toInt(), R.string.color_green),
    CollageColor(0xFFF38BA8.toInt(), R.string.collage_color_pink),
)

internal val collageLayouts = listOf(
    CollageLayout("2x1", 2, 1),
    CollageLayout("1x2", 1, 2),
    CollageLayout("2x2", 2, 2),
    CollageLayout("3x1", 3, 1),
    CollageLayout("1x3", 1, 3),
    CollageLayout("3x2", 3, 2),
    CollageLayout("2x3", 2, 3),
    CollageLayout("3x3", 3, 3),
    CollageLayout("4x1", 4, 1),
    CollageLayout("1x4", 1, 4),
    CollageLayout("4x2", 4, 2),
    CollageLayout("2x4", 2, 4),
    CollageLayout("4x3", 4, 3),
    CollageLayout("3x4", 3, 4),
    CollageLayout("4x4", 4, 4),
    CollageLayout("5x1", 5, 1),
    CollageLayout("1x5", 1, 5),
    CollageLayout("5x2", 5, 2),
    CollageLayout("2x5", 2, 5),
    CollageLayout("5x3", 5, 3),
    CollageLayout("3x5", 3, 5),
    CollageLayout("5x5", 5, 5),
    CollageLayout("6x1", 6, 1),
    CollageLayout("6x2", 6, 2),
    CollageLayout("6x3", 6, 3),
)

internal data class CollageSelectionUndo(
    val previousLayout: CollageLayout,
    val removedUris: List<Uri>,
) {
    init {
        require(removedUris.isNotEmpty())
    }
}

internal data class CollageSelectionState(
    val layout: CollageLayout = collageLayouts[2],
    val uris: List<Uri> = emptyList(),
    val pendingLayout: CollageLayout? = null,
    val undo: CollageSelectionUndo? = null,
) {
    init {
        require(uris.size <= layout.slots)
    }
}

internal object CollageSelectionReducer {
    fun requestLayout(state: CollageSelectionState, target: CollageLayout): CollageSelectionState = when {
        target == state.layout -> state.copy(pendingLayout = null)
        state.uris.size <= target.slots -> state.copy(layout = target, pendingLayout = null)
        else -> state.copy(pendingLayout = target)
    }

    fun cancelPending(state: CollageSelectionState): CollageSelectionState =
        state.copy(pendingLayout = null)

    fun confirmPending(state: CollageSelectionState): CollageSelectionState {
        val target = state.pendingLayout ?: return state
        if (state.uris.size <= target.slots) return state.copy(layout = target, pendingLayout = null)
        return state.copy(
            layout = target,
            uris = state.uris.take(target.slots),
            pendingLayout = null,
            undo = CollageSelectionUndo(state.layout, state.uris.drop(target.slots)),
        )
    }

    fun undo(state: CollageSelectionState): CollageSelectionState {
        val undo = state.undo ?: return state
        return state.copy(
            layout = undo.previousLayout,
            uris = state.uris + undo.removedUris,
            pendingLayout = null,
            undo = null,
        )
    }

    fun replace(
        state: CollageSelectionState,
        picked: List<Uri>,
        maxItems: Int,
        keepCurrentLayout: Boolean = true,
    ): CollageSelectionState {
        val uris = picked.distinctBy(Uri::toString)
            .take(maxItems.coerceIn(0, collageLayouts.maxOf(CollageLayout::slots)))
        val layout = state.layout.takeIf { keepCurrentLayout && it.slots >= uris.size }
            ?: collageLayouts.firstOrNull { it.slots >= uris.size }
            ?: collageLayouts.maxBy(CollageLayout::slots)
        return state.copy(layout = layout, uris = uris, pendingLayout = null, undo = null)
    }

    fun append(state: CollageSelectionState, picked: List<Uri>): CollageSelectionState {
        val capacity = (state.layout.slots - state.uris.size).coerceAtLeast(0)
        if (capacity == 0) return state
        val existing = state.uris.mapTo(hashSetOf(), Uri::toString)
        val additions = picked.filter { existing.add(it.toString()) }.take(capacity)
        return state.copy(uris = state.uris + additions, pendingLayout = null, undo = null)
    }

    fun remove(state: CollageSelectionState, index: Int): CollageSelectionState =
        if (index !in state.uris.indices) state else state.copy(
            uris = state.uris.filterIndexed { position, _ -> position != index },
            pendingLayout = null,
            undo = null,
        )

    fun retainReadable(state: CollageSelectionState, readable: Set<Uri>): CollageSelectionState {
        val uris = state.uris.filter(readable::contains)
        val undo = state.undo?.let { snapshot ->
            snapshot.copy(removedUris = snapshot.removedUris.filter(readable::contains))
        }?.takeIf { it.removedUris.isNotEmpty() }
        return state.copy(
            uris = uris,
            pendingLayout = state.pendingLayout?.takeIf { uris.size > it.slots },
            undo = undo,
        )
    }
}

internal object CollageSelectionPersistence {
    private const val STATE_URIS = "collage_uris"
    private const val STATE_LAYOUT = "collage_layout"
    private const val STATE_PENDING_LAYOUT = "collage_pending_layout"
    private const val STATE_UNDO_LAYOUT = "collage_undo_layout"
    private const val STATE_UNDO_URIS = "collage_undo_uris"

    fun save(outState: Bundle, state: CollageSelectionState) {
        WorkflowStateRestoration.putUris(outState, STATE_URIS, state.uris)
        outState.putString(STATE_LAYOUT, state.layout.name)
        outState.putString(STATE_PENDING_LAYOUT, state.pendingLayout?.name)
        outState.putString(STATE_UNDO_LAYOUT, state.undo?.previousLayout?.name)
        WorkflowStateRestoration.putUris(outState, STATE_UNDO_URIS, state.undo?.removedUris.orEmpty())
    }

    fun restore(savedState: Bundle): CollageSelectionState {
        val uris = WorkflowStateRestoration.restoreUris(savedState, STATE_URIS)
            .take(CollageActivity.MAX_INBOUND_ITEMS)
        val requestedLayout = layout(savedState.getString(STATE_LAYOUT)) ?: collageLayouts[2]
        val currentLayout = requestedLayout.takeIf { it.slots >= uris.size }
            ?: collageLayouts.firstOrNull { it.slots >= uris.size }
            ?: collageLayouts.maxBy(CollageLayout::slots)
        val pendingLayout = layout(savedState.getString(STATE_PENDING_LAYOUT))
            ?.takeIf { it != currentLayout && uris.size > it.slots }
        val currentUriStrings = uris.mapTo(hashSetOf(), Uri::toString)
        val removedUris = WorkflowStateRestoration.restoreUris(savedState, STATE_UNDO_URIS)
            .filter { currentUriStrings.add(it.toString()) }
            .take((CollageActivity.MAX_INBOUND_ITEMS - uris.size).coerceAtLeast(0))
        val undo = layout(savedState.getString(STATE_UNDO_LAYOUT))
            ?.takeIf { removedUris.isNotEmpty() && uris.size + removedUris.size <= it.slots }
            ?.let { CollageSelectionUndo(it, removedUris) }
        return CollageSelectionState(currentLayout, uris, pendingLayout, undo)
    }

    private fun layout(name: String?): CollageLayout? = collageLayouts.firstOrNull { it.name == name }
}

class CollageActivity : ComponentActivity() {

    private val selectionState = mutableStateOf(CollageSelectionState())
    private val isSaving = mutableStateOf(false)
    private val spacing = mutableIntStateOf(4) // pixels gap
    private val bgColorIdx = mutableIntStateOf(0)
    private val cellAspect = mutableFloatStateOf(4f / 3f) // cell aspect ratio (w/h)
    // When true, the next picker result replaces all existing selections (initial pick / explicit "Pick All").
    // When false, it appends to fill empty cells (tapping the '+' tile).
    private var nextPickReplaces = true

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { WorkflowStateRestoration.persistReadGrant(this, it) }
            selectionState.value = if (nextPickReplaces) {
                CollageSelectionReducer.replace(selectionState.value, uris, MAX_INBOUND_ITEMS)
            } else {
                CollageSelectionReducer.append(selectionState.value, uris)
            }
            nextPickReplaces = true // default back so the toolbar "Pick" button always replaces.
        }
    }

    private fun launchReplacePicker() {
        nextPickReplaces = true
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun launchAppendPicker() {
        if (selectionState.value.uris.size >= selectionState.value.layout.slots) return
        nextPickReplaces = false
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        var restored = emptyList<Uri>()
        if (savedInstanceState != null) {
            selectionState.value = CollageSelectionPersistence.restore(savedInstanceState)
            restored = selectionState.value.uris + selectionState.value.undo?.removedUris.orEmpty()
            spacing.intValue = savedInstanceState.getInt(STATE_SPACING, 4).coerceIn(0, 64)
            bgColorIdx.intValue = savedInstanceState.getInt(STATE_BACKGROUND, 0)
                .coerceIn(collageBgColors.indices)
            cellAspect.floatValue = savedInstanceState.getFloat(STATE_CELL_ASPECT, 4f / 3f)
                .coerceIn(0.5f, 2f)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(InboundShareContract.EXTRA_URIS)
                ?.distinctBy(Uri::toString)
                ?.take(MAX_INBOUND_ITEMS)
                ?.let { incoming ->
                    selectionState.value = CollageSelectionReducer.replace(
                        selectionState.value,
                        incoming,
                        MAX_INBOUND_ITEMS,
                        keepCurrentLayout = false,
                    )
                }
        }

        setContent {
            SnapCropTheme {
                val state = selectionState.value
                CollageScreen(
                    uris = state.uris,
                    layout = state.layout,
                    pendingLayout = state.pendingLayout,
                    undo = state.undo,
                    isSaving = isSaving.value,
                    spacing = spacing.intValue,
                    bgColorIdx = bgColorIdx.intValue,
                    onLayoutChange = { selectionState.value = CollageSelectionReducer.requestLayout(state, it) },
                    onConfirmLayoutChange = {
                        selectionState.value = CollageSelectionReducer.confirmPending(selectionState.value)
                    },
                    onCancelLayoutChange = {
                        selectionState.value = CollageSelectionReducer.cancelPending(selectionState.value)
                    },
                    onUndoLayoutChange = {
                        selectionState.value = CollageSelectionReducer.undo(selectionState.value)
                    },
                    onUndoDismissed = {
                        selectionState.value = selectionState.value.copy(undo = null)
                    },
                    onSpacingChange = { spacing.intValue = it },
                    onBgColorChange = { bgColorIdx.intValue = it },
                    cellAspect = cellAspect.floatValue,
                    onCellAspectChange = { cellAspect.floatValue = it },
                    onPickImages = { launchReplacePicker() },
                    onAddImage = { launchAppendPicker() },
                    onRemoveImage = { idx ->
                        selectionState.value = CollageSelectionReducer.remove(selectionState.value, idx)
                    },
                    onSave = { buildAndSave() },
                    onClose = { finish() }
                )
            }
        }

        if (savedInstanceState != null && restored.isNotEmpty()) {
            lifecycleScope.launch {
                val validated = withContext(Dispatchers.IO) {
                    WorkflowStateRestoration.validateReadableUris(this@CollageActivity, restored)
                }
                if (validated.unavailableCount > 0) {
                    selectionState.value = CollageSelectionReducer.retainReadable(
                        selectionState.value,
                        validated.uris.toSet(),
                    )
                    Toast.makeText(this@CollageActivity, R.string.workflow_restore_missing_media, Toast.LENGTH_LONG).show()
                    if (selectionState.value.uris.isEmpty()) launchReplacePicker()
                }
            }
        }

        // First launch: pick replaces (list is empty anyway).
        nextPickReplaces = true
        if (selectionState.value.uris.isEmpty()) pickImagesLauncher.launch(arrayOf("image/*"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        CollageSelectionPersistence.save(outState, selectionState.value)
        outState.putInt(STATE_SPACING, spacing.intValue)
        outState.putInt(STATE_BACKGROUND, bgColorIdx.intValue)
        outState.putFloat(STATE_CELL_ASPECT, cellAspect.floatValue)
        super.onSaveInstanceState(outState)
    }

    private fun buildAndSave() {
        val selection = selectionState.value
        if (selection.uris.size < 2 || isSaving.value) return
        startCollage(
            uris = selection.uris,
            layout = selection.layout,
            gap = spacing.intValue,
            aspect = cellAspect.floatValue,
            backgroundColor = collageBgColors[bgColorIdx.intValue.coerceIn(collageBgColors.indices)].color,
            allowedOmissions = emptySet(),
        )
    }

    private fun startCollage(
        uris: List<Uri>,
        layout: CollageLayout,
        gap: Int,
        aspect: Float,
        backgroundColor: Int,
        allowedOmissions: Set<Int>,
    ) {
        if (isSaving.value) return
        isSaving.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = RasterCompositionPipeline.compose(
                resolver = contentResolver,
                uris = uris,
                minimumInputs = 2,
                allowedOmissions = allowedOmissions,
                planner = { bounds ->
                    RasterCompositionLayouts.collage(
                        bounds = bounds,
                        columns = layout.cols,
                        rows = layout.rows,
                        gap = gap,
                        cellAspect = aspect,
                        backgroundColor = backgroundColor,
                    )
                },
            )) {
                is RasterCompositionResult.Success -> {
                    try {
                        saveToGallery(result.bitmap)
                    } catch (_: OutOfMemoryError) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@CollageActivity,
                                R.string.raster_composition_memory_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                            isSaving.value = false
                        }
                    } finally {
                        if (!result.bitmap.isRecycled) result.bitmap.recycle()
                    }
                }
                is RasterCompositionResult.ConfirmationRequired -> {
                    withContext(Dispatchers.Main) {
                        isSaving.value = false
                        showRasterOmissionConfirmation(result) {
                            startCollage(
                                uris,
                                layout,
                                gap,
                                aspect,
                                backgroundColor,
                                allowedOmissions = result.failedPositions.toSet(),
                            )
                        }
                    }
                }
                is RasterCompositionResult.Failure -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CollageActivity,
                            result.reason.messageRes,
                            Toast.LENGTH_LONG,
                        ).show()
                        isSaving.value = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    companion object {
        const val MAX_INBOUND_ITEMS = 25
        private const val STATE_SPACING = "collage_spacing"
        private const val STATE_BACKGROUND = "collage_background"
        private const val STATE_CELL_ASPECT = "collage_cell_aspect"
    }

    private fun getSaveFormat(): Triple<Bitmap.CompressFormat, Int, String> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                          else Bitmap.CompressFormat.WEBP
                Triple(fmt, quality, "webp")
            }
            prefs.getBoolean("use_jpeg", false) -> Triple(Bitmap.CompressFormat.JPEG, quality, "jpg")
            else -> Triple(Bitmap.CompressFormat.PNG, 100, "png")
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val (fmt, quality, ext) = getSaveFormat()
        val mime = when (ext) { "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png" }
        val savePath = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val result = MediaStoreImageWriter.write(
            resolver = contentResolver,
            request = MediaStoreImageWriter.Request(
                displayName = "SnapCrop_Collage_${System.currentTimeMillis()}.$ext",
                mimeType = mime,
                relativePath = savePath,
            ),
        ) { output ->
            bitmap.compress(fmt, quality, output)
        }
        runOnUiThread {
            if (result is MediaStoreImageWriter.Result.Success) {
                Toast.makeText(this, getString(R.string.collage_saved), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                isSaving.value = false
            }
        }
    }
}

@Composable
internal fun CollageScreen(
    uris: List<Uri>,
    layout: CollageLayout,
    pendingLayout: CollageLayout?,
    undo: CollageSelectionUndo?,
    isSaving: Boolean,
    spacing: Int,
    bgColorIdx: Int,
    onLayoutChange: (CollageLayout) -> Unit,
    onConfirmLayoutChange: () -> Unit,
    onCancelLayoutChange: () -> Unit,
    onUndoLayoutChange: () -> Unit,
    onUndoDismissed: () -> Unit,
    onSpacingChange: (Int) -> Unit,
    onBgColorChange: (Int) -> Unit,
    cellAspect: Float,
    onCellAspectChange: (Float) -> Unit,
    onPickImages: () -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoMessage = undo?.let {
        stringResource(R.string.collage_layout_removed, it.removedUris.size)
    }.orEmpty()
    val undoLabel = stringResource(R.string.editor_undo)
    LaunchedEffect(undo, undoMessage) {
        if (undo != null) {
            when (snackbarHostState.showSnackbar(
                message = undoMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )) {
                SnackbarResult.ActionPerformed -> onUndoLayoutChange()
                SnackbarResult.Dismissed -> onUndoDismissed()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
      Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = OnSurface) }
            Text(stringResource(R.string.collage_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f))
            Text("${uris.size}/${layout.slots}", color = OnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp))
            TextButton(
                onClick = onPickImages,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(if (uris.isEmpty()) stringResource(R.string.collage_add_images) else stringResource(R.string.collage_replace), color = Primary, fontSize = 12.sp)
            }
        }

        // Layout picker
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(collageLayouts, key = { it.name }) { l ->
                val layoutCd = stringResource(R.string.collage_layout_cd, l.name, l.slots, "")
                FilterChip(
                    selected = layout == l,
                    onClick = { onLayoutChange(l) },
                    label = { Text(l.name, fontSize = 12.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = layoutCd
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Spacing slider
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val gapCd = stringResource(R.string.collage_gap)
            Text(stringResource(R.string.collage_gap_px, spacing), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = spacing.toFloat(), onValueChange = { onSpacingChange(it.toInt()) },
                valueRange = 0f..20f,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = gapCd
                },
                colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceVariant)
            )
        }

        // Cell aspect ratio
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.collage_cells), color = OnSurfaceVariant, fontSize = 11.sp)
            listOf("4:3" to 4f/3f, "1:1" to 1f, "16:9" to 16f/9f, "3:4" to 3f/4f).forEach { (label, ratio) ->
                val isSelected = kotlin.math.abs(cellAspect - ratio) < 0.01f
                val aspectCd = stringResource(
                    R.string.collage_aspect_cd,
                    label,
                    ""
                )
                FilterChip(
                    selected = isSelected,
                    onClick = { onCellAspectChange(ratio) },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.semantics {
                        contentDescription = aspectCd
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryContainer, selectedLabelColor = Primary,
                        containerColor = SurfaceVariant, labelColor = OnSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Background color picker
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.collage_bg_label), color = OnSurfaceVariant, fontSize = 11.sp)
            collageBgColors.forEachIndexed { i, colorOption ->
                val name = stringResource(colorOption.nameRes)
                val bgCd = stringResource(
                    R.string.collage_background_cd,
                    name,
                    ""
                )
                Box(
                    Modifier.size(48.dp)
                        .semantics {
                            contentDescription = bgCd
                            selected = i == bgColorIdx
                            role = Role.RadioButton
                        }
                        .clickable(role = Role.RadioButton) { onBgColorChange(i) }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .background(Color(colorOption.color), RoundedCornerShape(4.dp))
                            .then(if (i == bgColorIdx) Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp)) else Modifier)
                    )
                }
            }
        }

        // Collage preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            if (uris.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Surface(color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.padding(14.dp).size(28.dp), tint = Primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.collage_empty_title), color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.collage_empty_subtitle),
                        color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
            } else {
                // Grid preview
                Column(
                    Modifier.fillMaxWidth().aspectRatio(layout.cols.toFloat() / layout.rows * cellAspect)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariant),
                    verticalArrangement = Arrangement.spacedBy(spacing.dp)
                ) {
                    for (row in 0 until layout.rows) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth().padding(
                                start = spacing.dp, end = spacing.dp,
                                top = if (row == 0) spacing.dp else 0.dp,
                                bottom = if (row == layout.rows - 1) spacing.dp else 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing.dp)
                        ) {
                            for (col in 0 until layout.cols) {
                                val idx = row * layout.cols + col
                                val occupied = idx < uris.size
                                val cellCd = if (occupied) {
                                    stringResource(R.string.collage_cell_loaded_cd, idx + 1)
                                } else {
                                    stringResource(R.string.collage_cell_empty_cd, idx + 1)
                                }
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (occupied) MediaSurface else SurfaceVariant)
                                        .semantics {
                                            contentDescription = cellCd
                                        }
                                        .then(
                                            if (!occupied) Modifier.clickable(role = Role.Button, onClick = onAddImage)
                                            else Modifier,
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (occupied) {
                                        val removeCellDescription = stringResource(R.string.collage_remove_cd, idx + 1)
                                        AsyncImage(
                                            model = uris[idx], contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { onRemoveImage(idx) },
                                            modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(Icons.Default.Close, removeCellDescription,
                                                tint = OnMediaSurface, modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        Text("+", color = OnSurfaceVariant, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save button
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSaving) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collage_rendering), color = OnSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val canSave = uris.size >= 2
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp),
                        tint = if (canSave) OnPrimary else OnSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(if (canSave) stringResource(R.string.collage_save_button) else stringResource(R.string.collage_save_needs_two),
                        color = if (canSave) OnPrimary else OnSurfaceVariant,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
      }

      SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier
              .align(Alignment.BottomCenter)
              .safeDrawingPadding()
              .padding(horizontal = 12.dp, vertical = 76.dp),
      )

      pendingLayout?.let { target ->
          val removalCount = (uris.size - target.slots).coerceAtLeast(0)
          AlertDialog(
              onDismissRequest = onCancelLayoutChange,
              confirmButton = {
                  TextButton(onClick = onConfirmLayoutChange) {
                      Text(stringResource(R.string.collage_layout_confirm), color = Danger)
                  }
              },
              dismissButton = {
                  TextButton(onClick = onCancelLayoutChange) {
                      Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                  }
              },
              title = {
                  Text(stringResource(R.string.collage_layout_reduce_title, target.name), color = OnSurface)
              },
              text = {
                  Text(
                      stringResource(
                          R.string.collage_layout_reduce_body,
                          target.slots,
                          removalCount,
                      ),
                      color = OnSurfaceVariant,
                  )
              },
              containerColor = SurfaceVariant,
          )
      }
    }
}
