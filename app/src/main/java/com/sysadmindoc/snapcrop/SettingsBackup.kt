package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export/import of SnapCrop's registered settings, presets, and app-crop profiles.
 * Network credentials and transient capture state are deliberately outside the registry.
 */
object SettingsBackup {
    const val SCHEMA = "snapcrop.settings"
    const val VERSION = 2

    data class ImportReport(
        val restoredCount: Int,
        val migratedCount: Int,
        val ignoredUnknownCount: Int,
        val ignoredInvalidCount: Int
    )

    fun export(prefs: SharedPreferences): JSONObject {
        val entries = JSONObject()
        val values = prefs.all
        SettingsPreferenceSchema.preferences.forEach { preference ->
            val value = values[preference.key] ?: return@forEach
            encode(preference.type, value)?.let { entries.put(preference.key, it) }
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("version", VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("preferenceSchemaVersion", SettingsPreferenceSchema.VERSION)
            .put("migrations", SettingsPreferenceSchema.migrationRulesJson())
            .put("entries", entries)
    }

    /** Compatibility wrapper for callers that only need success/failure and a restored count. */
    fun import(prefs: SharedPreferences, json: JSONObject): Int =
        importWithReport(prefs, json)?.restoredCount ?: -1

    /** Returns a detailed report, or null when the document is not a supported valid backup. */
    fun importWithReport(prefs: SharedPreferences, json: JSONObject): ImportReport? {
        if (json.optString("schema") != SCHEMA) return null
        val documentVersion = json.optInt("version", -1)
        if (documentVersion !in 1..VERSION) return null
        if (documentVersion >= 2 &&
            json.optInt("preferenceSchemaVersion", -1) !in 1..SettingsPreferenceSchema.VERSION
        ) return null
        val entries = json.optJSONObject("entries") ?: return null

        val sourceKeys = buildList {
            val iterator = entries.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val ignoredUnknown = sourceKeys.count { SettingsPreferenceSchema.resolve(it) == null }
        var ignoredInvalid = 0
        var migrated = 0
        val staged = LinkedHashMap<RegisteredPreference, Any>()

        SettingsPreferenceSchema.preferences.forEach { preference ->
            val candidates = buildList {
                if (entries.has(preference.key)) add(preference.key to false)
                preference.legacyKeys.sorted().forEach { legacyKey ->
                    if (entries.has(legacyKey)) add(legacyKey to true)
                }
            }
            if (candidates.isEmpty()) return@forEach
            val (sourceKey, isMigration) = candidates.first()
            ignoredInvalid += candidates.size - 1
            val entry = entries.optJSONObject(sourceKey)
            val value = entry?.let { decode(preference.type, it) }
            if (value == null || !validate(preference.key, value)) {
                if (preference.key == CustomRedactionPatternStore.PREF_KEY) return null
                ignoredInvalid++
                return@forEach
            }
            staged[preference] = value
            if (isMigration) migrated++
        }

        // v1 lacked enough metadata to distinguish a legitimate defaults-only backup from a
        // crafted empty document. Keep its original fail-safe behavior and never clear on empty.
        if (documentVersion == 1 && staged.isEmpty()) return null

        val editor = prefs.edit()
        SettingsPreferenceSchema.removeRestorableKeys(editor)
        staged.forEach { (preference, value) ->
            check(SettingsPreferenceSchema.writeTyped(editor, preference.key, preference.type, value))
        }
        if (!editor.commit()) return null
        return ImportReport(staged.size, migrated, ignoredUnknown, ignoredInvalid)
    }

    private fun encode(type: PreferenceValueType, value: Any): JSONObject? {
        val encoded = when (type) {
            PreferenceValueType.BOOLEAN -> value as? Boolean
            PreferenceValueType.INT -> value as? Int
            PreferenceValueType.LONG -> value as? Long
            PreferenceValueType.FLOAT -> (value as? Float)?.toDouble()
            PreferenceValueType.STRING -> value as? String
            PreferenceValueType.STRING_SET -> (value as? Set<*>)
                ?.takeIf { set -> set.all { it is String } }
                ?.let { set -> JSONArray().apply { set.filterIsInstance<String>().sorted().forEach(::put) } }
        } ?: return null
        return JSONObject().put("t", type.tag).put("v", encoded)
    }

    private fun decode(type: PreferenceValueType, entry: JSONObject): Any? {
        if (entry.optString("t") != type.tag || !entry.has("v")) return null
        val raw = entry.opt("v")
        return when (type) {
            PreferenceValueType.BOOLEAN -> raw as? Boolean
            PreferenceValueType.INT -> (raw as? Number)?.toLong()
                ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
                ?.toInt()
            PreferenceValueType.LONG -> (raw as? Number)?.toLong()
            PreferenceValueType.FLOAT -> (raw as? Number)?.toDouble()
                ?.takeIf(Double::isFinite)
                ?.toFloat()
            PreferenceValueType.STRING -> (raw as? String)?.takeIf { it.length <= MAX_STRING_LEN }
            PreferenceValueType.STRING_SET -> decodeStringSet(raw as? JSONArray)
        }
    }

    private fun decodeStringSet(array: JSONArray?): Set<String>? {
        array ?: return null
        if (array.length() > MAX_SET_SIZE) return null
        val values = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val value = array.opt(index) as? String ?: return null
            if (value.length > MAX_STRING_LEN) return null
            values += value
        }
        return values
    }

    private fun validate(key: String, value: Any): Boolean = when (key) {
        CustomRedactionPatternStore.PREF_KEY ->
            CustomRedactionPatternStore.import(value as String) != null
        else -> true
    }

    private const val MAX_STRING_LEN = 100_000
    private const val MAX_SET_SIZE = 5_000
}
