package com.sysadmindoc.snapcrop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView

class SnapCropApp : Application() {
    companion object {
        const val CHANNEL_ID = "snapcrop_bg"
        const val CHANNEL_DETECTED = "snapcrop_detected"
        const val CHANNEL_STEP_CAPTURE = "snapcrop_step_capture"
    }

    override fun onCreate() {
        super.onCreate()
        if (Application.getProcessName().endsWith(":web_capture")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("web_capture")
            }
            @Suppress("DEPRECATION")
            WebView.enableSlowWholeDocumentDraw()
            CrashReporter.install(this)
            return
        }
        CrashReporter.install(this)
        try { IndexWorker.schedule(this) } catch (_: Exception) {}
        val manager = getSystemService(NotificationManager::class.java)

        // Delete old channel (had higher importance) so Android picks up the new one
        manager.deleteNotificationChannel("snapcrop_service")
        manager.deleteNotificationChannel("snapcrop_crop")

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Screenshot monitoring service"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DETECTED,
                "Screenshot Detected",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Actions for detected screenshots"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STEP_CAPTURE,
                "Step Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Step Capture status and stop control"
                setShowBadge(false)
            }
        )
    }
}
