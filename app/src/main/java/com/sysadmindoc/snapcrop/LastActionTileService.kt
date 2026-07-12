package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class LastActionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!MediaCapabilityResolver.current(this).canMonitorScreenshots) {
            openPhotoAccess()
            updateTile()
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_RUN_LAST_ACTION
            }
        )
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val action = getSharedPreferences("snapcrop", MODE_PRIVATE)
            .getString(ScreenshotService.PREF_LAST_ACTION, ScreenshotService.LAST_ACTION_QUICK_CROP)
        val hasAccess = MediaCapabilityResolver.current(this).canMonitorScreenshots
        tile.state = if (hasAccess) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_last_action_label)
        tile.subtitle = if (!hasAccess) {
            getString(R.string.tile_photo_access_needed)
        } else if (action == ScreenshotService.LAST_ACTION_QUICK_CROP) {
            getString(R.string.tile_last_action_quick)
        } else {
            getString(R.string.tile_last_action_ready)
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
                PendingIntent.getActivity(this, 31, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
