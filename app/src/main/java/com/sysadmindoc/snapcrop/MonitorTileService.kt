package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
            if (!MediaCapabilityResolver.current(this).canMonitorScreenshots) {
                openPhotoAccess()
                updateTile()
                return
            }
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
        tile.label = getString(R.string.tile_monitor_label)
        tile.subtitle = when {
            running -> getString(R.string.tile_monitor_on)
            !MediaCapabilityResolver.current(this).canMonitorScreenshots -> getString(R.string.tile_photo_access_needed)
            else -> getString(R.string.tile_monitor_off)
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPhotoAccess() {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
