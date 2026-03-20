package com.sysadmindoc.snapcrop

import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.IOException

class ScreenshotService : Service() {

    companion object {
        const val ACTION_QUICK_SAVE = "com.sysadmindoc.snapcrop.QUICK_SAVE"
        const val EXTRA_URI = "extra_uri"
    }

    private var observer: ContentObserver? = null
    private var lastProcessedUri: String? = null
    private var lastProcessedTime = 0L
    private var screenFlash: ScreenFlash? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUICK_SAVE -> {
                val uriStr = intent.getStringExtra(EXTRA_URI)
                if (uriStr != null) quickSave(Uri.parse(uriStr))
                return START_STICKY
            }
        }

        startForeground(1, buildServiceNotification())
        registerObserver()
        return START_STICKY
    }

    private fun buildServiceNotification() = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_ID)
        .setContentTitle("SnapCrop")
        .setContentText("Monitoring for screenshots...")
        .setSmallIcon(R.drawable.ic_crop)
        .setOngoing(true)
        .setSilent(true)
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
        val now = System.currentTimeMillis()
        val uriStr = uri.toString()
        if (uriStr == lastProcessedUri && now - lastProcessedTime < 1000) return

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

                    val isRecent = (now / 1000 - dateAdded) < 5
                    val isScreenshot = path.contains("screenshot", ignoreCase = true) ||
                            path.contains("Screen", ignoreCase = true) ||
                            name.contains("screenshot", ignoreCase = true) ||
                            name.contains("Screen", ignoreCase = true)

                    if (isRecent && isScreenshot) {
                        lastProcessedUri = uriStr
                        lastProcessedTime = now
                        flashAndOpenEditor(uri)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun flashAndOpenEditor(uri: Uri) {
        val canFlash = Settings.canDrawOverlays(this)

        if (canFlash) {
            Handler(Looper.getMainLooper()).post {
                if (screenFlash == null) screenFlash = ScreenFlash(this)
                screenFlash?.flash { launchEditor(uri) }
            }
        } else {
            // No overlay permission — just open editor directly
            launchEditor(uri)
        }
    }

    private fun launchEditor(uri: Uri) {
        val intent = Intent(this, CropActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun quickSave(uri: Uri) {
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return

            val statusBarPx = SystemBars.statusBarHeight(resources)
            val navBarPx = SystemBars.navigationBarHeight(resources)
            val cropRect = AutoCrop.detect(bitmap, statusBarPx, navBarPx)
            val isFullImage = cropRect.left == 0 && cropRect.top == 0 &&
                    cropRect.right == bitmap.width && cropRect.bottom == bitmap.height

            if (isFullImage) {
                bitmap.recycle()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "No borders detected", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val cropped = Bitmap.createBitmap(
                bitmap,
                cropRect.left.coerceAtLeast(0),
                cropRect.top.coerceAtLeast(0),
                cropRect.width().coerceAtMost(bitmap.width - cropRect.left.coerceAtLeast(0)),
                cropRect.height().coerceAtMost(bitmap.height - cropRect.top.coerceAtLeast(0))
            )

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCrop")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (savedUri != null) {
                try {
                    contentResolver.openOutputStream(savedUri)?.use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(savedUri, values, null, null)

                    try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "Autocropped & saved", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    contentResolver.delete(savedUri, null, null)
                }
            }

            cropped.recycle()
            bitmap.recycle()
        } catch (_: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Quick save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        super.onDestroy()
    }
}
