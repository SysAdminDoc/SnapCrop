package com.sysadmindoc.snapcrop

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRecoveryPolicyTest {
    @Test
    fun firstDenialIsRetryableEvenWhenAndroidShowsNoRationaleYet() {
        assertEquals(
            PermissionRecoveryState.RETRYABLE,
            PermissionRecoveryPolicy.afterRequest(
                previouslyRequested = false,
                granted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun repeatDenialWithoutRationaleRequiresSettings() {
        assertEquals(
            PermissionRecoveryState.SETTINGS_REQUIRED,
            PermissionRecoveryPolicy.afterRequest(
                previouslyRequested = true,
                granted = false,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            PermissionRecoveryState.RETRYABLE,
            PermissionRecoveryPolicy.afterRequest(
                previouslyRequested = true,
                granted = false,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun grantsClearStoredDenialAndRevocationReturnsToRequestable() {
        assertEquals(
            PermissionRecoveryState.GRANTED,
            PermissionRecoveryPolicy.restore(PermissionRecoveryState.SETTINGS_REQUIRED.storedValue, granted = true),
        )
        assertEquals(
            PermissionRecoveryState.REQUESTABLE,
            PermissionRecoveryPolicy.restore(PermissionRecoveryState.GRANTED.storedValue, granted = false),
        )
    }

    @Test
    fun preferenceKeysAreStableAndCapabilitySpecific() {
        assertEquals("permission_requested_images", PermissionRecoveryPolicy.requestedPreference(PermissionCapability.IMAGES))
        assertEquals("permission_recovery_notifications", PermissionRecoveryPolicy.statePreference(PermissionCapability.NOTIFICATIONS))
    }

    @Test
    fun captureRequestsOnlyItsNextMissingCapabilityAndRunsAfterNotificationDenial() {
        assertEquals(PendingPermissionDecision.REQUEST_IMAGES, captureDecision())
        assertEquals(
            PendingPermissionDecision.REQUEST_NOTIFICATIONS,
            captureDecision(canMonitor = true),
        )
        assertEquals(
            PendingPermissionDecision.EXECUTE,
            captureDecision(
                canMonitor = true,
                completed = PermissionCapability.NOTIFICATIONS,
            ),
        )
        assertEquals(
            PendingPermissionDecision.CANCEL,
            captureDecision(completed = PermissionCapability.IMAGES),
        )
    }

    @Test
    fun unrelatedPendingActionsNeverExecuteOnTheWrongSettingsReturn() {
        assertEquals(
            PendingPermissionDecision.OPEN_OVERLAY_SETTINGS,
            decision(PendingPermissionActionKind.PIN),
        )
        assertEquals(
            PendingPermissionDecision.CANCEL,
            decision(PendingPermissionActionKind.PIN, completed = PermissionCapability.OVERLAY),
        )
        assertEquals(
            PendingPermissionDecision.REQUEST_IMAGES,
            decision(PendingPermissionActionKind.OPEN_LATEST),
        )
        assertEquals(
            PendingPermissionDecision.CANCEL,
            decision(PendingPermissionActionKind.OPEN_LATEST, completed = PermissionCapability.IMAGES),
        )
    }

    @Test
    fun settingsRoutesAreCapabilitySpecific() {
        val media = PermissionSettingsRouteFactory.forCapability(PermissionCapability.IMAGES)
        val notification = PermissionSettingsRouteFactory.forCapability(PermissionCapability.NOTIFICATIONS)
        val overlay = PermissionSettingsRouteFactory.forCapability(PermissionCapability.OVERLAY)
        val accessibility = PermissionSettingsRouteFactory.forCapability(PermissionCapability.ACCESSIBILITY)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, media.action)
        assertEquals(true, media.includePackageUri)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notification.action)
        assertEquals(true, notification.includeAppPackageExtra)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlay.action)
        assertEquals(true, overlay.includePackageUri)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, accessibility.action)
    }

    private fun captureDecision(
        canMonitor: Boolean = false,
        completed: PermissionCapability? = null,
    ): PendingPermissionDecision = decision(
        action = PendingPermissionActionKind.CAPTURE,
        canMonitor = canMonitor,
        completed = completed,
    )

    private fun decision(
        action: PendingPermissionActionKind,
        canMonitor: Boolean = false,
        completed: PermissionCapability? = null,
    ): PendingPermissionDecision = PendingPermissionPolicy.next(
        action = action,
        canMonitorScreenshots = canMonitor,
        canQueryImages = false,
        notificationAccess = false,
        overlayAccess = false,
        accessibilityReady = false,
        notificationsRequireRuntimeGrant = true,
        completedCapability = completed,
    )
}
