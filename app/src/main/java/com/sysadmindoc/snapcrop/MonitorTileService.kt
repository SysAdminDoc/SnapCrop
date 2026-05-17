package com.sysadmindoc.snapcrop

import android.content.Intent
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
            // Overlay access is optional; ScreenshotService shows notification actions
            // when Android blocks immediate background editor launch.
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
