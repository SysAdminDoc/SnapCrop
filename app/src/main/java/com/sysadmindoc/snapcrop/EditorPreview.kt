package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun BeforeAfterPreview(
    bitmap: Bitmap,
    imageBitmap: ImageBitmap,
    cropLeft: Int,
    cropTop: Int,
    cropRight: Int,
    cropBottom: Int,
    onDismiss: () -> Unit
) {
    val cropW = cropRight - cropLeft
    val cropH = cropBottom - cropTop
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
    var dividerX by remember { mutableFloatStateOf(0.5f) }

    Box(modifier = Modifier.fillMaxSize()) {
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
    }
}
