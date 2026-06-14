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

    /** Returns the number of keys restored, or -1 if the document is not a valid backup. */
    fun import(prefs: SharedPreferences, json: JSONObject): Int {
        if (json.optString("schema") != SCHEMA) return -1
        val entries = json.optJSONObject("entries") ?: return -1
        val editor = prefs.edit()
        editor.clear()
        var count = 0
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val e = entries.optJSONObject(key) ?: continue
            when (e.optString("t")) {
                "b" -> editor.putBoolean(key, e.optBoolean("v"))
                "i" -> editor.putInt(key, e.optInt("v"))
                "l" -> editor.putLong(key, e.optLong("v"))
                "f" -> editor.putFloat(key, e.optDouble("v").toFloat())
                "s" -> editor.putString(key, e.optString("v"))
                "ss" -> {
                    val arr = e.optJSONArray("v")
                    val set = buildSet { for (i in 0 until (arr?.length() ?: 0)) add(arr!!.getString(i)) }
                    editor.putStringSet(key, set)
                }
                else -> continue
            }
            count++
        }
        editor.apply()
        return count
    }
}
