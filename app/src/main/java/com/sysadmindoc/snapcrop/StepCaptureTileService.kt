package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Quick Settings tile that starts/stops a step-capture session. */
class StepCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
            return
        }

        if (StepCaptureService.toggleCapture(this)) {
            updateTile()
            return
        }

        Toast.makeText(this, getString(R.string.toast_enable_accessibility), Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapseCompat(intent, requestCode = 21)
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
        val capturing = StepCaptureService.isCapturing()
        tile.state = when {
            capturing -> Tile.STATE_ACTIVE
            StepCaptureService.isReady() -> Tile.STATE_INACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.tile_step_label)
        tile.subtitle = when {
            capturing -> getString(R.string.tile_step_capturing)
            StepCaptureService.isReady() -> getString(R.string.tile_step_ready)
            else -> getString(R.string.tile_step_enable)
        }
        tile.updateTile()
    }
}
