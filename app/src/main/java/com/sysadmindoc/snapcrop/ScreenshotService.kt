package com.sysadmindoc.snapcrop

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ScreenshotService : Service() {

    private var observer: ContentObserver? = null
    private var lastProcessedUri: String? = null
    private var lastProcessedTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        registerObserver()
        return START_STICKY
    }

    private fun buildNotification() = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_ID)
        .setContentTitle("SnapCrop")
        .setContentText("Monitoring for screenshots...")
        .setSmallIcon(R.drawable.ic_crop)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun registerObserver() {
        observer?.let { contentResolver.unregisterContentObserver(it) }

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { handleNewImage(it) }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    private fun handleNewImage(uri: Uri) {
        // Debounce: ignore if same URI or within 1 second
        val now = System.currentTimeMillis()
        val uriStr = uri.toString()
        if (uriStr == lastProcessedUri && now - lastProcessedTime < 1000) return

        // Check if this is a screenshot
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: return
                    val path = cursor.getString(1) ?: ""
                    val dateAdded = cursor.getLong(2)

                    // Only process recent images in screenshot-like paths
                    val isRecent = (now / 1000 - dateAdded) < 5
                    val isScreenshot = path.contains("screenshot", ignoreCase = true) ||
                            path.contains("Screen", ignoreCase = true) ||
                            name.contains("screenshot", ignoreCase = true) ||
                            name.contains("Screen", ignoreCase = true)

                    if (isRecent && isScreenshot) {
                        lastProcessedUri = uriStr
                        lastProcessedTime = now
                        launchCropEditor(uri)
                    }
                }
            }
        } catch (_: Exception) {
            // SecurityException or other query failure — ignore
        }
    }

    private fun launchCropEditor(uri: Uri) {
        val intent = Intent(this, CropActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        super.onDestroy()
    }
}
