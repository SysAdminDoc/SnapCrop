package com.sysadmindoc.snapcrop

import android.content.Context
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OperationJournalTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        OperationJournal.clear(context)
        OperationJournal.setEnabled(context, true)
    }

    @After
    fun tearDown() {
        OperationJournal.clear(context)
    }

    @Test
    fun recordsOnlyTypedMetadataAndNeverExceptionContent() {
        val secret = "content://private/path bearer-secret OCR words"
        assertTrue(
            OperationJournal.record(
                context,
                DiagnosticOperation.EXPORT,
                DiagnosticStage.SAVE,
                DiagnosticResult.FAILED,
                code = DiagnosticCode.ENCODE_FAILURE,
                error = IllegalStateException(secret)
            )
        )

        val text = OperationJournal.formatted(context)
        assertTrue(text.contains("EXPORT/SAVE FAILED"))
        assertTrue(text.contains("errorClass=IllegalStateException"))
        assertTrue(text.contains("code=ENCODE_FAILURE"))
        assertFalse(text.contains(secret))
        assertFalse(text.contains("content://"))
    }

    @Test
    fun ringIsCountAndSizeBounded() {
        repeat(OperationJournal.MAX_EVENTS + 40) {
            OperationJournal.record(
                context,
                DiagnosticOperation.QUICK_CROP,
                DiagnosticStage.PROCESS,
                DiagnosticResult.SUCCESS
            )
        }

        val file = File(context.filesDir, "diagnostics/operation_journal.jsonl")
        assertEquals(OperationJournal.MAX_EVENTS, OperationJournal.events(context).size)
        assertTrue(file.length() <= OperationJournal.MAX_BYTES)
    }

    @Test
    fun disablingPurgesAndPreventsWrites() {
        OperationJournal.record(context, DiagnosticOperation.MODEL, DiagnosticStage.PROCESS, DiagnosticResult.FAILED)
        OperationJournal.setEnabled(context, false)

        assertTrue(OperationJournal.events(context).isEmpty())
        assertFalse(OperationJournal.record(context, DiagnosticOperation.DELETE, DiagnosticStage.COMPLETE, DiagnosticResult.SUCCESS))
        assertTrue(OperationJournal.events(context).isEmpty())
    }

    @Test
    fun corruptOversizedJournalFailsClosed() {
        val file = File(context.filesDir, "diagnostics/operation_journal.jsonl").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(OperationJournal.MAX_BYTES + 1) { 'x'.code.toByte() })
        }

        assertTrue(OperationJournal.events(context).isEmpty())
        assertTrue(OperationJournal.record(context, DiagnosticOperation.EXPORT, DiagnosticStage.SAVE, DiagnosticResult.SUCCESS))
        assertEquals(1, OperationJournal.events(context).size)
        assertTrue(file.length() <= OperationJournal.MAX_BYTES)
    }

    @Test
    fun dropsExpiredAndMalformedLinesButKeepsValidEvents() {
        val now = System.currentTimeMillis()
        val file = File(context.filesDir, "diagnostics/operation_journal.jsonl").apply {
            parentFile?.mkdirs()
            writeText(
                listOf(
                    eventJson(now - 20L * 24 * 60 * 60 * 1000),
                    "{truncated",
                    eventJson(now)
                ).joinToString("\n", postfix = "\n")
            )
        }

        val events = OperationJournal.events(context)
        assertEquals(1, events.size)
        assertEquals(DiagnosticOperation.EXPORT, events.single().operation)
        assertTrue(file.length() <= OperationJournal.MAX_BYTES)
    }

    @Test
    fun durationIsClampedAcrossClockAnomalies() {
        OperationJournal.record(
            context, DiagnosticOperation.MODEL, DiagnosticStage.PROCESS, DiagnosticResult.SUCCESS,
            startedAtElapsedMs = Long.MIN_VALUE
        )
        OperationJournal.record(
            context, DiagnosticOperation.MODEL, DiagnosticStage.PROCESS, DiagnosticResult.SUCCESS,
            startedAtElapsedMs = Long.MAX_VALUE
        )

        val durations = OperationJournal.events(context).map { it.durationMs }
        assertEquals(listOf(24 * 60 * 60 * 1000L, 0L), durations)
    }

    @Test
    fun concurrentWritersKeepValidBoundedOutput() {
        val threads = List(4) {
            Thread {
                repeat(30) {
                    OperationJournal.record(
                        context, DiagnosticOperation.QUICK_CROP, DiagnosticStage.PROCESS,
                        DiagnosticResult.SUCCESS
                    )
                }
            }.apply { start() }
        }
        threads.forEach(Thread::join)

        assertEquals(120, OperationJournal.events(context).size)
    }

    @Test
    fun storageFailureIsReturnedWithoutThrowing() {
        OperationJournal.clear(context)
        val diagnostics = File(context.filesDir, "diagnostics").apply {
            deleteRecursively()
            writeText("not a directory")
        }
        try {
            assertFalse(
                OperationJournal.record(
                    context, DiagnosticOperation.EXPORT, DiagnosticStage.SAVE,
                    DiagnosticResult.FAILED
                )
            )
        } finally {
            diagnostics.delete()
        }
    }

    private fun eventJson(timestampMs: Long): String = JSONObject()
        .put("t", timestampMs)
        .put("o", DiagnosticOperation.EXPORT.name)
        .put("s", DiagnosticStage.SAVE.name)
        .put("d", 10)
        .put("r", DiagnosticResult.SUCCESS.name)
        .put("c", DiagnosticCode.NONE.name)
        .toString()
}
