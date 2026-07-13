package com.sysadmindoc.snapcrop

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.util.Locale

internal enum class NetworkEndpointScope {
    INVALID,
    UNRESOLVED,
    PUBLIC,
    LOCAL_NETWORK,
    LOOPBACK,
    NON_PUBLIC
}

internal enum class NetworkTransportScope {
    UNKNOWN,
    BROADCAST_CAPABLE,
    CELLULAR,
    VPN
}

internal enum class LocalNetworkPermissionDecision {
    NOT_REQUIRED,
    ALREADY_GRANTED,
    REQUEST_PERMISSION
}

/** Pure endpoint and permission policy. DNS, ConnectivityManager, and permission UI stay outside. */
internal object LocalNetworkEndpointPolicy {
    const val ANDROID_17_API = 37

    fun classify(
        endpoint: String,
        resolvedAddresses: Collection<InetAddress> = emptyList(),
        directlyConnectedRoutes: Collection<NetworkAddressPrefix> = emptyList()
    ): NetworkEndpointScope {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull() ?: return NetworkEndpointScope.INVALID
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.host.isNullOrBlank()) {
            return NetworkEndpointScope.INVALID
        }

        val host = normalizeHost(uri.host) ?: return NetworkEndpointScope.INVALID
        if (host == "localhost" || host.endsWith(".localhost")) return NetworkEndpointScope.LOOPBACK
        if (host.endsWith(".local")) return NetworkEndpointScope.LOCAL_NETWORK

        parseIpLiteral(host)?.let { return classifyAddresses(listOf(it), directlyConnectedRoutes) }
        if (resolvedAddresses.isEmpty()) return NetworkEndpointScope.UNRESOLVED
        return classifyAddresses(resolvedAddresses, directlyConnectedRoutes)
    }

    fun permissionDecision(
        apiLevel: Int,
        targetSdk: Int,
        endpointScope: NetworkEndpointScope,
        permissionGranted: Boolean,
        transport: NetworkTransportScope = NetworkTransportScope.UNKNOWN
    ): LocalNetworkPermissionDecision {
        if (apiLevel < ANDROID_17_API || targetSdk < ANDROID_17_API) {
            return LocalNetworkPermissionDecision.NOT_REQUIRED
        }
        if (endpointScope != NetworkEndpointScope.LOCAL_NETWORK) {
            return LocalNetworkPermissionDecision.NOT_REQUIRED
        }
        if (transport == NetworkTransportScope.CELLULAR || transport == NetworkTransportScope.VPN) {
            return LocalNetworkPermissionDecision.NOT_REQUIRED
        }
        return if (permissionGranted) {
            LocalNetworkPermissionDecision.ALREADY_GRANTED
        } else {
            LocalNetworkPermissionDecision.REQUEST_PERMISSION
        }
    }

    private fun classifyAddresses(
        addresses: Collection<InetAddress>,
        directlyConnectedRoutes: Collection<NetworkAddressPrefix>
    ): NetworkEndpointScope {
        if (addresses.any { NetworkAddressClassifier.isAndroidLocalNetworkAddress(it, directlyConnectedRoutes) }) {
            return NetworkEndpointScope.LOCAL_NETWORK
        }
        if (addresses.all(InetAddress::isLoopbackAddress)) return NetworkEndpointScope.LOOPBACK
        if (addresses.all(NetworkAddressClassifier::isPublicInternetAddress)) return NetworkEndpointScope.PUBLIC
        return NetworkEndpointScope.NON_PUBLIC
    }

    private fun normalizeHost(rawHost: String): String? {
        val unwrapped = rawHost.removePrefix("[").removeSuffix("]").trimEnd('.')
        if (unwrapped.isBlank() || unwrapped.length > 253 || unwrapped.any(Char::isISOControl)) return null
        if (unwrapped.contains(':')) return unwrapped.lowercase(Locale.ROOT)
        return runCatching { IDN.toASCII(unwrapped).lowercase(Locale.ROOT) }.getOrNull()
    }

    /** Parses only syntactic literals; ordinary host names are never resolved on the calling thread. */
    private fun parseIpLiteral(host: String): InetAddress? {
        if (host.contains(':')) {
            if (host.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' && it !in setOf(':', '.') }) return null
            return runCatching { InetAddress.getByName(host) }.getOrNull()
        }
        if (!host.all { it.isDigit() || it == '.' }) return null
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = parts.map { part ->
            if (part.isEmpty() || part.length > 3) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }.map(Int::toByte).toByteArray()
        return InetAddress.getByAddress(bytes)
    }
}
