package com.sysadmindoc.snapcrop

import java.io.File
import java.util.concurrent.CancellationException

/** Writes and dispatches one cache-backed export without publishing partial artifacts. */
internal object CropCacheArtifactPublisher {
    enum class Stage {
        WRITE,
        EMPTY_OUTPUT,
        DISPATCH,
    }

    sealed interface Result {
        data class Success(val file: File, val bytesWritten: Long) : Result

        data class Failure(
            val stage: Stage,
            val cause: Throwable? = null,
            val cleanupAttempted: Boolean = false,
            val cleanupSucceeded: Boolean = false,
        ) : Result
    }

    suspend fun publish(
        file: File,
        writer: (File) -> Boolean,
        dispatcher: suspend (File) -> Unit,
    ): Result {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return Result.Failure(Stage.WRITE)
        }
        if (file.exists() && !file.delete()) {
            return Result.Failure(Stage.WRITE)
        }

        val wrote = try {
            writer(file)
        } catch (error: Throwable) {
            return failure(file, Stage.WRITE, error)
        }
        if (!wrote) return failure(file, Stage.WRITE)
        if (!file.isFile || file.length() <= 0L) return failure(file, Stage.EMPTY_OUTPUT)

        try {
            dispatcher(file)
        } catch (error: Throwable) {
            return failure(file, Stage.DISPATCH, error)
        }
        return Result.Success(file, file.length())
    }

    private fun failure(file: File, stage: Stage, cause: Throwable? = null): Result.Failure {
        val cleanupAttempted = file.exists()
        val cleanupSucceeded = !cleanupAttempted || runCatching { file.delete() }.getOrDefault(false)
        val failure = Result.Failure(
            stage = stage,
            cause = cause,
            cleanupAttempted = cleanupAttempted,
            cleanupSucceeded = cleanupSucceeded,
        )
        cause?.let(::rethrowFatal)
        return failure
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is CancellationException || error is Error) throw error
    }
}
