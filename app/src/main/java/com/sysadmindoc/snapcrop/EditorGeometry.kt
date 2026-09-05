package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

internal fun createEditorSpaceStraightenedBitmap(bitmap: Bitmap, angleDegrees: Float): Bitmap {
    if (angleDegrees == 0f) return bitmap
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    Canvas(result).apply {
        rotate(angleDegrees, bitmap.width / 2f, bitmap.height / 2f)
        drawBitmap(bitmap, 0f, 0f, paint)
    }
    // A Canvas draw never produces a gain map, so an Ultra HDR source would lose its HDR here.
    // The renderer reads the gain map *from* this result for every later stage, so failing to
    // reattach it once drops HDR for the whole pipeline rather than just the rotation step.
    preserveUltraHdrGainmap(
        bitmap,
        result,
        Matrix().apply { setRotate(angleDegrees, bitmap.width / 2f, bitmap.height / 2f) },
    )
    return result
}
