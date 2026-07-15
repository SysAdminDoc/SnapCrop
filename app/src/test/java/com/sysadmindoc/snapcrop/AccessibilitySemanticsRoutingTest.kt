package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsRoutingTest {
    @Test
    fun compactActionsReserveUnambiguousFortyEightDpTargets() {
        val files = listOf(
            "CropEditorScreen.kt",
            "EditorLayers.kt",
            "RedactionLayers.kt",
            "CollageActivity.kt",
            "StitchActivity.kt",
            "GalleryScreen.kt",
            "MainActivity.kt",
            "SettingsActivity.kt",
        ).map(::source)

        files.forEach { code ->
            assertFalse(code.contains("Modifier.size(36.dp)"))
            assertFalse(code.contains("Modifier.size(44.dp)"))
            assertFalse(code.contains("sizeIn(minWidth = 40.dp"))
        }
    }

    @Test
    fun selectionAndNestedActionsUseSingleSemanticOwners() {
        val layers = source("EditorLayers.kt")
        val redactions = source("RedactionLayers.kt")
        val collage = source("CollageActivity.kt")
        val gallery = source("GalleryScreen.kt")
        val home = source("MainActivity.kt")

        assertTrue(layers.contains(".toggleable("))
        assertTrue(layers.contains("role = Role.Checkbox"))
        assertTrue(redactions.contains(".selectable("))
        assertTrue(redactions.contains("role = Role.RadioButton"))
        assertTrue(collage.contains("if (!occupied) Modifier.clickable"))
        assertTrue(gallery.contains("selected = isSelected || isActive\n                role = Role.Button"))
        assertTrue(gallery.contains("contentDescription = null"))
        assertTrue(home.contains("Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onOpen)"))
    }

    @Test
    fun composeAccessibilityChecksArePartOfTheAndroidTestGate() {
        val catalog = File("../gradle/libs.versions.toml").takeIf(File::isFile)
            ?: File("gradle/libs.versions.toml")
        val dependencies = catalog.readText()
        val build = File("build.gradle.kts").readText()
        val test = File("src/androidTest/java/com/sysadmindoc/snapcrop/AccessibilitySemanticsInstrumentedTest.kt").readText()

        assertTrue(dependencies.contains("ui-test-junit4-accessibility"))
        assertTrue(build.contains("libs.androidx.ui.test.junit4.accessibility"))
        assertTrue(test.contains("enableAccessibilityChecks()"))
        assertTrue(test.contains("tryPerformAccessibilityChecks()"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
