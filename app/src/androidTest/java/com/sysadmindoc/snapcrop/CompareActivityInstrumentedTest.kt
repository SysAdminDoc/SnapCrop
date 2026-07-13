package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CompareActivityInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun boundedPairExercisesAllModesAlignmentRecreationAndAccessibility() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val before = insertImage("compare-before", 120, 200, Color.rgb(24, 30, 42))
        val after = insertImage("compare-after", 120, 212, Color.rgb(24, 30, 42), changed = true)
        try {
            composeRule.enableAccessibilityChecks()
            ActivityScenario.launch<CompareActivity>(
                CompareActivity.intent(context, listOf(before, after))
            ).use { scenario ->
                composeRule.waitUntil(15_000) {
                    composeRule.onAllNodesWithTag("compare-preview").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag("compare-preview").assertIsDisplayed()
                composeRule.onNodeWithTag("compare-mode-swipe").assertIsSelected()

                composeRule.onNodeWithTag("compare-mode-overlay").performClick().assertIsSelected()
                composeRule.onNodeWithTag("compare-opacity-slider").assertIsDisplayed()
                composeRule.onNodeWithTag("compare-mode-blink").performClick().assertIsSelected()
                composeRule.onNodeWithTag("compare-blink-toggle").assertIsDisplayed()
                composeRule.onNodeWithTag("compare-mode-difference").performClick().assertIsSelected()
                composeRule.onNodeWithTag("compare-next-region").assertIsDisplayed()
                composeRule.onNodeWithTag("compare-align-center").performClick().assertIsSelected()
                composeRule.onRoot().tryPerformAccessibilityChecks()
                InstrumentationRegistry.getArguments().getString("compareVisualHoldMs")
                    ?.toLongOrNull()
                    ?.coerceIn(0L, 30_000L)
                    ?.takeIf { it > 0L }
                    ?.let(Thread::sleep)

                scenario.recreate()
                composeRule.waitUntil(15_000) {
                    composeRule.onAllNodesWithTag("compare-preview").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag("compare-mode-difference").assertIsSelected()
                composeRule.onNodeWithTag("compare-align-center").assertIsSelected()
            }
        } finally {
            context.contentResolver.delete(before, null, null)
            context.contentResolver.delete(after, null, null)
        }
    }

    private fun insertImage(
        name: String,
        width: Int,
        height: Int,
        color: Int,
        changed: Boolean = false,
    ): android.net.Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCropTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            if (changed) {
                for (y in 60 until minOf(90, height)) {
                    for (x in 30 until minOf(80, width)) setPixel(x, y, Color.rgb(255, 82, 168))
                }
            }
        }
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } finally {
            bitmap.recycle()
        }
        return uri
    }
}
