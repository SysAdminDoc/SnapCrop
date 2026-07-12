package com.sysadmindoc.snapcrop

import android.content.SharedPreferences

enum class WorkflowId {
    EDIT_IMAGE,
    WEB_CAPTURE,
    BATCH_CROP,
    DELAYED_CAPTURE,
    LONG_SCREENSHOT,
    STEP_CAPTURE,
    STITCH,
    COLLAGE,
    DEVICE_FRAME,
    VIDEO_CLIP,
    GALLERY,
    PDF_REPORT,
    PROJECT_REOPEN,
}

/** Stores only allowlisted enum names, most recent first. */
object RecentWorkflowStore {
    const val PREF_NAME = "snapcrop_recent_workflows"
    const val MAX_ITEMS = 6
    const val PREF_ENABLED = "recent_workflows_enabled"
    internal const val PREF_ITEMS = "recent_workflow_ids"

    fun isEnabled(prefs: SharedPreferences): Boolean =
        runCatching { prefs.getBoolean(PREF_ENABLED, true) }.getOrDefault(true)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().apply {
            putBoolean(PREF_ENABLED, enabled)
            if (!enabled) remove(PREF_ITEMS)
        }.commit()
    }

    fun load(prefs: SharedPreferences): List<WorkflowId> {
        if (!isEnabled(prefs)) return emptyList()
        val raw = try {
            prefs.getString(PREF_ITEMS, null)
        } catch (_: ClassCastException) {
            prefs.edit().remove(PREF_ITEMS).apply()
            return emptyList()
        } ?: return emptyList()
        if (raw.length > MAX_ENCODED_LENGTH || raw.any(Char::isISOControl)) {
            prefs.edit().remove(PREF_ITEMS).apply()
            return emptyList()
        }

        val parsed = raw.split(',').asSequence()
            .map(String::trim)
            .mapNotNull(::workflowIdOrNull)
            .distinct()
            .take(MAX_ITEMS)
            .toList()
        val canonical = encode(parsed)
        if (canonical != raw) {
            prefs.edit().apply {
                if (canonical.isEmpty()) remove(PREF_ITEMS) else putString(PREF_ITEMS, canonical)
            }.apply()
        }
        return parsed
    }

    @Synchronized
    fun record(prefs: SharedPreferences, workflow: WorkflowId): List<WorkflowId> {
        if (!isEnabled(prefs)) return emptyList()
        val updated = buildList {
            add(workflow)
            load(prefs).forEach { if (it != workflow && size < MAX_ITEMS) add(it) }
        }
        prefs.edit().putString(PREF_ITEMS, encode(updated)).apply()
        return updated
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(PREF_ITEMS).commit()
    }

    private fun encode(items: List<WorkflowId>): String = items.joinToString(",", transform = WorkflowId::name)

    private fun workflowIdOrNull(value: String): WorkflowId? =
        WorkflowId.entries.firstOrNull { it.name == value }

    private const val MAX_ENCODED_LENGTH = 1_024
}
