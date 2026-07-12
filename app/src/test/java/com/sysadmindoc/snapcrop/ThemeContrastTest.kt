package com.sysadmindoc.snapcrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sysadmindoc.snapcrop.ui.theme.Black
import com.sysadmindoc.snapcrop.ui.theme.Danger
import com.sysadmindoc.snapcrop.ui.theme.MediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnMediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnMediaSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.OnPrimary
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.Success
import com.sysadmindoc.snapcrop.ui.theme.Surface
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary
import com.sysadmindoc.snapcrop.ui.theme.Warning
import com.sysadmindoc.snapcrop.ui.theme.isDarkTheme
import com.sysadmindoc.snapcrop.ui.theme.resolveDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun darkLightAndSystemPreferencesResolveDeterministically() {
        assertTrue(resolveDarkTheme("dark", systemDark = false))
        assertFalse(resolveDarkTheme("light", systemDark = true))
        assertTrue(resolveDarkTheme("system", systemDark = true))
        assertFalse(resolveDarkTheme("system", systemDark = false))
        assertTrue(resolveDarkTheme(null, systemDark = false))
    }

    @Test
    fun appAndMediaTextTokensMeetNormalTextContrastInBothThemes() {
        listOf(true, false).forEach { dark ->
            isDarkTheme = dark
            val pairs = listOf(
                "body/background" to (OnSurface to Black),
                "body/surface" to (OnSurface to Surface),
                "secondary/surfaceVariant" to (OnSurfaceVariant to SurfaceVariant),
                "primary/background" to (Primary to Black),
                "secondary/background" to (Secondary to Black),
                "tertiary/background" to (Tertiary to Black),
                "warning/background" to (Warning to Black),
                "danger/background" to (Danger to Black),
                "success/background" to (Success to Black),
                "onPrimary/primary" to (OnPrimary to Primary),
                "media/body" to (OnMediaSurface to MediaSurface),
                "media/secondary" to (OnMediaSurfaceVariant to MediaSurface)
            )
            pairs.forEach { (label, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertTrue("theme=${if (dark) "dark" else "light"} $label ratio=$ratio", ratio >= 4.5)
            }
        }
        isDarkTheme = true
    }

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
