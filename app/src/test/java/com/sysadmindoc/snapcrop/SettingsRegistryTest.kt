package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsRegistryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)

    @After
    fun clearPreferences() {
        prefs.edit().clear().commit()
    }

    @Test
    fun everyControlHasOneStableSearchEntry() {
        val destinations = SettingsDestination.entries
        val entries = SettingsRegistry.entries(context)

        assertEquals(destinations.size, destinations.map(SettingsDestination::wireValue).toSet().size)
        assertEquals(destinations.toSet(), entries.map(SettingsSearchEntry::destination).toSet())
        assertEquals(destinations.size, entries.size)
        assertTrue(destinations.all { it.wireValue.matches(Regex("[a-z0-9_]+")) })
        assertTrue(entries.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test
    fun intentRoundTripsOnlyAllowlistedDestinationIds() {
        SettingsDestination.entries.forEach { destination ->
            assertEquals(destination, SettingsRegistry.destination(SettingsRegistry.intent(context, destination)))
        }
        assertNull(SettingsRegistry.destination(android.content.Intent()))
        assertNull(
            SettingsRegistry.destination(
                android.content.Intent().putExtra(SettingsRegistry.EXTRA_DESTINATION, "endpoint=https://secret.invalid")
            )
        )
    }

    @Test
    fun searchIsCaseAccentAndTokenInsensitiveButRequiresEveryToken() {
        val entries = SettingsRegistry.entries(context)

        assertEquals(
            listOf(SettingsDestination.PROJECT_SIDECARS),
            SettingsRegistry.search(entries, "PROJECT reversible").map(SettingsSearchEntry::destination),
        )
        assertEquals(
            SettingsRegistry.search(entries, "metadata"),
            SettingsRegistry.search(entries, "MÉTADATA"),
        )
        assertTrue(SettingsRegistry.search(entries, "network impossible-token").isEmpty())
    }

    @Test
    fun searchNeverIndexesCurrentOrSecretValues() {
        prefs.edit()
            .putString(NetworkExportSettings.PREF_ENDPOINT, "https://private-endpoint.invalid/top-secret")
            .putString("filename_template", "customer-secret-template")
            .putString("watermark_text", "private-watermark-value")
            .putString(UserAppProfileStore.PREF_KEY, "private-profile-name")
            .commit()
        val entries = SettingsRegistry.entries(context)

        listOf(
            "private-endpoint",
            "top-secret",
            "customer-secret-template",
            "private-watermark-value",
            "private-profile-name",
        ).forEach { value -> assertTrue(SettingsRegistry.search(entries, value).isEmpty()) }
    }

    @Test
    fun resetRemovesOnlyTheExplicitNonSecretAllowlist() {
        val imageFormat = SettingsRegistry.entries(context)
            .single { it.destination == SettingsDestination.IMAGE_FORMAT }
        prefs.edit()
            .putBoolean("use_jpeg", true)
            .putInt("jpeg_quality", 51)
            .putBoolean("target_size_enabled", true)
            .putString("filename_template", "private-name")
            .putString(NetworkExportSettings.PREF_ENDPOINT, "https://private.invalid")
            .putString(UserAppProfileStore.PREF_KEY, "user-owned-rules")
            .putString("unrelated", "preserve")
            .commit()

        assertTrue(SettingsRegistry.reset(prefs, imageFormat))
        assertFalse(prefs.contains("use_jpeg"))
        assertFalse(prefs.contains("jpeg_quality"))
        assertFalse(prefs.contains("target_size_enabled"))
        assertFalse(prefs.contains("filename_template"))
        assertEquals("https://private.invalid", prefs.getString(NetworkExportSettings.PREF_ENDPOINT, null))
        assertEquals("user-owned-rules", prefs.getString(UserAppProfileStore.PREF_KEY, null))
        assertEquals("preserve", prefs.getString("unrelated", null))

        val network = SettingsRegistry.entries(context)
            .single { it.destination == SettingsDestination.NETWORK_EXPORTS }
        assertFalse(SettingsRegistry.reset(prefs, network))
        assertEquals("https://private.invalid", prefs.getString(NetworkExportSettings.PREF_ENDPOINT, null))
    }
}
