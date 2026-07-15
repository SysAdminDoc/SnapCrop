package com.sysadmindoc.snapcrop

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun exportUsesRegisteredSchemaAndExcludesCredentialsAndTransientState() {
        val prefs = preferences("export_schema")
        prefs.edit()
            .putBoolean("delete_original", true)
            .putBoolean("target_size_allow_resize", true)
            .putInt("jpeg_quality", 88)
            .putString("save_path", "Pictures/SnapCrop")
            .putString(NetworkExportSettings.PREF_AUTHORIZATION, "Bearer secret")
            .putString(ScreenshotService.PREF_LAST_SEED_URI, "content://private")
            .putString("future_internal_key", "private")
            .apply()

        val json = SettingsBackup.export(prefs)
        val entries = json.getJSONObject("entries")

        assertEquals(SettingsBackup.SCHEMA, json.getString("schema"))
        assertEquals(SettingsBackup.VERSION, json.getInt("version"))
        assertEquals(BuildConfig.VERSION_NAME, json.getString("appVersion"))
        assertEquals(SettingsPreferenceSchema.VERSION, json.getInt("preferenceSchemaVersion"))
        assertTrue(json.getJSONArray("migrations").toString().contains("secure_editor"))
        assertTrue(entries.has("delete_original"))
        assertTrue(entries.has("target_size_allow_resize"))
        assertTrue(entries.has("jpeg_quality"))
        assertTrue(entries.has("save_path"))
        assertFalse(entries.has(NetworkExportSettings.PREF_AUTHORIZATION))
        assertFalse(entries.has(ScreenshotService.PREF_LAST_SEED_URI))
        assertFalse(entries.has("future_internal_key"))
    }

    @Test
    fun currentBackupRoundTripsKnownTypesAndReportsInvalidEntries() {
        val source = preferences("roundtrip_source")
        source.edit()
            .putBoolean("delete_original", true)
            .putInt("jpeg_quality", 88)
            .putString("save_path", "Pictures/SnapCrop")
            .apply()
        val json = SettingsBackup.export(source)
        json.getJSONObject("entries")
            .put("border_size", JSONObject().put("t", "s").put("v", "wrong"))
            .put("unknown_export_key", JSONObject().put("t", "s").put("v", "ignored"))

        val target = preferences("roundtrip_target")
        val report = SettingsBackup.importWithReport(target, json)

        assertNotNull(report)
        assertEquals(3, report?.restoredCount)
        assertEquals(1, report?.ignoredInvalidCount)
        assertEquals(1, report?.ignoredUnknownCount)
        assertTrue(target.getBoolean("delete_original", false))
        assertEquals(88, target.getInt("jpeg_quality", 0))
        assertEquals("Pictures/SnapCrop", target.getString("save_path", ""))
    }

    @Test
    fun versionOneBackupMigratesRenamedKeyAndPreservesUnknownLocalState() {
        val legacy = JSONObject()
            .put("schema", SettingsBackup.SCHEMA)
            .put("version", 1)
            .put("entries", JSONObject()
                .put("secure_editor", JSONObject().put("t", "b").put("v", true))
                .put("unknown_old_key", JSONObject().put("t", "s").put("v", "ignored")))
        val target = preferences("legacy_target")
        target.edit()
            .putBoolean("delete_original", true)
            .putString("future_internal_key", "keep")
            .apply()

        val report = SettingsBackup.importWithReport(target, legacy)

        assertEquals(1, report?.restoredCount)
        assertEquals(1, report?.migratedCount)
        assertEquals(1, report?.ignoredUnknownCount)
        assertTrue(target.getBoolean(SecurePreviewPolicy.PREF_ENABLED, false))
        assertFalse(target.contains("secure_editor"))
        assertFalse(target.contains("delete_original"))
        assertEquals("keep", target.getString("future_internal_key", null))
    }

    @Test
    fun liveSchemaMigrationMovesInstalledLegacyPreference() {
        val prefs = preferences("live_migration")
        prefs.edit().putBoolean("secure_editor", true).apply()

        assertEquals(1, SettingsPreferenceSchema.migrateLivePreferences(prefs))
        assertTrue(prefs.getBoolean(SecurePreviewPolicy.PREF_ENABLED, false))
        assertFalse(prefs.contains("secure_editor"))
        assertEquals(0, SettingsPreferenceSchema.migrateLivePreferences(prefs))
    }

    @Test
    fun defaultsOnlyVersionTwoBackupClearsRegisteredValuesButKeepsUnknownState() {
        val target = preferences("defaults_target")
        target.edit()
            .putBoolean("delete_original", true)
            .putString("future_internal_key", "keep")
            .apply()
        val emptyBackup = SettingsBackup.export(preferences("defaults_source"))

        val report = SettingsBackup.importWithReport(target, emptyBackup)

        assertEquals(0, report?.restoredCount)
        assertFalse(target.contains("delete_original"))
        assertEquals("keep", target.getString("future_internal_key", null))
    }

    @Test
    fun malformedCustomPatternsRejectWholeRestoreAtomically() {
        val source = preferences("pattern_source")
        val patterns = listOf(CustomRedactionPattern("ticket", "Ticket", "TKT-[0-9]{6}"))
        assertTrue(CustomRedactionPatternStore.save(source, patterns))
        val valid = SettingsBackup.export(source)
        val target = preferences("pattern_target")
        assertTrue(SettingsBackup.import(target, valid) > 0)
        assertEquals(patterns, CustomRedactionPatternStore.load(target))

        valid.getJSONObject("entries")
            .getJSONObject(CustomRedactionPatternStore.PREF_KEY)
            .put("v", "not-json")
        target.edit().putString("save_path", "Downloads/SnapCrop").commit()

        assertNull(SettingsBackup.importWithReport(target, valid))
        assertEquals("Downloads/SnapCrop", target.getString("save_path", null))
    }

    @Test
    fun importRejectsForeignFutureAndEmptyLegacyDocuments() {
        val target = preferences("reject_target")
        assertNull(SettingsBackup.importWithReport(target, JSONObject().put("schema", "something.else")))
        assertNull(SettingsBackup.importWithReport(target, JSONObject()
            .put("schema", SettingsBackup.SCHEMA)
            .put("version", SettingsBackup.VERSION + 1)
            .put("entries", JSONObject())))
        assertNull(SettingsBackup.importWithReport(target, JSONObject()
            .put("schema", SettingsBackup.SCHEMA)
            .put("version", 1)
            .put("entries", JSONObject())))
    }

    @Test
    fun streamImportRejectsOversizeMalformedDuplicateAndFutureDocumentsWithExactCounts() {
        val target = preferences("bounded_stream_target")
        target.edit().putString("save_path", "Downloads/SnapCrop").commit()

        val oversized = SettingsBackup.importFromStream(
            target,
            ByteArrayInputStream(ByteArray(SettingsBackup.MAX_IMPORT_BYTES + 1) { 'x'.code.toByte() }),
        ) as SettingsBackup.ImportOutcome.Rejected
        val malformed = SettingsBackup.importFromStream(
            target,
            ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)),
        ) as SettingsBackup.ImportOutcome.Rejected
        val duplicate = SettingsBackup.importFromStream(
            target,
            """{"schema":"${SettingsBackup.SCHEMA}","schema":"duplicate","version":2,"preferenceSchemaVersion":2,"entries":{}}"""
                .byteInputStream(),
        ) as SettingsBackup.ImportOutcome.Rejected
        val future = SettingsBackup.importFromStream(
            target,
            JSONObject()
                .put("schema", SettingsBackup.SCHEMA)
                .put("version", SettingsBackup.VERSION + 1)
                .put("entries", JSONObject())
                .toString()
                .byteInputStream(),
        ) as SettingsBackup.ImportOutcome.Rejected

        assertEquals(SettingsBackup.ImportRejectReason.TOO_LARGE, oversized.reason)
        assertEquals(SettingsBackup.ImportRejectReason.MALFORMED, malformed.reason)
        assertEquals(SettingsBackup.ImportRejectReason.DUPLICATE_KEY, duplicate.reason)
        assertEquals(SettingsBackup.ImportRejectReason.UNSUPPORTED_VERSION, future.reason)
        listOf(oversized, malformed, duplicate, future).forEach { report ->
            assertEquals(0, report.ignoredUnknownCount)
            assertEquals(1, report.ignoredInvalidCount)
        }
        assertEquals("Downloads/SnapCrop", target.getString("save_path", null))
    }

    @Test
    fun invalidNestedProfileRejectsWholeRestoreAndReportsUnknownSeparately() {
        val source = preferences("nested_profile_source")
        source.edit()
            .putString("save_path", "Pictures/SnapCrop")
            .putString(
                UserAppProfileStore.PREF_KEY,
                """{"schema":"com.sysadmindoc.snapcrop.appProfiles","version":1,"profiles":[{"id":"same","label":"One","enabled":true,"sourceHints":[],"ocrKeywords":[],"crop":{"left":0,"top":0,"right":0,"bottom":0},"albumName":"One","redactSensitiveText":false,"exportFormat":"png"},{"id":"same","label":"Two","enabled":true,"sourceHints":[],"ocrKeywords":[],"crop":{"left":0,"top":0,"right":0,"bottom":0},"albumName":"Two","redactSensitiveText":false,"exportFormat":"png"}]}""",
            )
            .commit()
        val backup = SettingsBackup.export(source)
        backup.getJSONObject("entries")
            .put("future_key", JSONObject().put("t", "s").put("v", "ignored"))
        val target = preferences("nested_profile_target")
        target.edit()
            .putString("save_path", "Downloads/SnapCrop")
            .putBoolean("delete_original", true)
            .commit()

        val outcome = SettingsBackup.importFromStream(target, backup.toString().byteInputStream())
            as SettingsBackup.ImportOutcome.Rejected

        assertEquals(SettingsBackup.ImportRejectReason.INVALID_STRUCTURED_VALUE, outcome.reason)
        assertEquals(1, outcome.ignoredUnknownCount)
        assertEquals(1, outcome.ignoredInvalidCount)
        assertEquals("Downloads/SnapCrop", target.getString("save_path", null))
        assertTrue(target.getBoolean("delete_original", false))
    }

    @Test
    fun duplicateAndOutOfRangePresetPayloadsRejectAtomically() {
        val target = preferences("preset_validation_target")
        target.edit().putString("save_path", "Downloads/SnapCrop").commit()
        val source = preferences("preset_validation_source")
        source.edit()
            .putString("save_path", "Pictures/SnapCrop")
            .putString(
                ExportPresetStore.PREF_PRESETS,
                """{"version":1,"presets":[{"id":"preset-one","name":"Docs","settings":{"format":"jpeg","quality":101,"targetSizeEnabled":true,"targetSizeKb":500,"targetSizeAllowResize":true,"borderSize":0,"borderColor":0,"watermarkEnabled":false,"watermarkText":"SnapCrop","filenameTemplate":"SnapCrop_%timestamp%","savePath":"Pictures/SnapCrop"}}]}""",
            )
            .commit()

        assertNull(SettingsBackup.importWithReport(target, SettingsBackup.export(source)))
        assertEquals("Downloads/SnapCrop", target.getString("save_path", null))

        source.edit().putString(
            DrawStylePresetStore.KEY,
            """[{"name":"Pen","color":-65536,"strokeWidth":6,"dashed":false,"tool":"PEN"},{"name":"pen","color":-65536,"strokeWidth":7,"dashed":false,"tool":"PEN"}]""",
        ).commit()
        assertNull(SettingsBackup.importWithReport(target, SettingsBackup.export(source)))
        assertEquals("Downloads/SnapCrop", target.getString("save_path", null))
    }

    @Test
    fun presetSelectionsMustResolveInsideTheStagedPayload() {
        val source = preferences("preset_reference_source")
        source.edit()
            .putString(ExportPresetStore.PREF_EDITOR_PRESET_ID, "missing-preset")
            .putString(DrawStylePresetStore.KEY_DEFAULT, "Missing pen")
            .commit()
        val target = preferences("preset_reference_target")
        target.edit().putBoolean("delete_original", true).commit()

        val outcome = SettingsBackup.importFromStream(
            target,
            SettingsBackup.export(source).toString().byteInputStream(),
        ) as SettingsBackup.ImportOutcome.Rejected

        assertEquals(SettingsBackup.ImportRejectReason.INVALID_STRUCTURED_VALUE, outcome.reason)
        assertEquals(1, outcome.ignoredInvalidCount)
        assertTrue(target.getBoolean("delete_original", false))
    }

    private fun preferences(name: String) =
        context.getSharedPreferences("settings_backup_test_$name", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
}
