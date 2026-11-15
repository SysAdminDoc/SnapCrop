package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Issue #3: after a pinch zoom inside Draw mode, strokes landed away from the finger because the
 * gesture handler kept mapping touches through the pre-zoom transform.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class EditorPinchGestureTest {
    @get:Rule
    val compose = createComposeRule()

    private var draftProvider: (() -> EditorDraft)? = null
    private var rootView: android.view.View? = null

    @Test
    fun strokeAfterPinchZoomLandsUnderTheFinger() {
        val canvas = openDrawMode()
        val c = canvasCentre(canvas)

        canvas.performTouchInput {
            pinch(c - Offset(40f, 0f), c - Offset(80f, 0f), c + Offset(40f, 0f), c + Offset(80f, 0f), 400L)
        }
        compose.waitForIdle()
        val before = capture(canvas)
        assertTrue("capture shows the white image at the centre", before.isWhite(c.x.roundToInt(), c.y.roundToInt()))

        val y = c.y + 50f
        val startX = c.x - 70f
        val endX = c.x + 70f
        canvas.performTouchInput { swipe(Offset(startX, y), Offset(endX, y), 400L) }
        compose.waitForIdle()
        val after = capture(canvas)

        assertEquals(1, compose.runOnIdle { draftProvider!!().draws.size })
        val ink = newRedPixels(before, after)
        assertTrue("stroke was drawn", ink.count > 0)
        // Pen ink sits on the swipe line (round caps and stroke width add a few pixels).
        assertTrue("ink top ${ink.top} near swipe y $y", abs(ink.top - y) <= 16f)
        assertTrue("ink bottom ${ink.bottom} near swipe y $y", abs(ink.bottom - y) <= 16f)
        assertTrue("ink starts at ${ink.left}, finger started at $startX", abs(ink.left - startX) <= 20f)
        assertTrue("ink ends at ${ink.right}, finger ended at $endX", abs(ink.right - endX) <= 20f)
    }

    @Test
    fun secondFingerTurnsAStrokeIntoAPinchWithoutDrawing() {
        val canvas = openDrawMode()
        val c = canvasCentre(canvas)

        canvas.performTouchInput {
            down(0, c)
            moveTo(0, c + Offset(30f, 0f)) // past touch slop: the stroke has started
            moveTo(0, c + Offset(60f, 0f))
            down(1, c - Offset(60f, 0f))
            moveTo(0, c + Offset(90f, 0f))
            moveTo(1, c - Offset(90f, 0f))
            moveTo(0, c + Offset(120f, 0f))
            moveTo(1, c - Offset(120f, 0f))
            up(1)
            up(0)
        }

        assertEquals(0, compose.runOnIdle { draftProvider!!().draws.size })

        // The same opening stroke with no second finger still draws.
        canvas.performTouchInput {
            down(0, c)
            moveTo(0, c + Offset(30f, 0f))
            moveTo(0, c + Offset(60f, 0f))
            up(0)
        }
        assertEquals(1, compose.runOnIdle { draftProvider!!().draws.size })
    }

    private fun openDrawMode(): SemanticsNodeInteraction {
        // Portrait, like the screenshots in the report, so the fitted image is centred horizontally.
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        compose.setContent {
            rootView = LocalView.current
            SnapCropTheme {
                CropEditorScreen(
                    bitmap = bitmap,
                    initialCropRect = Rect(0, 0, bitmap.width, bitmap.height),
                    cropMethod = "",
                    onSave = { _, _, _, _, _ -> },
                    onSaveCopy = { _, _, _, _, _ -> },
                    onShare = { _, _, _, _, _ -> },
                    onCopyClipboard = { _, _, _, _, _ -> },
                    onDiscard = {},
                    onDelete = {},
                    onAutoCrop = { Rect(0, 0, bitmap.width, bitmap.height) },
                    onSmartCrop = {},
                    onRemoveBg = { callback -> callback(null) },
                    onResize = { _, _ -> },
                    onRotate = {},
                    onFlipH = {},
                    onFlipV = {},
                    registerStateProvider = { draftProvider = it },
                    replaceOriginalOnSave = false,
                )
            }
        }
        compose.onAllNodes(hasText("Draw") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        return compose.onNodeWithContentDescription("Editor canvas")
    }

    private fun canvasCentre(canvas: SemanticsNodeInteraction): Offset {
        val size = canvas.fetchSemanticsNode().size
        assertTrue("canvas big enough for the gestures: $size", size.width >= 300 && size.height >= 300)
        return Offset(size.width / 2f, size.height / 2f)
    }

    /** Canvas pixels in canvas-local coordinates, rendered by drawing the root view in software. */
    private class Capture(val bitmap: Bitmap, val originX: Int, val originY: Int, val width: Int, val height: Int) {
        fun isRed(x: Int, y: Int): Boolean {
            val c = bitmap.getPixel(originX + x, originY + y)
            return android.graphics.Color.red(c) > 180 && android.graphics.Color.green(c) < 90 && android.graphics.Color.blue(c) < 90
        }

        fun isWhite(x: Int, y: Int): Boolean {
            val c = bitmap.getPixel(originX + x, originY + y)
            return android.graphics.Color.red(c) > 230 && android.graphics.Color.green(c) > 230 && android.graphics.Color.blue(c) > 230
        }
    }

    // captureToImage waits for a frame-commit callback Robolectric never fires, so draw the view directly.
    private fun capture(canvas: SemanticsNodeInteraction): Capture {
        compose.waitForIdle()
        val node = canvas.fetchSemanticsNode()
        val bitmap = compose.runOnIdle {
            val view = rootView!!
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        return Capture(bitmap, node.positionInRoot.x.roundToInt(), node.positionInRoot.y.roundToInt(), node.size.width, node.size.height)
    }

    private class Ink(val count: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun newRedPixels(before: Capture, after: Capture): Ink {
        var count = 0
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = Int.MIN_VALUE; var bottom = Int.MIN_VALUE
        for (y in 0 until after.height) for (x in 0 until after.width) {
            if (after.isRed(x, y) && !before.isRed(x, y)) {
                count++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return Ink(count, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
}
