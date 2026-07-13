package com.sysadmindoc.snapcrop

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreImageWriterTest {
    private val request = MediaStoreImageWriter.Request(
        displayName = "SnapCrop_Test.png",
        mimeType = "image/png",
        relativePath = "Pictures/SnapCrop",
    )

    @Test
    fun successfulWritePublishesExactlyOnce() {
        val gateway = FakeGateway()

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(byteArrayOf(1, 2, 3, 4))
            true
        }

        assertEquals(MediaStoreImageWriter.Result.Success(gateway.uri, 4L), result)
        assertEquals(1, gateway.insertCalls)
        assertEquals(1, gateway.openCalls)
        assertEquals(1, gateway.publishCalls)
        assertEquals(0, gateway.deleteCalls)
        assertEquals(listOf<Byte>(1, 2, 3, 4), gateway.output.toByteArray().toList())
    }

    @Test
    fun bitmapGoldenPathProducesReadablePixels() {
        val gateway = FakeGateway()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff336699.toInt())
        }

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val success = result as MediaStoreImageWriter.Result.Success
        assertTrue(success.bytesWritten > 0)
        val decoded = BitmapFactory.decodeByteArray(gateway.output.toByteArray(), 0, gateway.output.size())
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(0xff336699.toInt(), decoded.getPixel(0, 0))
        decoded.recycle()
        bitmap.recycle()
    }

    @Test
    fun nullInsertFailsWithoutCleanup() {
        val gateway = FakeGateway(insertedUri = null)

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        assertFailure(result, MediaStoreImageWriter.Stage.INSERT, cleanupAttempted = false)
        assertEquals(0, gateway.openCalls)
        assertEquals(0, gateway.deleteCalls)
    }

    @Test
    fun insertExceptionFailsWithoutCleanup() {
        val gateway = FakeGateway().apply { insertFailure = IOException("insert") }

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        assertFailure(result, MediaStoreImageWriter.Stage.INSERT, cleanupAttempted = false)
        assertEquals(0, gateway.deleteCalls)
    }

    @Test
    fun nullStreamDeletesPendingRow() {
        val gateway = FakeGateway().apply { stream = null }

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        assertFailure(result, MediaStoreImageWriter.Stage.OPEN_STREAM)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun streamOpenExceptionDeletesPendingRow() {
        val gateway = FakeGateway().apply { openFailure = IOException("open") }

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        assertFailure(result, MediaStoreImageWriter.Stage.OPEN_STREAM)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun encoderFalseDeletesPendingRow() {
        val gateway = FakeGateway()

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(1)
            false
        }

        assertFailure(result, MediaStoreImageWriter.Stage.ENCODE)
        assertEquals(1, gateway.deleteCalls)
        assertEquals(0, gateway.publishCalls)
    }

    @Test
    fun emptyEncoderOutputDeletesPendingRow() {
        val gateway = FakeGateway()

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        assertFailure(result, MediaStoreImageWriter.Stage.EMPTY_OUTPUT)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun encoderExceptionDeletesPendingRow() {
        val gateway = FakeGateway()

        val result = MediaStoreImageWriter.write(gateway, request) {
            throw IOException("encode")
        }

        assertFailure(result, MediaStoreImageWriter.Stage.ENCODE)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun streamCloseExceptionDeletesPendingRow() {
        val gateway = FakeGateway().apply {
            stream = object : ByteArrayOutputStream() {
                override fun close() {
                    throw IOException("close")
                }
            }
        }

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(1)
            true
        }

        assertFailure(result, MediaStoreImageWriter.Stage.ENCODE)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun beforePublishFailureDeletesPendingRow() {
        val gateway = FakeGateway()

        val result = MediaStoreImageWriter.write(
            gateway = gateway,
            request = request,
            beforePublish = { throw IOException("metadata") },
            encoder = { output -> output.write(1); true },
        )

        assertFailure(result, MediaStoreImageWriter.Stage.BEFORE_PUBLISH)
        assertEquals(1, gateway.deleteCalls)
        assertEquals(0, gateway.publishCalls)
    }

    @Test
    fun nonSinglePublishUpdateDeletesPendingRow() {
        val gateway = FakeGateway().apply { publishCount = 0 }

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(1)
            true
        }

        assertFailure(result, MediaStoreImageWriter.Stage.PUBLISH)
        assertEquals(1, gateway.publishCalls)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun multiRowPublishUpdateDeletesPendingRow() {
        val gateway = FakeGateway().apply { publishCount = 2 }

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(1)
            true
        }

        assertFailure(result, MediaStoreImageWriter.Stage.PUBLISH)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun publishExceptionDeletesPendingRow() {
        val gateway = FakeGateway().apply { publishFailure = IOException("publish") }

        val result = MediaStoreImageWriter.write(gateway, request) { output ->
            output.write(1)
            true
        }

        assertFailure(result, MediaStoreImageWriter.Stage.PUBLISH)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun cleanupFailureIsReported() {
        val gateway = FakeGateway().apply {
            stream = null
            deleteFailure = IOException("delete")
        }

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        val failure = result as MediaStoreImageWriter.Result.Failure
        assertEquals(MediaStoreImageWriter.Stage.OPEN_STREAM, failure.stage)
        assertTrue(failure.cleanupAttempted)
        assertFalse(failure.cleanupSucceeded)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun zeroDeleteCountIsReportedAsCleanupFailure() {
        val gateway = FakeGateway().apply {
            stream = null
            deleteCount = 0
        }

        val result = MediaStoreImageWriter.write(gateway, request) { true }

        val failure = result as MediaStoreImageWriter.Result.Failure
        assertTrue(failure.cleanupAttempted)
        assertFalse(failure.cleanupSucceeded)
        assertEquals(1, gateway.deleteCalls)
    }

    @Test
    fun cancellationDeletesPendingRowAndRethrows() {
        val gateway = FakeGateway()

        try {
            MediaStoreImageWriter.write(gateway, request) {
                throw CancellationException("cancel")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }

        assertEquals(1, gateway.deleteCalls)
        assertEquals(0, gateway.publishCalls)
    }

    @Test
    fun fatalErrorDeletesPendingRowAndRethrows() {
        val gateway = FakeGateway()

        try {
            MediaStoreImageWriter.write(gateway, request) {
                throw AssertionError("fatal")
            }
            fail("Expected fatal error")
        } catch (_: AssertionError) {
        }

        assertEquals(1, gateway.deleteCalls)
        assertEquals(0, gateway.publishCalls)
    }

    private fun assertFailure(
        result: MediaStoreImageWriter.Result,
        stage: MediaStoreImageWriter.Stage,
        cleanupAttempted: Boolean = true,
    ) {
        val failure = result as MediaStoreImageWriter.Result.Failure
        assertEquals(stage, failure.stage)
        assertEquals(cleanupAttempted, failure.cleanupAttempted)
        if (cleanupAttempted) assertTrue(failure.cleanupSucceeded)
    }

    private class FakeGateway(
        insertedUri: Uri? = Uri.parse("content://media/external/images/media/42"),
    ) : MediaStoreImageWriter.Gateway {
        val uri: Uri = Uri.parse("content://media/external/images/media/42")
        val output = ByteArrayOutputStream()
        var stream: OutputStream? = output
        var publishCount = 1
        var deleteCount = 1
        var insertFailure: Exception? = null
        var openFailure: Exception? = null
        var publishFailure: Exception? = null
        var deleteFailure: Exception? = null
        var insertCalls = 0
        var openCalls = 0
        var publishCalls = 0
        var deleteCalls = 0
        private val insertedUri = insertedUri

        override fun insert(request: MediaStoreImageWriter.Request): Uri? {
            insertCalls++
            insertFailure?.let { throw it }
            return insertedUri
        }

        override fun openOutputStream(uri: Uri): OutputStream? {
            openCalls++
            openFailure?.let { throw it }
            return stream
        }

        override fun publish(uri: Uri): Int {
            publishCalls++
            publishFailure?.let { throw it }
            return publishCount
        }

        override fun delete(uri: Uri): Int {
            deleteCalls++
            deleteFailure?.let { throw it }
            return deleteCount
        }
    }
}
