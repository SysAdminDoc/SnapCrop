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

    /**
     * Seen on a phone: the crop started a few rows into the first line of text and cut the tops of
     * the letters off, because rows that are mostly margin passed as margin.
     */
    @Test
    fun detectWithMethod_keepsTheSparseEdgesOfContent() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(250, 250, 250))
        val ink = Color.rgb(20, 20, 20)
        fillRect(bitmap, Rect(40, 110, 320, 126), ink) // body of a line of text
        fillRect(bitmap, Rect(280, 102, 283, 110), ink) // a tall letter rising out of it
        fillRect(bitmap, Rect(100, 96, 103, 99), ink) // dots over two i's, a 3 px gap above the line
        fillRect(bitmap, Rect(190, 96, 193, 99), ink)
        fillRect(bitmap, Rect(30, 300, 330, 700), Color.rgb(40, 90, 220)) // a photo
        fillRect(bitmap, Rect(100, 700, 103, 706), ink) // caption marks hanging under it
        fillRect(bitmap, Rect(190, 709, 193, 711), ink)
        fillRect(bitmap, Rect(24, 500, 27, 503), ink) // small marks just off its sides
        fillRect(bitmap, Rect(333, 590, 336, 593), ink)

        val result = AutoCrop.detectWithMethod(bitmap = bitmap, appProfilesEnabled = false)

        assertEquals("border", result.method)
        assertEquals(Rect(22, 94, 338, 713), result.rect)
    }

    /** Grain in a margin isn't the edge of the content, so it mustn't undo the crop. */
    @Test
    fun detectWithMethod_speckledMarginStillCrops() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(250, 250, 250))
        fillRect(bitmap, Rect(40, 200, 320, 600), Color.rgb(51, 51, 51))
        for (y in (0 until 200) + (600 until 800)) {
            val x = 10 + 18 * (y % 19)
            bitmap.setPixel(x, y, Color.rgb(20, 20, 20))
        }

        val result = AutoCrop.detectWithMethod(bitmap = bitmap, appProfilesEnabled = false)

        assertEquals("border", result.method)
        assertEquals(Rect(38, 198, 322, 602), result.rect)
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
