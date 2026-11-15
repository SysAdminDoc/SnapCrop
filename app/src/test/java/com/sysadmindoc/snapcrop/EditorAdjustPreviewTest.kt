package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** Issue #4: in Adjust the photo stayed out of view and several adjustments only showed after saving. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class EditorAdjustPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private var rootView: android.view.View? = null

    private fun openAdjust(bitmap: Bitmap) {
        compose.hostEditor(bitmap, onRootView = { rootView = it })
        compose.onAllNodes(hasText("Adjust") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun photoKeepsMostOfThePhoneScreenWhileAdjusting() {
        openAdjust(whiteTestBitmap())
        val canvas = compose.onNodeWithContentDescription("Editor canvas").fetchSemanticsNode()
        // 900 px tall at mdpi; the unbounded slider stack left the canvas almost nothing.
        assertTrue("canvas height ${canvas.size.height}", canvas.size.height >= 900 * 0.35f)
        // Shortening the panel must not push Auto and Reset out of reach. The crop bar has its own
        // Auto/Reset pair, so check every pair: Adjust's and the crop bar's.
        for ((label, partner) in listOf("Auto" to "Reset", "Reset" to "Auto")) {
            val pair = hasText(label) and hasClickAction() and hasAnySibling(hasText(partner))
            val count = compose.onAllNodes(pair).fetchSemanticsNodes().size
            assertEquals("$label buttons", 2, count)
            repeat(count) { compose.onAllNodes(pair)[it].assertIsDisplayed() }
        }
    }

    @Test
    fun pixelFilterShowsOnTheCanvasBeforeSaving() {
        val blue = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.BLUE) }
        openAdjust(blue)
        val before = canvasCenterColor()
        assertTrue("starts blue: ${hex(before)}", isBlue(before))

        // Red pop keeps reds and turns everything else gray; it has no color matrix to preview with.
        compose.onNode(hasText("Red") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { isGray(canvasCenterColor()) }
    }

    private fun canvasCenterColor(): Int {
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription("Editor canvas").fetchSemanticsNode()
        val frame = compose.runOnIdle {
            val view = rootView!!
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                .also { view.draw(android.graphics.Canvas(it)) }
        }
        val x = (node.positionInRoot.x + node.size.width / 2f).roundToInt()
        val y = (node.positionInRoot.y + node.size.height / 2f).roundToInt()
        return frame.getPixel(x, y).also { frame.recycle() }
    }

    private fun isBlue(c: Int) = android.graphics.Color.blue(c) > 200 && android.graphics.Color.red(c) < 60

    private fun isGray(c: Int): Boolean {
        val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
        return maxOf(r, g, b) - minOf(r, g, b) < 12
    }

    private fun hex(c: Int) = "#%08X".format(c)
}
