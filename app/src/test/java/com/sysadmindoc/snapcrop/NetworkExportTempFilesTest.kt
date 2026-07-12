package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkExportTempFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createPurgesStaleFilesButPreservesRecentFilesAndDirectories() {
        val cache = temporaryFolder.newFolder("cache")
        val directory = cache.resolve("network-export").apply { mkdirs() }
        val now = 2L * 24L * 60L * 60L * 1000L
        val stale = directory.resolve("stale.pdf").apply { writeText("stale"); setLastModified(0) }
        val recent = directory.resolve("recent.pdf").apply { writeText("recent"); setLastModified(now) }
        val nested = directory.resolve("keep-directory").apply { mkdirs(); setLastModified(0) }

        val created = NetworkExportTempFiles.create(cache, ".pdf", now)

        assertFalse(stale.exists())
        assertTrue(recent.exists())
        assertTrue(nested.isDirectory)
        assertTrue(created.isFile)
    }

    @Test
    fun boundedOutputStopsBeforeConfiguredLimitIsExceeded() {
        val file = temporaryFolder.newFile("bounded.pdf")

        assertThrows(IllegalStateException::class.java) {
            NetworkExportTempFiles.boundedOutput(file, maximumBytes = 16).use { output ->
                output.write(ByteArray(17))
            }
        }
        assertTrue(file.length() <= 16)
    }
}
