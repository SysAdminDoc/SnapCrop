package com.sysadmindoc.snapcrop

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class MonitorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ScreenshotService.isRunning) {
            stopService(Intent(this, ScreenshotService::class.java))
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit()
                .putBoolean("auto_start", false).apply()
        } else {
            if (Build.VERSION.SDK_INT >= 31 && !Settings.canDrawOverlays(this)) {
                // Can't start without overlay permission — open app
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE))
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
                return
            }
            ContextCompat.startForegroundService(this, Intent(this, ScreenshotService::class.java))
            getSharedPreferences("snapcrop", MODE_PRIVATE).edit()
                .putBoolean("auto_start", true).apply()
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = ScreenshotService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SnapCrop"
        tile.subtitle = if (running) "Monitoring" else "Off"
        tile.updateTile()
    }
}
