package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun exportImportRoundTripsAllTypes() {
        val src = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        src.edit()
            .putBoolean("delete_original", true)
            .putInt("jpeg_quality", 88)
            .putFloat("some_float", 1.5f)
            .putLong("counter", 42L)
            .putString("save_path", "Pictures/SnapCrop")
            .putStringSet("share_targets", setOf("a", "b"))
            .apply()

        val json = SettingsBackup.export(src)

        val dst = context.getSharedPreferences("restore_target", Context.MODE_PRIVATE)
        val count = SettingsBackup.import(dst, json)

        assertTrue(count >= 6)
        assertTrue(dst.getBoolean("delete_original", false))
        assertEquals(88, dst.getInt("jpeg_quality", 0))
        assertEquals(1.5f, dst.getFloat("some_float", 0f), 0.0001f)
        assertEquals(42L, dst.getLong("counter", 0))
        assertEquals("Pictures/SnapCrop", dst.getString("save_path", ""))
        assertEquals(setOf("a", "b"), dst.getStringSet("share_targets", emptySet()))
    }

    @Test
    fun importRejectsForeignJson() {
        val dst = context.getSharedPreferences("reject_target", Context.MODE_PRIVATE)
        val result = SettingsBackup.import(dst, org.json.JSONObject().put("schema", "something.else"))
        assertEquals(-1, result)
    }

    @Test
    fun restoreClearsPriorKeys() {
        val dst = context.getSharedPreferences("clear_target", Context.MODE_PRIVATE)
        dst.edit().putString("stale_key", "old").apply()
        val src = context.getSharedPreferences("clear_source", Context.MODE_PRIVATE)
        src.edit().putString("fresh_key", "new").apply()
        SettingsBackup.import(dst, SettingsBackup.export(src))
        assertFalse(dst.contains("stale_key"))
        assertEquals("new", dst.getString("fresh_key", ""))
    }
}
