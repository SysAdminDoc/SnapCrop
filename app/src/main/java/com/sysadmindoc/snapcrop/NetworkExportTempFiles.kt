package com.sysadmindoc.snapcrop

import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

object NetworkExportTempFiles {
    const val MAX_TEMP_BYTES = 256L * 1024L * 1024L
    private const val STALE_AGE_MS = 24L * 60L * 60L * 1000L

    fun create(cacheDirectory: File, suffix: String, now: Long = System.currentTimeMillis()): File {
        val directory = cacheDirectory.resolve(CacheCleanupPolicy.NETWORK_EXPORT_DIRECTORY)
        check(directory.mkdirs() || directory.isDirectory) { "Could not create network export cache" }
        purge(directory, now)
        return File.createTempFile("export-", suffix, directory)
    }

    internal fun purge(directory: File, now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { it.isFile && now - it.lastModified() >= STALE_AGE_MS }
            ?.forEach(File::delete)
    }

    fun boundedOutput(file: File, maximumBytes: Long = MAX_TEMP_BYTES): OutputStream =
        object : FilterOutputStream(FileOutputStream(file).buffered()) {
            private var written = 0L

            override fun write(value: Int) {
                ensureCapacity(1)
                out.write(value)
                written++
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                ensureCapacity(length)
                out.write(buffer, offset, length)
                written += length
            }

            private fun ensureCapacity(additional: Int) {
                if (written + additional > maximumBytes) throw IllegalStateException("Temporary export exceeds 256 MiB")
            }
        }
}
