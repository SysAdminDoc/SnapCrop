package com.sysadmindoc.snapcrop

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.runtime.mutableIntStateOf
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireAccessibilityFramework() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        composeRule.enableAccessibilityChecks()
    }

    @Test
    fun homePermissionAndGalleryEmptyStatesPassAccessibilityChecks() {
        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_gallery)).performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}

@OptIn(ExperimentalTestApi::class)
class SettingsAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun settingsVisibleControlsPassAccessibilityChecks() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}

@OptIn(ExperimentalTestApi::class)
class GalleryAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun requireAccessibilityFramework() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        composeRule.enableAccessibilityChecks()
    }

    @Test
    fun permissionAndFilterStatesPassAccessibilityChecks() {
        val surface = mutableIntStateOf(0)
        composeRule.setContent {
            SnapCropTheme {
                if (surface.intValue == 0) {
                    GalleryScreen(
                        onOpenEditor = {},
                        onPlayVideo = {},
                        onShareUris = {},
                        onDeleteUris = {},
                        onExportPdf = {},
                        onBatchResize = {},
                        onBatchRename = {},
                        onBack = {},
                        imageAccess = MediaAccess.NONE,
                        videoAccess = MediaAccess.NONE,
                        imagePermissionRecovery = PermissionRecoveryState.SETTINGS_REQUIRED,
                        videoPermissionRecovery = PermissionRecoveryState.SETTINGS_REQUIRED,
                    )
                } else {
                    GalleryFilterDialog(
                        state = GalleryFilterState(),
                        sourceOptions = emptyList(),
                        categoryOptions = emptyList(),
                        formatOptions = GalleryFormat.entries,
                        indexEnabled = false,
                        resultCount = 0,
                        eligibleCollectionCount = 0,
                        skippedCollectionCount = 0,
                        onChange = {},
                        onSeedCollection = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.runOnIdle { surface.intValue = 1 }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun viewerErrorAndDestructiveDialogPassAccessibilityChecks() {
        val sample = Photo(
            id = 0,
            uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "0"),
            dateAdded = 0,
            name = "Accessibility sample",
            width = 96,
            height = 160,
            mimeType = "image/png",
        )
        val surface = mutableIntStateOf(0)
        composeRule.setContent {
            SnapCropTheme {
                when (surface.intValue) {
                    0 -> PhotoViewer(
                        photos = listOf(sample),
                        initialIndex = 0,
                        onCurrentPhotoChanged = {},
                        onClose = {},
                        onEdit = {},
                        onShare = {},
                        onEditSource = {},
                        onEditNote = {},
                        onRequestOverlayForPin = {},
                        onOpenSource = {},
                        onDelete = {},
                        onToggleFavorite = { false },
                    )
                    1 -> GalleryDeleteDialog(itemCount = 1, onConfirm = {}, onDismiss = {})
                    else -> ProjectLoadErrorPanel(
                        message = "The linked source is unavailable.",
                        canRelink = true,
                        onRelink = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.runOnIdle { surface.intValue = 1 }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.runOnIdle { surface.intValue = 2 }
        composeRule.waitForIdle()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}

@OptIn(ExperimentalTestApi::class)
class EditorAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var bitmap: Bitmap

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        bitmap = Bitmap.createBitmap(96, 160, Bitmap.Config.ARGB_8888)
        composeRule.enableAccessibilityChecks()
    }

    @After
    fun tearDown() {
        if (::bitmap.isInitialized && !bitmap.isRecycled) bitmap.recycle()
    }

    @Test
    fun compactEditorPassesAccessibilityChecks() {
        setEditorContent()
        composeRule.onRoot().tryPerformAccessibilityChecks()
        openComposedPreviewAndCheck()
    }

    @Test
    fun wideEditorPassesAccessibilityChecks() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val configuration = composeRule.activity.resources.configuration
        assumeTrue(
            editorLayoutClass(
                configuration.screenWidthDp.toFloat(),
                configuration.screenHeightDp.toFloat(),
            ) == EditorLayoutClass.Wide
        )
        setEditorContent()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.editor_inspector)).fetchSemanticsNode()
        composeRule.onRoot().tryPerformAccessibilityChecks()
        openComposedPreviewAndCheck()
    }

    private fun openComposedPreviewAndCheck() {
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.crop_preview)
        ).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(
                    composeRule.activity.getString(R.string.crop_preview_after)
                ).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    private fun setEditorContent() {
        composeRule.setContent {
            SnapCropTheme {
                CropEditorScreen(
                    bitmap = bitmap,
                    initialCropRect = Rect(0, 0, bitmap.width, bitmap.height),
                    cropMethod = "Accessibility test",
                    onSave = { _, _, _, _, _ -> },
                    onSaveCopy = { _, _, _, _, _ -> },
                    onShare = { _, _, _, _, _ -> },
                    onCopyClipboard = { _, _, _, _, _ -> },
                    onDiscard = {},
                    onDelete = {},
                    onAutoCrop = { Rect(0, 0, bitmap.width, bitmap.height) },
                    onSmartCrop = {},
                    onRemoveBg = { callback -> callback(null) },
                    onResize = {},
                    onRotate = {},
                    onFlipH = {},
                    onFlipV = {},
                    replaceOriginalOnSave = false,
                )
            }
        }
        composeRule.waitForIdle()
    }
}
