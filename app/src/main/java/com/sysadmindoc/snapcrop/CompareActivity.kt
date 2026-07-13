package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.MediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class LoadedCompareImage(
    val uri: Uri,
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sampleSize: Int,
)

internal sealed interface CompareUiState {
    data object Loading : CompareUiState
    data class Failed(val message: String) : CompareUiState
    data class Ready(
        val before: LoadedCompareImage,
        val after: LoadedCompareImage,
        val analysis: CompareAnalysisResult,
        val analyzing: Boolean = false,
    ) : CompareUiState
}

class CompareActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_BEFORE_URI = "compare_before_uri"
        private const val EXTRA_AFTER_URI = "compare_after_uri"
        private const val STATE_MODE = "compare_mode"
        private const val STATE_ALIGNMENT = "compare_alignment"
        private const val STATE_DIVIDER = "compare_divider"
        private const val STATE_OPACITY = "compare_opacity"
        private const val STATE_BLINK_RUNNING = "compare_blink_running"

        fun intent(context: Context, uris: List<Uri>): Intent {
            require(uris.size == 2 && uris[0] != uris[1]) { "Compare requires two distinct images" }
            return Intent(context, CompareActivity::class.java)
                .putExtra(EXTRA_BEFORE_URI, uris[0].toString())
                .putExtra(EXTRA_AFTER_URI, uris[1].toString())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private var uiState by mutableStateOf<CompareUiState>(CompareUiState.Loading)
    private var mode by mutableStateOf(CompareMode.SWIPE)
    private var alignment by mutableStateOf(CompareAlignment.TOP_LEFT)
    private var divider by mutableFloatStateOf(0.5f)
    private var overlayOpacity by mutableFloatStateOf(0.5f)
    private var blinkRunning by mutableStateOf(false)
    private var beforeUri: Uri? = null
    private var afterUri: Uri? = null
    private var analysisJob: Job? = null
    private var analysisGeneration = 0
    private val retiredDifferenceBitmaps = mutableSetOf<Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureWindow(this)
        beforeUri = (savedInstanceState?.getString(EXTRA_BEFORE_URI)
            ?: intent.getStringExtra(EXTRA_BEFORE_URI))?.let(Uri::parse)
        afterUri = (savedInstanceState?.getString(EXTRA_AFTER_URI)
            ?: intent.getStringExtra(EXTRA_AFTER_URI))?.let(Uri::parse)
        mode = savedInstanceState?.getString(STATE_MODE)?.let {
            runCatching { CompareMode.valueOf(it) }.getOrNull()
        } ?: CompareMode.SWIPE
        alignment = savedInstanceState?.getString(STATE_ALIGNMENT)?.let {
            runCatching { CompareAlignment.valueOf(it) }.getOrNull()
        } ?: CompareAlignment.TOP_LEFT
        divider = savedInstanceState?.getFloat(STATE_DIVIDER, 0.5f) ?: 0.5f
        overlayOpacity = savedInstanceState?.getFloat(STATE_OPACITY, 0.5f) ?: 0.5f
        blinkRunning = savedInstanceState?.getBoolean(STATE_BLINK_RUNNING, false) ?: false

        setContent {
            SnapCropTheme {
                CompareScreen(
                    state = uiState,
                    mode = mode,
                    alignment = alignment,
                    divider = divider,
                    overlayOpacity = overlayOpacity,
                    blinkRunning = blinkRunning,
                    onClose = ::finish,
                    onRetry = ::load,
                    onModeChange = {
                        mode = it
                        if (it != CompareMode.BLINK) blinkRunning = false
                    },
                    onAlignmentChange = ::changeAlignment,
                    onDividerChange = { divider = it.coerceIn(0f, 1f) },
                    onOverlayOpacityChange = { overlayOpacity = it.coerceIn(0f, 1f) },
                    onBlinkRunningChange = { blinkRunning = it },
                    onSwap = ::swap,
                )
            }
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        applySecureWindow(this)
    }

    override fun onPause() {
        blinkRunning = false
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_BEFORE_URI, beforeUri?.toString())
        outState.putString(EXTRA_AFTER_URI, afterUri?.toString())
        outState.putString(STATE_MODE, mode.name)
        outState.putString(STATE_ALIGNMENT, alignment.name)
        outState.putFloat(STATE_DIVIDER, divider)
        outState.putFloat(STATE_OPACITY, overlayOpacity)
        outState.putBoolean(STATE_BLINK_RUNNING, blinkRunning)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        analysisGeneration++
        analysisJob?.cancel()
        val ready = uiState as? CompareUiState.Ready
        val recycle = {
            ready?.before?.bitmap?.recycleSafely()
            ready?.after?.bitmap?.recycleSafely()
            ready?.analysis?.differenceBitmap?.recycleSafely()
            retiredDifferenceBitmaps.forEach(Bitmap::recycleSafely)
            retiredDifferenceBitmaps.clear()
        }
        if (analysisJob?.isActive == true) analysisJob?.invokeOnCompletion { recycle() } else recycle()
        super.onDestroy()
    }

    private fun load() {
        val first = beforeUri
        val second = afterUri
        if (first == null || second == null || first == second) {
            uiState = CompareUiState.Failed(getString(R.string.compare_invalid_pair))
            return
        }
        recycleReadyState()
        uiState = CompareUiState.Loading
        analysisJob?.cancel()
        val generation = ++analysisGeneration
        analysisJob = lifecycleScope.launch(Dispatchers.IO) {
            var before: LoadedCompareImage? = null
            var after: LoadedCompareImage? = null
            var result: CompareAnalysisResult? = null
            var published = false
            try {
                before = decode(first)
                after = decode(second)
                result = CompareAnalyzer.analyze(
                    CompareSource(before.bitmap, before.sourceWidth, before.sourceHeight),
                    CompareSource(after.bitmap, after.sourceWidth, after.sourceHeight),
                    alignment,
                )
                withContext(Dispatchers.Main) {
                    if (generation != analysisGeneration) return@withContext
                    uiState = CompareUiState.Ready(before, after, result)
                    published = true
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation == analysisGeneration) {
                        uiState = CompareUiState.Failed(
                            error.message?.takeIf(String::isNotBlank)
                                ?: getString(R.string.compare_load_failed)
                        )
                    }
                }
            } finally {
                if (!published) {
                    before?.bitmap?.recycleSafely()
                    after?.bitmap?.recycleSafely()
                    result?.differenceBitmap?.recycleSafely()
                }
            }
        }
    }

    private fun decode(uri: Uri): LoadedCompareImage {
        return when (val result = BatchImageIntake.decodeForAnalysis(contentResolver, uri)) {
            is BatchImageIntakeResult.Ready -> LoadedCompareImage(
                uri = uri,
                bitmap = result.bitmap,
                sourceWidth = result.sourceWidth,
                sourceHeight = result.sourceHeight,
                sampleSize = result.sampleSize,
            )
            is BatchImageIntakeResult.Oversized -> error(getString(R.string.compare_too_large))
            is BatchImageIntakeResult.Unreadable -> error(getString(R.string.compare_unreadable))
            is BatchImageIntakeResult.Failed -> error(getString(R.string.compare_unreadable))
            BatchImageIntakeResult.Cancelled -> error(getString(R.string.compare_load_failed))
            is BatchImageIntakeResult.Skipped -> error(getString(R.string.compare_load_failed))
        }
    }

    private fun changeAlignment(newAlignment: CompareAlignment) {
        if (newAlignment == alignment) return
        alignment = newAlignment
        reanalyze()
    }

    private fun swap() {
        val ready = uiState as? CompareUiState.Ready ?: return
        beforeUri = ready.after.uri
        afterUri = ready.before.uri
        reanalyze(ready.after, ready.before)
    }

    private fun reanalyze(
        before: LoadedCompareImage? = null,
        after: LoadedCompareImage? = null,
    ) {
        val ready = uiState as? CompareUiState.Ready ?: return
        val nextBefore = before ?: ready.before
        val nextAfter = after ?: ready.after
        uiState = ready.copy(before = nextBefore, after = nextAfter, analyzing = true)
        analysisJob?.cancel()
        val generation = ++analysisGeneration
        analysisJob = lifecycleScope.launch(Dispatchers.Default) {
            var next: CompareAnalysisResult? = null
            var published = false
            try {
                next = CompareAnalyzer.analyze(
                    CompareSource(nextBefore.bitmap, nextBefore.sourceWidth, nextBefore.sourceHeight),
                    CompareSource(nextAfter.bitmap, nextAfter.sourceWidth, nextAfter.sourceHeight),
                    alignment,
                )
                withContext(Dispatchers.Main) {
                    if (generation != analysisGeneration) return@withContext
                    val previous = ready.analysis.differenceBitmap
                    uiState = CompareUiState.Ready(nextBefore, nextAfter, next)
                    published = true
                    retireDifferenceBitmap(previous)
                }
            } finally {
                if (!published) next?.differenceBitmap?.recycleSafely()
            }
        }
    }

    private fun retireDifferenceBitmap(bitmap: Bitmap) {
        retiredDifferenceBitmaps += bitmap
        window.decorView.postDelayed({
            if (retiredDifferenceBitmaps.remove(bitmap)) bitmap.recycleSafely()
        }, 64L)
    }

    private fun recycleReadyState() {
        (uiState as? CompareUiState.Ready)?.let { ready ->
            ready.before.bitmap.recycleSafely()
            ready.after.bitmap.recycleSafely()
            ready.analysis.differenceBitmap.recycleSafely()
        }
    }
}

@Composable
internal fun CompareScreen(
    state: CompareUiState,
    mode: CompareMode,
    alignment: CompareAlignment,
    divider: Float,
    overlayOpacity: Float,
    blinkRunning: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onModeChange: (CompareMode) -> Unit,
    onAlignmentChange: (CompareAlignment) -> Unit,
    onDividerChange: (Float) -> Unit,
    onOverlayOpacityChange: (Float) -> Unit,
    onBlinkRunningChange: (Boolean) -> Unit,
    onSwap: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Black).safeDrawingPadding().padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = OnSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.compare_title),
                    color = OnSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.compare_private_summary),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (state is CompareUiState.Ready) {
                IconButton(onClick = onSwap, enabled = !state.analyzing) {
                    Icon(Icons.Default.SwapHoriz, stringResource(R.string.compare_swap), tint = Primary)
                }
            }
        }

        when (state) {
            CompareUiState.Loading -> CompareLoading()
            is CompareUiState.Failed -> CompareFailure(state.message, onRetry, onClose)
            is CompareUiState.Ready -> CompareReadyScreen(
                ready = state,
                mode = mode,
                alignment = alignment,
                divider = divider,
                overlayOpacity = overlayOpacity,
                blinkRunning = blinkRunning,
                onModeChange = onModeChange,
                onAlignmentChange = onAlignmentChange,
                onDividerChange = onDividerChange,
                onOverlayOpacityChange = onOverlayOpacityChange,
                onBlinkRunningChange = onBlinkRunningChange,
            )
        }
    }
}

@Composable
private fun CompareLoading() {
    val loadingLabel = stringResource(R.string.compare_loading)
    Box(
        Modifier.fillMaxSize().semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = loadingLabel
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(12.dp))
            Text(loadingLabel, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun CompareFailure(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.compare_load_failed), color = OnSurface, fontWeight = FontWeight.Bold)
                Text(message, color = OnSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gallery_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareReadyScreen(
    ready: CompareUiState.Ready,
    mode: CompareMode,
    alignment: CompareAlignment,
    divider: Float,
    overlayOpacity: Float,
    blinkRunning: Boolean,
    onModeChange: (CompareMode) -> Unit,
    onAlignmentChange: (CompareAlignment) -> Unit,
    onDividerChange: (Float) -> Unit,
    onOverlayOpacityChange: (Float) -> Unit,
    onBlinkRunningChange: (Boolean) -> Unit,
) {
    var selectedRegion by rememberSaveable(ready.analysis.totalRegionCount) { mutableIntStateOf(0) }
    val mismatch = ready.before.sourceWidth != ready.after.sourceWidth ||
            ready.before.sourceHeight != ready.after.sourceHeight
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp && maxHeight >= 520.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ComparePreview(
                    ready, mode, alignment, divider, overlayOpacity, blinkRunning,
                    selectedRegion, onDividerChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                CompareControls(
                    ready, mode, alignment, divider, overlayOpacity, blinkRunning, mismatch,
                    selectedRegion, onModeChange, onAlignmentChange, onDividerChange,
                    onOverlayOpacityChange, onBlinkRunningChange,
                    onSelectedRegionChange = { selectedRegion = it },
                    modifier = Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompareSummary(ready, alignment, mismatch)
                CompareModeRow(mode, onModeChange)
                ComparePreview(
                    ready, mode, alignment, divider, overlayOpacity, blinkRunning,
                    selectedRegion, onDividerChange,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                CompareModeControl(
                    ready, mode, divider, overlayOpacity, blinkRunning, selectedRegion,
                    onDividerChange, onOverlayOpacityChange, onBlinkRunningChange,
                    onSelectedRegionChange = { selectedRegion = it },
                )
                if (mismatch) CompareAlignmentRow(alignment, onAlignmentChange)
            }
        }
    }
}

@Composable
private fun CompareControls(
    ready: CompareUiState.Ready,
    mode: CompareMode,
    alignment: CompareAlignment,
    divider: Float,
    overlayOpacity: Float,
    blinkRunning: Boolean,
    mismatch: Boolean,
    selectedRegion: Int,
    onModeChange: (CompareMode) -> Unit,
    onAlignmentChange: (CompareAlignment) -> Unit,
    onDividerChange: (Float) -> Unit,
    onOverlayOpacityChange: (Float) -> Unit,
    onBlinkRunningChange: (Boolean) -> Unit,
    onSelectedRegionChange: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(end = 4.dp)) {
        CompareSummary(ready, alignment, mismatch)
        CompareModeRow(mode, onModeChange)
        CompareModeControl(
            ready, mode, divider, overlayOpacity, blinkRunning, selectedRegion,
            onDividerChange, onOverlayOpacityChange, onBlinkRunningChange,
            onSelectedRegionChange,
        )
        if (mismatch) CompareAlignmentRow(alignment, onAlignmentChange)
    }
}

@Composable
private fun CompareSummary(
    ready: CompareUiState.Ready,
    alignment: CompareAlignment,
    mismatch: Boolean,
) {
    val percent = formatChangedPercent(ready.analysis.changedPercent)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareImageLabel("A", stringResource(R.string.compare_before), ready.before, Modifier.weight(1f))
                CompareImageLabel("B", stringResource(R.string.compare_after), ready.after, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.compare_changed, percent, ready.analysis.changedPixels),
                color = if (ready.analysis.changedPixels == 0) OnSurfaceVariant else Tertiary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (mismatch) {
                Text(
                    stringResource(R.string.compare_mismatch, alignmentLabel(alignment)),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (ready.analysis.analysisScale < 0.999f) {
                Text(
                    stringResource(R.string.compare_sampled),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (ready.analyzing) {
                Text(stringResource(R.string.compare_reanalyzing), color = Primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CompareImageLabel(
    badge: String,
    label: String,
    image: LoadedCompareImage,
    modifier: Modifier,
) {
    Column(modifier) {
        Text("$badge · $label", color = OnSurface, fontWeight = FontWeight.Medium)
        Text(
            "${image.sourceWidth} × ${image.sourceHeight}",
            color = OnSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompareModeRow(mode: CompareMode, onModeChange: (CompareMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompareMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                label = { Text(modeLabel(option)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("compare-mode-${option.name.lowercase(Locale.ROOT)}"),
            )
        }
    }
}

@Composable
private fun CompareAlignmentRow(
    alignment: CompareAlignment,
    onAlignmentChange: (CompareAlignment) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(R.string.compare_alignment), color = OnSurface, fontWeight = FontWeight.Medium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompareAlignment.entries.forEach { option ->
                FilterChip(
                    selected = alignment == option,
                    onClick = { onAlignmentChange(option) },
                    label = { Text(alignmentLabel(option)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("compare-align-${option.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
    }
}

@Composable
private fun CompareModeControl(
    ready: CompareUiState.Ready,
    mode: CompareMode,
    divider: Float,
    overlayOpacity: Float,
    blinkRunning: Boolean,
    selectedRegion: Int,
    onDividerChange: (Float) -> Unit,
    onOverlayOpacityChange: (Float) -> Unit,
    onBlinkRunningChange: (Boolean) -> Unit,
    onSelectedRegionChange: (Int) -> Unit,
) {
    when (mode) {
        CompareMode.SWIPE -> LabeledSlider(
            stringResource(R.string.compare_divider), divider, onDividerChange,
            Modifier.testTag("compare-divider-slider"),
        )
        CompareMode.OVERLAY -> LabeledSlider(
            stringResource(R.string.compare_opacity), overlayOpacity, onOverlayOpacityChange,
            Modifier.testTag("compare-opacity-slider"),
        )
        CompareMode.BLINK -> Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.compare_blink_hint), color = OnSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { onBlinkRunningChange(!blinkRunning) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("compare-blink-toggle"),
            ) {
                Icon(if (blinkRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (blinkRunning) R.string.compare_pause else R.string.compare_play))
            }
        }
        CompareMode.DIFFERENCE -> {
            val regions = ready.analysis.regions
            val countLabel = if (ready.analysis.totalRegionCount > regions.size) {
                stringResource(R.string.compare_regions_more, regions.size)
            } else {
                stringResource(R.string.compare_regions, regions.size)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(countLabel, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(
                    enabled = regions.isNotEmpty(),
                    onClick = {
                        onSelectedRegionChange(if (regions.isEmpty()) 0 else (selectedRegion + 1) % regions.size)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("compare-next-region"),
                ) { Text(stringResource(R.string.compare_next_change)) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label · ${(value * 100).roundToInt()}%", color = OnSurfaceVariant, fontSize = 12.sp)
        Slider(value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun ComparePreview(
    ready: CompareUiState.Ready,
    mode: CompareMode,
    alignment: CompareAlignment,
    divider: Float,
    overlayOpacity: Float,
    blinkRunning: Boolean,
    selectedRegion: Int,
    onDividerChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAfter by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mode, blinkRunning) {
        if (mode != CompareMode.BLINK || !blinkRunning) {
            showAfter = false
            return@LaunchedEffect
        }
        while (true) {
            delay(800)
            showAfter = !showAfter
        }
    }
    val beforeImage = remember(ready.before.bitmap) { ready.before.bitmap.asImageBitmap() }
    val afterImage = remember(ready.after.bitmap) { ready.after.bitmap.asImageBitmap() }
    val differenceImage = remember(ready.analysis.differenceBitmap) {
        ready.analysis.differenceBitmap.asImageBitmap()
    }
    val canvasDescription = stringResource(
        R.string.compare_canvas_description,
        modeLabel(mode),
        formatChangedPercent(ready.analysis.changedPercent),
    )
    val dividerState = stringResource(R.string.compare_divider_state, (divider * 100).roundToInt())
    val opacityState = stringResource(R.string.compare_opacity_state, (overlayOpacity * 100).roundToInt())
    val blinkingState = stringResource(R.string.compare_blinking_state)
    val pausedState = stringResource(R.string.compare_paused_state)
    val regionState = stringResource(R.string.compare_region_state, ready.analysis.totalRegionCount)
    val moveDividerLeft = stringResource(R.string.compare_divider_left)
    val moveDividerRight = stringResource(R.string.compare_divider_right)
    Canvas(
        modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MediaSurface)
            .pointerInput(mode) {
                if (mode == CompareMode.SWIPE) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val commonWidth = max(ready.before.sourceWidth, ready.after.sourceWidth)
                        val commonHeight = max(ready.before.sourceHeight, ready.after.sourceHeight)
                        val fitScale = min(
                            size.width.toFloat() / commonWidth,
                            size.height.toFloat() / commonHeight,
                        )
                        val contentWidth = commonWidth * fitScale
                        val contentLeft = (size.width - contentWidth) / 2f
                        onDividerChange(
                            compareSwipeFraction(change.position.x, contentLeft, contentWidth)
                        )
                    }
                }
            }
            .semantics {
                contentDescription = canvasDescription
                stateDescription = when (mode) {
                    CompareMode.SWIPE -> dividerState
                    CompareMode.OVERLAY -> opacityState
                    CompareMode.BLINK -> if (blinkRunning) blinkingState else pausedState
                    CompareMode.DIFFERENCE -> regionState
                }
                if (mode == CompareMode.SWIPE) {
                    customActions = listOf(
                        CustomAccessibilityAction(moveDividerLeft) {
                            onDividerChange((divider - 0.1f).coerceAtLeast(0f)); true
                        },
                        CustomAccessibilityAction(moveDividerRight) {
                            onDividerChange((divider + 0.1f).coerceAtMost(1f)); true
                        },
                    )
                }
            }
            .testTag("compare-preview")
    ) {
        val commonWidth = max(ready.before.sourceWidth, ready.after.sourceWidth)
        val commonHeight = max(ready.before.sourceHeight, ready.after.sourceHeight)
        val scale = min(size.width / commonWidth, size.height / commonHeight)
        val contentWidth = commonWidth * scale
        val contentHeight = commonHeight * scale
        val contentLeft = (size.width - contentWidth) / 2f
        val contentTop = (size.height - contentHeight) / 2f
        val beforeOffset = CompareAnalyzer.offset(
            commonWidth, commonHeight, ready.before.sourceWidth, ready.before.sourceHeight, alignment
        )
        val afterOffset = CompareAnalyzer.offset(
            commonWidth, commonHeight, ready.after.sourceWidth, ready.after.sourceHeight, alignment
        )

        fun drawBefore(alpha: Float = 1f) {
            drawImage(
                beforeImage,
                dstOffset = IntOffset(
                    (contentLeft + beforeOffset.x * scale).roundToInt(),
                    (contentTop + beforeOffset.y * scale).roundToInt(),
                ),
                dstSize = IntSize(
                    max(1, (ready.before.sourceWidth * scale).roundToInt()),
                    max(1, (ready.before.sourceHeight * scale).roundToInt()),
                ),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }

        fun drawAfter(alpha: Float = 1f) {
            drawImage(
                afterImage,
                dstOffset = IntOffset(
                    (contentLeft + afterOffset.x * scale).roundToInt(),
                    (contentTop + afterOffset.y * scale).roundToInt(),
                ),
                dstSize = IntSize(
                    max(1, (ready.after.sourceWidth * scale).roundToInt()),
                    max(1, (ready.after.sourceHeight * scale).roundToInt()),
                ),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }

        when (mode) {
            CompareMode.SWIPE -> {
                drawAfter()
                val dividerX = contentLeft + contentWidth * divider
                clipRect(left = contentLeft, top = contentTop, right = dividerX, bottom = contentTop + contentHeight) {
                    drawBefore()
                }
                drawLine(Primary, Offset(dividerX, contentTop), Offset(dividerX, contentTop + contentHeight), 3.dp.toPx())
            }
            CompareMode.OVERLAY -> {
                drawBefore()
                drawAfter(overlayOpacity)
            }
            CompareMode.BLINK -> if (showAfter) drawAfter() else drawBefore()
            CompareMode.DIFFERENCE -> {
                drawAfter(0.18f)
                drawImage(
                    differenceImage,
                    dstOffset = IntOffset(contentLeft.roundToInt(), contentTop.roundToInt()),
                    dstSize = IntSize(max(1, contentWidth.roundToInt()), max(1, contentHeight.roundToInt())),
                    filterQuality = FilterQuality.None,
                )
                ready.analysis.regions.forEachIndexed { index, region ->
                    val regionScaleX = contentWidth / ready.analysis.width
                    val regionScaleY = contentHeight / ready.analysis.height
                    drawRect(
                        color = if (index == selectedRegion) Primary else Tertiary,
                        topLeft = Offset(
                            contentLeft + region.left * regionScaleX,
                            contentTop + region.top * regionScaleY,
                        ),
                        size = Size(
                            (region.right - region.left) * regionScaleX,
                            (region.bottom - region.top) * regionScaleY,
                        ),
                        style = Stroke(width = if (index == selectedRegion) 4.dp.toPx() else 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

internal fun compareSwipeFraction(pointerX: Float, contentLeft: Float, contentWidth: Float): Float =
    if (contentWidth <= 0f) 0.5f else ((pointerX - contentLeft) / contentWidth).coerceIn(0f, 1f)

@Composable
private fun modeLabel(mode: CompareMode): String = stringResource(
    when (mode) {
        CompareMode.SWIPE -> R.string.compare_mode_swipe
        CompareMode.OVERLAY -> R.string.compare_mode_overlay
        CompareMode.BLINK -> R.string.compare_mode_blink
        CompareMode.DIFFERENCE -> R.string.compare_mode_difference
    }
)

@Composable
private fun alignmentLabel(alignment: CompareAlignment): String = stringResource(
    when (alignment) {
        CompareAlignment.TOP_LEFT -> R.string.compare_align_top_left
        CompareAlignment.CENTER -> R.string.compare_align_center
        CompareAlignment.BOTTOM_LEFT -> R.string.compare_align_bottom_left
    }
)

internal fun formatChangedPercent(percent: Double): String = when {
    percent <= 0.0 -> "0.00%"
    percent < 0.01 -> "<0.01%"
    else -> String.format(Locale.US, "%.2f%%", percent)
}

private fun Bitmap.recycleSafely() {
    if (!isRecycled) recycle()
}
