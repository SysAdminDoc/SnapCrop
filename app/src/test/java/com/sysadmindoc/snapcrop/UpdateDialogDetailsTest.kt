package com.sysadmindoc.snapcrop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class UpdateDialogDetailsTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Seen on a phone: with a release's full notes, the checksum to compare against the download
     * sat below the fold of the dialog's scrolling box.
     */
    @Test
    fun checksumIsVisibleWithoutScrollingPastLongNotes() {
        val digest = "6b18d9c2c0d2dc9acd499daba3c4ef89ceb628c1ad39ff2770b13ef97ac42d59"
        val notes = (1..40).joinToString("\n") { "- Release note line $it about a fix in this version." }
        compose.setContent {
            SnapCropTheme {
                UpdateDialogDetails(
                    UpdateChecker.UpdateInfo(
                        versionName = "6.91.0",
                        htmlUrl = "https://github.com/SysAdminDoc/SnapCrop/releases/tag/v6.91.0",
                        notes = notes,
                        apkUrl = "https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.91.0/SnapCrop-6.91.0.apk",
                        apkSha256 = digest,
                    )
                )
            }
        }

        compose.onNodeWithText("Downloads the exact versioned APK asset.").assertIsDisplayed()
        compose.onNodeWithText("SHA-256: $digest").assertIsDisplayed()
        compose.onNodeWithText("Release note line 1", substring = true).assertIsDisplayed()
    }
}
