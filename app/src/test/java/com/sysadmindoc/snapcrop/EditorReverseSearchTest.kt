package com.sysadmindoc.snapcrop

import android.graphics.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #4: "Search image" in the editor's menu asks which engine, then searches the current crop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class EditorReverseSearchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pickingAnEngineSearchesTheCurrentCrop() {
        val picks = mutableListOf<Pair<ReverseSearchEngine, Rect>>()
        compose.hostEditor(
            bitmap = whiteTestBitmap(400, 800),
            reverseSearchEngines = ReverseSearchEngine.entries,
            onReverseSearch = { engine, rect -> picks += engine to Rect(rect) },
        )
        compose.onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        compose.onNode(hasText("Search image") and hasClickAction()).performClick()
        compose.onNode(hasText("Search the web for this image")).assertExists()
        compose.onNode(hasText("Opens in the Google app")).assertExists()
        compose.onNode(hasText("SnapCrop sends the image to Yandex and opens the results in your browser")).assertExists()

        compose.onNode(hasText("Bing") and hasClickAction()).performClick()

        compose.runOnIdle {
            assertEquals(listOf(ReverseSearchEngine.BING to Rect(0, 0, 400, 800)), picks)
        }
        compose.onAllNodes(hasText("Search the web for this image")).assertCountEquals(0)
    }

    @Test
    fun menuHasNoSearchWhenNoEngineIsAvailable() {
        compose.hostEditor(reverseSearchEngines = emptyList())
        compose.onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        compose.onNode(hasText("Flip V") and hasClickAction()).assertExists()
        compose.onAllNodes(hasText("Search image")).assertCountEquals(0)
    }
}
