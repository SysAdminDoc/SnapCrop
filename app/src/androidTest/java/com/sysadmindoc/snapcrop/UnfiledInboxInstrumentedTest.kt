package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class UnfiledInboxInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var mediaUri: Uri? = null
    private val displayName = "Screenshot_unfiled_${System.nanoTime()}.png"

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        composeRule.enableAccessibilityChecks()
        mediaUri = insertScreenshot()
    }

    @After
    fun tearDown() {
        mediaUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
    }

    @Test
    fun keepActionClearsInboxWithoutMovingMedia() {
        composeRule.setContent {
            SnapCropTheme {
                GalleryScreen(
                    onOpenEditor = {},
                    onPlayVideo = {},
                    onShareUris = {},
                    onDeleteUris = {},
                    onExportPdf = {},
                    onBatchResize = {},
                    onBatchRename = {},
                    onBack = {},
                    imageAccess = MediaAccess.FULL,
                    videoAccess = MediaAccess.NONE,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onNodeWithTag("gallery-unfiled-album").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("gallery-unfiled-album").performClick()
        val photoDescription = "$displayName, screenshot"
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onNodeWithContentDescription(photoDescription).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithContentDescription(photoDescription).performTouchInput { longClick() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.gallery_unfiled_keep)).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { screenshot ->
            File(context.getExternalFilesDir(null), "unfiled-inbox-qa.png").outputStream().use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            screenshot.recycle()
        }
        composeRule.onNodeWithText(context.getString(R.string.gallery_unfiled_keep))
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onNodeWithContentDescription(photoDescription).fetchSemanticsNode() }.isFailure
        }
        composeRule.onRoot().tryPerformAccessibilityChecks()
        checkNotNull(mediaUri).let { uri ->
            checkNotNull(context.contentResolver.openInputStream(uri)).close()
        }
    }

    private fun insertScreenshot(): Uri {
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCropUnfiledTest/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        resolver.openOutputStream(uri)?.use { output ->
            Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.rgb(42, 86, 132))
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                bitmap.recycle()
            }
        } ?: error("Could not create Unfiled fixture")
        check(
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            ) == 1,
        )
        return uri
    }
}
