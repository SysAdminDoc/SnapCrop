package com.sysadmindoc.snapcrop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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
    private const val SEP = "\u001F"

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
        return targets.take(12).map {
            listOf(
                it.packageName.escape(),
                it.className.escape(),
                it.label.escape(),
                it.count.toString(),
                it.lastUsed.toString()
            ).joinToString(SEP)
        }.toSet()
    }

    internal fun decode(values: Set<String>): List<ShareTargetShortcut> {
        return values.mapNotNull { value ->
            val parts = value.split(SEP)
            if (parts.size != 5) return@mapNotNull null
            ShareTargetShortcut(
                packageName = parts[0].unescape(),
                className = parts[1].unescape(),
                label = parts[2].unescape(),
                count = parts[3].toIntOrNull() ?: 0,
                lastUsed = parts[4].toLongOrNull() ?: 0L
            )
        }
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

    private fun String.escape(): String = replace("\\", "\\\\").replace(SEP, "\\u001F")
    private fun String.unescape(): String = replace("\\u001F", SEP).replace("\\\\", "\\")
}
