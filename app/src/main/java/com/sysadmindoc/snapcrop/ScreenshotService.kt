package com.sysadmindoc.snapcrop

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
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
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.IOException

private data class SaveFormat(val format: Bitmap.CompressFormat, val quality: Int, val ext: String, val mime: String)

class ScreenshotService : Service() {

    companion object {
        const val ACTION_QUICK_SAVE = "com.sysadmindoc.snapcrop.QUICK_SAVE"
        const val ACTION_SHARE = "com.sysadmindoc.snapcrop.SHARE"
        const val ACTION_DISMISS = "com.sysadmindoc.snapcrop.DISMISS_NOTIF"
        const val ACTION_DELAYED_CAPTURE = "com.sysadmindoc.snapcrop.DELAYED_CAPTURE"
        const val ACTION_RUN_LAST_ACTION = "com.sysadmindoc.snapcrop.RUN_LAST_ACTION"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        const val LAST_ACTION_QUICK_CROP = "quick_crop"
        const val PREF_LAST_ACTION = "last_action"
        private const val DETECTED_NOTIF_ID = 2
        private const val COUNTDOWN_NOTIF_ID = 3
        @Volatile var isRunning = false
            private set
    }

    private var observer: ContentObserver? = null
    private var lastProcessedId = -1L
    private var lastProcessedTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    // Lifecycle-bound scope so quick-save / last-action work is cancelled when the service stops,
    // rather than leaking a fresh CoroutineScope per invocation that runs after onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var foregroundStarted = false
    private var delayedCaptureBaseline: Long = 0L
    private var delayedCaptureActive = false

    private fun ensureForeground() {
        if (foregroundStarted) return
        startForeground(1, buildServiceNotification())
        foregroundStarted = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Any onStartCommand path must promote to foreground within 5s on Android 8+
        // to avoid ForegroundServiceDidNotStartInTimeException. Promote first, then dispatch.
        ensureForeground()
        when (intent?.action) {
            ACTION_QUICK_SAVE -> {
                val uriStr = intent.getStringExtra(EXTRA_URI)
                if (uriStr != null) quickSave(Uri.parse(uriStr))
                dismissDetectedNotification()
                return START_STICKY
            }
            ACTION_SHARE -> {
                val uriStr = intent.getStringExtra(EXTRA_URI)
                if (uriStr != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(Intent.createChooser(shareIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                dismissDetectedNotification()
                return START_STICKY
            }
            ACTION_DISMISS -> {
                dismissDetectedNotification()
                return START_STICKY
            }
            ACTION_DELAYED_CAPTURE -> {
                val delaySec = intent.getIntExtra(EXTRA_DELAY_SECONDS, 3).coerceIn(1, 30)
                // Register the observer so the live capture path is available if the user
                // happens to take a screenshot before the post-countdown poll fires.
                registerObserver()
                isRunning = true
                startDelayedCapture(delaySec)
                return START_STICKY
            }
            ACTION_RUN_LAST_ACTION -> {
                runLastAction(stopWhenDone = !isRunning)
                return START_NOT_STICKY
            }
        }

        registerObserver()
        isRunning = true
        return START_STICKY
    }

    private fun buildServiceNotification(): Notification {
        val builder = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_monitoring_body))
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
        // Suppress the auto-launch path while a delayed capture is in flight —
        // the countdown handler owns that screenshot and will route it itself.
        if (delayedCaptureActive) return
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
        if (delayedCaptureActive) return
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

    private fun findMostRecentScreenshot(): Pair<Long, Uri>? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val path = cursor.getString(2) ?: ""
                    val lowerName = name.lowercase()
                    val lowerPath = path.lowercase()
                    val isScreenshot = lowerPath.contains("screenshot") ||
                            lowerPath.contains("screen") ||
                            lowerName.contains("screenshot") ||
                            lowerName.contains("screen")
                    val isOurSave = path.contains("SnapCrop") || name.startsWith("SnapCrop_") ||
                            name.startsWith("reddit_") || name.startsWith("twitter_")
                    if (!isScreenshot || isOurSave) continue

                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) return Pair(id, uri)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun runLastAction(stopWhenDone: Boolean) {
        serviceScope.launch {
            val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
            val action = prefs.getString(PREF_LAST_ACTION, LAST_ACTION_QUICK_CROP)
                ?: LAST_ACTION_QUICK_CROP
            if (action != LAST_ACTION_QUICK_CROP) {
                handler.post { Toast.makeText(this@ScreenshotService, getString(R.string.toast_no_last_action), Toast.LENGTH_SHORT).show() }
                if (stopWhenDone) stopSelf()
                return@launch
            }

            val found = findMostRecentScreenshot()
            if (found == null) {
                handler.post { Toast.makeText(this@ScreenshotService, getString(R.string.toast_no_recent_screenshot), Toast.LENGTH_SHORT).show() }
                if (stopWhenDone) stopSelf()
                return@launch
            }

            quickSave(found.second, stopWhenDone = stopWhenDone)
        }
    }

    private fun launchEditor(uri: Uri) {
        // Show notification with action buttons as fallback
        showDetectedNotification(uri)

        val intent = Intent(this, CropActivity::class.java).apply {
            data = uri
            putExtra(CropActivity.EXTRA_SHOW_FLASH, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // On Android 12+ without SYSTEM_ALERT_WINDOW, background activity launch may fail.
            // The notification actions serve as the fallback.
            handler.post { Toast.makeText(this, getString(R.string.toast_screenshot_detected), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun getSaveFormat(prefs: SharedPreferences, overrideFormat: String? = null): SaveFormat {
        val quality = prefs.getInt("jpeg_quality", 95)
        return when (UserAppProfileStore.normalizeExportFormat(overrideFormat.orEmpty())) {
            "webp" -> {
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                          else Bitmap.CompressFormat.WEBP
                SaveFormat(fmt, quality, "webp", "image/webp")
            }
            "jpeg" -> SaveFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
            "png" -> SaveFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
            else -> when {
                prefs.getBoolean("use_webp", false) -> {
                    @Suppress("DEPRECATION")
                    val fmt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                              else Bitmap.CompressFormat.WEBP
                    SaveFormat(fmt, quality, "webp", "image/webp")
                }
                prefs.getBoolean("use_jpeg", false) -> SaveFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
                else -> SaveFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
            }
        }
    }

    private fun quickSave(uri: Uri, stopWhenDone: Boolean = false) {
        serviceScope.launch {
            var bitmap: Bitmap? = null
            var cropped: Bitmap? = null
            try {
                bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@launch

                val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
                val sourceHints = CropSourceHints.normalize(CropSourceHints.fromMedia(contentResolver, uri))
                val userProfiles = UserAppProfileStore.load(prefs)
                val profileTextHints = if (UserAppProfileStore.needsOcr(userProfiles)) {
                    TextExtractor.extract(bitmap).map { it.text }
                } else {
                    emptyList()
                }
                val statusBarPx = SystemBars.statusBarHeight(resources)
                val navBarPx = SystemBars.navigationBarHeight(resources)
                val cropResult = AutoCrop.detectWithMethod(
                    bitmap = bitmap,
                    statusBarPx = statusBarPx,
                    navBarPx = navBarPx,
                    sourceHints = sourceHints,
                    profileTextHints = profileTextHints,
                    userProfiles = userProfiles,
                    appProfilesEnabled = prefs.getBoolean("app_crop_profiles", true)
                )
                val actionRule = ConditionalAutoActions.resolve(
                    prefs = prefs,
                    cropMethod = cropResult.method,
                    sourceHints = sourceHints,
                    userProfiles = userProfiles,
                    profileTextHints = profileTextHints
                )
                val cropRect = cropResult.rect
                val isFullImage = cropRect.left == 0 && cropRect.top == 0 &&
                        cropRect.right == bitmap.width && cropRect.bottom == bitmap.height

                if (isFullImage && actionRule == null) {
                    handler.post { Toast.makeText(this@ScreenshotService, getString(R.string.toast_no_borders), Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                cropped = Bitmap.createBitmap(
                    bitmap,
                    cropRect.left.coerceAtLeast(0),
                    cropRect.top.coerceAtLeast(0),
                    cropRect.width().coerceAtMost(bitmap.width - cropRect.left.coerceAtLeast(0)),
                    cropRect.height().coerceAtMost(bitmap.height - cropRect.top.coerceAtLeast(0))
                )

                var redactionCount = 0
                if (actionRule?.redactSensitiveText == true) {
                    val currentCropped = cropped
                    val actionResult = ConditionalAutoActions.redactSensitiveText(currentCropped)
                    redactionCount = actionResult.redactionCount
                    if (actionResult.bitmap !== currentCropped) {
                        currentCropped.recycle()
                        cropped = actionResult.bitmap
                    }
                }

                val (fmt, quality, ext, mime) = getSaveFormat(prefs, actionRule?.exportFormat)
                val savePath = actionRule?.let { ConditionalAutoActions.savePath(prefs, it) }
                    ?: (prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop")
                val displayPrefix = actionRule?.id ?: "SnapCrop"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${displayPrefix}_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (savedUri != null) {
                    try {
                        contentResolver.openOutputStream(savedUri)?.use { out ->
                            cropped.compress(fmt, quality, out)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(savedUri, values, null, null)

                        // Services cannot show Android 11+ scoped-storage delete confirmation.
                        // On newer devices, leave the source screenshot in place.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                        }

                        prefs.edit().putString(PREF_LAST_ACTION, LAST_ACTION_QUICK_CROP).apply()
                        handler.post { copyUriToClipboard(savedUri) }
                        val message = if (actionRule != null) {
                            buildString {
                                append(getString(R.string.notif_quick_auto_rule, actionRule.label, actionRule.albumName))
                                if (redactionCount > 0) append(", redacted $redactionCount")
                                if (actionRule.redactSensitiveText && redactionCount == 0) append(", no sensitive text found")
                                if (actionRule.exportFormat != "default") append(", ${actionRule.exportFormat.uppercase()} export")
                            }
                        } else {
                            getString(R.string.toast_autocropped_saved)
                        }
                        handler.post { Toast.makeText(this@ScreenshotService, message, Toast.LENGTH_SHORT).show() }
                    } catch (e: IOException) {
                        contentResolver.delete(savedUri, null, null)
                    }
                }
            } catch (_: Exception) {
                handler.post { Toast.makeText(this@ScreenshotService, getString(R.string.toast_quick_save_failed), Toast.LENGTH_SHORT).show() }
            } finally {
                cropped?.recycle()
                bitmap?.recycle()
                if (stopWhenDone) stopSelf()
            }
        }
    }

    private fun showDetectedNotification(uri: Uri) {
        val editIntent = PendingIntent.getActivity(this, 10,
            Intent(this, CropActivity::class.java).apply {
                data = uri; putExtra(CropActivity.EXTRA_SHOW_FLASH, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val shareIntent = PendingIntent.getService(this, 11,
            Intent(this, ScreenshotService::class.java).apply {
                action = ACTION_SHARE; putExtra(EXTRA_URI, uri.toString())
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val quickSaveIntent = PendingIntent.getService(this, 12,
            Intent(this, ScreenshotService::class.java).apply {
                action = ACTION_QUICK_SAVE; putExtra(EXTRA_URI, uri.toString())
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val dismissIntent = PendingIntent.getService(this, 13,
            Intent(this, ScreenshotService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Load thumbnail for rich notification
        val thumbnail = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) { null }

        val builder = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_DETECTED)
            .setContentTitle(getString(R.string.notif_detected_title))
            .setContentText(getString(R.string.notif_detected_body))
            .setSmallIcon(R.drawable.ic_crop)
            .setContentIntent(editIntent)
            .setAutoCancel(true)
            .setDeleteIntent(dismissIntent)
            .addAction(0, getString(R.string.notif_action_edit), editIntent)
            .addAction(0, getString(R.string.notif_action_share), shareIntent)
            .addAction(0, getString(R.string.notif_action_quick_crop), quickSaveIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(30_000)

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail).bigLargeIcon(null as android.graphics.Bitmap?))
        }

        val notification = builder.build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(DETECTED_NOTIF_ID, notification)
        // Do NOT recycle thumbnail here — the notification system reads it asynchronously
    }

    private fun copyUriToClipboard(uri: Uri) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(contentResolver, "SnapCrop", uri))
        } catch (_: Exception) {}
    }

    private fun dismissDetectedNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(DETECTED_NOTIF_ID)
    }

    private fun startDelayedCapture(seconds: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        // Snapshot the wall-clock baseline (in MediaStore seconds). Any screenshot whose
        // DATE_ADDED is strictly greater than this baseline is "new" — using a numeric
        // timestamp instead of comparing against lastProcessedId avoids a race where the
        // observer races us and consumes the screenshot before this branch fires.
        delayedCaptureBaseline = System.currentTimeMillis() / 1000
        delayedCaptureActive = true

        // Countdown ticks: chain a single Runnable instead of seeding N postDelayed callbacks
        // so cancellation in onDestroy reliably clears every pending tick.
        val tick = object : Runnable {
            var remaining = seconds
            override fun run() {
                if (remaining <= 0) return
                val builder = NotificationCompat.Builder(this@ScreenshotService, SnapCropApp.CHANNEL_DETECTED)
                    .setContentTitle(getString(R.string.notif_countdown_title, remaining))
                    .setContentText(getString(R.string.notif_countdown_body))
                    .setSmallIcon(R.drawable.ic_crop)
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                nm.notify(COUNTDOWN_NOTIF_ID, builder.build())
                remaining--
                if (remaining > 0) handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)

        // After countdown, look for a new screenshot newer than the baseline.
        handler.postDelayed({
            nm.cancel(COUNTDOWN_NOTIF_ID)
            // Some OEMs take an extra moment to write the file; poll briefly.
            findDelayedCaptureScreenshot(attemptsLeft = 6)
        }, (seconds * 1000L) + 200L)
    }

    private fun findDelayedCaptureScreenshot(attemptsLeft: Int) {
        if (!delayedCaptureActive) return
        val found = findScreenshotAddedAfter(delayedCaptureBaseline)
        if (found != null) {
            delayedCaptureActive = false
            lastProcessedTime = System.currentTimeMillis()
            lastProcessedId = found.first
            launchEditor(found.second)
            return
        }
        if (attemptsLeft <= 0) {
            delayedCaptureActive = false
            Toast.makeText(this, getString(R.string.toast_no_screenshot), Toast.LENGTH_SHORT).show()
            return
        }
        handler.postDelayed({ findDelayedCaptureScreenshot(attemptsLeft - 1) }, 500)
    }

    private fun findScreenshotAddedAfter(thresholdSec: Long): Pair<Long, Uri>? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(thresholdSec.toString())
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
                    val isOurSave = path.contains("SnapCrop") || name.startsWith("SnapCrop_")
                    if (isOurSave) continue
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) return Pair(id, uri)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return null
    }

    override fun onDestroy() {
        isRunning = false
        delayedCaptureActive = false
        foregroundStarted = false
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        // Clear any lingering countdown notification if the user toggled the service off mid-capture.
        try { getSystemService(NotificationManager::class.java)?.cancel(COUNTDOWN_NOTIF_ID) } catch (_: Exception) {}
        dismissDetectedNotification()
        super.onDestroy()
    }
}
