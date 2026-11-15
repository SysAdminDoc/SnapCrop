package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Issue #4: any of ML Kit's languages can be the translation target, and the pick is remembered. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class OcrLanguagePickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun languageFromTheFullListBecomesTheTargetAndIsRemembered() {
        val bitmap = whiteTestBitmap()
        compose.hostEditor(bitmap, initialOcrBlocks = listOf(TextBlock("Hello there", Rect(0, 0, bitmap.width, bitmap.height))))
        compose.onAllNodes(hasText("OCR") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Editor canvas").performClick()

        val total = TextTranslator.targetLanguages.size
        compose.onNodeWithText("All $total languages").performScrollTo().performClick()
        val languageList = SemanticsMatcher("list of $total rows") {
            it.config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount == total
        }
        compose.onNode(hasScrollToNodeAction() and languageList).performScrollToNode(hasText("Greek"))
        compose.onNode(hasText("Greek") and hasClickAction()).performClick()
        compose.waitForIdle()

        // The picker closed and Greek now leads the chip row, selected.
        compose.onNode(hasText("Greek") and hasClickAction()).assertIsSelected()
        val stored = RuntimeEnvironment.getApplication()
            .getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TextTranslator.PREF_TARGET, null)
        assertEquals("el", stored)
    }
}
