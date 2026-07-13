package com.sysadmindoc.snapcrop

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Shared IP classification for public-only fetches and Android local-network gating. */
internal object NetworkAddressClassifier {
    /**
     * A strict internet-address predicate. This intentionally rejects documentation, benchmark,
     * transition, reserved, loopback, and local ranges in addition to Android LAN addresses.
     */
    fun isPublicInternetAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        val bytes = address.unsignedBytes()
        return when (address) {
            is Inet4Address -> when {
                bytes[0] == 0 || bytes[0] >= 224 -> false
                bytes[0] == 10 || bytes[0] == 127 -> false
                bytes[0] == 100 && bytes[1] in 64..127 -> false
                bytes[0] == 169 && bytes[1] == 254 -> false
                bytes[0] == 172 && bytes[1] in 16..31 -> false
                bytes[0] == 192 && bytes[1] == 168 -> false
                bytes[0] == 192 && bytes[1] == 0 -> false
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

    /**
     * Matches Android's protected LAN address classes. Loopback is deliberately excluded: Android
     * 17 treats same-profile loopback separately from local-network permission enforcement.
     */
    fun isAndroidLocalNetworkAddress(
        address: InetAddress,
        directlyConnectedRoutes: Collection<NetworkAddressPrefix> = emptyList()
    ): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress) return false
        if (address.isLinkLocalAddress || address.isMulticastAddress || address.isSiteLocalAddress) return true

        val bytes = address.unsignedBytes()
        return when (address) {
            is Inet4Address -> when {
                bytes[0] == 10 -> true
                bytes[0] == 100 && bytes[1] in 64..127 -> true
                bytes[0] == 169 && bytes[1] == 254 -> true
                bytes[0] == 172 && bytes[1] in 16..31 -> true
                bytes[0] == 192 && bytes[1] == 168 -> true
                bytes[0] in 224..239 -> true
                bytes == listOf(255, 255, 255, 255) -> true
                else -> false
            }

            is Inet6Address -> {
                val uniqueLocal = bytes.firstOrNull()?.and(0xFE) == 0xFC
                uniqueLocal || directlyConnectedRoutes.any { it.contains(address) }
            }

            else -> false
        }
    }

    private fun InetAddress.unsignedBytes(): List<Int> = address.map { it.toInt() and 0xFF }
}

/** Pure prefix model that Android LinkProperties routes can be converted into without policy I/O. */
internal data class NetworkAddressPrefix(
    val networkAddress: InetAddress,
    val prefixLength: Int
) {
    init {
        require(prefixLength in 0..networkAddress.address.size * 8) { "Invalid address prefix length" }
    }

    fun contains(candidate: InetAddress): Boolean {
        val network = networkAddress.address
        val address = candidate.address
        if (network.size != address.size) return false

        val completeBytes = prefixLength / 8
        for (index in 0 until completeBytes) {
            if (network[index] != address[index]) return false
        }
        val remainingBits = prefixLength % 8
        if (remainingBits == 0) return true
        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (network[completeBytes].toInt() and mask) == (address[completeBytes].toInt() and mask)
    }
}
