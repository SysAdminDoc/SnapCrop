package com.sysadmindoc.snapcrop

import android.content.Context

internal enum class GalleryContentStatus {
    LOADING,
    PARTIAL_PERMISSION,
    FAILED,
    EMPTY,
    READY,
}

internal data class GalleryContentState(
    val status: GalleryContentStatus,
    val itemCount: Int,
    val retryAvailable: Boolean,
)

internal object GalleryContentStateResolver {
    fun resolve(
        loading: Boolean,
        failed: Boolean,
        itemCount: Int,
        imageAccess: MediaAccess,
        videoAccess: MediaAccess,
    ): GalleryContentState {
        val count = itemCount.coerceAtLeast(0)
        val status = when {
            failed -> GalleryContentStatus.FAILED
            loading -> GalleryContentStatus.LOADING
            imageAccess != MediaAccess.FULL || videoAccess != MediaAccess.FULL ->
                GalleryContentStatus.PARTIAL_PERMISSION
            count == 0 -> GalleryContentStatus.EMPTY
            else -> GalleryContentStatus.READY
        }
        return GalleryContentState(status, count, retryAvailable = status == GalleryContentStatus.FAILED)
    }
}

internal data class IndexHealthSnapshot(
    val indexedCount: Int = 0,
    val eligibleCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val lastSuccessfulScanMs: Long = 0L,
)

internal object IndexHealthStore {
    private const val PREF_INDEXED = "index_health_indexed"
    private const val PREF_ELIGIBLE = "index_health_eligible"
    private const val PREF_PENDING = "index_health_pending"
    private const val PREF_FAILED = "index_health_failed"
    private const val PREF_LAST_SUCCESS = "index_health_last_success"

    fun load(context: Context): IndexHealthSnapshot {
        val prefs = context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
        return IndexHealthSnapshot(
            indexedCount = prefs.getInt(PREF_INDEXED, 0).coerceAtLeast(0),
            eligibleCount = prefs.getInt(PREF_ELIGIBLE, 0).coerceAtLeast(0),
            pendingCount = prefs.getInt(PREF_PENDING, 0).coerceIn(0, 1),
            failedCount = prefs.getInt(PREF_FAILED, 0).coerceIn(0, 999),
            lastSuccessfulScanMs = prefs.getLong(PREF_LAST_SUCCESS, 0L).coerceAtLeast(0L),
        )
    }

    fun markStarted(context: Context) {
        context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(PREF_PENDING, 1).apply()
    }

    fun markSuccess(context: Context, indexedCount: Int, eligibleCount: Int = indexedCount) {
        context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_INDEXED, indexedCount.coerceAtLeast(0))
            .putInt(PREF_ELIGIBLE, eligibleCount.coerceAtLeast(0))
            .putInt(PREF_PENDING, 0)
            .putInt(PREF_FAILED, 0)
            .putLong(PREF_LAST_SUCCESS, System.currentTimeMillis())
            .apply()
    }

    fun markFailure(context: Context) {
        val current = load(context)
        context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_PENDING, 0)
            .putInt(PREF_FAILED, (current.failedCount + 1).coerceAtMost(999))
            .apply()
    }

    fun updateObservedCounts(context: Context, indexedCount: Int, eligibleCount: Int) {
        context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_INDEXED, indexedCount.coerceAtLeast(0))
            .putInt(PREF_ELIGIBLE, eligibleCount.coerceAtLeast(0))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_INDEXED)
            .remove(PREF_ELIGIBLE)
            .remove(PREF_PENDING)
            .remove(PREF_FAILED)
            .remove(PREF_LAST_SUCCESS)
            .apply()
    }
}
