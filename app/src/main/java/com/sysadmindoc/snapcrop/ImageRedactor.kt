package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

object ImageRedactor {
    fun pixelate(bitmap: Bitmap, rects: List<Rect>): Bitmap {
        if (rects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val blockSize = 12

        for (rect in rects) {
            val left = rect.left.coerceIn(0, result.width)
            val top = rect.top.coerceIn(0, result.height)
            val right = rect.right.coerceIn(0, result.width)
            val bottom = rect.bottom.coerceIn(0, result.height)
            if (right - left < 2 || bottom - top < 2) continue

            var region: Bitmap? = null
            var tiny: Bitmap? = null
            var mosaic: Bitmap? = null
            try {
                region = Bitmap.createBitmap(result, left, top, right - left, bottom - top)
                tiny = Bitmap.createScaledBitmap(
                    region,
                    ((right - left) / blockSize).coerceAtLeast(1),
                    ((bottom - top) / blockSize).coerceAtLeast(1),
                    false
                )
                mosaic = Bitmap.createScaledBitmap(tiny, right - left, bottom - top, false)
                canvas.drawBitmap(mosaic, left.toFloat(), top.toFloat(), null)
            } finally {
                region?.recycle()
                tiny?.recycle()
                mosaic?.recycle()
            }
        }

        return result
    }
}
