package com.sysadmindoc.snapcrop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONObject

data class ShareTargetShortcut(
    val packageName: String,
    val className: String,
    val label: String,
    val count: Int,
    val lastUsed: Long
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, className)
}

object ShareTargetStore {
    private const val PREFS = "snapcrop_share_targets"
    private const val KEY_TARGETS = "targets"

    fun record(context: Context, componentName: ComponentName) {
        val label = resolveLabel(context, componentName)
        val existing = load(context).associateBy { "${it.packageName}/${it.className}" }.toMutableMap()
        val key = "${componentName.packageName}/${componentName.className}"
        val current = existing[key]
        existing[key] = ShareTargetShortcut(
            packageName = componentName.packageName,
            className = componentName.className,
            label = label,
            count = (current?.count ?: 0) + 1,
            lastUsed = System.currentTimeMillis()
        )
        save(context, existing.values.sortedWith(compareByDescending<ShareTargetShortcut> { it.count }.thenByDescending { it.lastUsed }))
    }

    fun top(context: Context, limit: Int = 3): List<ShareTargetShortcut> =
        load(context).sortedWith(compareByDescending<ShareTargetShortcut> { it.count }.thenByDescending { it.lastUsed })
            .take(limit)

    fun buildInitialIntents(context: Context, baseIntent: Intent, limit: Int = 3): Array<Intent> {
        return top(context, limit).mapNotNull { target ->
            val intent = Intent(baseIntent).apply {
                component = target.componentName
                putExtra(Intent.EXTRA_TITLE, target.label)
            }
            if (intent.resolveActivity(context.packageManager) != null) intent else null
        }.toTypedArray()
    }

    internal fun encode(targets: List<ShareTargetShortcut>): Set<String> {
        return targets.take(12).map { t ->
            JSONObject().apply {
                put("p", t.packageName)
                put("c", t.className)
                put("l", t.label)
                put("n", t.count)
                put("t", t.lastUsed)
            }.toString()
        }.toSet()
    }

    internal fun decode(values: Set<String>): List<ShareTargetShortcut> {
        return values.mapNotNull { value ->
            try {
                val j = JSONObject(value)
                ShareTargetShortcut(
                    packageName = j.getString("p"),
                    className = j.getString("c"),
                    label = j.getString("l"),
                    count = j.optInt("n"),
                    lastUsed = j.optLong("t")
                )
            } catch (_: Exception) {
                decodeLegacy(value)
            }
        }
    }

    private val LEGACY_SEP = Char(0x1F).toString()

    private fun decodeLegacy(value: String): ShareTargetShortcut? {
        val parts = value.split(LEGACY_SEP)
        if (parts.size != 5) return null
        fun String.unesc() = replace("\\u001F", LEGACY_SEP).replace("\\\\", "\\")
        return ShareTargetShortcut(
            packageName = parts[0].unesc(),
            className = parts[1].unesc(),
            label = parts[2].unesc(),
            count = parts[3].toIntOrNull() ?: 0,
            lastUsed = parts[4].toLongOrNull() ?: 0L
        )
    }

    private fun load(context: Context): List<ShareTargetShortcut> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return decode(prefs.getStringSet(KEY_TARGETS, emptySet()).orEmpty())
    }

    private fun save(context: Context, targets: List<ShareTargetShortcut>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_TARGETS, encode(targets))
            .apply()
    }

    private fun resolveLabel(context: Context, componentName: ComponentName): String {
        return try {
            val info = context.packageManager.getActivityInfo(componentName, PackageManager.MATCH_DEFAULT_ONLY)
            info.loadLabel(context.packageManager).toString().ifBlank { componentName.packageName }
        } catch (_: Exception) {
            componentName.packageName
        }
    }
}
