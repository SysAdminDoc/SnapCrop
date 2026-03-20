package com.sysadmindoc.snapcrop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start", false)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenshotService::class.java)
                )
            }
        }
    }
}
