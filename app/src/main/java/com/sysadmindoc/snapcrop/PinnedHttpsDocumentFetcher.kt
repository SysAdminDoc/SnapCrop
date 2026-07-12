package com.sysadmindoc.snapcrop

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal data class FetchedWebDocument(
    val html: String,
    val finalUrl: String,
    val charset: String
)

/**
 * Fetches only the main HTML document over a TLS socket pinned to a prevalidated public address.
 * External resources remain disabled in WebView, closing redirect and DNS-rebinding paths to LAN.
 */
internal object PinnedHttpsDocumentFetcher {
    private const val MAX_REDIRECTS = 5
    private const val MAX_HTML_BYTES = 4 * 1024 * 1024
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val TOTAL_TIMEOUT_MS = 20_000L
    private val dnsExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "snapcrop-web-dns").apply { isDaemon = true }
    }

    fun fetch(startUrl: String): FetchedWebDocument? {
        val deadlineNanos = System.nanoTime() + TOTAL_TIMEOUT_MS * 1_000_000L
        var current = WebCapturePolicy.normalizeHttpsUrl(startUrl) ?: return null
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            if (remainingMillis(deadlineNanos) <= 0) return null
            val response = fetchOnce(current, deadlineNanos) ?: return null
            if (response.status in 300..399) {
                if (redirectIndex == MAX_REDIRECTS) return null
                val location = response.headers["location"] ?: return null
                current = WebCapturePolicy.normalizeHttpsUrl(
                    runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return null
                ) ?: return null
                return@repeat
            }
            if (response.status != 200) return null
            val contentType = response.headers["content-type"].orEmpty()
            if (contentType.substringBefore(';').trim().lowercase() != "text/html") {
                return null
            }
            if (response.headers["content-encoding"]?.lowercase()?.let { it != "identity" } == true) return null
            val charset = Regex("charset\\s*=\\s*[\"']?([^;\"'\\s]+)", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.getOrNull(1)
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            return FetchedWebDocument(response.body.toString(charset), current, charset.name())
        }
        return null
    }

    internal fun parseResponseForTest(bytes: ByteArray): Triple<Int, Map<String, String>, ByteArray>? =
        readResponse(BufferedInputStream(ByteArrayInputStream(bytes)))?.let {
            Triple(it.status, it.headers, it.body)
        }

    private data class Response(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun fetchOnce(url: String, deadlineNanos: Long): Response? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val asciiHost = runCatching { IDN.toASCII(host) }.getOrNull() ?: return null
        val port = if (uri.port == -1) 443 else uri.port
        if (port != 443) return null
        val addresses = resolvePublicAddresses(asciiHost, deadlineNanos) ?: return null
        if (addresses.isEmpty() || addresses.any { !WebCapturePolicy.isPublicAddress(it) }) return null
        for (address in addresses) {
            val response = fetchFromAddress(uri, asciiHost, port, address, deadlineNanos)
            if (response != null) return response
        }
        return null
    }

    private fun fetchFromAddress(
        uri: URI,
        asciiHost: String,
        port: Int,
        address: InetAddress,
        deadlineNanos: Long
    ): Response? {
        val plainSocket = Socket()
        var tlsSocket: SSLSocket? = null
        return try {
            val connectTimeout = minOf(CONNECT_TIMEOUT_MS, remainingMillis(deadlineNanos)).coerceAtLeast(1)
            plainSocket.connect(InetSocketAddress(address, port), connectTimeout)
            plainSocket.soTimeout = minOf(READ_TIMEOUT_MS, remainingMillis(deadlineNanos)).coerceAtLeast(1)
            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plainSocket, asciiHost, port, true) as SSLSocket
            tlsSocket = socket
            socket.sslParameters = socket.sslParameters.apply {
                serverNames = listOf(SNIHostName(asciiHost))
                endpointIdentificationAlgorithm = "HTTPS"
                applicationProtocols = arrayOf("http/1.1")
            }
            socket.startHandshake()
            val path = buildString {
                append(uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(asciiHost).append("\r\n")
                append("User-Agent: SnapCrop/1 WebCapture\r\n")
                append("Accept: text/html\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            socket.outputStream.flush()
            readResponse(BufferedInputStream(socket.inputStream), deadlineNanos)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { tlsSocket?.close() }
            runCatching { plainSocket.close() }
        }
    }

    private fun readResponse(input: BufferedInputStream, deadlineNanos: Long = Long.MAX_VALUE): Response? {
        var headerBytes = 0
        fun line(): String? {
            val value = readAsciiLine(input, 8 * 1024, deadlineNanos) ?: return null
            headerBytes += value.length + 2
            if (headerBytes > MAX_HEADER_BYTES) return null
            return value
        }
        val statusLine = line() ?: return null
        val status = Regex("^HTTP/1\\.[01] ([2-5][0-9]{2})(?: |$)")
            .find(statusLine)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val headers = linkedMapOf<String, String>()
        while (true) {
            val current = line() ?: return null
            if (current.isEmpty()) break
            val separator = current.indexOf(':')
            if (separator <= 0) return null
            val name = current.substring(0, separator).trim().lowercase()
            val value = current.substring(separator + 1).trim()
            if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9a-z-]+"))) return null
            if (name in setOf("content-length", "transfer-encoding") && name in headers) return null
            if (name !in headers) headers[name] = value
        }
        if (headers["content-length"] != null && headers["transfer-encoding"] != null) return null
        if (status in 300..399) return Response(status, headers, ByteArray(0))
        val body = when {
            headers["transfer-encoding"] != null -> {
                if (!headers["transfer-encoding"].equals("chunked", ignoreCase = true)) return null
                readChunked(input, deadlineNanos)
            }
            headers["content-length"] != null -> {
                val length = headers["content-length"]?.toLongOrNull() ?: return null
                if (length !in 0L..MAX_HTML_BYTES.toLong()) return null
                readExact(input, length.toInt(), deadlineNanos)
            }
            else -> readUntilEof(input, deadlineNanos)
        } ?: return null
        return Response(status, headers, body)
    }

    private fun readChunked(input: BufferedInputStream, deadlineNanos: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        while (true) {
            val size = readAsciiLine(input, 128, deadlineNanos)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: return null
            if (size == 0) {
                var trailerBytes = 0
                while (true) {
                    val trailer = readAsciiLine(input, 8 * 1024, deadlineNanos) ?: return null
                    trailerBytes += trailer.length + 2
                    if (trailerBytes > MAX_HEADER_BYTES) return null
                    if (trailer.isEmpty()) break
                    if (trailer.indexOf(':') <= 0) return null
                }
                break
            }
            if (size < 0 || size > MAX_HTML_BYTES - output.size()) return null
            output.write(readExact(input, size, deadlineNanos) ?: return null)
            if (input.read() != '\r'.code || input.read() != '\n'.code) return null
        }
        return output.toByteArray()
    }

    private fun readUntilEof(input: BufferedInputStream, deadlineNanos: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            if (remainingMillis(deadlineNanos) <= 0) return null
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_HTML_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun readExact(input: BufferedInputStream, length: Int, deadlineNanos: Long): ByteArray? {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            if (remainingMillis(deadlineNanos) <= 0) return null
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) return null
            offset += count
        }
        return bytes
    }

    private fun readAsciiLine(input: BufferedInputStream, maxChars: Int, deadlineNanos: Long): String? {
        val output = ByteArrayOutputStream()
        while (output.size() <= maxChars) {
            if (remainingMillis(deadlineNanos) <= 0) return null
            val value = input.read()
            if (value < 0) return null
            if (value == '\n'.code) {
                val bytes = output.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, length, Charsets.US_ASCII)
            }
            output.write(value)
        }
        return null
    }

    private fun remainingMillis(deadlineNanos: Long): Int {
        if (deadlineNanos == Long.MAX_VALUE) return Int.MAX_VALUE
        return ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun resolvePublicAddresses(host: String, deadlineNanos: Long): Array<InetAddress>? {
        val task = dnsExecutor.submit<Array<InetAddress>> { InetAddress.getAllByName(host) }
        val timeout = minOf(3_000, remainingMillis(deadlineNanos)).coerceAtLeast(1)
        return try {
            task.get(timeout.toLong(), TimeUnit.MILLISECONDS)
                .takeIf { it.isNotEmpty() && it.all(WebCapturePolicy::isPublicAddress) }
        } catch (_: Exception) {
            task.cancel(true)
            null
        }
    }
}
