package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal fun renderEditorPreviewBitmap(
    bitmap: Bitmap,
    rect: Rect,
    redactions: List<RedactionRegion>,
    drawPaths: List<DrawPath>,
    adjustments: FloatArray,
    cutout: CutoutEditState,
): Bitmap = CropImageRenderer.render(
    bitmap = bitmap,
    rect = Rect(rect),
    redactions = redactions.map { it.copy(bounds = Rect(it.bounds)) },
    drawPaths = drawPaths.map { path ->
        path.copy(
            points = path.points.map { PointF(it.x, it.y) },
            controlPoint = path.controlPoint?.let { PointF(it.x, it.y) },
        )
    },
    adj = adjustments.copyOf(),
    cutout = cutout.copy(bands = cutout.bands.map { it.copy() }),
)

@Composable
internal fun BeforeAfterPreview(
    bitmap: Bitmap,
    imageBitmap: ImageBitmap,
    cropLeft: Int,
    cropTop: Int,
    cropRight: Int,
    cropBottom: Int,
    redactions: List<RedactionRegion> = emptyList(),
    drawPaths: List<DrawPath> = emptyList(),
    adjustments: FloatArray = floatArrayOf(0f, 1f, 1f),
    cutBands: List<CutBand> = emptyList(),
    cutSeparatorStyle: CutSeparatorStyle = CutSeparatorStyle.STRAIGHT,
    onDismiss: () -> Unit
) {
    val redactionSnapshot = redactions.map { it.copy(bounds = Rect(it.bounds)) }
    val drawSnapshot = drawPaths.map { path ->
        path.copy(
            points = path.points.map { PointF(it.x, it.y) },
            controlPoint = path.controlPoint?.let { PointF(it.x, it.y) },
        )
    }
    val adjustmentSnapshot = adjustments.copyOf()
    val adjustmentKey = adjustmentSnapshot.toList()
    val cutoutSnapshot = CutoutEditState(cutBands.map { it.copy() }, cutSeparatorStyle)
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewFailed by remember { mutableStateOf(false) }
    LaunchedEffect(
        bitmap,
        cropLeft,
        cropTop,
        cropRight,
        cropBottom,
        redactionSnapshot,
        drawSnapshot,
        adjustmentKey,
        cutoutSnapshot,
    ) {
        previewBitmap = null
        previewFailed = false
        var candidate: Bitmap? = null
        try {
            candidate = withContext(Dispatchers.Default) {
                renderEditorPreviewBitmap(
                    bitmap = bitmap,
                    rect = Rect(cropLeft, cropTop, cropRight, cropBottom),
                    redactions = redactionSnapshot,
                    drawPaths = drawSnapshot,
                    adjustments = adjustmentSnapshot,
                    cutout = cutoutSnapshot,
                )
            }
            currentCoroutineContext().ensureActive()
            previewBitmap = candidate
            candidate = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewFailed = true
        } finally {
            candidate?.takeIf { !it.isRecycled }?.recycle()
        }
    }
    val croppedPreview = previewBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
    DisposableEffect(previewBitmap) {
        val ownedPreview = previewBitmap
        onDispose {
            ownedPreview?.takeIf { !it.isRecycled }?.recycle()
        }
    }
    var dividerX by remember { mutableFloatStateOf(0.5f) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (croppedPreview != null) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDismiss() }) }
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
            val previewScale = min(size.width / croppedPreview.width, size.height / croppedPreview.height)
            val previewWidth = croppedPreview.width * previewScale
            val previewHeight = croppedPreview.height * previewScale
            val previewX = (size.width - previewWidth) / 2
            val previewY = (size.height - previewHeight) / 2
            drawImage(
                croppedPreview,
                dstOffset = IntOffset(previewX.roundToInt(), previewY.roundToInt()),
                dstSize = IntSize(previewWidth.roundToInt(), previewHeight.roundToInt())
            )

            val dividerPx = size.width * dividerX
            clipRect(left = 0f, top = 0f, right = dividerPx, bottom = size.height) {
                val originalScale = min(size.width / imageBitmap.width, size.height / imageBitmap.height)
                val originalWidth = imageBitmap.width * originalScale
                val originalHeight = imageBitmap.height * originalScale
                val originalX = (size.width - originalWidth) / 2
                val originalY = (size.height - originalHeight) / 2
                drawImage(
                    imageBitmap,
                    dstOffset = IntOffset(originalX.roundToInt(), originalY.roundToInt()),
                    dstSize = IntSize(originalWidth.roundToInt(), originalHeight.roundToInt())
                )
            }

            drawLine(Color.White, Offset(dividerPx, 0f), Offset(dividerPx, size.height), strokeWidth = 3f)
            drawCircle(Color.White, 12f, Offset(dividerPx, size.height / 2))
        }
        Text(
            stringResource(R.string.crop_preview_before),
            Modifier.align(Alignment.TopStart).padding(12.dp),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.crop_preview_after),
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        } else if (previewFailed) {
            Text(
                stringResource(R.string.crop_preview_failed),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = Color.White,
                fontSize = 14.sp,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
    }
}
