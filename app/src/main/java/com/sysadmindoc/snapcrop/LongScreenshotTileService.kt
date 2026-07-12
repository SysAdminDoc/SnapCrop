package com.sysadmindoc.snapcrop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
            Toast.makeText(this, getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
            return
        }

        val purpose = AccessibilityPurpose.LONG_SCREENSHOT
        val action = AccessibilityDisclosure.route(
            purpose = purpose,
            serviceReady = ScrollCaptureService.isReady(),
            stepCaptureActive = false,
            hasCurrentConsent = AccessibilityDisclosure.hasCurrentConsent(this, purpose)
        )
        if (action == AccessibilityAction.START) {
            ScrollCaptureService.requestLongScreenshot(this, startDelayMs = 900L)
        } else {
            startActivityAndCollapseCompat(AccessibilityDisclosure.intent(this, purpose), requestCode = 20)
        }
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
        tile.label = getString(R.string.tile_long_label)
        tile.subtitle = if (ScrollCaptureService.isReady()) getString(R.string.tile_long_ready) else getString(R.string.tile_long_enable)
        tile.updateTile()
    }
}
