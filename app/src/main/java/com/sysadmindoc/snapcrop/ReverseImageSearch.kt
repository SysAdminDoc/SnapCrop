package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.roundToInt

/** Engines the editor's "Search image" action offers (issue #4). */
enum class ReverseSearchEngine {
    /** Handed to the Google app, which opens Lens. Google's web upload only shows results in the uploading session. */
    GOOGLE_LENS,
    BING,
    YANDEX,
}

internal sealed interface ReverseSearchOutcome {
    data class Results(val url: String) : ReverseSearchOutcome
    data class Failed(val reason: Reason) : ReverseSearchOutcome

    enum class Reason { NETWORK, NO_RESULTS_PAGE }
}

internal object ReverseImageSearch {
    const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 88
    const val CACHE_FILE_NAME = "reverse-search.jpg"

    internal const val BING_UPLOAD_URL =
        "https://www.bing.com/images/search?view=detailv2&iss=sbiupload&FORM=SBIHMP"
    internal const val YANDEX_UPLOAD_URL =
        "https://yandex.com/images/search?rpt=imageview&format=json" +
            "&request=%7B%22blocks%22%3A%5B%7B%22block%22%3A%22b-page_type_search-by-image__link%22%7D%5D%7D"
    private const val USER_AGENT = "SnapCrop (Android)"
    private const val MAX_RESPONSE_BYTES = 1 shl 20

    /** Engines SnapCrop can offer on this device. Lens needs the Google app. */
    fun availableEngines(googleAppInstalled: Boolean): List<ReverseSearchEngine> =
        ReverseSearchEngine.entries.filter { it != ReverseSearchEngine.GOOGLE_LENS || googleAppInstalled }

    /** Shares [uri] straight to the Google app, which opens it in Lens. */
    fun googleLensIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        setPackage(GOOGLE_APP_PACKAGE)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("SnapCrop image search", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * JPEG of [bitmap] for an engine, at most [MAX_EDGE] on the long side. Transparent areas are
     * flattened onto white (JPEG would turn them black), and no EXIF is written.
     */
    fun encodeForSearch(bitmap: Bitmap): ByteArray {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val factor = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
        val w = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(
                bitmap,
                android.graphics.Rect(0, 0, bitmap.width, bitmap.height),
                android.graphics.Rect(0, 0, w, h),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
            )
            return ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                .toByteArray()
        } finally {
            flat.recycle()
        }
    }

    /** Uploads [jpeg] to [engine] and returns its results page. Blocking; call off the main thread. */
    fun upload(
        engine: ReverseSearchEngine,
        jpeg: ByteArray,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    ): ReverseSearchOutcome {
        require(engine != ReverseSearchEngine.GOOGLE_LENS) { "Lens goes through the Google app" }
        val boundary = "SnapCropSearch${UUID.randomUUID().toString().replace("-", "")}"
        val (endpoint, body) = when (engine) {
            ReverseSearchEngine.BING -> BING_UPLOAD_URL to multipartBody(
                boundary, "imageBin", fileName = null, contentType = null,
                value = Base64.encode(jpeg, Base64.NO_WRAP),
            )
            ReverseSearchEngine.YANDEX -> YANDEX_UPLOAD_URL to multipartBody(
                boundary, "upfile", fileName = "image.jpg", contentType = "image/jpeg", value = jpeg,
            )
            ReverseSearchEngine.GOOGLE_LENS -> error("unreachable")
        }
        val connection = try {
            open(URL(endpoint))
        } catch (_: IOException) {
            return ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NETWORK)
        }
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val url = when (engine) {
                ReverseSearchEngine.BING -> bingResultUrl(status, connection.getHeaderField("Location"))
                ReverseSearchEngine.YANDEX ->
                    if (status == HttpURLConnection.HTTP_OK) {
                        yandexResultUrl(readBounded(connection.inputStream))
                    } else null
                ReverseSearchEngine.GOOGLE_LENS -> null
            }
            if (url != null) ReverseSearchOutcome.Results(url)
            else ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NO_RESULTS_PAGE)
        } catch (_: IOException) {
            ReverseSearchOutcome.Failed(ReverseSearchOutcome.Reason.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    internal fun multipartBody(
        boundary: String,
        fieldName: String,
        fileName: String?,
        contentType: String?,
        value: ByteArray,
    ): ByteArray {
        val head = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(fieldName).append('"')
            if (fileName != null) append("; filename=\"").append(fileName).append('"')
            append("\r\n")
            if (contentType != null) append("Content-Type: ").append(contentType).append("\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        return head + value + tail
    }

    /** Bing answers an upload with a redirect to its results page; anything else means no results. */
    internal fun bingResultUrl(status: Int, location: String?): String? {
        if (status !in 300..399 || location.isNullOrBlank()) return null
        val resolved = runCatching { URI("https://www.bing.com/").resolve(location.trim()) }.getOrNull() ?: return null
        if (resolved.scheme != "https" || resolved.host?.lowercase() != "www.bing.com") return null
        val path = resolved.path.orEmpty()
        if (!path.startsWith("/images/search") && !path.startsWith("/search")) return null
        return resolved.toString()
    }

    /** Yandex answers with JSON naming the stored image; its results page is keyed by that id. */
    internal fun yandexResultUrl(json: String): String? {
        val blocks = runCatching { JSONObject(json).getJSONArray("blocks") }.getOrNull() ?: return null
        for (i in 0 until blocks.length()) {
            val id = blocks.optJSONObject(i)?.optJSONObject("params")?.optString("cbirId").orEmpty()
            if (id.isNotBlank()) {
                return "https://yandex.com/images/search?rpt=imageview&cbir_id=" +
                    URLEncoder.encode(id, Charsets.UTF_8.name())
            }
        }
        return null
    }

    private fun readBounded(input: InputStream): String = input.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < MAX_RESPONSE_BYTES) {
            val read = stream.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    }
}
