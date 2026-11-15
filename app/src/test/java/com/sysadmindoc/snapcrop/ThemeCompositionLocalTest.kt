package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.DarkSnapCropColors
import com.sysadmindoc.snapcrop.ui.theme.LightSnapCropColors
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
class ThemeCompositionLocalTest {
    @get:Rule
    val compose = createComposeRule()

    private val prefs by lazy {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
    }

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    private class Seen {
        var token: Color = Color.Unspecified
        var material: Color = Color.Unspecified
        var primary: Color = Color.Unspecified
    }

    @Composable
    private fun Record(into: Seen) {
        into.token = Black
        into.material = MaterialTheme.colorScheme.background
        into.primary = Primary
    }

    /** A global palette made the last theme composed win for both subtrees. */
    @Test
    fun darkAndLightSubtreesInOneCompositionEachReadTheirOwnPalette() {
        val dark = Seen()
        val light = Seen()
        compose.setContent {
            Row {
                SnapCropTheme(darkOverride = true) { Record(dark) }
                SnapCropTheme(darkOverride = false) { Record(light) }
            }
        }
        compose.waitForIdle()

        assertNotEquals(DarkSnapCropColors.black, LightSnapCropColors.black)
        assertEquals(DarkSnapCropColors.black, dark.token)
        assertEquals(DarkSnapCropColors.black, dark.material)
        assertEquals(DarkSnapCropColors.primary, dark.primary)
        assertEquals(LightSnapCropColors.black, light.token)
        assertEquals(LightSnapCropColors.black, light.material)
        assertEquals(LightSnapCropColors.primary, light.primary)
    }

    @Test
    fun choosingAThemeInSettingsRethemesAScreenThatIsAlreadyOpen() {
        prefs.edit().putString("theme", "dark").commit()
        val seen = Seen()
        compose.setContent { SnapCropTheme { Record(seen) } }
        compose.waitForIdle()
        assertEquals(DarkSnapCropColors.black, seen.token)

        compose.runOnIdle { prefs.edit().putString("theme", "light").commit() }
        compose.waitForIdle()
        assertEquals(LightSnapCropColors.black, seen.token)
        assertEquals(LightSnapCropColors.black, seen.material)

        compose.runOnIdle { prefs.edit().putString("theme", "dark").commit() }
        compose.waitForIdle()
        assertEquals(DarkSnapCropColors.black, seen.token)
    }

    /** On API 30+ clear() reports a null key, and the theme falls back to its dark default. */
    @Test
    fun clearingPreferencesReturnsAnOpenScreenToTheDefaultTheme() {
        prefs.edit().putString("theme", "light").commit()
        val seen = Seen()
        compose.setContent { SnapCropTheme { Record(seen) } }
        compose.waitForIdle()
        assertEquals(LightSnapCropColors.black, seen.token)

        compose.runOnIdle { prefs.edit().clear().commit() }
        compose.waitForIdle()
        assertEquals(DarkSnapCropColors.black, seen.token)
    }

    @Test
    fun aSavedLightPreferenceIsUsedOnFirstComposition() {
        prefs.edit().putString("theme", "light").commit()
        val seen = Seen()
        compose.setContent { SnapCropTheme { Record(seen) } }
        compose.waitForIdle()
        assertEquals(LightSnapCropColors.black, seen.token)
        assertEquals(LightSnapCropColors.black, seen.material)
    }
}
