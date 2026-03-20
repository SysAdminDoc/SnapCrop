package com.sysadmindoc.snapcrop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SnapCropApp : Application() {
    companion object {
        const val CHANNEL_ID = "snapcrop_service"
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screenshot Monitor",
                NotificationManager.IMPORTANCE_MIN // Minimal visibility — no sound, no peek, tiny icon
            ).apply {
                description = "Background screenshot monitoring"
                setShowBadge(false)
            }
        )
    }
}
