package com.sysadmindoc.snapcrop

import android.net.Uri
import java.net.IDN
import java.net.InetAddress
import kotlin.math.ceil

internal object WebCapturePolicy {
    const val MAX_URL_CHARS = 2_048
    const val MAX_CAPTURE_PIXELS = 12_000_000L
    const val MAX_CAPTURE_HEIGHT = 16_384

    fun normalizeHttpsUrl(input: String): String? {
        val candidate = input.trim().let { value ->
            if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
        }
        if (candidate.isBlank() || candidate.length > MAX_URL_CHARS || candidate.any(Char::isISOControl)) return null
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val rawHost = uri.host?.trimEnd('.') ?: return null
        val host = runCatching { IDN.toASCII(rawHost).lowercase() }.getOrNull() ?: return null
        if (host.isBlank() || host.length > 253 || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return null
        if (looksLikeIpLiteral(host)) return null
        val authority = if (host.contains(':')) "[$host]" else host
        return uri.buildUpon().scheme("https").encodedAuthority(authority).fragment(null).build().toString()
    }

    fun isPublicAddress(address: InetAddress): Boolean =
        NetworkAddressClassifier.isPublicInternetAddress(address)

    fun captureDimensions(width: Int, height: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || height > MAX_CAPTURE_HEIGHT) return null
        if (width.toLong() * height > MAX_CAPTURE_PIXELS) return null
        return width to height
    }

    fun preflightCaptureDimensions(viewWidth: Int, contentHeight: Int, scale: Float): Pair<Int, Int>? {
        if (!scale.isFinite() || scale <= 0f || contentHeight <= 0) return null
        val scaledHeight = ceil(contentHeight.toDouble() * scale.toDouble())
        if (scaledHeight > Int.MAX_VALUE) return null
        return captureDimensions(viewWidth, scaledHeight.toInt())
    }

    private fun looksLikeIpLiteral(host: String): Boolean =
        host.startsWith("[") || host.any { it == ':' } || host.all { it.isDigit() || it == '.' }
}
