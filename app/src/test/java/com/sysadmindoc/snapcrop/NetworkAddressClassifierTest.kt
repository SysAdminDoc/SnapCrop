package com.sysadmindoc.snapcrop

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressClassifierTest {
    @Test
    fun androidIpv4LocalRangesIncludeExactBoundariesOnly() {
        listOf(
            "10.0.0.0", "10.255.255.255",
            "100.64.0.0", "100.127.255.255",
            "169.254.0.0", "169.254.255.255",
            "172.16.0.0", "172.31.255.255",
            "192.168.0.0", "192.168.255.255",
            "224.0.0.0", "239.255.255.255", "255.255.255.255"
        ).forEach { address ->
            assertTrue(address, NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip(address)))
        }

        listOf(
            "9.255.255.255", "11.0.0.0",
            "100.63.255.255", "100.128.0.0",
            "169.253.255.255", "169.255.0.0",
            "172.15.255.255", "172.32.0.0",
            "192.167.255.255", "192.169.0.0",
            "223.255.255.255", "240.0.0.0", "127.0.0.1", "0.0.0.0"
        ).forEach { address ->
            assertFalse(address, NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip(address)))
        }
    }

    @Test
    fun androidIpv6LocalRangesIncludeLinkLocalUniqueLocalMulticastAndDirectRoutes() {
        listOf("fe80::", "febf:ffff::1", "fc00::1", "fdff:ffff::1", "ff00::", "ffff::1")
            .forEach { address ->
                assertTrue(address, NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip(address)))
            }

        val route = NetworkAddressPrefix(ip("2001:db8:1234::"), 64)
        assertTrue(
            NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip("2001:db8:1234::abcd"), listOf(route))
        )
        assertFalse(
            NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip("2001:db8:1235::1"), listOf(route))
        )
        assertFalse(NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip("::1")))
        assertFalse(NetworkAddressClassifier.isAndroidLocalNetworkAddress(ip("2606:4700:4700::1111")))
    }

    @Test
    fun networkPrefixHandlesPartialBytesAndAddressFamilies() {
        val ipv4 = NetworkAddressPrefix(ip("192.168.16.0"), 20)
        assertTrue(ipv4.contains(ip("192.168.31.255")))
        assertFalse(ipv4.contains(ip("192.168.32.0")))
        assertFalse(ipv4.contains(ip("2001:db8::1")))

        val ipv6 = NetworkAddressPrefix(ip("2001:db8:abcd:1200::"), 56)
        assertTrue(ipv6.contains(ip("2001:db8:abcd:12ff::1")))
        assertFalse(ipv6.contains(ip("2001:db8:abce::1")))
    }

    @Test
    fun publicPredicateRetainsWebCaptureReservedRangeProtection() {
        listOf(
            "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.168.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1",
            "203.0.113.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1"
        ).forEach { address ->
            assertFalse(address, NetworkAddressClassifier.isPublicInternetAddress(ip(address)))
        }
        listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111").forEach { address ->
            assertTrue(address, NetworkAddressClassifier.isPublicInternetAddress(ip(address)))
        }
    }

    private fun ip(value: String): InetAddress = InetAddress.getByName(value)
}
