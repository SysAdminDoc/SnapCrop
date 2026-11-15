package com.sysadmindoc.snapcrop

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Issue #2: pick a word or two out of an OCR block instead of always taking the whole block. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class OcrSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun copyUsesTheSelectedWords() {
        val field = openOcrDialog()
        field.performTextInputSelection(TextRange(5, 9))
        compose.onNodeWithText("Copy and Translate use “Dana”").assertExists()

        copyButton().performClick()

        assertEquals("Dana", clipboardText())
    }

    @Test
    fun copyTakesTheWholeBlockWithoutASelection() {
        openOcrDialog()
        compose.onNodeWithText("Long-press a word to select it", substring = true).assertExists()

        copyButton().performClick()

        assertEquals(BLOCK, clipboardText())
    }

    @Test
    fun selectionHelpersTrimAndCap() {
        assertEquals("Dana", ocrSelectionText(" Dana at", TextRange(0, 5)))
        assertEquals("Dana", ocrSelectionText("Call Dana", TextRange(9, 5)))
        assertNull(ocrSelectionText("Call Dana", TextRange(4, 5)))
        assertNull(ocrSelectionText("Call Dana", TextRange(3)))
        assertEquals("two lines", ocrSelectionPreview("two\n  lines"))
        val long = ocrSelectionPreview("word ".repeat(30))
        assertEquals(60, long.length)
        assertEquals(Char(0x2026), long.last())
    }

    private fun openOcrDialog(): SemanticsNodeInteraction {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        compose.setContent {
            SnapCropTheme {
                CropEditorScreen(
                    bitmap = bitmap,
                    initialCropRect = Rect(0, 0, bitmap.width, bitmap.height),
                    cropMethod = "",
                    initialOcrBlocks = listOf(TextBlock(BLOCK, Rect(0, 0, bitmap.width, bitmap.height))),
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
                    replaceOriginalOnSave = false,
                )
            }
        }
        compose.onAllNodes(hasText("OCR") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Editor canvas").performClick()
        compose.waitForIdle()
        return compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection))
    }

    private fun copyButton() = compose.onNode(hasText("Copy") and hasClickAction())

    private fun clipboardText(): String? = compose.runOnIdle {
        val cm = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private companion object {
        const val BLOCK = "Call Dana at the office"
    }
}
