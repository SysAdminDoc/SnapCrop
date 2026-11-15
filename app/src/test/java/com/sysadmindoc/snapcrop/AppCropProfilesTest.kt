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
    fun visualOnlyMatchStillFindsALogoInsideTheBar() {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(18, 18, 18))
        fillRect(bitmap, Rect(0, 0, 360, 64), Color.WHITE)
        fillRect(bitmap, Rect(0, 736, 360, 800), Color.WHITE)
        fillRect(bitmap, Rect(16, 20, 48, 44), 0xFFFF4500.toInt())

        val result = applyWithoutHints(bitmap)

        assertEquals("profile:Reddit", result.method)
        assertEquals(Rect(0, 56, 360, 744), result.rect)
    }

    /**
     * Seen on a phone: a plain page with a blue grid line down its left edge came up as X/Twitter,
     * and a page with a warm photo at the bottom came up as Reddit, and both lost real content to
     * the trim. Accent color that runs through the band is part of the picture, not app chrome.
     */
    @Test
    fun visualOnlyMatchIgnoresAccentColorRunningThroughTheBand() {
        val line = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        line.eraseColor(Color.rgb(245, 245, 245))
        fillRect(line, Rect(0, 0, 3, 800), Color.rgb(0, 90, 200))
        fillRect(line, Rect(40, 300, 320, 500), Color.rgb(40, 40, 40))
        assertEquals("full", applyWithoutHints(line).method)

        val photo = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.rgb(250, 250, 250))
        fillRect(photo, Rect(40, 120, 320, 300), Color.rgb(40, 40, 40))
        for (y in 560 until 800) {
            val t = (y - 560) / 239f
            fillRect(photo, Rect(0, y, 360, y + 1), Color.rgb((170 + 40 * t).toInt(), 90, (70 - 40 * t).toInt()))
        }
        assertEquals("full", applyWithoutHints(photo).method)

        // A full-width bar in the accent color is a colored toolbar, not the app's logo.
        val bar = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        bar.eraseColor(Color.WHITE)
        fillRect(bar, Rect(0, 0, 360, 80), 0xFFFF4500.toInt())
        fillRect(bar, Rect(20, 100, 340, 720), Color.rgb(51, 51, 51))
        fillRect(bar, Rect(0, 720, 360, 800), Color.rgb(238, 238, 238))
        assertEquals("full", applyWithoutHints(bar).method)
    }

    private fun applyWithoutHints(bitmap: Bitmap) = AppCropProfiles.apply(
        bitmap = bitmap,
        baseResult = AutoCrop.CropResult(Rect(0, 0, bitmap.width, bitmap.height), "full"),
        statusBarPx = 0,
        navBarPx = 0,
        sourceHints = listOf("content://media/external/images/media/7525"),
        enabled = true
    )

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
