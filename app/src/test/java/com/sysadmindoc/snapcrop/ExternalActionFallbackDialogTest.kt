package com.sysadmindoc.snapcrop

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExternalActionFallbackDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unavailableUrlOffersCopyUrlAndDismiss() {
        compose.setContent {
            SnapCropTheme(darkOverride = false) {
                ExternalActionFallbackDialog(
                    fallback = ExternalActionFallback(
                        ExternalLaunchOutcome.UNAVAILABLE,
                        "https://example.com",
                        ExternalFallbackCopyKind.URL,
                    ),
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("No compatible app").assertIsDisplayed()
        compose.onNodeWithText("Copy URL").assertHasClickAction()
        compose.onNodeWithText("Close").assertHasClickAction()
    }

    @Test
    fun failedValueOffersCopyValueWithoutExposingItInMessage() {
        val sensitiveValue = "person@example.com"
        compose.setContent {
            SnapCropTheme(darkOverride = true) {
                ExternalActionFallbackDialog(
                    fallback = ExternalActionFallback(
                        ExternalLaunchOutcome.FAILED,
                        sensitiveValue,
                        ExternalFallbackCopyKind.VALUE,
                    ),
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Couldn't open the other app").assertIsDisplayed()
        compose.onNodeWithText("Copy value").assertHasClickAction()
        compose.onNodeWithText(sensitiveValue).assertDoesNotExist()
    }
}
