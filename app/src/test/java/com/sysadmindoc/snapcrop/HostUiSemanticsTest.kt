package com.sysadmindoc.snapcrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsProperties.Role as RoleProperty
import androidx.compose.ui.semantics.SemanticsProperties.StateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HostUiSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyReferenceExposesLocalizedNameAndState() {
        setReference(HostWorkflow.HOME, HostUiState.EMPTY)

        compose.onNodeWithTag("host-ui-home-empty")
            .assertContentDescriptionEquals("Home, Empty state reference")
            .assert(SemanticsMatcher.expectValue(StateDescription, "Empty state"))
    }

    @Test
    fun errorReferenceExposesNameStateRoleTraversalAndMinimumTarget() {
        setReference(HostWorkflow.SETTINGS, HostUiState.ERROR)

        compose.onNodeWithTag("host-ui-settings-error")
            .assertContentDescriptionEquals("Settings, Error state reference")
            .assert(SemanticsMatcher.expectValue(StateDescription, "Error state"))

        val retry = compose.onNodeWithText("Retry")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(RoleProperty, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        assertEquals(
            1f,
            retry.fetchSemanticsNode().config[SemanticsProperties.TraversalIndex],
        )
    }

    @Test
    fun loadingReferenceExposesProgress() {
        setReference(HostWorkflow.COMPARE, HostUiState.LOADING)
        compose.onNodeWithTag("host-ui-compare-loading")
            .assertContentDescriptionEquals("Compare, Loading state reference")
            .assert(SemanticsMatcher.expectValue(StateDescription, "Loading state"))
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun dialogReferenceExposesOrderedNamedButtons() {
        setReference(HostWorkflow.WEB_CAPTURE, HostUiState.DESTRUCTIVE_DIALOG)
        compose.onNodeWithTag("host-ui-web_capture-destructive_dialog")
            .assertContentDescriptionEquals("Web page capture, Destructive confirmation reference")
            .assert(SemanticsMatcher.expectValue(StateDescription, "Destructive confirmation"))
        val keep = compose.onNodeWithText("Keep")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(RoleProperty, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        val remove = compose.onNodeWithText("Remove")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(RoleProperty, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        assertEquals(1f, keep.fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
        assertEquals(2f, remove.fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
    }

    private fun setReference(workflow: HostWorkflow, state: HostUiState) {
        compose.setContent {
            SnapCropTheme(darkOverride = false) {
                HostUiStateOverlay(workflow = workflow, state = state) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}
