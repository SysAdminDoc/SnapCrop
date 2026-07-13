package com.sysadmindoc.snapcrop

import android.net.Uri

internal enum class BatchItemOutcome {
    SAVED,
    SKIPPED,
    OVERSIZED,
    UNREADABLE,
    FAILED,
    CANCELLED,
}

internal data class BatchRunSummary(
    val total: Int,
    val saved: Int,
    val skipped: Int,
    val oversized: Int,
    val unreadable: Int,
    val failed: Int,
    val cancelled: Int,
)

internal object BatchWorkflowRunner {
    suspend fun run(
        uris: List<Uri>,
        cancelled: () -> Boolean,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
        process: suspend (Uri) -> BatchItemOutcome,
    ): BatchRunSummary {
        val accepted = uris.take(BatchImageIntake.MAX_ITEMS)
        var saved = 0
        var skipped = 0
        var oversized = uris.size - accepted.size
        var unreadable = 0
        var failed = 0

        for ((index, uri) in accepted.withIndex()) {
            if (cancelled()) break
            onProgress(index, uris.size)
            when (process(uri)) {
                BatchItemOutcome.SAVED -> saved++
                BatchItemOutcome.SKIPPED -> skipped++
                BatchItemOutcome.OVERSIZED -> oversized++
                BatchItemOutcome.UNREADABLE -> unreadable++
                BatchItemOutcome.FAILED -> failed++
                BatchItemOutcome.CANCELLED -> break
            }
        }

        return BatchRunSummary(
            total = uris.size,
            saved = saved,
            skipped = skipped,
            oversized = oversized,
            unreadable = unreadable,
            failed = failed,
            cancelled = (uris.size - saved - skipped - oversized - unreadable - failed).coerceAtLeast(0),
        )
    }
}
