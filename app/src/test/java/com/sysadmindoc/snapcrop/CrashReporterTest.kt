package com.sysadmindoc.snapcrop

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashReporterTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        CrashReporter.clear(context)
    }

    @Test
    fun recordWritesReadableReport() {
        val file = CrashReporter.record(context, Thread.currentThread(), IllegalStateException("boom-xyz"))
        assertTrue(file.exists())
        val text = checkNotNull(CrashReporter.readReport(file))
        assertTrue(text.contains("boom-xyz"))
        assertTrue(text.contains("SnapCrop "))
        assertTrue(text.contains("Android: "))
        assertTrue(text.contains("Thread category: "))
        assertFalse(text.contains("Device: "))
        assertTrue(file.length() <= CrashReporter.MAX_REPORT_BYTES)
        assertFalse(file.parentFile?.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        assertEquals(1, CrashReporter.crashLogs(context).size)
    }

    @Test
    fun redactsLocationsEndpointsAndQueryValuesButKeepsStackSymbols() {
        val secretMessage = "Open content://media/private/123 from C:\\Users\\Alice\\secret.png " +
            "or /data/user/0/app/files/secret and /secret.txt at api.example.com/v1?token=abc " +
            "for alice@example.org through \\\\server\\share\\private.txt query=hello"
        val error = IllegalStateException(secretMessage).apply {
            stackTrace = arrayOf(StackTraceElement("com.example.CropEngine", "save", "CropEngine.kt", 42))
        }

        val text = checkNotNull(
            CrashReporter.readReport(CrashReporter.record(context, Thread.currentThread(), error))
        )

        assertTrue(text.contains("[redacted-uri]"))
        assertTrue(text.contains("[redacted-path]"))
        assertTrue(text.contains("[redacted-endpoint]"))
        assertTrue(text.contains("query=[redacted-query-value]"))
        listOf(
            "media/private", "Alice", "secret.png", "/secret.txt", "api.example.com",
            "token=abc", "alice@example.org", "server", "private.txt", "hello",
        ).forEach {
            assertFalse(text.contains(it))
        }
        assertTrue(text.contains("at com.example.CropEngine.save(line 42)"))
        assertFalse(text.contains("CropEngine.kt"))
    }

    @Test
    fun retainsAtMostFiveMostRecentLogs() {
        repeat(8) { i ->
            // Distinct lastModified ordering via the filename timestamp is enough for the cap check.
            CrashReporter.record(context, Thread.currentThread(), RuntimeException("crash-$i"))
            Thread.sleep(2)
        }
        assertTrue(CrashReporter.crashLogs(context).size <= CrashReporter.MAX_LOGS)
    }

    @Test
    fun agePerFileAndTotalByteCeilingsAreEnforced() {
        val expired = CrashReporter.record(context, Thread.currentThread(), RuntimeException("expired"))
        expired.setLastModified(System.currentTimeMillis() - CrashReporter.RETENTION_MS - 1_000)
        assertTrue(CrashReporter.crashLogs(context).isEmpty())

        val dir = expired.parentFile
        repeat(4) { index ->
            File(dir, "crash_budget_$index.txt").apply {
                writeBytes(ByteArray(CrashReporter.MAX_REPORT_BYTES - 100) { 'x'.code.toByte() })
                setLastModified(System.currentTimeMillis() - index)
            }
        }
        val retained = CrashReporter.crashLogs(context)
        assertTrue(retained.all { it.length() <= CrashReporter.MAX_REPORT_BYTES })
        assertTrue(retained.sumOf(File::length) <= CrashReporter.MAX_TOTAL_BYTES)
    }

    @Test
    fun oversizedRenderedStackIsBoundedAndStillReadable() {
        val error = RuntimeException("bounded").apply {
            stackTrace = Array(500) { index ->
                StackTraceElement("com.example.${"LongClass".repeat(30)}", "frame$index", "Secret.kt", index)
            }
        }
        val file = CrashReporter.record(context, Thread.currentThread(), error)

        assertTrue(file.length() <= CrashReporter.MAX_REPORT_BYTES)
        assertNotNull(CrashReporter.readReport(file))
        assertTrue(checkNotNull(CrashReporter.readReport(file)).contains("frame(s) omitted"))
    }

    @Test
    fun clearReportsCompletePartialAndFailedOutcomesFromDisk() {
        CrashReporter.record(context, Thread.currentThread(), RuntimeException("x"))
        val complete = CrashReporter.clear(context)
        assertEquals(CrashClearStatus.COMPLETE, complete.status)
        assertEquals(1, complete.deleted)
        assertTrue(complete.remaining.isEmpty())

        val first = CrashReporter.record(context, Thread.currentThread(), RuntimeException("first"))
        Thread.sleep(2)
        CrashReporter.record(context, Thread.currentThread(), RuntimeException("second"))
        val partial = CrashReporter.clear(context) { file ->
            if (file == first) false else file.delete()
        }
        assertEquals(CrashClearStatus.PARTIAL, partial.status)
        assertEquals(2, partial.requested)
        assertEquals(1, partial.deleted)
        assertEquals(1, partial.retained)
        assertEquals(CrashReporter.crashLogs(context), partial.remaining)

        val failed = CrashReporter.clear(context) { false }
        assertEquals(CrashClearStatus.FAILED, failed.status)
        assertEquals(0, failed.deleted)
        assertEquals(1, failed.retained)
        assertEquals(CrashReporter.crashLogs(context), failed.remaining)
    }
}
