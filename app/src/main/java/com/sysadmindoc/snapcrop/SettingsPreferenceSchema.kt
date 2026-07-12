package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal enum class PreferenceValueType(val tag: String) {
    BOOLEAN("b"),
    INT("i"),
    LONG("l"),
    FLOAT("f"),
    STRING("s"),
    STRING_SET("ss")
}

internal data class RegisteredPreference(
    val key: String,
    val type: PreferenceValueType,
    val legacyKeys: Set<String> = emptySet()
)

/** The restorable public contract for the otherwise unstructured `snapcrop` preference file. */
internal object SettingsPreferenceSchema {
    const val PREFS_NAME = "snapcrop"
    const val VERSION = 2

    val preferences: List<RegisteredPreference> = listOf(
        boolean("auto_start"),
        boolean("delete_original"),
        boolean("use_jpeg"),
        boolean("use_webp"),
        boolean("project_sidecars"),
        boolean("ocr_text_sidecars"),
        boolean("app_crop_profiles"),
        boolean("target_size_enabled"),
        boolean("strip_exif"),
        boolean(NetworkExportSettings.PREF_ENABLED),
        boolean("watermark_enabled"),
        boolean("conditional_auto_actions"),
        boolean("redact_on_share"),
        boolean(ScreenshotIndexStore.PREF_ENABLED),
        boolean(AdvancedEraseBackendRegistry.PREF_ALLOW_EXPERIMENTAL),
        boolean(SecurePreviewPolicy.PREF_ENABLED, "secure_editor"),
        boolean(OperationJournal.PREF_ENABLED),
        boolean(UpdateChecker.PREF_AUTO),
        int("jpeg_quality"),
        int("target_size_kb"),
        int("border_size"),
        int("border_color"),
        int("save_counter"),
        string("theme"),
        string("filename_template"),
        string(NetworkExportSettings.PREF_TARGET),
        string(NetworkExportSettings.PREF_ENDPOINT),
        string("watermark_text"),
        string("save_path"),
        string("batch_rename_template"),
        string("batch_rename_profile"),
        string(ImageRedactor.PREF_REDACTION_STYLE),
        string(OcrScript.PREF_KEY),
        string(UserAppProfileStore.PREF_KEY),
        string(CustomRedactionPatternStore.PREF_KEY),
        string(AdvancedEraseBackendRegistry.PREF_SELECTED_BACKEND),
        string(ScreenshotService.PREF_LAST_ACTION),
        string(PREF_SHARE_METADATA_DEFAULT),
        string(ExportPresetStore.PREF_EDITOR_PRESET_ID),
        string(ExportPresetStore.PREF_QUICK_PRESET_ID),
        string(ExportPresetStore.PREF_PRESETS),
        string(DrawStylePresetStore.KEY),
        string(DrawStylePresetStore.KEY_DEFAULT)
    )

    private val byCurrentKey = preferences.associateBy(RegisteredPreference::key)
    private val byLegacyKey = buildMap {
        preferences.forEach { preference ->
            preference.legacyKeys.forEach { legacyKey -> put(legacyKey, preference) }
        }
    }

    data class Resolution(val preference: RegisteredPreference, val migrated: Boolean)

    fun resolve(key: String): Resolution? = byCurrentKey[key]?.let { Resolution(it, false) }
        ?: byLegacyKey[key]?.let { Resolution(it, true) }

    fun migrationRulesJson(): JSONArray = JSONArray().apply {
        preferences.forEach { preference ->
            preference.legacyKeys.sorted().forEach { legacyKey ->
                put(JSONObject().put("from", legacyKey).put("to", preference.key))
            }
        }
    }

    /** Migrates installed preferences before any component reads them. */
    fun migrateLivePreferences(prefs: SharedPreferences): Int {
        val values = prefs.all
        val editor = prefs.edit()
        var migrated = 0
        preferences.forEach { preference ->
            preference.legacyKeys.forEach { legacyKey ->
                if (!values.containsKey(legacyKey)) return@forEach
                if (!values.containsKey(preference.key) &&
                    writeTyped(editor, preference.key, preference.type, values[legacyKey])
                ) {
                    migrated++
                }
                editor.remove(legacyKey)
            }
        }
        if (migrated > 0 || preferences.any { entry -> entry.legacyKeys.any(values::containsKey) }) {
            editor.apply()
        }
        return migrated
    }

    fun removeRestorableKeys(editor: SharedPreferences.Editor) {
        preferences.forEach { preference ->
            editor.remove(preference.key)
            preference.legacyKeys.forEach(editor::remove)
        }
    }

    fun writeTyped(
        editor: SharedPreferences.Editor,
        key: String,
        type: PreferenceValueType,
        value: Any?
    ): Boolean = when (type) {
        PreferenceValueType.BOOLEAN -> (value as? Boolean)?.let { editor.putBoolean(key, it); true } ?: false
        PreferenceValueType.INT -> (value as? Int)?.let { editor.putInt(key, it); true } ?: false
        PreferenceValueType.LONG -> (value as? Long)?.let { editor.putLong(key, it); true } ?: false
        PreferenceValueType.FLOAT -> (value as? Float)?.let { editor.putFloat(key, it); true } ?: false
        PreferenceValueType.STRING -> (value as? String)?.let { editor.putString(key, it); true } ?: false
        PreferenceValueType.STRING_SET -> (value as? Set<*>)
            ?.takeIf { set -> set.all { it is String } }
            ?.let { editor.putStringSet(key, it.filterIsInstance<String>().toSet()); true }
            ?: false
    }

    private fun boolean(key: String, vararg legacy: String) =
        RegisteredPreference(key, PreferenceValueType.BOOLEAN, legacy.toSet())

    private fun int(key: String, vararg legacy: String) =
        RegisteredPreference(key, PreferenceValueType.INT, legacy.toSet())

    private fun string(key: String, vararg legacy: String) =
        RegisteredPreference(key, PreferenceValueType.STRING, legacy.toSet())
}
