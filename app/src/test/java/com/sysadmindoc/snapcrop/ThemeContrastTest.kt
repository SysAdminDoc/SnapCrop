package com.sysadmindoc.snapcrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sysadmindoc.snapcrop.ui.theme.MediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnMediaSurface
import com.sysadmindoc.snapcrop.ui.theme.OnMediaSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.resolveDarkTheme
import com.sysadmindoc.snapcrop.ui.theme.snapCropColors
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
            val c = snapCropColors(dark)
            val pairs = listOf(
                "body/background" to (c.onSurface to c.black),
                "body/surface" to (c.onSurface to c.surface),
                "secondary/surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
                "primary/background" to (c.primary to c.black),
                "secondary/background" to (c.secondary to c.black),
                "tertiary/background" to (c.tertiary to c.black),
                "warning/background" to (c.warning to c.black),
                "danger/background" to (c.danger to c.black),
                "success/background" to (c.success to c.black),
                "onPrimary/primary" to (c.onPrimary to c.primary),
                "media/body" to (OnMediaSurface to MediaSurface),
                "media/secondary" to (OnMediaSurfaceVariant to MediaSurface)
            )
            pairs.forEach { (label, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertTrue("theme=${if (dark) "dark" else "light"} $label ratio=$ratio", ratio >= 4.5)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
