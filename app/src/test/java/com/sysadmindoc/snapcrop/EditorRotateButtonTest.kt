package com.sysadmindoc.snapcrop

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #4: rotating took the overflow menu every quarter turn; crop mode now has a button. */
@RunWith(RobolectricTestRunner::class)
class EditorRotateButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
    fun phoneCropModeRotatesOnEveryTap() = assertRotateButtonTurnsTheImage(inScrollingPanel = false)

    @Test
    @Config(sdk = [35], qualifiers = "w1000dp-h700dp-mdpi")
    fun wideCropPanelRotatesOnEveryTap() = assertRotateButtonTurnsTheImage(inScrollingPanel = true)

    private fun assertRotateButtonTurnsTheImage(inScrollingPanel: Boolean) {
        var turns = 0
        compose.hostEditor(onRotate = { turns++ })
        val button = compose.onNode(hasContentDescription("Rotate") and hasClickAction())
        if (inScrollingPanel) button.performScrollTo()
        button.performClick()
        button.performClick()
        compose.runOnIdle { assertEquals(2, turns) }
    }
}
