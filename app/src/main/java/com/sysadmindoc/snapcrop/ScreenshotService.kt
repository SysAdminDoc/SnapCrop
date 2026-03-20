package com.sysadmindoc.snapcrop

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.IOException

class ScreenshotService : Service() {

    companion object {
        const val ACTION_QUICK_SAVE = "com.sysadmindoc.snapcrop.QUICK_SAVE"
        const val EXTRA_URI = "extra_uri"
        @Volatile var isRunning = false
            private set
    }

    private var observer: ContentObserver? = null
    private var lastProcessedId = -1L
    private var lastProcessedTime = 0L
    private val handler = Handler(Looper.getMainLooper())

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
        isRunning = true
        return START_STICKY
    }

    private fun buildServiceNotification(): Notification {
        val builder = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_ID)
            .setContentTitle("SnapCrop")
            .setContentText("Monitoring for screenshots")
            .setSmallIcon(R.drawable.ic_crop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED)
        }

        return builder.build()
    }

    private fun registerObserver() {
        observer?.let { contentResolver.unregisterContentObserver(it) }

        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onMediaStoreChanged()
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    private fun onMediaStoreChanged() {
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < 1500) return

        // Try immediately
        val found = findLatestScreenshot()
        if (found != null) {
            lastProcessedTime = System.currentTimeMillis()
            lastProcessedId = found.first
            launchEditor(found.second)
            return
        }

        // Not found yet — file may still be writing. Retry at 500ms and 1200ms.
        handler.postDelayed({ retryFind() }, 500)
        handler.postDelayed({ retryFind() }, 1200)
    }

    private fun retryFind() {
        if (System.currentTimeMillis() - lastProcessedTime < 1500) return
        val found = findLatestScreenshot() ?: return
        lastProcessedTime = System.currentTimeMillis()
        lastProcessedId = found.first
        launchEditor(found.second)
    }

    /**
     * Queries MediaStore for the most recent screenshot added in the last 15 seconds.
     * Does NOT filter by IS_PENDING — some OEMs don't use it or set it differently.
     * Instead validates by attempting to open the stream.
     */
    private fun findLatestScreenshot(): Pair<Long, Uri>? {
        val nowSec = System.currentTimeMillis() / 1000
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf("${nowSec - 15}")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val path = cursor.getString(2) ?: ""

                    if (id == lastProcessedId) continue

                    val lowerName = name.lowercase()
                    val lowerPath = path.lowercase()

                    val isScreenshot = lowerPath.contains("screenshot") ||
                            lowerPath.contains("screen") ||
                            lowerName.contains("screenshot") ||
                            lowerName.contains("screen")

                    val isOurSave = path.contains("SnapCrop") || name.startsWith("SnapCrop_")

                    if (isScreenshot && !isOurSave) {
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )
                        // Validate the file is readable
                        try {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeStream(stream, null, opts)
                                if (opts.outWidth > 0 && opts.outHeight > 0) {
                                    return Pair(id, uri)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun launchEditor(uri: Uri) {
        val intent = Intent(this, CropActivity::class.java).apply {
            data = uri
            putExtra(CropActivity.EXTRA_SHOW_FLASH, true)
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
                handler.post { Toast.makeText(this, "No borders detected", Toast.LENGTH_SHORT).show() }
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

                    handler.post { Toast.makeText(this, "Autocropped & saved", Toast.LENGTH_SHORT).show() }
                } catch (e: IOException) {
                    contentResolver.delete(savedUri, null, null)
                }
            }

            cropped.recycle()
            bitmap.recycle()
        } catch (_: Exception) {
            handler.post { Toast.makeText(this, "Quick save failed", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        super.onDestroy()
    }
}
