package com.sysadmindoc.snapcrop

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Adds or clears `FLAG_SECURE` on [activity] based on the user's "secure_editor" preference, and
 * hides the window from Recents on API 33+. Idempotent — call from both `onCreate` and `onResume`
 * so toggling the setting (or restoring a backup) takes effect immediately, and so every surface
 * that shows the un-redacted capture is covered, not just the main editor.
 */
fun applySecureWindow(activity: Activity) {
    val secure = activity.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        .getBoolean("secure_editor", false)
    val flag = WindowManager.LayoutParams.FLAG_SECURE
    if (secure) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try { activity.setRecentsScreenshotEnabled(!secure) } catch (_: Exception) {}
    }
}
