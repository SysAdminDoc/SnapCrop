package com.sysadmindoc.snapcrop

import android.content.Intent
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
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_last_action_label)
        tile.subtitle = if (action == ScreenshotService.LAST_ACTION_QUICK_CROP) {
            getString(R.string.tile_last_action_quick)
        } else {
            getString(R.string.tile_last_action_ready)
        }
        tile.updateTile()
    }
}
