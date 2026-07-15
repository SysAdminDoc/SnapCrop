package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Export/import of SnapCrop's registered settings, presets, and app-crop profiles.
 * Network credentials and transient capture state are deliberately outside the registry.
 */
object SettingsBackup {
    const val SCHEMA = "snapcrop.settings"
    const val VERSION = 2
    /** Hard ceiling for a settings document supplied by an external document provider. */
    const val MAX_IMPORT_BYTES = 2 * 1024 * 1024

    data class ImportReport(
        val restoredCount: Int,
        val migratedCount: Int,
        val ignoredUnknownCount: Int,
        val ignoredInvalidCount: Int
    )

    enum class ImportRejectReason {
        TOO_LARGE,
        MALFORMED,
        DUPLICATE_KEY,
        UNSUPPORTED_SCHEMA,
        UNSUPPORTED_VERSION,
        INVALID_STRUCTURED_VALUE,
        COMMIT_FAILED,
        IO_FAILURE,
    }

    sealed interface ImportOutcome {
        data class Applied(val report: ImportReport) : ImportOutcome
        data class Rejected(
            val reason: ImportRejectReason,
            val ignoredUnknownCount: Int = 0,
            val ignoredInvalidCount: Int = 1,
        ) : ImportOutcome
    }

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
    fun importWithReport(prefs: SharedPreferences, json: JSONObject): ImportReport? =
        (importDocument(prefs, json) as? ImportOutcome.Applied)?.report

    fun importFromStream(prefs: SharedPreferences, input: InputStream): ImportOutcome {
        val bytes = try {
            val output = ByteArrayOutputStream(minOf(MAX_IMPORT_BYTES, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    return ImportOutcome.Rejected(ImportRejectReason.TOO_LARGE)
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } catch (_: Exception) {
            return ImportOutcome.Rejected(ImportRejectReason.IO_FAILURE, ignoredInvalidCount = 0)
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            return ImportOutcome.Rejected(ImportRejectReason.MALFORMED)
        }
        when (StrictJsonValidator.validate(text)) {
            StrictJsonIssue.DUPLICATE_KEY ->
                return ImportOutcome.Rejected(ImportRejectReason.DUPLICATE_KEY)
            StrictJsonIssue.MALFORMED ->
                return ImportOutcome.Rejected(ImportRejectReason.MALFORMED)
            null -> Unit
        }
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return ImportOutcome.Rejected(ImportRejectReason.MALFORMED)
        }
        return importDocument(prefs, json)
    }

    private fun importDocument(prefs: SharedPreferences, json: JSONObject): ImportOutcome {
        if (json.optString("schema") != SCHEMA) {
            return ImportOutcome.Rejected(ImportRejectReason.UNSUPPORTED_SCHEMA)
        }
        val documentVersion = json.optInt("version", -1)
        if (documentVersion !in 1..VERSION) {
            return ImportOutcome.Rejected(ImportRejectReason.UNSUPPORTED_VERSION)
        }
        if (documentVersion >= 2 &&
            json.optInt("preferenceSchemaVersion", -1) !in 1..SettingsPreferenceSchema.VERSION
        ) {
            return ImportOutcome.Rejected(ImportRejectReason.UNSUPPORTED_VERSION)
        }
        val entries = json.optJSONObject("entries")
            ?: return ImportOutcome.Rejected(ImportRejectReason.MALFORMED)

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
                ignoredInvalid++
                if (preference.key in STRUCTURED_KEYS) {
                    return ImportOutcome.Rejected(
                        ImportRejectReason.INVALID_STRUCTURED_VALUE,
                        ignoredUnknown,
                        ignoredInvalid,
                    )
                }
                return@forEach
            }
            staged[preference] = value
            if (isMigration) migrated++
        }

        // v1 lacked enough metadata to distinguish a legitimate defaults-only backup from a
        // crafted empty document. Keep its original fail-safe behavior and never clear on empty.
        if (documentVersion == 1 && staged.isEmpty()) {
            return ImportOutcome.Rejected(
                ImportRejectReason.MALFORMED,
                ignoredUnknown,
                ignoredInvalid + 1,
            )
        }

        val valuesByKey = staged.entries.associate { (preference, value) -> preference.key to value }
        val presetIds = (valuesByKey[ExportPresetStore.PREF_PRESETS] as? String)
            ?.let(ExportPresetStore::validatedIdsForSettingsImport)
        val selectedPresetIds = listOfNotNull(
            valuesByKey[ExportPresetStore.PREF_EDITOR_PRESET_ID] as? String,
            valuesByKey[ExportPresetStore.PREF_QUICK_PRESET_ID] as? String,
        )
        if (selectedPresetIds.any { presetIds == null || it !in presetIds }) {
            return ImportOutcome.Rejected(
                ImportRejectReason.INVALID_STRUCTURED_VALUE,
                ignoredUnknown,
                ignoredInvalid + 1,
            )
        }
        val drawPresetNames = (valuesByKey[DrawStylePresetStore.KEY] as? String)
            ?.let(DrawStylePresetStore::validatedNamesForSettingsImport)
        val defaultDrawPreset = valuesByKey[DrawStylePresetStore.KEY_DEFAULT] as? String
        if (defaultDrawPreset != null && (drawPresetNames == null || defaultDrawPreset !in drawPresetNames)) {
            return ImportOutcome.Rejected(
                ImportRejectReason.INVALID_STRUCTURED_VALUE,
                ignoredUnknown,
                ignoredInvalid + 1,
            )
        }

        val editor = prefs.edit()
        SettingsPreferenceSchema.removeRestorableKeys(editor)
        staged.forEach { (preference, value) ->
            check(SettingsPreferenceSchema.writeTyped(editor, preference.key, preference.type, value))
        }
        if (!editor.commit()) {
            return ImportOutcome.Rejected(
                ImportRejectReason.COMMIT_FAILED,
                ignoredUnknown,
                ignoredInvalid,
            )
        }
        return ImportOutcome.Applied(
            ImportReport(staged.size, migrated, ignoredUnknown, ignoredInvalid),
        )
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
            StrictJsonValidator.validate(value as String) == null &&
                CustomRedactionPatternStore.import(value) != null
        UserAppProfileStore.PREF_KEY ->
            UserAppProfileStore.validateForSettingsImport(value as String)
        ExportPresetStore.PREF_PRESETS ->
            ExportPresetStore.validatedIdsForSettingsImport(value as String) != null
        DrawStylePresetStore.KEY ->
            DrawStylePresetStore.validatedNamesForSettingsImport(value as String) != null
        else -> true
    }

    private val STRUCTURED_KEYS = setOf(
        CustomRedactionPatternStore.PREF_KEY,
        UserAppProfileStore.PREF_KEY,
        ExportPresetStore.PREF_PRESETS,
        DrawStylePresetStore.KEY,
    )
    private const val MAX_STRING_LEN = 100_000
    private const val MAX_SET_SIZE = 5_000
}

internal enum class StrictJsonIssue { MALFORMED, DUPLICATE_KEY }

/** Strict JSON structure pass used before JSONObject can collapse duplicate object names. */
internal object StrictJsonValidator {
    private const val MAX_DEPTH = 48
    private const val MAX_VALUES = 100_000

    fun validate(json: String): StrictJsonIssue? {
        var values = 0
        return try {
            JsonReader(StringReader(json)).use { reader ->
                fun readValue(depth: Int) {
                    if (depth > MAX_DEPTH || ++values > MAX_VALUES) throw MalformedJsonException()
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> {
                            reader.beginObject()
                            val names = mutableSetOf<String>()
                            while (reader.hasNext()) {
                                if (!names.add(reader.nextName())) throw DuplicateJsonKeyException()
                                readValue(depth + 1)
                            }
                            reader.endObject()
                        }
                        JsonToken.BEGIN_ARRAY -> {
                            reader.beginArray()
                            while (reader.hasNext()) readValue(depth + 1)
                            reader.endArray()
                        }
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.NUMBER -> reader.nextString()
                        JsonToken.BOOLEAN -> reader.nextBoolean()
                        JsonToken.NULL -> reader.nextNull()
                        else -> throw MalformedJsonException()
                    }
                }
                readValue(0)
                if (reader.peek() != JsonToken.END_DOCUMENT) throw MalformedJsonException()
            }
            null
        } catch (_: DuplicateJsonKeyException) {
            StrictJsonIssue.DUPLICATE_KEY
        } catch (_: Exception) {
            StrictJsonIssue.MALFORMED
        }
    }

    private class DuplicateJsonKeyException : RuntimeException()
    private class MalformedJsonException : RuntimeException()
}
