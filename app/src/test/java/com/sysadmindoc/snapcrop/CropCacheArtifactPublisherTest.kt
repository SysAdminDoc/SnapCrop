package com.sysadmindoc.snapcrop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CropCacheArtifactPublisherTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("snapcrop-cache-publisher").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun successfulWriteDispatchesExactArtifactOnce() = runBlocking {
        val file = File(directory, "clip.png")
        var dispatchCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.writeBytes(byteArrayOf(1, 2, 3)); true },
            dispatcher = { target ->
                dispatchCalls++
                assertEquals(listOf<Byte>(1, 2, 3), target.readBytes().toList())
            },
        )

        assertEquals(CropCacheArtifactPublisher.Result.Success(file, 3), result)
        assertEquals(1, dispatchCalls)
        assertTrue(file.exists())
    }

    @Test
    fun falseWriterDeletesPartialArtifactAndNeverDispatches() = runBlocking {
        val file = File(directory, "partial.png")
        var dispatchCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.writeBytes(byteArrayOf(1)); false },
            dispatcher = { dispatchCalls++ },
        )

        assertFailure(result, CropCacheArtifactPublisher.Stage.WRITE)
        assertEquals(0, dispatchCalls)
        assertFalse(file.exists())
    }

    @Test
    fun emptyWriterDeletesArtifactAndNeverDispatches() = runBlocking {
        val file = File(directory, "empty.png")
        var dispatchCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.createNewFile() },
            dispatcher = { dispatchCalls++ },
        )

        assertFailure(result, CropCacheArtifactPublisher.Stage.EMPTY_OUTPUT)
        assertEquals(0, dispatchCalls)
        assertFalse(file.exists())
    }

    @Test
    fun throwingWriterDeletesPartialArtifactAndNeverDispatches() = runBlocking {
        val file = File(directory, "throw.png")
        var dispatchCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.writeBytes(byteArrayOf(1)); throw IOException("encode") },
            dispatcher = { dispatchCalls++ },
        )

        val failure = assertFailure(result, CropCacheArtifactPublisher.Stage.WRITE)
        assertEquals("encode", failure.cause?.message)
        assertEquals(0, dispatchCalls)
        assertFalse(file.exists())
    }

    @Test
    fun clipboardFailureIsIndependentAndDeletesArtifact() = runBlocking {
        val file = File(directory, "clipboard.png")
        var clipboardCalls = 0
        var shareCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.writeBytes(byteArrayOf(1)); true },
            dispatcher = {
                clipboardCalls++
                throw IOException("clipboard")
            },
        )

        val failure = assertFailure(result, CropCacheArtifactPublisher.Stage.DISPATCH)
        assertEquals("clipboard", failure.cause?.message)
        assertEquals(1, clipboardCalls)
        assertEquals(0, shareCalls)
        assertFalse(file.exists())
    }

    @Test
    fun shareFailureIsIndependentAndDeletesArtifact() = runBlocking {
        val file = File(directory, "share.png")
        var clipboardCalls = 0
        var shareCalls = 0

        val result = CropCacheArtifactPublisher.publish(
            file = file,
            writer = { target -> target.writeBytes(byteArrayOf(1)); true },
            dispatcher = {
                shareCalls++
                throw IOException("share")
            },
        )

        val failure = assertFailure(result, CropCacheArtifactPublisher.Stage.DISPATCH)
        assertEquals("share", failure.cause?.message)
        assertEquals(0, clipboardCalls)
        assertEquals(1, shareCalls)
        assertFalse(file.exists())
    }

    @Test
    fun clipboardAndShareSuccessInvokeOnlyRequestedDispatcher() = runBlocking {
        var clipboardCalls = 0
        var shareCalls = 0

        val clipboard = CropCacheArtifactPublisher.publish(
            file = File(directory, "clipboard-success.png"),
            writer = { target -> target.writeBytes(byteArrayOf(1)); true },
            dispatcher = { clipboardCalls++ },
        )
        val share = CropCacheArtifactPublisher.publish(
            file = File(directory, "share-success.png"),
            writer = { target -> target.writeBytes(byteArrayOf(2)); true },
            dispatcher = { shareCalls++ },
        )

        assertTrue(clipboard is CropCacheArtifactPublisher.Result.Success)
        assertTrue(share is CropCacheArtifactPublisher.Result.Success)
        assertEquals(1, clipboardCalls)
        assertEquals(1, shareCalls)
    }

    @Test
    fun cancellationDeletesPartialArtifactAndRethrows() = runBlocking {
        val file = File(directory, "cancel.png")

        try {
            CropCacheArtifactPublisher.publish(
                file = file,
                writer = { target ->
                    target.writeBytes(byteArrayOf(1))
                    throw CancellationException("cancel")
                },
                dispatcher = {},
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }

        assertFalse(file.exists())
    }

    private fun assertFailure(
        result: CropCacheArtifactPublisher.Result,
        stage: CropCacheArtifactPublisher.Stage,
    ): CropCacheArtifactPublisher.Result.Failure {
        val failure = result as CropCacheArtifactPublisher.Result.Failure
        assertEquals(stage, failure.stage)
        assertTrue(failure.cleanupSucceeded)
        return failure
    }
}
