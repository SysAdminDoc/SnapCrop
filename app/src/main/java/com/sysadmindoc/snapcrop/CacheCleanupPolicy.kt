package com.sysadmindoc.snapcrop

import java.io.File
import java.nio.file.Files

internal enum class CacheCleanupStatus {
    SUCCESS,
    PARTIAL,
    FAILURE,
}

internal data class CacheCleanupResult(
    val status: CacheCleanupStatus,
    val deletedItems: Int,
    val deletedBytes: Long,
    val retainedItems: Int,
    val failedItems: Int,
)

/**
 * Removes only old, disposable export artifacts. Workflow recovery stores are deliberately absent
 * from the allowlist so Settings cannot invalidate an active capture or editor handoff.
 */
internal object CacheCleanupPolicy {
    internal const val STALE_AGE_MS = 24L * 60L * 60L * 1000L
    internal const val SHARED_CROPS_DIRECTORY = "shared_crops"
    internal const val CLIPBOARD_DIRECTORY = "clipboard"
    internal const val SANITIZED_SHARE_DIRECTORY = "share_clean"
    internal const val NETWORK_EXPORT_DIRECTORY = "network-export"

    private val disposableDirectories = listOf(
        SHARED_CROPS_DIRECTORY,
        CLIPBOARD_DIRECTORY,
        SANITIZED_SHARE_DIRECTORY,
        NETWORK_EXPORT_DIRECTORY,
    )

    fun cleanup(
        cacheDirectory: File,
        now: Long = System.currentTimeMillis(),
        deleteEntry: (File) -> Boolean = { it.delete() },
    ): CacheCleanupResult {
        val totals = Totals()
        disposableDirectories.forEach { directoryName ->
            val root = cacheDirectory.resolve(directoryName)
            if (!root.exists()) return@forEach
            if (root.isDirectory && !isSymbolicLink(root)) {
                cleanupDirectory(root, now, deleteEntry, totals, isRoot = true)
            } else {
                cleanupLeaf(root, now, deleteEntry, totals)
            }
        }
        val status = when {
            totals.failedItems == 0 -> CacheCleanupStatus.SUCCESS
            totals.deletedItems > 0 -> CacheCleanupStatus.PARTIAL
            else -> CacheCleanupStatus.FAILURE
        }
        return CacheCleanupResult(
            status = status,
            deletedItems = totals.deletedItems,
            deletedBytes = totals.deletedBytes,
            retainedItems = totals.retainedItems,
            failedItems = totals.failedItems,
        )
    }

    private fun cleanupDirectory(
        directory: File,
        now: Long,
        deleteEntry: (File) -> Boolean,
        totals: Totals,
        isRoot: Boolean,
    ) {
        val children = runCatching { directory.listFiles() }.getOrNull()
        if (children == null) {
            if (directory.exists()) totals.retain(failed = true)
            return
        }
        children.forEach { entry ->
            if (entry.isDirectory && !isSymbolicLink(entry)) {
                cleanupDirectory(entry, now, deleteEntry, totals, isRoot = false)
            } else {
                cleanupLeaf(entry, now, deleteEntry, totals)
            }
        }
        if (!isRoot && isStale(directory, now) && directory.list()?.isEmpty() == true) {
            runCatching { deleteEntry(directory) }
        }
    }

    private fun cleanupLeaf(
        entry: File,
        now: Long,
        deleteEntry: (File) -> Boolean,
        totals: Totals,
    ) {
        if (!isStale(entry, now)) {
            totals.retain()
            return
        }
        val bytes = entry.length().coerceAtLeast(0L)
        val deleted = runCatching { deleteEntry(entry) }.getOrDefault(false)
        if (deleted) {
            totals.deletedItems++
            totals.deletedBytes += bytes
        } else if (entry.exists()) {
            totals.retain(failed = true)
        }
    }

    private fun isStale(entry: File, now: Long): Boolean {
        val modified = entry.lastModified()
        return modified >= 0L && now >= modified && now - modified >= STALE_AGE_MS
    }

    private fun isSymbolicLink(entry: File): Boolean =
        runCatching { Files.isSymbolicLink(entry.toPath()) }.getOrDefault(true)

    private class Totals {
        var deletedItems = 0
        var deletedBytes = 0L
        var retainedItems = 0
        var failedItems = 0

        fun retain(failed: Boolean = false) {
            retainedItems++
            if (failed) failedItems++
        }
    }
}
