package com.sysadmindoc.snapcrop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.memoryCacheMaxSizePercentWhileInBackground

class SnapCropApp : Application(), SingletonImageLoader.Factory {
    companion object {
        const val CHANNEL_ID = "snapcrop_bg"
        const val CHANNEL_DETECTED = "snapcrop_detected"
        const val CHANNEL_STEP_CAPTURE = "snapcrop_step_capture"
        const val BACKGROUND_IMAGE_CACHE_PERCENT = 0.25
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
        SettingsPreferenceSchema.migrateLivePreferences(
            getSharedPreferences(SettingsPreferenceSchema.PREFS_NAME, MODE_PRIVATE)
        )
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

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCacheMaxSizePercentWhileInBackground(BACKGROUND_IMAGE_CACHE_PERCENT)
            .build()

}
