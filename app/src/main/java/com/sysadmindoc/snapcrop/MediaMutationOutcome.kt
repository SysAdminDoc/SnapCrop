package com.sysadmindoc.snapcrop

enum class MediaMutationResult {
    SUCCESS,
    PARTIAL,
    RETAINED
}

data class MediaMutationOutcome(
    val requested: Int,
    val succeeded: Int
) {
    init {
        require(requested >= 0)
        require(succeeded in 0..requested)
    }

    val retained: Int = requested - succeeded

    val result: MediaMutationResult = when {
        succeeded == 0 -> MediaMutationResult.RETAINED
        succeeded == requested -> MediaMutationResult.SUCCESS
        else -> MediaMutationResult.PARTIAL
    }

    internal val diagnosticResult: DiagnosticResult
        get() = when (result) {
            MediaMutationResult.SUCCESS -> DiagnosticResult.SUCCESS
            MediaMutationResult.PARTIAL -> DiagnosticResult.PARTIAL
            MediaMutationResult.RETAINED -> DiagnosticResult.FAILED
        }

    internal val diagnosticPartial: DiagnosticPartial.Counts?
        get() = if (result == MediaMutationResult.PARTIAL) {
            DiagnosticPartial.Counts(requested, succeeded, retained)
        } else {
            null
        }

    internal fun messageResource(scopedTrash: Boolean): Int = when {
        result == MediaMutationResult.RETAINED -> R.string.toast_items_retained
        scopedTrash && result == MediaMutationResult.PARTIAL -> R.string.toast_trashed_partial
        scopedTrash -> R.string.toast_trashed_count
        result == MediaMutationResult.PARTIAL -> R.string.toast_deleted_partial
        else -> R.string.toast_deleted_items_count
    }
}

internal object MediaMutationMetadataPolicy {
    fun cleanImmediately(platformSdk: Int): Boolean = platformSdk < 30
}
