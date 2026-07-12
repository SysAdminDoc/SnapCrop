package com.sysadmindoc.snapcrop

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GalleryOpenRequest(val uri: Uri, val dateAdded: Long)

internal object ScreenshotReminderContract {
    const val ACTION_OPEN = "com.sysadmindoc.snapcrop.action.OPEN_SCREENSHOT_REMINDER"
    const val EXTRA_URI = "reminder_media_uri"
    const val EXTRA_DATE_ADDED = "reminder_media_date_added"

    fun parse(intent: Intent?): GalleryOpenRequest? {
        if (intent?.action != ACTION_OPEN) return null
        val rawUri = intent.getStringExtra(EXTRA_URI).orEmpty()
        if (rawUri.length !in 1..8_192) return null
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            ?.takeIf { it.scheme.equals("content", true) && it.authority == MediaStore.AUTHORITY }
            ?: return null
        val dateAdded = intent.getLongExtra(EXTRA_DATE_ADDED, -1L).takeIf { it >= 0L } ?: return null
        return GalleryOpenRequest(uri, dateAdded)
    }

    fun clear(intent: Intent?) {
        intent?.removeExtra(EXTRA_URI)
        intent?.removeExtra(EXTRA_DATE_ADDED)
        if (intent?.action == ACTION_OPEN) {
            intent.action = null
            intent.data = null
            intent.clipData = null
        }
    }
}

internal object ScreenshotReminderScheduler {
    const val TAG = "snapcrop_screenshot_reminder"
    private const val WORK_PREFIX = "snapcrop_screenshot_reminder_"

    fun schedule(context: Context, reminder: ScreenshotNoteReminder) {
        val reminderAt = reminder.reminderAt ?: return
        val token = reminder.reminderToken ?: return
        val request = OneTimeWorkRequestBuilder<ScreenshotReminderWorker>()
            .setInitialDelay(initialDelay(reminderAt, System.currentTimeMillis()), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ScreenshotReminderWorker.KEY_URI, reminder.uri.toString())
                    .putLong(ScreenshotReminderWorker.KEY_DATE_ADDED, reminder.dateAdded)
                    .putLong(ScreenshotReminderWorker.KEY_REMINDER_AT, reminderAt)
                    .putString(ScreenshotReminderWorker.KEY_TOKEN, token)
                    .build()
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(reminder.uri, reminder.dateAdded),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, uri: Uri, dateAdded: Long) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(uri, dateAdded))
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(uri, dateAdded))
    }

    fun notificationsAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(SnapCropApp.CHANNEL_REMINDERS)
                ?.importance != NotificationManager.IMPORTANCE_NONE
        } else true
    }

    internal fun initialDelay(reminderAt: Long, now: Long): Long = (reminderAt - now).coerceAtLeast(0L)

    internal fun workName(uri: Uri, dateAdded: Long): String = WORK_PREFIX + identityDigest(uri, dateAdded).toHex()

    internal fun notificationId(uri: Uri, dateAdded: Long): Int =
        (ByteBuffer.wrap(identityDigest(uri, dateAdded), 0, Int.SIZE_BYTES).int and Int.MAX_VALUE)
            .coerceAtLeast(1)

    private fun identityDigest(uri: Uri, dateAdded: Long): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("${uri}\u0000$dateAdded".toByteArray(StandardCharsets.UTF_8))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

internal enum class ReminderDeliveryDisposition { NO_OP, CLEAR_MISSING, KEEP_DISABLED, DELIVER }

internal fun reminderDeliveryDisposition(
    stored: ScreenshotNoteReminder?,
    expectedAt: Long,
    expectedToken: String,
    mediaExists: Boolean,
    notificationsAvailable: Boolean
): ReminderDeliveryDisposition = when {
    stored?.reminderAt != expectedAt || stored.reminderToken != expectedToken -> ReminderDeliveryDisposition.NO_OP
    !mediaExists -> ReminderDeliveryDisposition.CLEAR_MISSING
    !notificationsAvailable -> ReminderDeliveryDisposition.KEEP_DISABLED
    else -> ReminderDeliveryDisposition.DELIVER
}

class ScreenshotReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return Result.failure()
        val dateAdded = inputData.getLong(KEY_DATE_ADDED, -1L)
        val reminderAt = inputData.getLong(KEY_REMINDER_AT, -1L)
        val token = inputData.getString(KEY_TOKEN).orEmpty()
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY ||
            dateAdded < 0L || reminderAt < 0L || token.isBlank()
        ) return Result.failure()

        val store = ScreenshotIndexStore(applicationContext)
        val stored = store.noteReminder(uri, dateAdded)
        if (stored?.reminderAt != reminderAt || stored.reminderToken != token) return Result.success()
        val disposition = reminderDeliveryDisposition(
            stored = stored,
            expectedAt = reminderAt,
            expectedToken = token,
            mediaExists = mediaIdentityExists(uri, dateAdded),
            notificationsAvailable = ScreenshotReminderScheduler.notificationsAvailable(applicationContext)
        )
        when (disposition) {
            ReminderDeliveryDisposition.NO_OP,
            ReminderDeliveryDisposition.KEEP_DISABLED -> return Result.success()
            ReminderDeliveryDisposition.CLEAR_MISSING -> {
                store.completeReminder(uri, dateAdded, token)
                return Result.success()
            }
            ReminderDeliveryDisposition.DELIVER -> Unit
        }
        // Claim by token immediately before posting. A cancel or reschedule that won the race
        // changes/removes the token, so this stale worker cannot alert.
        if (!store.completeReminder(uri, dateAdded, token)) return Result.success()

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ScreenshotReminderContract.ACTION_OPEN
            data = uri
            clipData = ClipData.newRawUri("Screenshot reminder", uri)
            putExtra(ScreenshotReminderContract.EXTRA_URI, uri.toString())
            putExtra(ScreenshotReminderContract.EXTRA_DATE_ADDED, dateAdded)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val requestCode = ScreenshotReminderScheduler.notificationId(uri, dateAdded)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, SnapCropApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_crop)
            .setContentTitle(applicationContext.getString(R.string.screenshot_reminder_notification_title))
            .setContentText(applicationContext.getString(R.string.screenshot_reminder_notification_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(
                if (SecurePreviewPolicy.isEnabled(applicationContext)) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PRIVATE
        )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the availability check and notify(). Restore the
            // claimed timestamp as a visible missed reminder; do not enqueue a surprise retry.
            store.restoreMissedReminder(stored)
        }
        return Result.success()
    }

    private fun mediaIdentityExists(uri: Uri, expectedDateAdded: Long): Boolean = runCatching {
        applicationContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_ADDED),
            null,
            null,
            null
        )?.use { cursor -> cursor.moveToFirst() && cursor.getLong(0) == expectedDateAdded } ?: false
    }.getOrDefault(false)

    companion object {
        internal const val KEY_URI = "media_uri"
        internal const val KEY_DATE_ADDED = "media_date_added"
        internal const val KEY_REMINDER_AT = "reminder_at"
        internal const val KEY_TOKEN = "reminder_token"
    }
}
