package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

internal fun createEditorSpaceStraightenedBitmap(bitmap: Bitmap, angleDegrees: Float): Bitmap {
    if (angleDegrees == 0f) return bitmap
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    Canvas(result).apply {
        rotate(angleDegrees, bitmap.width / 2f, bitmap.height / 2f)
        drawBitmap(bitmap, 0f, 0f, paint)
    }
    return result
}
