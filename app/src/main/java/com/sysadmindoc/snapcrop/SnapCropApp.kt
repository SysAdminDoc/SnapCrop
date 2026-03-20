package com.sysadmindoc.snapcrop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SnapCropApp : Application() {
    companion object {
        const val CHANNEL_ID = "snapcrop_bg"
    }

    override fun onCreate() {
        super.onCreate()
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
    }
}
