package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoCropTest {
    @Test
    fun detectWithMethod_stripsUniformBorderWithPadding() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        fillRect(bitmap, Rect(40, 30, 280, 210), Color.rgb(32, 96, 160))
        fillRect(bitmap, Rect(160, 30, 280, 210), Color.rgb(48, 112, 176))

        val result = AutoCrop.detectWithMethod(
            bitmap = bitmap,
            appProfilesEnabled = false
        )

        assertEquals("border", result.method)
        assertEquals(Rect(38, 28, 282, 212), result.rect)
    }

    @Test
    fun detectWithMethod_prefersSystemBarStripWhenNoBorderExists() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 60, 90))

        val result = AutoCrop.detectWithMethod(
            bitmap = bitmap,
            statusBarPx = 24,
            navBarPx = 48,
            appProfilesEnabled = false
        )

        assertEquals("statusbar", result.method)
        assertEquals(Rect(0, 24, 200, 352), result.rect)
    }

    @Test
    fun detectWithMethod_keepsTinyImagesUncropped() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val result = AutoCrop.detectWithMethod(
            bitmap = bitmap,
            appProfilesEnabled = false
        )

        assertEquals("too_small", result.method)
        assertEquals(Rect(0, 0, 80, 80), result.rect)
    }

    private fun fillRect(bitmap: Bitmap, rect: Rect, color: Int) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                bitmap.setPixel(x, y, color)
            }
        }
    }
}
