package com.sysadmindoc.snapcrop

import android.provider.Settings

enum class PermissionCapability(val preferenceSuffix: String) {
    IMAGES("images"),
    VIDEOS("videos"),
    NOTIFICATIONS("notifications"),
    OVERLAY("overlay"),
    ACCESSIBILITY("accessibility"),
    LOCAL_NETWORK("local_network"),
}

enum class PermissionRecoveryState(val storedValue: String) {
    REQUESTABLE("requestable"),
    RETRYABLE("retryable"),
    SETTINGS_REQUIRED("settings_required"),
    GRANTED("granted"),
}

internal object PermissionRecoveryPolicy {
    private const val REQUESTED_PREFIX = "permission_requested_"
    private const val STATE_PREFIX = "permission_recovery_"

    fun requestedPreference(capability: PermissionCapability): String =
        REQUESTED_PREFIX + capability.preferenceSuffix

    fun statePreference(capability: PermissionCapability): String =
        STATE_PREFIX + capability.preferenceSuffix

    fun afterRequest(
        previouslyRequested: Boolean,
        granted: Boolean,
        shouldShowRationale: Boolean,
    ): PermissionRecoveryState = when {
        granted -> PermissionRecoveryState.GRANTED
        previouslyRequested && !shouldShowRationale -> PermissionRecoveryState.SETTINGS_REQUIRED
        else -> PermissionRecoveryState.RETRYABLE
    }

    fun restore(storedValue: String?, granted: Boolean): PermissionRecoveryState {
        if (granted) return PermissionRecoveryState.GRANTED
        return PermissionRecoveryState.entries
            .firstOrNull { it.storedValue == storedValue && it != PermissionRecoveryState.GRANTED }
            ?: PermissionRecoveryState.REQUESTABLE
    }
}

internal enum class PendingPermissionActionKind {
    CAPTURE,
    OPEN_LATEST,
    PIN,
    ACCESSIBILITY,
}

internal enum class PendingPermissionDecision {
    REQUEST_IMAGES,
    REQUEST_NOTIFICATIONS,
    OPEN_OVERLAY_SETTINGS,
    OPEN_ACCESSIBILITY_SETTINGS,
    EXECUTE,
    CANCEL,
}

internal object PendingPermissionPolicy {
    fun next(
        action: PendingPermissionActionKind,
        canMonitorScreenshots: Boolean,
        canQueryImages: Boolean,
        notificationAccess: Boolean,
        overlayAccess: Boolean,
        accessibilityReady: Boolean,
        notificationsRequireRuntimeGrant: Boolean,
        completedCapability: PermissionCapability?,
    ): PendingPermissionDecision = when (action) {
        PendingPermissionActionKind.CAPTURE -> when {
            !canMonitorScreenshots && completedCapability == PermissionCapability.IMAGES ->
                PendingPermissionDecision.CANCEL
            !canMonitorScreenshots -> PendingPermissionDecision.REQUEST_IMAGES
            notificationsRequireRuntimeGrant && !notificationAccess &&
                completedCapability != PermissionCapability.NOTIFICATIONS ->
                PendingPermissionDecision.REQUEST_NOTIFICATIONS
            else -> PendingPermissionDecision.EXECUTE
        }
        PendingPermissionActionKind.OPEN_LATEST -> when {
            canQueryImages -> PendingPermissionDecision.EXECUTE
            completedCapability == PermissionCapability.IMAGES -> PendingPermissionDecision.CANCEL
            else -> PendingPermissionDecision.REQUEST_IMAGES
        }
        PendingPermissionActionKind.PIN -> when {
            overlayAccess -> PendingPermissionDecision.EXECUTE
            completedCapability == PermissionCapability.OVERLAY -> PendingPermissionDecision.CANCEL
            else -> PendingPermissionDecision.OPEN_OVERLAY_SETTINGS
        }
        PendingPermissionActionKind.ACCESSIBILITY -> when {
            accessibilityReady -> PendingPermissionDecision.EXECUTE
            completedCapability == PermissionCapability.ACCESSIBILITY -> PendingPermissionDecision.CANCEL
            else -> PendingPermissionDecision.OPEN_ACCESSIBILITY_SETTINGS
        }
    }
}

internal data class PermissionSettingsRoute(
    val action: String,
    val includePackageUri: Boolean = false,
    val includeAppPackageExtra: Boolean = false,
)

internal object PermissionSettingsRouteFactory {
    fun forCapability(capability: PermissionCapability): PermissionSettingsRoute = when (capability) {
        PermissionCapability.IMAGES,
        PermissionCapability.VIDEOS,
        PermissionCapability.LOCAL_NETWORK -> PermissionSettingsRoute(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            includePackageUri = true,
        )
        PermissionCapability.NOTIFICATIONS -> PermissionSettingsRoute(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            includeAppPackageExtra = true,
        )
        PermissionCapability.OVERLAY -> PermissionSettingsRoute(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            includePackageUri = true,
        )
        PermissionCapability.ACCESSIBILITY -> PermissionSettingsRoute(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
