package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import java.util.concurrent.CancellationException

internal enum class VideoLoadFailure {
    SOURCE_UNAVAILABLE,
    INVALID_DURATION,
    FRAME_UNAVAILABLE,
    RETRIEVER_FAILURE,
}

internal sealed interface VideoMetadataResult {
    data class Ready(val durationMs: Long) : VideoMetadataResult
    data class Failed(val reason: VideoLoadFailure, val cause: Throwable? = null) : VideoMetadataResult
}

internal sealed interface VideoFrameResult {
    data class Ready(val bitmap: Bitmap) : VideoFrameResult
    data class Failed(val reason: VideoLoadFailure, val cause: Throwable? = null) : VideoFrameResult
}

internal object VideoClipLoader {
    fun metadata(reader: () -> Long): VideoMetadataResult = try {
        val duration = reader()
        if (duration > 0L) VideoMetadataResult.Ready(duration)
        else VideoMetadataResult.Failed(VideoLoadFailure.INVALID_DURATION)
    } catch (error: Throwable) {
        rethrowFatal(error)
        VideoMetadataResult.Failed(VideoLoadFailure.RETRIEVER_FAILURE, error)
    }

    fun frame(reader: () -> Bitmap?): VideoFrameResult = try {
        reader()?.let(VideoFrameResult::Ready)
            ?: VideoFrameResult.Failed(VideoLoadFailure.FRAME_UNAVAILABLE)
    } catch (error: Throwable) {
        rethrowFatal(error)
        VideoFrameResult.Failed(VideoLoadFailure.RETRIEVER_FAILURE, error)
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is CancellationException || error is Error) throw error
    }
}
