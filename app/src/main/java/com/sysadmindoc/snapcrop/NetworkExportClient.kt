package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

enum class NetworkExportTarget(val prefValue: String, val label: String) {
    HTTP("http", "HTTPS endpoint"),
    WEBDAV("webdav", "WebDAV / Nextcloud"),
    IMGUR("imgur", "Imgur anonymous");

    companion object {
        fun fromPref(value: String?): NetworkExportTarget =
            entries.firstOrNull { it.prefValue == value } ?: HTTP
    }
}

data class NetworkExportSettings(
    val enabled: Boolean,
    val target: NetworkExportTarget,
    val endpoint: String,
    val authorizationHeader: String,
    val imgurClientId: String
) {
    val isConfigured: Boolean
        get() = enabled && when (target) {
            NetworkExportTarget.HTTP -> endpoint.startsWith("https://")
            NetworkExportTarget.WEBDAV -> endpoint.startsWith("https://")
            NetworkExportTarget.IMGUR -> imgurClientId.isNotBlank()
        }

    val destinationLabel: String
        get() = when (target) {
            NetworkExportTarget.HTTP -> endpoint.ifBlank { target.label }
            NetworkExportTarget.WEBDAV -> endpoint.ifBlank { target.label }
            NetworkExportTarget.IMGUR -> target.label
        }

    companion object {
        const val PREF_ENABLED = "network_exports_enabled"
        const val PREF_TARGET = "network_export_target"
        const val PREF_ENDPOINT = "network_export_endpoint"
        const val PREF_AUTHORIZATION = "network_export_authorization"
        const val PREF_IMGUR_CLIENT_ID = "network_export_imgur_client_id"

        fun fromPrefs(prefs: SharedPreferences, credentialStore: NetworkCredentialStore? = null): NetworkExportSettings {
            return NetworkExportSettings(
                enabled = prefs.getBoolean(PREF_ENABLED, false),
                target = NetworkExportTarget.fromPref(prefs.getString(PREF_TARGET, NetworkExportTarget.HTTP.prefValue)),
                endpoint = prefs.getString(PREF_ENDPOINT, "").orEmpty().trim(),
                authorizationHeader = credentialStore?.getString(PREF_AUTHORIZATION).orEmpty().trim(),
                imgurClientId = credentialStore?.getString(PREF_IMGUR_CLIENT_ID).orEmpty().trim()
            )
        }
    }
}

data class NetworkExportResult(
    val success: Boolean,
    val target: NetworkExportTarget,
    val statusCode: Int,
    val message: String,
    val cancelled: Boolean = false
)

data class NetworkUploadSource(
    val fileName: String,
    val mimeType: String,
    val declaredLength: Long?,
    val openStream: () -> InputStream
)

data class NetworkUploadProgress(val bytesSent: Long, val totalBytes: Long?)

class NetworkExportCancellation(private val timeoutMillis: Long = 2L * 60L * 1000L) {
    private val cancelled = AtomicBoolean(false)
    private val startedAtNanos = System.nanoTime()
    @Volatile private var connection: HttpURLConnection? = null

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        connection?.disconnect()
    }

    internal fun attach(value: HttpURLConnection) {
        connection = value
        if (isCancelled) value.disconnect()
    }

    internal fun detach(value: HttpURLConnection) {
        if (connection === value) connection = null
    }

    internal fun throwIfCancelled() {
        if (isCancelled) throw UploadCancelledException()
        if ((System.nanoTime() - startedAtNanos) / 1_000_000L >= timeoutMillis) {
            connection?.disconnect()
            throw UploadTimeoutException()
        }
    }
}

internal class UploadCancelledException : Exception("Upload cancelled")
internal class UploadTimeoutException : Exception("Upload timed out")

object NetworkExportClient {
    const val MAX_UPLOAD_BYTES = 64L * 1024L * 1024L
    const val MAX_IMGUR_UPLOAD_BYTES = 50_000_000L
    const val MAX_IMGUR_BATCH_FILES = 50
    const val MAX_IMGUR_BATCH_BYTES = 256L * 1024L * 1024L
    internal const val STREAM_BUFFER_BYTES = 64 * 1024
    private const val MAX_RESPONSE_BYTES = 64 * 1024

    /** Never transmit credentials over a non-TLS connection. */
    private fun rejectInsecureAuth(target: NetworkExportTarget, endpoint: String, authorizationHeader: String): NetworkExportResult? {
        return if (authorizationHeader.isNotBlank() && !endpoint.startsWith("https://")) {
            NetworkExportResult(false, target, 0, "Refusing to send credentials over an insecure connection — use an https:// endpoint.")
        } else null
    }

    fun uploadReportPdf(
        settings: NetworkExportSettings,
        source: NetworkUploadSource,
        cancellation: NetworkExportCancellation = NetworkExportCancellation(),
        onProgress: (NetworkUploadProgress) -> Unit = {}
    ): NetworkExportResult {
        if (!settings.isConfigured) {
            return NetworkExportResult(false, settings.target, 0, "Network export is not configured")
        }
        return when (settings.target) {
            NetworkExportTarget.HTTP -> multipartUpload(
                target = settings.target,
                endpoint = settings.endpoint,
                authorizationHeader = settings.authorizationHeader,
                fieldName = "file",
                source = source,
                cancellation = cancellation,
                onProgress = onProgress
            )
            NetworkExportTarget.WEBDAV -> putUpload(
                settings = settings,
                endpoint = appendWebDavFileName(settings.endpoint, source.fileName),
                source = source,
                cancellation = cancellation,
                onProgress = onProgress
            )
            NetworkExportTarget.IMGUR -> NetworkExportResult(
                false,
                settings.target,
                0,
                "Imgur accepts images; selected images are uploaded separately"
            )
        }
    }

    fun uploadImageToImgur(
        settings: NetworkExportSettings,
        source: NetworkUploadSource,
        cancellation: NetworkExportCancellation = NetworkExportCancellation(),
        onProgress: (NetworkUploadProgress) -> Unit = {}
    ): NetworkExportResult {
        if (!settings.isConfigured || settings.target != NetworkExportTarget.IMGUR) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "Imgur export is not configured")
        }
        return multipartUpload(
            target = NetworkExportTarget.IMGUR,
            endpoint = "https://api.imgur.com/3/image",
            authorizationHeader = "Client-ID ${settings.imgurClientId}",
            fieldName = "image",
            source = source.copy(mimeType = source.mimeType.ifBlank { "image/png" }),
            cancellation = cancellation,
            onProgress = onProgress
        )
    }

    internal fun appendWebDavFileName(endpoint: String, fileName: String): String {
        val uri = URI(endpoint.trim())
        val encoded = URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
        val path = uri.rawPath.orEmpty().let { if (it.endsWith("/")) "$it$encoded" else "$it/$encoded" }
        return buildString {
            append(uri.scheme).append("://").append(uri.rawAuthority).append(path)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    private fun multipartUpload(
        target: NetworkExportTarget,
        endpoint: String,
        authorizationHeader: String,
        fieldName: String,
        source: NetworkUploadSource,
        cancellation: NetworkExportCancellation,
        onProgress: (NetworkUploadProgress) -> Unit
    ): NetworkExportResult {
        rejectInsecureAuth(target, endpoint, authorizationHeader)?.let { return it }
        validateEndpoint(endpoint, authorizationHeader)?.let { return NetworkExportResult(false, target, 0, it) }
        val maximumBytes = if (target == NetworkExportTarget.IMGUR) MAX_IMGUR_UPLOAD_BYTES else MAX_UPLOAD_BYTES
        validateSource(source, maximumBytes)?.let { return NetworkExportResult(false, target, 0, it) }
        val boundary = "SnapCropBoundary${java.util.UUID.randomUUID().toString().replace("-", "")}"
        val safeFileName = sanitizeHeaderValue(source.fileName)
        val encodedFileName = URLEncoder.encode(source.fileName, Charsets.UTF_8.name()).replace("+", "%20").take(768)
        val safeMimeType = sanitizeHeaderValue(source.mimeType.ifBlank { "application/octet-stream" })
        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"").append(sanitizeHeaderValue(fieldName))
                .append("\"; filename=\"").append(safeFileName).append("\"; filename*=UTF-8''")
                .append(encodedFileName).append("\r\n")
            append("Content-Type: ").append(safeMimeType).append("\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("X-SnapCrop-Export", "true")
            if (authorizationHeader.isNotBlank()) setRequestProperty("Authorization", authorizationHeader)
            source.declaredLength?.let { setFixedLengthStreamingMode(prefix.size.toLong() + it + suffix.size) }
                ?: setChunkedStreamingMode(STREAM_BUFFER_BYTES)
        }
        cancellation.attach(connection)

        return try {
            cancellation.throwIfCancelled()
            connection.outputStream.use { out ->
                out.write(prefix)
                source.openStream().use { input ->
                    copyBounded(input, out, source.declaredLength, cancellation, onProgress, maximumBytes)
                }
                out.write(suffix)
            }
            cancellation.throwIfCancelled()
            connection.toResult(target, cancellation)
        } catch (_: UploadCancelledException) {
            NetworkExportResult(false, target, 0, "Upload cancelled", cancelled = true)
        } catch (e: Exception) {
            if (cancellation.isCancelled) NetworkExportResult(false, target, 0, "Upload cancelled", cancelled = true)
            else NetworkExportResult(false, target, 0, e.message ?: "Upload failed")
        } finally {
            cancellation.detach(connection)
            connection.disconnect()
        }
    }

    private fun putUpload(
        settings: NetworkExportSettings,
        endpoint: String,
        source: NetworkUploadSource,
        cancellation: NetworkExportCancellation,
        onProgress: (NetworkUploadProgress) -> Unit
    ): NetworkExportResult {
        rejectInsecureAuth(settings.target, endpoint, settings.authorizationHeader)?.let { return it }
        validateEndpoint(endpoint, settings.authorizationHeader)?.let { return NetworkExportResult(false, settings.target, 0, it) }
        validateSource(source, MAX_UPLOAD_BYTES)?.let { return NetworkExportResult(false, settings.target, 0, it) }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", sanitizeHeaderValue(source.mimeType.ifBlank { "application/octet-stream" }))
            setRequestProperty("X-SnapCrop-File-Name", sanitizeHeaderValue(source.fileName))
            source.declaredLength?.let(::setFixedLengthStreamingMode) ?: setChunkedStreamingMode(STREAM_BUFFER_BYTES)
            if (settings.authorizationHeader.isNotBlank()) {
                setRequestProperty("Authorization", settings.authorizationHeader)
            }
        }
        cancellation.attach(connection)
        return try {
            cancellation.throwIfCancelled()
            connection.outputStream.use { output ->
                source.openStream().use { input ->
                    copyBounded(input, output, source.declaredLength, cancellation, onProgress, MAX_UPLOAD_BYTES)
                }
            }
            cancellation.throwIfCancelled()
            connection.toResult(settings.target, cancellation)
        } catch (_: UploadCancelledException) {
            NetworkExportResult(false, settings.target, 0, "Upload cancelled", cancelled = true)
        } catch (e: Exception) {
            if (cancellation.isCancelled) NetworkExportResult(false, settings.target, 0, "Upload cancelled", cancelled = true)
            else NetworkExportResult(false, settings.target, 0, e.message ?: "Upload failed")
        } finally {
            cancellation.detach(connection)
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.toResult(target: NetworkExportTarget, cancellation: NetworkExportCancellation): NetworkExportResult {
        cancellation.throwIfCancelled()
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = try {
            stream?.use { readBoundedResponse(it, cancellation) }.orEmpty()
        } catch (e: UploadCancelledException) {
            throw e
        } catch (e: UploadTimeoutException) {
            throw e
        } catch (_: Exception) {
            ""
        }
        val trimmed = body.replace(Regex("\\s+"), " ").trim().take(160)
        val message = buildString {
            append(target.label).append(" HTTP ").append(code)
            if (trimmed.isNotBlank()) append(": ").append(trimmed)
        }
        return NetworkExportResult(code in 200..299, target, code, message)
    }

    private fun validateSource(source: NetworkUploadSource, maximumBytes: Long): String? = when {
        source.fileName.isBlank() -> "Upload filename is required"
        source.declaredLength != null && source.declaredLength <= 0L -> "Upload is empty"
        source.declaredLength != null && source.declaredLength > maximumBytes -> "Upload exceeds the ${formatLimit(maximumBytes)} limit"
        else -> null
    }

    private fun validateEndpoint(endpoint: String, authorizationHeader: String): String? {
        if (authorizationHeader.any { it.code < 0x20 || it.code == 0x7F }) return "Authorization value contains control characters"
        return try {
            val uri = URI(endpoint)
            when {
                uri.scheme != "https" -> "Upload endpoint must use https://"
                uri.host.isNullOrBlank() -> "Upload endpoint host is missing"
                uri.userInfo != null -> "Upload endpoint must not contain embedded credentials"
                uri.fragment != null -> "Upload endpoint must not contain a fragment"
                else -> null
            }
        } catch (_: Exception) {
            "Upload endpoint is invalid"
        }
    }

    private fun sanitizeHeaderValue(value: String): String =
        value.replace(Regex("[\\\\\\r\\n\\u0000-\\u001F\\u007F\"]"), "_").take(255)

    internal fun copyBounded(
        input: InputStream,
        output: OutputStream,
        declaredLength: Long?,
        cancellation: NetworkExportCancellation,
        onProgress: (NetworkUploadProgress) -> Unit = {},
        maximumBytes: Long = MAX_UPLOAD_BYTES
    ): Long {
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            cancellation.throwIfCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            copied += read
            if (copied > maximumBytes) throw IllegalArgumentException("Upload exceeds the ${formatLimit(maximumBytes)} limit")
            if (declaredLength != null && copied > declaredLength) {
                throw IllegalArgumentException("Upload size changed while reading")
            }
            output.write(buffer, 0, read)
            onProgress(NetworkUploadProgress(copied, declaredLength))
        }
        if (copied == 0L) throw IllegalArgumentException("Upload is empty")
        if (declaredLength != null && copied != declaredLength) {
            throw IllegalArgumentException("Upload size changed while reading")
        }
        return copied
    }

    private fun readBoundedResponse(input: InputStream, cancellation: NetworkExportCancellation): String {
        val buffer = ByteArray(4096)
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < MAX_RESPONSE_BYTES) {
            cancellation.throwIfCancelled()
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - bytes.size()))
            if (read < 0) break
            if (read > 0) bytes.write(buffer, 0, read)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun formatLimit(bytes: Long): String =
        if (bytes % (1024L * 1024L) == 0L) "${bytes / (1024L * 1024L)} MiB" else "$bytes-byte"
}
