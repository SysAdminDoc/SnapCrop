package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #4 follow-up: the Rotate button turned the picture but left a redaction over the spot the
 * private detail used to be, while the panel still counted it as enabled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorGeometryFollowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
    fun redactionsDrawingsAndCropFollowRotateThenFlip() {
        val shown = mutableStateOf(whiteTestBitmap(400, 800))
        val crop = mutableStateOf(Rect(0, 0, 400, 800))
        var draft: (() -> EditorDraft)? = null
        val link = RedactionRegion("link", Rect(20, 600, 300, 640), setOf(RedactionCategory.MANUAL), RedactionSource.MANUAL, RedactionStyle.SOLID)
        val box = DrawPath(points = listOf(PointF(10f, 10f), PointF(100f, 50f)), color = Color.RED, strokeWidth = 4f, shapeType = "rect")
        // The activity drops the scan when it swaps the bitmap.
        val scan = mutableStateOf(listOf(TextBlock("visit example.com", Rect(20, 600, 300, 640))))

        compose.hostEditor(
            shownBitmap = shown,
            cropRect = crop,
            initialRedactions = listOf(link),
            initialDrawPaths = listOf(box),
            initialOcrBlocksState = scan,
            onRotate = { mapped ->
                shown.value = transformed(shown.value, Matrix().apply { postRotate(90f) })
                crop.value = mapped
                scan.value = emptyList()
            },
            onFlipV = { mapped ->
                shown.value = transformed(shown.value, Matrix().apply { preScale(1f, -1f) })
                crop.value = mapped
            },
            registerStateProvider = { draft = it },
        )
        compose.runOnIdle { assertEquals(1, draft!!().ocrBlocks.size) }

        compose.onNode(hasContentDescription("Rotate") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertNotNull(draft)
            val state = draft!!()
            assertEquals(Rect(0, 0, 800, 400), state.crop)
            assertEquals(Rect(160, 20, 200, 300), state.redactions.single().bounds)
            assertEquals(listOf(PointF(790f, 10f), PointF(750f, 100f)), state.draws.single().points)
            // The draft provider is registered once; it has to see the scan the swap left behind.
            assertEquals(emptyList<TextBlock>(), state.ocrBlocks)
            assertEquals(false, state.ocrReviewed)
        }

        compose.onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        compose.onNode(hasText("Flip V") and hasClickAction()).performClick()
        compose.runOnIdle {
            val state = draft!!()
            assertEquals(Rect(0, 0, 800, 400), state.crop)
            assertEquals(Rect(160, 100, 200, 380), state.redactions.single().bounds)
            assertEquals(listOf(PointF(790f, 390f), PointF(750f, 300f)), state.draws.single().points)
        }
    }

    private fun transformed(source: Bitmap, matrix: Matrix): Bitmap =
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
