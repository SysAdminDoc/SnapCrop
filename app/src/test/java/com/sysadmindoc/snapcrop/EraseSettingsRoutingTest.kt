package com.sysadmindoc.snapcrop

import android.content.Context
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EraseSettingsRoutingTest {
    @Test
    fun currentBuildShowsStatusWithoutAnExperimentalControlOrSearchRoute() {
        val settings = source("SettingsActivity.kt")
        val registry = source("SettingsRegistry.kt")

        assertTrue(settings.contains("R.string.settings_erase_status"))
        assertFalse(settings.contains("allowAdvancedErase"))
        assertFalse(settings.contains("PREF_ALLOW_EXPERIMENTAL"))
        assertFalse(settings.contains("AdvancedEraseBackendRegistry.candidates"))
        assertFalse(registry.contains("SMART_ERASE"))
        assertFalse(SettingsDestination.entries.any { it.wireValue == "smart_erase" })
        assertFalse(SettingsRegistry.entries(RuntimeEnvironment.getApplication()).any {
            it.searchableText.contains("model pack") || it.searchableText.contains("inpaint")
        })
    }

    @Test
    fun retiredNoOpPreferencesAreRemovedAndCannotBeRestored() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("advanced_erase_allow_experimental", true)
            .putString("advanced_erase_selected_backend", "research_model_pack")
            .commit()

        SettingsPreferenceSchema.migrateLivePreferences(prefs)

        assertFalse(prefs.contains("advanced_erase_allow_experimental"))
        assertFalse(prefs.contains("advanced_erase_selected_backend"))
        assertNull(SettingsPreferenceSchema.resolve("advanced_erase_allow_experimental"))
        assertNull(SettingsPreferenceSchema.resolve("advanced_erase_selected_backend"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/sysadmindoc/snapcrop/$name").readText()
}
