package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

object SmartReframeEngine {
    suspend fun reframe(bitmap: Bitmap, targetRatio: Float): Rect {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 || targetRatio <= 0f) {
            return Rect(0, 0, bitmap.width.coerceAtLeast(0), bitmap.height.coerceAtLeast(0))
        }

        return withTimeoutOrNull(6500L) {
            val focusRects = mutableListOf<Rect>()
            val objectRect = SmartCropEngine.detect(bitmap)
            if (!objectRect.isFullImage(bitmap)) focusRects.add(objectRect)
            focusRects.addAll(TextExtractor.extract(bitmap).map { it.bounds })
            focusRects.addAll(FaceDetector.detect(bitmap))

            val focus = if (focusRects.isEmpty()) {
                Rect(bitmap.width / 3, bitmap.height / 3, bitmap.width * 2 / 3, bitmap.height * 2 / 3)
            } else {
                focusRects.union().padded(bitmap.width, bitmap.height)
            }
            largestAspectCrop(bitmap.width, bitmap.height, targetRatio, focus.centerX(), focus.centerY())
        } ?: centerCrop(bitmap.width, bitmap.height, targetRatio)
    }

    private fun largestAspectCrop(width: Int, height: Int, ratio: Float, centerX: Int, centerY: Int): Rect {
        var cropW = width
        var cropH = (cropW / ratio).roundToInt()
        if (cropH > height) {
            cropH = height
            cropW = (cropH * ratio).roundToInt()
        }
        cropW = cropW.coerceIn(50.coerceAtMost(width), width)
        cropH = cropH.coerceIn(50.coerceAtMost(height), height)

        val left = (centerX - cropW / 2).coerceIn(0, width - cropW)
        val top = (centerY - cropH / 2).coerceIn(0, height - cropH)
        return Rect(left, top, left + cropW, top + cropH)
    }

    private fun centerCrop(width: Int, height: Int, ratio: Float): Rect =
        largestAspectCrop(width, height, ratio, width / 2, height / 2)

    private fun List<Rect>.union(): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = 0
        var bottom = 0
        for (rect in this) {
            left = minOf(left, rect.left)
            top = minOf(top, rect.top)
            right = maxOf(right, rect.right)
            bottom = maxOf(bottom, rect.bottom)
        }
        return Rect(left, top, right, bottom)
    }

    private fun Rect.padded(maxWidth: Int, maxHeight: Int): Rect {
        val padX = (maxWidth * 0.06f).roundToInt().coerceAtLeast(12)
        val padY = (maxHeight * 0.06f).roundToInt().coerceAtLeast(12)
        return Rect(
            (left - padX).coerceIn(0, maxWidth),
            (top - padY).coerceIn(0, maxHeight),
            (right + padX).coerceIn(0, maxWidth),
            (bottom + padY).coerceIn(0, maxHeight)
        )
    }

    private fun Rect.isFullImage(bitmap: Bitmap): Boolean =
        left <= 0 && top <= 0 && right >= bitmap.width && bottom >= bitmap.height
}
