package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRoutingTest {
    @Test
    fun helpGalleryAndReportUseTypedControlDestinations() {
        val help = source("LocalHelp.kt")
        val main = source("MainActivity.kt")
        val gallery = source("GalleryScreen.kt")
        val settings = source("SettingsActivity.kt")

        assertTrue(help.contains("SETTINGS_PROJECT_SIDECARS"))
        assertTrue(help.contains("SETTINGS_SECURE_EDITOR"))
        assertTrue(main.contains("SettingsDestination.PROJECT_SIDECARS"))
        assertTrue(main.contains("SettingsDestination.SECURE_EDITOR"))
        assertTrue(main.contains("SettingsDestination.NETWORK_EXPORTS"))
        assertTrue(main.contains("SettingsDestination.SCREENSHOT_INDEX"))
        assertTrue(gallery.contains("onManageIndex"))
        assertTrue(settings.contains("revealedDestination == SettingsDestination.SCREENSHOT_INDEX"))
        assertTrue(settings.contains("revealedDestination == SettingsDestination.LOCAL_NETWORK"))
        assertTrue(settings.contains("revealedDestination == SettingsDestination.WATERMARK"))
        assertTrue(settings.contains("revealedDestination == SettingsDestination.EXPORT_BORDER"))
    }

    @Test
    fun everySearchDestinationHasAnExactComposeAnchor() {
        val registry = source("SettingsRegistry.kt")
        val settings = source("SettingsActivity.kt")
        val searchUi = source("SettingsSearchUi.kt")
        val destinations = Regex("^    ([A-Z][A-Z0-9_]*)\\(\"", RegexOption.MULTILINE)
            .findAll(registry)
            .map { it.groupValues[1] }
            .toList()

        destinations.forEach { destination ->
            assertTrue("Missing anchor for $destination", settings.contains("SettingsDestination.$destination"))
        }
        assertTrue(searchUi.contains("bringIntoViewRequester"))
        assertTrue(searchUi.contains("settings-anchor-"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
