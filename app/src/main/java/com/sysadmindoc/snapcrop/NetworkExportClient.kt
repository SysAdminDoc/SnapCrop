package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class NetworkExportTarget(val prefValue: String, val label: String) {
    HTTP("http", "HTTP endpoint"),
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
            NetworkExportTarget.HTTP -> endpoint.startsWith("https://") || endpoint.startsWith("http://")
            NetworkExportTarget.WEBDAV -> endpoint.startsWith("https://") || endpoint.startsWith("http://")
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
    val message: String
)

object NetworkExportClient {
    /** Never transmit credentials over a non-TLS connection. */
    private fun rejectInsecureAuth(target: NetworkExportTarget, endpoint: String, authorizationHeader: String): NetworkExportResult? {
        return if (authorizationHeader.isNotBlank() && !endpoint.startsWith("https://")) {
            NetworkExportResult(false, target, 0, "Refusing to send credentials over an insecure connection — use an https:// endpoint.")
        } else null
    }

    fun uploadReportPdf(
        settings: NetworkExportSettings,
        fileName: String,
        pdfBytes: ByteArray
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
                fileName = fileName,
                mimeType = "application/pdf",
                bytes = pdfBytes
            )
            NetworkExportTarget.WEBDAV -> putUpload(
                settings = settings,
                endpoint = appendWebDavFileName(settings.endpoint, fileName),
                fileName = fileName,
                mimeType = "application/pdf",
                bytes = pdfBytes
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
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): NetworkExportResult {
        if (!settings.isConfigured || settings.target != NetworkExportTarget.IMGUR) {
            return NetworkExportResult(false, NetworkExportTarget.IMGUR, 0, "Imgur export is not configured")
        }
        return multipartUpload(
            target = NetworkExportTarget.IMGUR,
            endpoint = "https://api.imgur.com/3/image",
            authorizationHeader = "Client-ID ${settings.imgurClientId}",
            fieldName = "image",
            fileName = fileName,
            mimeType = mimeType.ifBlank { "image/png" },
            bytes = bytes
        )
    }

    internal fun appendWebDavFileName(endpoint: String, fileName: String): String {
        val cleanEndpoint = endpoint.trim()
        val encoded = URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
        return if (cleanEndpoint.endsWith("/")) "$cleanEndpoint$encoded" else "$cleanEndpoint/$encoded"
    }

    private fun multipartUpload(
        target: NetworkExportTarget,
        endpoint: String,
        authorizationHeader: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): NetworkExportResult {
        rejectInsecureAuth(target, endpoint, authorizationHeader)?.let { return it }
        val boundary = "SnapCropBoundary${System.currentTimeMillis()}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("X-SnapCrop-Export", "true")
            if (authorizationHeader.isNotBlank()) setRequestProperty("Authorization", authorizationHeader)
        }

        return try {
            connection.outputStream.use { out ->
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n".toByteArray())
                out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            connection.toResult(target)
        } catch (e: Exception) {
            NetworkExportResult(false, target, 0, e.message ?: "Upload failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun putUpload(
        settings: NetworkExportSettings,
        endpoint: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): NetworkExportResult {
        rejectInsecureAuth(settings.target, endpoint, settings.authorizationHeader)?.let { return it }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("Content-Length", bytes.size.toString())
            setRequestProperty("X-SnapCrop-File-Name", fileName)
            if (settings.authorizationHeader.isNotBlank()) {
                setRequestProperty("Authorization", settings.authorizationHeader)
            }
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            connection.toResult(settings.target)
        } catch (e: Exception) {
            NetworkExportResult(false, settings.target, 0, e.message ?: "Upload failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.toResult(target: NetworkExportTarget): NetworkExportResult {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = try {
            stream?.use {
                val buffer = ByteArray(4096)
                val read = it.read(buffer)
                if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
            }.orEmpty()
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
}
