package com.sysadmindoc.snapcrop

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatchWorkflowRunnerTest {
    @Test
    fun everyTerminalOutcomeHasOneSummaryBucket() = runBlocking {
        val outcomes = BatchItemOutcome.entries
        val uris = outcomes.mapIndexed { index, _ -> Uri.parse("content://media/$index") }
        val progress = mutableListOf<Pair<Int, Int>>()

        val summary = BatchWorkflowRunner.run(
            uris = uris,
            cancelled = { false },
            onProgress = { completed, total -> progress += completed to total },
            process = { uri -> outcomes[uri.lastPathSegment!!.toInt()] },
        )

        assertEquals(6, summary.total)
        assertEquals(1, summary.saved)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.oversized)
        assertEquals(1, summary.unreadable)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.cancelled)
        assertEquals((0..5).map { it to 6 }, progress)
    }

    @Test
    fun overflowIsRejectedBeforeProcessingAndNotReportedAsCancellation() = runBlocking {
        val uris = (0 until BatchImageIntake.MAX_ITEMS + 5)
            .map { Uri.parse("content://media/$it") }
        var processed = 0

        val summary = BatchWorkflowRunner.run(
            uris = uris,
            cancelled = { false },
            onProgress = { _, _ -> },
            process = { processed++; BatchItemOutcome.SAVED },
        )

        assertEquals(BatchImageIntake.MAX_ITEMS, processed)
        assertEquals(BatchImageIntake.MAX_ITEMS, summary.saved)
        assertEquals(5, summary.oversized)
        assertEquals(0, summary.cancelled)
    }

    @Test
    fun cancellationBeforeAndDuringProcessingCountsEveryUnfinishedItem() = runBlocking {
        val uris = (0 until 5).map { Uri.parse("content://media/$it") }
        var progressCalls = 0
        val before = BatchWorkflowRunner.run(
            uris = uris,
            cancelled = { true },
            onProgress = { _, _ -> progressCalls++ },
            process = { BatchItemOutcome.SAVED },
        )
        assertEquals(5, before.cancelled)
        assertEquals(0, progressCalls)

        var processed = 0
        val during = BatchWorkflowRunner.run(
            uris = uris,
            cancelled = { false },
            onProgress = { _, _ -> },
            process = {
                processed++
                if (processed == 3) BatchItemOutcome.CANCELLED else BatchItemOutcome.SAVED
            },
        )
        assertEquals(2, during.saved)
        assertEquals(3, during.cancelled)
        assertEquals(3, processed)
    }
}
