package com.sysadmindoc.snapcrop

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkEndpointPolicyTest {
    @Test
    fun syntaxClassifiesInvalidLocalMdnsLoopbackAndLiteralEndpointsWithoutDns() {
        assertScope(NetworkEndpointScope.INVALID, "")
        assertScope(NetworkEndpointScope.INVALID, "http://192.168.1.4/upload")
        assertScope(NetworkEndpointScope.INVALID, "https:///missing-host")
        assertScope(NetworkEndpointScope.LOCAL_NETWORK, "https://NAS.LOCAL/dav")
        assertScope(NetworkEndpointScope.LOCAL_NETWORK, "https://printer.local./dav")
        assertScope(NetworkEndpointScope.LOOPBACK, "https://localhost/upload")
        assertScope(NetworkEndpointScope.LOOPBACK, "https://api.localhost/upload")
        assertScope(NetworkEndpointScope.LOOPBACK, "https://127.0.0.1/upload")
        assertScope(NetworkEndpointScope.LOOPBACK, "https://[::1]/upload")
        assertScope(NetworkEndpointScope.LOCAL_NETWORK, "https://192.168.1.4/dav")
        assertScope(NetworkEndpointScope.LOCAL_NETWORK, "https://[fe80::1]/dav")
        assertScope(NetworkEndpointScope.PUBLIC, "https://8.8.8.8/upload")
        assertScope(NetworkEndpointScope.PUBLIC, "https://[2606:4700:4700::1111]/upload")
        assertScope(NetworkEndpointScope.UNRESOLVED, "https://nextcloud.example/upload")
    }

    @Test
    fun resolvedNamesUseLocalPublicAndFailClosedMixedAddressClassification() {
        assertEquals(
            NetworkEndpointScope.LOCAL_NETWORK,
            classify("https://nas.example/dav", "192.168.1.10")
        )
        assertEquals(
            NetworkEndpointScope.LOCAL_NETWORK,
            classify("https://dual.example/dav", "8.8.8.8", "fd00::10")
        )
        assertEquals(
            NetworkEndpointScope.PUBLIC,
            classify("https://public.example/upload", "8.8.8.8", "2606:4700:4700::1111")
        )
        assertEquals(
            NetworkEndpointScope.LOOPBACK,
            classify("https://loop.example/upload", "127.0.0.1", "::1")
        )
        assertEquals(
            NetworkEndpointScope.NON_PUBLIC,
            classify("https://reserved.example/upload", "192.0.2.10")
        )
    }

    @Test
    fun resolvedGlobalIpv6OnDirectRouteRequiresLocalNetworkAccess() {
        val route = NetworkAddressPrefix(ip("2001:db8:44::"), 64)
        assertEquals(
            NetworkEndpointScope.LOCAL_NETWORK,
            LocalNetworkEndpointPolicy.classify(
                "https://device.example/dav",
                resolvedAddresses = listOf(ip("2001:db8:44::5")),
                directlyConnectedRoutes = listOf(route)
            )
        )
        assertEquals(
            NetworkEndpointScope.NON_PUBLIC,
            LocalNetworkEndpointPolicy.classify(
                "https://device.example/dav",
                resolvedAddresses = listOf(ip("2001:db8:45::5")),
                directlyConnectedRoutes = listOf(route)
            )
        )
    }

    @Test
    fun permissionGateAppliesOnlyToTarget37LocalTrafficOnEligibleTransport() {
        val local = NetworkEndpointScope.LOCAL_NETWORK
        assertDecision(LocalNetworkPermissionDecision.NOT_REQUIRED, 36, 37, local, false)
        assertDecision(LocalNetworkPermissionDecision.NOT_REQUIRED, 37, 36, local, false)
        assertDecision(LocalNetworkPermissionDecision.REQUEST_PERMISSION, 37, 37, local, false)
        assertDecision(LocalNetworkPermissionDecision.ALREADY_GRANTED, 37, 37, local, true)
        assertDecision(
            LocalNetworkPermissionDecision.REQUEST_PERMISSION,
            37,
            37,
            local,
            false,
            NetworkTransportScope.BROADCAST_CAPABLE
        )
        assertDecision(
            LocalNetworkPermissionDecision.NOT_REQUIRED,
            37,
            37,
            local,
            false,
            NetworkTransportScope.CELLULAR
        )
        assertDecision(
            LocalNetworkPermissionDecision.NOT_REQUIRED,
            37,
            37,
            local,
            false,
            NetworkTransportScope.VPN
        )
    }

    @Test
    fun publicLoopbackUnresolvedAndInvalidEndpointsNeverRequestLanPermission() {
        listOf(
            NetworkEndpointScope.PUBLIC,
            NetworkEndpointScope.LOOPBACK,
            NetworkEndpointScope.UNRESOLVED,
            NetworkEndpointScope.NON_PUBLIC,
            NetworkEndpointScope.INVALID
        ).forEach { scope ->
            assertDecision(LocalNetworkPermissionDecision.NOT_REQUIRED, 37, 37, scope, false)
        }
    }

    private fun assertScope(expected: NetworkEndpointScope, endpoint: String) {
        assertEquals(expected, LocalNetworkEndpointPolicy.classify(endpoint))
    }

    private fun classify(endpoint: String, vararg addresses: String): NetworkEndpointScope =
        LocalNetworkEndpointPolicy.classify(endpoint, addresses.map(::ip))

    private fun assertDecision(
        expected: LocalNetworkPermissionDecision,
        apiLevel: Int,
        targetSdk: Int,
        scope: NetworkEndpointScope,
        granted: Boolean,
        transport: NetworkTransportScope = NetworkTransportScope.UNKNOWN
    ) {
        assertEquals(
            expected,
            LocalNetworkEndpointPolicy.permissionDecision(apiLevel, targetSdk, scope, granted, transport)
        )
    }

    private fun ip(value: String): InetAddress = InetAddress.getByName(value)
}
