package com.sysadmindoc.snapcrop

import android.net.Uri
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
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

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map(Byte::toInt).map { it and 0xFF }
        return when (address) {
            is Inet4Address -> when {
                bytes[0] == 0 || bytes[0] >= 224 -> false
                bytes[0] == 10 || bytes[0] == 127 -> false
                bytes[0] == 100 && bytes[1] in 64..127 -> false
                bytes[0] == 169 && bytes[1] == 254 -> false
                bytes[0] == 172 && bytes[1] in 16..31 -> false
                bytes[0] == 192 && bytes[1] == 168 -> false
                bytes[0] == 192 && bytes[1] == 0 -> false
                bytes[0] == 192 && bytes[1] == 0 && bytes[2] == 2 -> false
                bytes[0] == 198 && bytes[1] in 18..19 -> false
                bytes[0] == 198 && bytes[1] == 51 && bytes[2] == 100 -> false
                bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113 -> false
                else -> true
            }
            is Inet6Address -> {
                val ipv4Mapped = bytes.take(10).all { it == 0 } && bytes[10] == 0xFF && bytes[11] == 0xFF
                val ipv4Compatible = bytes.take(12).all { it == 0 }
                val uniqueLocal = bytes.firstOrNull()?.and(0xFE) == 0xFC
                val documentation = bytes.take(4) == listOf(0x20, 0x01, 0x0D, 0xB8)
                val discardOnly = bytes.take(8) == listOf(0x01, 0, 0, 0, 0, 0, 0, 0)
                val nat64WellKnown = bytes.take(12) == listOf(0x00, 0x64, 0xFF, 0x9B, 0, 0, 0, 0, 0, 0, 0, 0)
                val nat64Local = bytes.take(6) == listOf(0x00, 0x64, 0xFF, 0x9B, 0x00, 0x01)
                val benchmarking = bytes.take(6) == listOf(0x20, 0x01, 0x00, 0x02, 0, 0)
                val orchid = bytes.take(3) == listOf(0x20, 0x01, 0x00) &&
                    ((bytes[3] and 0xF0) == 0x10 || (bytes[3] and 0xF0) == 0x20)
                !ipv4Mapped && !ipv4Compatible && !uniqueLocal && !documentation && !discardOnly &&
                    !nat64WellKnown && !nat64Local && !benchmarking && !orchid
            }
            else -> false
        }
    }

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
