package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSurfaceGuardTest {
    @Test
    fun majorMediaScreensDoNotPairThemeTextWithHardcodedBlackRoots() {
        val roots = listOf(
            "CropActivity.kt",
            "CropEditorScreen.kt",
            "GalleryScreen.kt",
            "StitchActivity.kt",
            "CollageActivity.kt",
            "DeviceFrameActivity.kt",
            "CompareActivity.kt",
            "VideoClipActivity.kt"
        )
        roots.forEach { name ->
            val source = File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
            assertFalse("$name has a hardcoded full-screen black surface", ROOT_BLACK.containsMatchIn(source))
        }
    }

    @Test
    fun launchThemesProvideMatchingLightAndDarkSystemBarIconModes() {
        val light = File("src/main/res/values/themes.xml").readText()
        val dark = File("src/main/res/values-night/themes.xml").readText()
        assertTrue(light.contains("android:windowLightStatusBar\">true"))
        assertTrue(light.contains("android:windowLightNavigationBar\">true"))
        assertTrue(dark.contains("android:windowLightStatusBar\">false"))
        assertTrue(dark.contains("android:windowLightNavigationBar\">false"))
    }

    companion object {
        private val ROOT_BLACK = Regex(
            "fillMaxSize\\(\\)(?:\\s*\\.\\w+\\([^)]*\\))*\\s*\\.background\\(Color\\.Black\\)",
            setOf(RegexOption.MULTILINE)
        )
    }
}
