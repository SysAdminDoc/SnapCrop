package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartEraseEngineTest {
    @Test
    fun eraseInPlace_replacesMaskedStripeWithNearbySafePixels() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        for (y in 0 until 64) {
            for (x in 30..34) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }

        SmartEraseEngine.eraseInPlace(
            bitmap,
            DrawPath(
                points = listOf(PointF(32f, 12f), PointF(32f, 52f)),
                color = Color.TRANSPARENT,
                strokeWidth = 4f,
                shapeType = "smart_erase"
            )
        )

        val center = bitmap.getPixel(32, 32)
        assertTrue(Color.red(center) < 100)
        assertTrue(Color.blue(center) > 150)
    }

    @Test
    fun eraseInPlace_ignoresSinglePointStroke() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)

        SmartEraseEngine.eraseInPlace(
            bitmap,
            DrawPath(
                points = listOf(PointF(8f, 8f)),
                color = Color.TRANSPARENT,
                strokeWidth = 8f,
                shapeType = "smart_erase"
            )
        )

        assertEquals(Color.GREEN, bitmap.getPixel(8, 8))
    }
}
