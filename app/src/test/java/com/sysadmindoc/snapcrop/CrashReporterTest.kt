package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
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
        val text = file.readText()
        assertTrue(text.contains("boom-xyz"))
        assertTrue(text.contains("SnapCrop "))
        assertTrue(text.contains("Android: "))
        assertEquals(1, CrashReporter.crashLogs(context).size)
    }

    @Test
    fun retainsAtMostFiveMostRecentLogs() {
        repeat(8) { i ->
            // Distinct lastModified ordering via the filename timestamp is enough for the cap check.
            CrashReporter.record(context, Thread.currentThread(), RuntimeException("crash-$i"))
            Thread.sleep(2)
        }
        assertTrue(CrashReporter.crashLogs(context).size <= 5)
    }

    @Test
    fun clearRemovesAllLogs() {
        CrashReporter.record(context, Thread.currentThread(), RuntimeException("x"))
        CrashReporter.clear(context)
        assertTrue(CrashReporter.crashLogs(context).isEmpty())
    }
}
