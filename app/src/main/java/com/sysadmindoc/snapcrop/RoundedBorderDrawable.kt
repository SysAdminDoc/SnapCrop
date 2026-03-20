package com.sysadmindoc.snapcrop

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class RoundedBorderDrawable(
    private val cornerRadius: Float,
    private val strokeWidth: Float,
    private val strokeColor: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@RoundedBorderDrawable.strokeWidth
        color = strokeColor
    }

    override fun draw(canvas: Canvas) {
        val half = strokeWidth / 2
        val rect = RectF(half, half, bounds.width() - half, bounds.height() - half)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
