package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheCleanupPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cleanupRemovesOnlyStaleAllowlistedArtifacts() {
        val cache = temporaryFolder.newFolder("cache")
        val now = 3L * CacheCleanupPolicy.STALE_AGE_MS
        val staleTime = now - CacheCleanupPolicy.STALE_AGE_MS - 1L
        val disposable = listOf("shared_crops", "clipboard", "share_clean/session", "network-export")
            .mapIndexed { index, directory -> staleFile(cache, directory, "stale-$index", staleTime) }
        val disposableBytes = disposable.sumOf(File::length)
        val recent = file(cache, "shared_crops", "active-share", now)
        val protected = listOf("step-capture", "long_screenshots", "diagnostics", "web_capture", "other")
            .mapIndexed { index, directory -> staleFile(cache, directory, "protected-$index", staleTime) }

        val result = CacheCleanupPolicy.cleanup(cache, now)

        assertEquals(CacheCleanupStatus.SUCCESS, result.status)
        assertEquals(disposable.size, result.deletedItems)
        assertEquals(disposableBytes, result.deletedBytes)
        assertEquals(1, result.retainedItems)
        assertEquals(0, result.failedItems)
        disposable.forEach { assertFalse(it.exists()) }
        assertTrue(recent.exists())
        protected.forEach { assertTrue(it.exists()) }
    }

    @Test
    fun cleanupReportsPartialFailureAndCountsRetainedArtifact() {
        val cache = temporaryFolder.newFolder("partial")
        val now = 3L * CacheCleanupPolicy.STALE_AGE_MS
        val staleTime = now - CacheCleanupPolicy.STALE_AGE_MS - 1L
        val removable = staleFile(cache, "network-export", "remove.pdf", staleTime)
        val locked = staleFile(cache, "clipboard", "locked.png", staleTime)

        val result = CacheCleanupPolicy.cleanup(cache, now) { entry ->
            if (entry == locked) false else entry.delete()
        }

        assertEquals(CacheCleanupStatus.PARTIAL, result.status)
        assertEquals(1, result.deletedItems)
        assertEquals(1, result.retainedItems)
        assertEquals(1, result.failedItems)
        assertFalse(removable.exists())
        assertTrue(locked.exists())
    }

    @Test
    fun cleanupReportsFailureWhenEveryStaleArtifactIsRetained() {
        val cache = temporaryFolder.newFolder("failure")
        val now = 3L * CacheCleanupPolicy.STALE_AGE_MS
        val staleTime = now - CacheCleanupPolicy.STALE_AGE_MS - 1L
        val locked = staleFile(cache, "shared_crops", "locked.png", staleTime)

        val result = CacheCleanupPolicy.cleanup(cache, now) { false }

        assertEquals(CacheCleanupStatus.FAILURE, result.status)
        assertEquals(0, result.deletedItems)
        assertEquals(1, result.retainedItems)
        assertEquals(1, result.failedItems)
        assertTrue(locked.exists())
    }

    @Test
    fun settingsRoutesCleanupThroughTheAllowlistWithoutDeletingTheCacheRoot() {
        val settings = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/SettingsActivity.kt")

        assertTrue(settings.contains("CacheCleanupPolicy.cleanup(cacheDir)"))
        assertFalse(settings.contains("cacheDir.deleteRecursively()"))
    }

    private fun staleFile(cache: File, directory: String, name: String, modified: Long): File =
        file(cache, directory, name, modified)

    private fun file(cache: File, directory: String, name: String, modified: Long): File =
        cache.resolve(directory).apply { mkdirs() }.resolve(name).apply {
            writeText(name)
            assertTrue(setLastModified(modified))
        }

    private fun sourceFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }
}
