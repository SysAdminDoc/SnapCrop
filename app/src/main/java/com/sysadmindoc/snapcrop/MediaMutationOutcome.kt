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
}
