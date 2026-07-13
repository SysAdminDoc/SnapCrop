package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalNetworkAccessAssessment(
    val endpointScope: NetworkEndpointScope,
    val transportScope: NetworkTransportScope,
    val permissionDecision: LocalNetworkPermissionDecision,
)

/** Android adapter around the pure endpoint policy. DNS and route inspection stay off the UI thread. */
internal object AndroidLocalNetworkAccess {
    const val PREF_PERMISSION_REQUESTED = "local_network_permission_requested"

    suspend fun assess(
        context: Context,
        settings: NetworkExportSettings,
    ): LocalNetworkAccessAssessment = withContext(Dispatchers.IO) {
        if (settings.target == NetworkExportTarget.IMGUR) {
            return@withContext LocalNetworkAccessAssessment(
                NetworkEndpointScope.PUBLIC,
                NetworkTransportScope.UNKNOWN,
                LocalNetworkPermissionDecision.NOT_REQUIRED,
            )
        }

        val routes = directlyConnectedRoutes(context)
        val initialScope = LocalNetworkEndpointPolicy.classify(
            endpoint = settings.endpoint,
            directlyConnectedRoutes = routes,
        )
        val scope = if (initialScope == NetworkEndpointScope.UNRESOLVED) {
            val host = runCatching { URI(settings.endpoint).host }.getOrNull()
            val addresses = if (host.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
            }
            LocalNetworkEndpointPolicy.classify(settings.endpoint, addresses, routes)
        } else {
            initialScope
        }
        val transport = transportScope(context)
        val granted = Build.VERSION.SDK_INT < LocalNetworkEndpointPolicy.ANDROID_17_API ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED
        LocalNetworkAccessAssessment(
            endpointScope = scope,
            transportScope = transport,
            permissionDecision = LocalNetworkEndpointPolicy.permissionDecision(
                apiLevel = Build.VERSION.SDK_INT,
                targetSdk = context.applicationInfo.targetSdkVersion,
                endpointScope = scope,
                permissionGranted = granted,
                transport = transport,
            ),
        )
    }

    private fun transportScope(context: Context): NetworkTransportScope {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkTransportScope.UNKNOWN
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?: return NetworkTransportScope.UNKNOWN
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransportScope.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportScope.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) ->
                NetworkTransportScope.BROADCAST_CAPABLE
            else -> NetworkTransportScope.UNKNOWN
        }
    }

    private fun directlyConnectedRoutes(context: Context): List<NetworkAddressPrefix> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val properties = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return emptyList()
        return properties.routes.mapNotNull { route ->
            if (route.hasGateway()) return@mapNotNull null
            val destination = route.destination
            runCatching {
                NetworkAddressPrefix(destination.address, destination.prefixLength)
            }.getOrNull()
        }
    }
}
