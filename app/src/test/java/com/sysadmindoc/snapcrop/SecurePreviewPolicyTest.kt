package com.sysadmindoc.snapcrop

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SecurePreviewPolicyTest {
    @Test
    fun secureOverlayFlagFollowsPreferenceWithoutChangingOtherFlags() {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val secured = SecurePreviewPolicy.overlayFlags(base, true)

        assertTrue(secured and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(secured and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertFalse(SecurePreviewPolicy.overlayFlags(secured, false) and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test
    fun securePolicySuppressesNotificationPixels() {
        assertTrue(SecurePreviewPolicy.showNotificationThumbnail(false))
        assertFalse(SecurePreviewPolicy.showNotificationThumbnail(true))
    }

    @Test
    fun activityWindowUpdatesWhenPreferenceChanges() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        val controller = Robolectric.buildActivity(SecurePreviewTestActivity::class.java).setup()
        val activity = controller.get()
        try {
            preferences.edit().remove(SecurePreviewPolicy.PREF_ENABLED).commit()
            assertFalse(SecurePreviewPolicy.isEnabled(activity))

            preferences.edit().putBoolean(SecurePreviewPolicy.PREF_ENABLED, true).commit()
            assertTrue(SecurePreviewPolicy.isEnabled(activity))
            applySecureWindow(activity)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

            preferences.edit().putBoolean(SecurePreviewPolicy.PREF_ENABLED, false).commit()
            applySecureWindow(activity)
            assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        } finally {
            controller.destroy()
            preferences.edit().remove(SecurePreviewPolicy.PREF_ENABLED).commit()
        }
    }
}

class SecurePreviewTestActivity : Activity()
