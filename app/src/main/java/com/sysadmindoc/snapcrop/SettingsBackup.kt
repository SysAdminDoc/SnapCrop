package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export/import of SnapCrop's settings, presets, and app-crop profiles to a single JSON document.
 * Because `allowBackup=false` and there are ~120 ungrouped preference keys, a reinstall/new device
 * otherwise loses everything. Network credentials live in a separate encrypted store and are
 * intentionally NOT included — they must be re-entered after a restore.
 */
object SettingsBackup {
    const val SCHEMA = "snapcrop.settings"
    const val VERSION = 1

    fun export(prefs: SharedPreferences): JSONObject {
        val entries = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray().apply { value.forEach { put(it.toString()) } })
                else -> continue
            }
            entries.put(key, entry)
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("version", VERSION)
            .put("entries", entries)
    }

    // Cap restored string/set sizes so a crafted backup can't inject pathological values.
    private const val MAX_STRING_LEN = 100_000
    private const val MAX_SET_SIZE = 5_000

    /** Returns the number of keys restored, or -1 if the document is not a valid backup. */
    fun import(prefs: SharedPreferences, json: JSONObject): Int {
        if (json.optString("schema") != SCHEMA) return -1
        if (json.optInt("version", -1) != VERSION) return -1
        val entries = json.optJSONObject("entries") ?: return -1

        // Stage all values first; only clear+apply if at least one parsed, so a malformed or empty
        // backup can never wipe the user's settings.
        val edits = ArrayList<(SharedPreferences.Editor) -> Unit>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val e = entries.optJSONObject(key) ?: continue
            when (e.optString("t")) {
                "b" -> { val v = e.optBoolean("v"); edits.add { it.putBoolean(key, v) } }
                "i" -> { val v = e.optInt("v"); edits.add { it.putInt(key, v) } }
                "l" -> { val v = e.optLong("v"); edits.add { it.putLong(key, v) } }
                "f" -> { val v = e.optDouble("v").toFloat(); edits.add { it.putFloat(key, v) } }
                "s" -> {
                    val v = e.optString("v")
                    if (v.length <= MAX_STRING_LEN) edits.add { it.putString(key, v) }
                }
                "ss" -> {
                    val arr = e.optJSONArray("v")
                    val set = LinkedHashSet<String>()
                    var i = 0
                    while (i < (arr?.length() ?: 0) && set.size < MAX_SET_SIZE) {
                        arr?.optString(i)?.let { if (it.length <= MAX_STRING_LEN) set.add(it) }
                        i++
                    }
                    edits.add { it.putStringSet(key, set) }
                }
                else -> continue
            }
        }
        if (edits.isEmpty()) return -1

        val editor = prefs.edit()
        editor.clear()
        edits.forEach { it(editor) }
        editor.apply()
        return edits.size
    }
}
