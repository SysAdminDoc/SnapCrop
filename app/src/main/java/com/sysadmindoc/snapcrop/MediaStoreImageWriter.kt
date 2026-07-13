package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

/**
 * Publishes one encoded image as an all-or-nothing MediaStore transaction.
 *
 * The row remains pending until the encoder has succeeded, emitted at least one byte, and any
 * caller-supplied pre-publish work has completed. Every failure after insertion attempts to delete
 * the pending row before returning a typed failure.
 */
internal object MediaStoreImageWriter {
    data class Request(
        val displayName: String,
        val mimeType: String,
        val relativePath: String,
    )

    enum class Stage {
        INSERT,
        OPEN_STREAM,
        ENCODE,
        EMPTY_OUTPUT,
        BEFORE_PUBLISH,
        PUBLISH,
    }

    sealed interface Result {
        data class Success(val uri: Uri, val bytesWritten: Long) : Result

        data class Failure(
            val stage: Stage,
            val cause: Throwable? = null,
            val cleanupAttempted: Boolean = false,
            val cleanupSucceeded: Boolean = false,
        ) : Result
    }

    internal interface Gateway {
        fun insert(request: Request): Uri?
        fun openOutputStream(uri: Uri): OutputStream?
        fun publish(uri: Uri): Int
        fun delete(uri: Uri): Int
    }

    fun write(
        resolver: ContentResolver,
        request: Request,
        beforePublish: (Uri) -> Unit = {},
        encoder: (OutputStream) -> Boolean,
    ): Result = write(ContentResolverGateway(resolver), request, beforePublish, encoder)

    internal fun write(
        gateway: Gateway,
        request: Request,
        beforePublish: (Uri) -> Unit = {},
        encoder: (OutputStream) -> Boolean,
    ): Result {
        val uri = try {
            gateway.insert(request)
        } catch (error: Throwable) {
            rethrowFatal(error)
            return Result.Failure(Stage.INSERT, error)
        } ?: return Result.Failure(Stage.INSERT)

        val output = try {
            gateway.openOutputStream(uri)
        } catch (error: Throwable) {
            return failureAfterInsert(gateway, uri, Stage.OPEN_STREAM, error)
        } ?: return failureAfterInsert(gateway, uri, Stage.OPEN_STREAM)

        var bytesWritten = 0L
        val encoded = try {
            output.use { rawOutput ->
                val countingOutput = CountingOutputStream(rawOutput)
                val succeeded = encoder(countingOutput)
                countingOutput.flush()
                bytesWritten = countingOutput.bytesWritten
                succeeded
            }
        } catch (error: Throwable) {
            return failureAfterInsert(gateway, uri, Stage.ENCODE, error)
        }

        if (!encoded) return failureAfterInsert(gateway, uri, Stage.ENCODE)
        if (bytesWritten <= 0L) return failureAfterInsert(gateway, uri, Stage.EMPTY_OUTPUT)

        try {
            beforePublish(uri)
        } catch (error: Throwable) {
            return failureAfterInsert(gateway, uri, Stage.BEFORE_PUBLISH, error)
        }

        val updated = try {
            gateway.publish(uri)
        } catch (error: Throwable) {
            return failureAfterInsert(gateway, uri, Stage.PUBLISH, error)
        }
        if (updated != 1) return failureAfterInsert(gateway, uri, Stage.PUBLISH)

        return Result.Success(uri, bytesWritten)
    }

    private fun failureAfterInsert(
        gateway: Gateway,
        uri: Uri,
        stage: Stage,
        cause: Throwable? = null,
    ): Result.Failure {
        val cleanupSucceeded = try {
            gateway.delete(uri) == 1
        } catch (_: Exception) {
            false
        }
        val failure = Result.Failure(
            stage = stage,
            cause = cause,
            cleanupAttempted = true,
            cleanupSucceeded = cleanupSucceeded,
        )
        cause?.let(::rethrowFatal)
        return failure
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is CancellationException || error is Error) throw error
    }

    private class ContentResolverGateway(
        private val resolver: ContentResolver,
    ) : Gateway {
        override fun insert(request: Request): Uri? {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, request.displayName)
                put(MediaStore.Images.Media.MIME_TYPE, request.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, request.relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }

        override fun openOutputStream(uri: Uri): OutputStream? =
            resolver.openOutputStream(uri)

        override fun publish(uri: Uri): Int = resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )

        override fun delete(uri: Uri): Int = resolver.delete(uri, null, null)
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            bytesWritten++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            bytesWritten += length
        }
    }
}
