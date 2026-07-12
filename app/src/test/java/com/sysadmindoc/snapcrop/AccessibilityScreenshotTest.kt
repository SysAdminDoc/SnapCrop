package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityScreenshotTest {
    @Test
    fun activeApplicationWinsOverHigherAccessibilityOverlay() {
        val candidates = listOf(
            AccessibilityWindowCandidate(90, AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, active = true, focused = false, layer = 10),
            AccessibilityWindowCandidate(42, AccessibilityWindowInfo.TYPE_APPLICATION, active = true, focused = true, layer = 5)
        )

        assertEquals(42, AccessibilityScreenshotPolicy.selectActiveWindowId(candidates))
    }

    @Test
    fun systemUiDoesNotPermitFocusedApplicationFallback() {
        val candidates = listOf(
            AccessibilityWindowCandidate(7, AccessibilityWindowInfo.TYPE_SYSTEM, active = true, focused = false, layer = 9),
            AccessibilityWindowCandidate(8, AccessibilityWindowInfo.TYPE_APPLICATION, active = false, focused = true, layer = 4)
        )

        assertEquals(null, AccessibilityScreenshotPolicy.selectActiveWindowId(candidates))
    }

    @Test
    fun screenshotErrorsMapToDistinctRecoveryReasons() {
        assertEquals(AccessibilityScreenshotFailure.SECURE_WINDOW, AccessibilityScreenshotPolicy.failureFromErrorCode(AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW))
        assertEquals(AccessibilityScreenshotFailure.INVALID_WINDOW, AccessibilityScreenshotPolicy.failureFromErrorCode(AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW))
        assertEquals(AccessibilityScreenshotFailure.THROTTLED, AccessibilityScreenshotPolicy.failureFromErrorCode(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT))
        assertEquals(AccessibilityScreenshotFailure.ACCESS_REVOKED, AccessibilityScreenshotPolicy.failureFromErrorCode(AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS))
        assertEquals(AccessibilityScreenshotFailure.INTERNAL, AccessibilityScreenshotPolicy.failureFromErrorCode(999))
    }

    @Test
    fun onlyDisplayFallbackNeedsSystemBarCropping() {
        assertTrue(AccessibilityScreenshotPolicy.shouldCropSystemInsets(windowScoped = false))
        assertFalse(AccessibilityScreenshotPolicy.shouldCropSystemInsets(windowScoped = true))
    }

    @Test
    fun tapCoordinatesAreTranslatedIntoWindowSpace() {
        val fractions = AccessibilityScreenshotPolicy.tapFractions(
            clickX = 500,
            clickY = 800,
            bitmapWidth = 800,
            bitmapHeight = 1200,
            windowBounds = AccessibilityWindowBounds(100, 200, 900, 1400)
        )

        assertEquals(0.5f, fractions.first, 0.0001f)
        assertEquals(0.5f, fractions.second, 0.0001f)
    }

    @Test
    fun windowScopedFixtureEliminatesDisplayOverlayContamination() {
        val expected = 0x00ff00
        val display = Array(12) { IntArray(8) { expected } }
        for (x in display[0].indices) {
            display[0][x] = 0xff0000
            display[display.lastIndex][x] = 0xff0000
        }
        for (y in 3..5) display[y][7] = 0xff00ff
        val window = Array(10) { IntArray(8) { expected } }

        val displayContamination = countPixelsOutside(display, expected)
        val windowContamination = countPixelsOutside(window, expected)

        assertTrue(displayContamination > 0)
        assertEquals(0, windowContamination)
        assertTrue(windowContamination <= displayContamination)
    }

    private fun countPixelsOutside(pixels: Array<IntArray>, expected: Int): Int =
        pixels.sumOf { row -> row.count { it != expected } }
}
