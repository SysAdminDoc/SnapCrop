package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

class NetworkExportClientTest {
    @Test
    fun httpAndWebDavRequireExplicitEndpoint() {
        val disabled = NetworkExportSettings(
            enabled = false,
            target = NetworkExportTarget.HTTP,
            endpoint = "https://example.test/upload",
            authorizationHeader = "",
            imgurClientId = ""
        )
        val enabledHttp = disabled.copy(enabled = true)
        val missingEndpoint = enabledHttp.copy(endpoint = "")

        assertFalse(disabled.isConfigured)
        assertTrue(enabledHttp.isConfigured)
        assertFalse(missingEndpoint.isConfigured)
        assertFalse(enabledHttp.copy(endpoint = "http://example.test/upload").isConfigured)
    }

    @Test
    fun imgurRequiresClientId() {
        val settings = NetworkExportSettings(
            enabled = true,
            target = NetworkExportTarget.IMGUR,
            endpoint = "",
            authorizationHeader = "",
            imgurClientId = ""
        )

        assertFalse(settings.isConfigured)
        assertTrue(settings.copy(imgurClientId = "client123").isConfigured)
    }

    @Test
    fun webDavFolderEndpointAppendsEncodedFileName() {
        val result = NetworkExportClient.appendWebDavFileName(
            endpoint = "https://nextcloud.example/remote.php/dav/files/me/SnapCrop/",
            fileName = "Incident Report 01.pdf"
        )

        assertEquals(
            "https://nextcloud.example/remote.php/dav/files/me/SnapCrop/Incident%20Report%2001.pdf",
            result
        )
    }

    @Test
    fun webDavFileNameIsInsertedBeforeQueryAndFragment() {
        assertEquals(
            "https://nextcloud.example/dav/Incident%20Report.pdf?token=abc#section",
            NetworkExportClient.appendWebDavFileName(
                "https://nextcloud.example/dav?token=abc#section",
                "Incident Report.pdf"
            )
        )
    }

    @Test
    fun largePayloadStreamsWithFixedBufferAndMonotonicProgress() {
        val size = 32L * 1024L * 1024L
        val input = GeneratedInputStream(size)
        val output = DiscardOutputStream()
        var previous = 0L
        var finalProgress = 0L

        val copied = NetworkExportClient.copyBounded(
            input,
            output,
            size,
            NetworkExportCancellation(),
            onProgress = { progress ->
                assertTrue(progress.bytesSent >= previous)
                previous = progress.bytesSent
                finalProgress = progress.bytesSent
            }
        )

        assertEquals(size, copied)
        assertEquals(size, output.written)
        assertEquals(size, finalProgress)
        assertTrue(input.maximumRequested <= NetworkExportClient.STREAM_BUFFER_BYTES)
    }

    @Test
    fun actualOversizeIsRejectedBeforeWritingExcessChunk() {
        val output = DiscardOutputStream()

        assertThrows(IllegalArgumentException::class.java) {
            NetworkExportClient.copyBounded(
                GeneratedInputStream(2048),
                output,
                declaredLength = null,
                cancellation = NetworkExportCancellation(),
                maximumBytes = 1024
            )
        }
        assertEquals(0, output.written)
    }

    @Test
    fun changedDeclaredSizeAndCancellationFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkExportClient.copyBounded(
                GeneratedInputStream(16),
                DiscardOutputStream(),
                declaredLength = 32,
                cancellation = NetworkExportCancellation()
            )
        }
        val cancellation = NetworkExportCancellation().apply { cancel() }
        assertThrows(UploadCancelledException::class.java) {
            NetworkExportClient.copyBounded(
                GeneratedInputStream(16),
                DiscardOutputStream(),
                declaredLength = 16,
                cancellation = cancellation
            )
        }
    }

    @Test
    fun declaredOversizeIsRejectedBeforeOpeningSourceOrConnection() {
        var opened = false
        val settings = NetworkExportSettings(
            enabled = true,
            target = NetworkExportTarget.HTTP,
            endpoint = "https://example.test/upload",
            authorizationHeader = "",
            imgurClientId = ""
        )

        val result = NetworkExportClient.uploadReportPdf(
            settings,
            NetworkUploadSource(
                "oversize.pdf",
                "application/pdf",
                NetworkExportClient.MAX_UPLOAD_BYTES + 1
            ) {
                opened = true
                ByteArrayInputStream(byteArrayOf(1))
            },
            publicNetworkAccess(),
        )

        assertFalse(result.success)
        assertFalse(opened)
        assertTrue(result.message.contains("64 MiB"))
    }

    @Test
    fun deniedLocalNetworkAccessDoesNotOpenSourceOrConnection() {
        var opened = false
        val settings = NetworkExportSettings(
            enabled = true,
            target = NetworkExportTarget.WEBDAV,
            endpoint = "https://192.168.1.5/dav",
            authorizationHeader = "",
            imgurClientId = "",
        )

        val result = NetworkExportClient.uploadReportPdf(
            settings,
            NetworkUploadSource("report.pdf", "application/pdf", 1) {
                opened = true
                ByteArrayInputStream(byteArrayOf(1))
            },
            LocalNetworkAccessAssessment(
                endpointScope = NetworkEndpointScope.LOCAL_NETWORK,
                transportScope = NetworkTransportScope.BROADCAST_CAPABLE,
                permissionDecision = LocalNetworkPermissionDecision.REQUEST_PERMISSION,
            ),
        )

        assertFalse(result.success)
        assertFalse(opened)
        assertEquals(NetworkExportFailureReason.LOCAL_NETWORK_PERMISSION, result.failureReason)
    }

    @Test
    fun deadlineStopsStreamingWithoutReadingPayload() {
        assertThrows(UploadTimeoutException::class.java) {
            NetworkExportClient.copyBounded(
                GeneratedInputStream(16),
                DiscardOutputStream(),
                declaredLength = 16,
                cancellation = NetworkExportCancellation(timeoutMillis = 0)
            )
        }
    }

    private class GeneratedInputStream(private var remaining: Long) : InputStream() {
        var maximumRequested = 0
            private set

        override fun read(): Int = if (remaining-- > 0) 0 else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            maximumRequested = maxOf(maximumRequested, length)
            if (remaining <= 0) return -1
            val count = minOf(remaining, length.toLong()).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }

    private fun publicNetworkAccess() = LocalNetworkAccessAssessment(
        endpointScope = NetworkEndpointScope.PUBLIC,
        transportScope = NetworkTransportScope.UNKNOWN,
        permissionDecision = LocalNetworkPermissionDecision.NOT_REQUIRED,
    )

    private class DiscardOutputStream : OutputStream() {
        var written = 0L
            private set

        override fun write(value: Int) {
            written++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            written += length
        }
    }
}
