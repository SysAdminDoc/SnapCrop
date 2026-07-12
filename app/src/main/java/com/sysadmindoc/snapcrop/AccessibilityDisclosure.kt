package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent

enum class AccessibilityPurpose {
    LONG_SCREENSHOT,
    STEP_CAPTURE
}

enum class AccessibilityAction {
    SHOW_DISCLOSURE,
    OPEN_SETTINGS,
    START,
    STOP
}

object AccessibilityDisclosure {
    const val EXTRA_PURPOSE = "com.sysadmindoc.snapcrop.ACCESSIBILITY_PURPOSE"
    const val CONSENT_VERSION = 1
    private const val PREFS = "snapcrop_accessibility_consent"

    fun intent(context: Context, purpose: AccessibilityPurpose): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_PURPOSE, purpose.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun purpose(intent: Intent?): AccessibilityPurpose? = intent
        ?.getStringExtra(EXTRA_PURPOSE)
        ?.let { runCatching { AccessibilityPurpose.valueOf(it) }.getOrNull() }

    fun hasCurrentConsent(context: Context, purpose: AccessibilityPurpose): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(versionKey(purpose), 0) == CONSENT_VERSION

    fun acceptedAt(context: Context, purpose: AccessibilityPurpose): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(timestampKey(purpose), 0L)

    fun recordConsent(context: Context, purpose: AccessibilityPurpose, acceptedAt: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(versionKey(purpose), CONSENT_VERSION)
            .putLong(timestampKey(purpose), acceptedAt)
            .apply()
    }

    fun route(
        purpose: AccessibilityPurpose,
        serviceReady: Boolean,
        stepCaptureActive: Boolean,
        hasCurrentConsent: Boolean
    ): AccessibilityAction = when {
        purpose == AccessibilityPurpose.STEP_CAPTURE && stepCaptureActive -> AccessibilityAction.STOP
        !hasCurrentConsent -> AccessibilityAction.SHOW_DISCLOSURE
        !serviceReady -> AccessibilityAction.OPEN_SETTINGS
        else -> AccessibilityAction.START
    }

    internal fun versionKey(purpose: AccessibilityPurpose) = "${purpose.name.lowercase()}_version"
    internal fun timestampKey(purpose: AccessibilityPurpose) = "${purpose.name.lowercase()}_accepted_at"
}
