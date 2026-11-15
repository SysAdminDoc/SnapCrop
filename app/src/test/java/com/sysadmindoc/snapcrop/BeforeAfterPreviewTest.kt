package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class BeforeAfterPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Seen on a phone: a tall screenshot cropped to a short band showed the cropped picture again
     * on the BEFORE side, around the narrower original.
     */
    @Test
    fun beforeSideShowsOnlyTheOriginal() {
        // 200x800, blue with a red band; the crop keeps just the band, so the result is all red.
        val original = Bitmap.createBitmap(200, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            for (y in 200 until 400) for (x in 0 until 200) setPixel(x, y, android.graphics.Color.RED)
        }
        var rootView: android.view.View? = null
        compose.setContent {
            rootView = LocalView.current
            Box(Modifier.size(400.dp).background(Color.Black)) {
                BeforeAfterPreview(
                    bitmap = original,
                    imageBitmap = original.asImageBitmap(),
                    cropLeft = 0,
                    cropTop = 200,
                    cropRight = 200,
                    cropBottom = 400,
                    onDismiss = {}
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("AFTER")).fetchSemanticsNodes().isNotEmpty() }

        val shot = compose.runOnIdle {
            val view = rootView!!
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        fun isRed(x: Int, y: Int) = shot.getPixel(x, y).let {
            android.graphics.Color.red(it) > 180 && android.graphics.Color.green(it) < 90 && android.graphics.Color.blue(it) < 90
        }
        fun isBlue(x: Int, y: Int) = shot.getPixel(x, y).let {
            android.graphics.Color.blue(it) > 180 && android.graphics.Color.red(it) < 90 && android.graphics.Color.green(it) < 90
        }

        // The divider starts in the middle (x 200). The original fits the 400 px box at x 150 to 250.
        assertTrue("cropped result on the AFTER side", isRed(300, 200))
        assertTrue("original on the BEFORE side", isBlue(175, 50))
        assertFalse("cropped result showing through beside the original", isRed(75, 200))
    }
}
