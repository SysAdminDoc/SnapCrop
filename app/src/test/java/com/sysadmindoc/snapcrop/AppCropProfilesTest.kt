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
class AppCropProfilesTest {
    @Test
    fun apply_trimsRedditChromeWhenSourceHintMatches() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(18, 18, 18))
        fillRect(bitmap, Rect(0, 0, 360, 64), Color.WHITE)
        fillRect(bitmap, Rect(0, 736, 360, 800), Color.WHITE)
        fillRect(bitmap, Rect(16, 20, 48, 44), 0xFFFF4500.toInt())

        val result = AppCropProfiles.apply(
            bitmap = bitmap,
            baseResult = AutoCrop.CropResult(Rect(0, 0, 360, 800), "full"),
            statusBarPx = 0,
            navBarPx = 0,
            sourceHints = listOf("content://media/com.reddit.frontpage/screenshot.png"),
            enabled = true
        )

        assertEquals("profile:Reddit", result.method)
        assertEquals(Rect(0, 56, 360, 744), result.rect)
    }

    @Test
    fun apply_returnsBaseResultWhenProfilesAreDisabled() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val base = AutoCrop.CropResult(Rect(0, 10, 360, 790), "statusbar")

        val result = AppCropProfiles.apply(
            bitmap = bitmap,
            baseResult = base,
            statusBarPx = 24,
            navBarPx = 48,
            sourceHints = listOf("com.twitter.android"),
            enabled = false
        )

        assertEquals(base, result)
    }

    private fun fillRect(bitmap: Bitmap, rect: Rect, color: Int) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                bitmap.setPixel(x, y, color)
            }
        }
    }
}
