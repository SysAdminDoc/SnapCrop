package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class LongScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Long screenshot requires Android 11 or newer", Toast.LENGTH_LONG).show()
            return
        }

        if (ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 900L)) {
            updateTile()
            return
        }

        Toast.makeText(this, "Enable SnapCrop Long screenshot in Accessibility", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapseCompat(intent, requestCode = 20)
        updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (ScrollCaptureService.isReady()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Long screenshot"
        tile.subtitle = if (ScrollCaptureService.isReady()) "Ready" else "Enable"
        tile.updateTile()
    }
}
