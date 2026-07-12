package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Snagit-style step capture. While active (toggled on by the user), each tap captures the current
 * screen and marks where the tap landed. Stopping assembles the frames into a single numbered guide
 * image. Privacy: nothing is captured unless the user has explicitly started a step-capture session.
 */
class StepCaptureService : AccessibilityService() {

    companion object {
        private const val CAPTURE_THROTTLE_MS = 450L
        private const val POST_CLICK_DELAY_MS = 130L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_ID = 4103
        private const val ACTION_STOP = "com.sysadmindoc.snapcrop.STOP_STEP_CAPTURE"

        private var activeService: WeakReference<StepCaptureService>? = null

        fun isReady(): Boolean = activeService?.get() != null

        fun isCapturing(): Boolean = activeService?.get()?.capturing == true

        fun isFinalizing(): Boolean = activeService?.get()?.state == SessionState.FINALIZING

        /** Toggles a capture session. Returns false when the service is not enabled. */
        fun toggleCapture(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Toast.makeText(context, context.getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
                return false
            }
            val service = activeService?.get() ?: return false
            if (service.state == SessionState.FINALIZING) {
                Toast.makeText(context, context.getString(R.string.step_capture_still_building), Toast.LENGTH_SHORT).show()
            } else if (service.capturing) {
                service.stopCapture(StepCaptureStopReason.MANUAL)
            } else {
                service.startCapture()
            }
            return true
        }
    }

    private enum class SessionState { IDLE, CAPTURING, FINALIZING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store by lazy { StepCaptureStore(cacheDir.resolve("step-capture")) }
    private val frames = mutableListOf<StoredStepFrame>()
    @Volatile private var state = SessionState.IDLE
    private val capturing: Boolean get() = state == SessionState.CAPTURING
    private var lastCaptureAt = 0L
    private var sessionStartedAt = 0L
    private var lastActivityAt = 0L
    private var generation = 0L
    private var captureInFlight = false
    private var watchdogJob: Job? = null
    private var finalizationJob: Job? = null
    private var receiverRegistered = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) stopCapture(StepCaptureStopReason.MANUAL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
        store.purgeStaleSessions()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                stopReceiver,
                IntentFilter(ACTION_STOP),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!capturing || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) return
        // Ignore taps inside SnapCrop itself (e.g. the QS tile shade interactions).
        if (event.packageName == packageName) return

        val now = SystemClock.elapsedRealtime()
        lastActivityAt = now
        if (captureInFlight || now - lastCaptureAt < CAPTURE_THROTTLE_MS) return
        lastCaptureAt = now

        val bounds = Rect()
        event.source?.let { node ->
            try { node.getBoundsInScreen(bounds) } catch (_: Exception) {}
            @Suppress("DEPRECATION") try { node.recycle() } catch (_: Exception) {}
        }
        val clickX = if (bounds.width() > 0) bounds.centerX() else -1
        val clickY = if (bounds.height() > 0) bounds.centerY() else -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureStep(clickX, clickY)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        generation++
        watchdogJob?.cancel()
        finalizationJob?.cancel()
        frames.clear()
        store.deleteSession()
        cancelNotification()
        if (receiverRegistered) runCatching { unregisterReceiver(stopReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture() {
        if (state != SessionState.IDLE) return
        try {
            store.startSession()
        } catch (error: Exception) {
            OperationJournal.enqueue(
                this, DiagnosticOperation.STEP_CAPTURE, DiagnosticStage.START,
                DiagnosticResult.FAILED, code = DiagnosticCode.STORAGE_FAILURE, error = error
            )
            Toast.makeText(this, getString(R.string.step_capture_storage_failed), Toast.LENGTH_LONG).show()
            return
        }
        frames.clear()
        generation++
        state = SessionState.CAPTURING
        lastCaptureAt = 0L
        sessionStartedAt = SystemClock.elapsedRealtime()
        lastActivityAt = sessionStartedAt
        showCaptureNotification(0)
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            Toast.makeText(this, getString(R.string.step_capture_notification_unavailable), Toast.LENGTH_LONG).show()
        }
        watchdogJob = scope.launch {
            while (capturing) {
                delay(WATCHDOG_INTERVAL_MS)
                StepCapturePolicy.timeoutReason(sessionStartedAt, lastActivityAt, SystemClock.elapsedRealtime())
                    ?.let { stopCapture(it) }
            }
        }
        requestTileUpdate()
    }

    private fun stopCapture(reason: StepCaptureStopReason) {
        if (state != SessionState.CAPTURING) return
        generation++
        state = SessionState.FINALIZING
        watchdogJob?.cancel()
        watchdogJob = null
        val captured = frames.toList()
        frames.clear()
        requestTileUpdate()
        if (captured.isEmpty()) {
            store.deleteSession()
            state = SessionState.IDLE
            cancelNotification()
            Toast.makeText(
                this,
                getString(if (reason == StepCaptureStopReason.STORAGE_FAILURE) R.string.step_capture_storage_failed else R.string.step_capture_none),
                Toast.LENGTH_SHORT
            ).show()
            OperationJournal.enqueue(
                this,
                DiagnosticOperation.STEP_CAPTURE,
                DiagnosticStage.COMPLETE,
                if (reason == StepCaptureStopReason.MANUAL) DiagnosticResult.CANCELLED else DiagnosticResult.FAILED,
                sessionStartedAt,
                diagnosticCode(reason)
            )
            requestTileUpdate()
            return
        }
        showBuildingNotification(captured.size)
        automaticStopMessage(reason)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        Toast.makeText(this, getString(R.string.step_capture_building, captured.size), Toast.LENGTH_SHORT).show()
        finalizationJob = scope.launch {
            try {
                val guide = try {
                    withContext(Dispatchers.Default) { StepGuideAssembler.assemble(captured) }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    OperationJournal.record(
                        this@StepCaptureService,
                        DiagnosticOperation.STEP_CAPTURE,
                        DiagnosticStage.ASSEMBLE,
                        DiagnosticResult.FAILED,
                        sessionStartedAt,
                        if (e is OutOfMemoryError) DiagnosticCode.MEMORY_LIMIT else DiagnosticCode.INTERNAL,
                        e
                    )
                    null
                }
                if (guide == null) {
                    Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_failed), Toast.LENGTH_SHORT).show()
                } else {
                    val uri = try {
                        withContext(Dispatchers.IO) { saveGuide(guide) }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        null
                    } finally {
                        guide.recycle()
                    }
                    if (uri != null) {
                        OperationJournal.record(
                            this@StepCaptureService,
                            DiagnosticOperation.STEP_CAPTURE,
                            DiagnosticStage.COMPLETE,
                            DiagnosticResult.SUCCESS,
                            sessionStartedAt
                        )
                        Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_saved), Toast.LENGTH_SHORT).show()
                        openEditor(uri)
                    } else {
                        OperationJournal.record(
                            this@StepCaptureService,
                            DiagnosticOperation.STEP_CAPTURE,
                            DiagnosticStage.SAVE,
                            DiagnosticResult.FAILED,
                            sessionStartedAt,
                            DiagnosticCode.STORAGE_FAILURE
                        )
                        Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                runCatching { store.deleteSession() }
                state = SessionState.IDLE
                finalizationJob = null
                cancelNotification()
                requestTileUpdate()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureStep(clickX: Int, clickY: Int) {
        captureInFlight = true
        val captureGeneration = generation
        scope.launch {
            var raw: Bitmap? = null
            var normalized: Bitmap? = null
            try {
                delay(POST_CLICK_DELAY_MS)
                if (!capturing || captureGeneration != generation) return@launch
                var windowBounds: AccessibilityWindowBounds? = null
                when (val capture = captureAccessibilityScreenshot()) {
                    is AccessibilityScreenshotResult.Success -> {
                        raw = capture.bitmap
                        windowBounds = capture.windowBounds
                    }
                    is AccessibilityScreenshotResult.Failure -> {
                        val message = captureFailureMessage(capture.reason)
                        showCaptureNotification(frames.size, message)
                        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                            Toast.makeText(this@StepCaptureService, message, Toast.LENGTH_LONG).show()
                        }
                        if (capture.reason == AccessibilityScreenshotFailure.ACCESS_REVOKED) {
                            stopCapture(StepCaptureStopReason.SCREENSHOT_ACCESS)
                        }
                        return@launch
                    }
                }
                val source = checkNotNull(raw)
                if (!capturing || captureGeneration != generation) return@launch
                val (tapX, tapY) = AccessibilityScreenshotPolicy.tapFractions(
                    clickX,
                    clickY,
                    source.width,
                    source.height,
                    windowBounds
                )
                val normalizedFrame = withContext(Dispatchers.Default) { StepCapturePolicy.normalizedBitmap(source) }
                normalized = normalizedFrame
                val candidate = withContext(Dispatchers.IO) { store.persist(normalizedFrame, tapX, tapY) }
                if (!capturing || captureGeneration != generation) {
                    store.deleteFrame(candidate)
                    return@launch
                }
                val violation = StepCapturePolicy.violation(frames, candidate)
                if (violation != null) {
                    store.deleteFrame(candidate)
                    stopCapture(violation)
                    return@launch
                }
                frames.add(candidate)
                showCaptureNotification(frames.size)
                if (frames.size >= StepCapturePolicy.MAX_FRAMES) stopCapture(StepCaptureStopReason.FRAME_LIMIT)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                stopCapture(
                    when (e) {
                        is StepCaptureLimitException -> e.reason
                        is OutOfMemoryError -> StepCaptureStopReason.MEMORY_LIMIT
                        else -> StepCaptureStopReason.STORAGE_FAILURE
                    }
                )
            } finally {
                normalized?.takeIf { it !== raw && !it.isRecycled }?.recycle()
                raw?.takeIf { !it.isRecycled }?.recycle()
                captureInFlight = false
            }
        }
    }

    private fun captureFailureMessage(reason: AccessibilityScreenshotFailure): String = getString(
        when (reason) {
            AccessibilityScreenshotFailure.SECURE_WINDOW -> R.string.accessibility_capture_secure
            AccessibilityScreenshotFailure.INVALID_WINDOW -> R.string.accessibility_capture_invalid_window
            AccessibilityScreenshotFailure.WINDOW_LOST -> R.string.accessibility_capture_window_lost
            AccessibilityScreenshotFailure.WINDOW_UNAVAILABLE -> R.string.accessibility_capture_window_unavailable
            AccessibilityScreenshotFailure.THROTTLED -> R.string.accessibility_capture_throttled
            AccessibilityScreenshotFailure.ACCESS_REVOKED -> R.string.accessibility_capture_access_revoked
            AccessibilityScreenshotFailure.INVALID_DISPLAY -> R.string.accessibility_capture_invalid_display
            AccessibilityScreenshotFailure.INTERNAL -> R.string.step_capture_missed
        }
    )

    private fun diagnosticCode(reason: StepCaptureStopReason): DiagnosticCode = when (reason) {
        StepCaptureStopReason.MANUAL -> DiagnosticCode.USER_CANCELLED
        StepCaptureStopReason.FRAME_LIMIT -> DiagnosticCode.FRAME_LIMIT
        StepCaptureStopReason.PIXEL_LIMIT -> DiagnosticCode.PIXEL_LIMIT
        StepCaptureStopReason.MEMORY_LIMIT -> DiagnosticCode.MEMORY_LIMIT
        StepCaptureStopReason.CACHE_LIMIT -> DiagnosticCode.CACHE_LIMIT
        StepCaptureStopReason.DURATION -> DiagnosticCode.TIME_LIMIT
        StepCaptureStopReason.INACTIVITY -> DiagnosticCode.INACTIVITY_TIMEOUT
        StepCaptureStopReason.STORAGE_FAILURE -> DiagnosticCode.STORAGE_FAILURE
        StepCaptureStopReason.SCREENSHOT_ACCESS -> DiagnosticCode.ACCESS_REVOKED
    }

    private fun saveGuide(bitmap: Bitmap): Uri? {
        val prefs = getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Steps_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val encoded = contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: throw IllegalStateException("Output stream unavailable")
            check(encoded) { "Could not encode step guide" }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(contentResolver.update(uri, values, null, null) == 1) { "Could not publish step guide" }
            uri
        } catch (_: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            null
        }
    }

    private fun openEditor(uri: Uri) {
        try {
            startActivity(
                Intent(this, CropActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.step_capture_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun automaticStopMessage(reason: StepCaptureStopReason): String? = when (reason) {
        StepCaptureStopReason.MANUAL -> null
        StepCaptureStopReason.FRAME_LIMIT -> getString(R.string.step_capture_limit_frames, StepCapturePolicy.MAX_FRAMES)
        StepCaptureStopReason.PIXEL_LIMIT -> getString(R.string.step_capture_limit_pixels)
        StepCaptureStopReason.MEMORY_LIMIT -> getString(R.string.step_capture_limit_memory)
        StepCaptureStopReason.CACHE_LIMIT -> getString(R.string.step_capture_limit_storage)
        StepCaptureStopReason.DURATION -> getString(R.string.step_capture_limit_duration)
        StepCaptureStopReason.INACTIVITY -> getString(R.string.step_capture_limit_inactivity)
        StepCaptureStopReason.STORAGE_FAILURE -> getString(R.string.step_capture_storage_failed)
        StepCaptureStopReason.SCREENSHOT_ACCESS -> getString(R.string.accessibility_capture_access_revoked)
    }

    private fun showCaptureNotification(count: Int, message: String = getString(R.string.step_capture_notification_active, count, StepCapturePolicy.MAX_FRAMES)) {
        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_STEP_CAPTURE)
            .setSmallIcon(R.drawable.ic_crop)
            .setContentTitle(getString(R.string.step_capture_notification_title))
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_crop, getString(R.string.step_capture_stop), stopPendingIntent)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
    }

    private fun showBuildingNotification(count: Int) {
        val notification = NotificationCompat.Builder(this, SnapCropApp.CHANNEL_STEP_CAPTURE)
            .setSmallIcon(R.drawable.ic_crop)
            .setContentTitle(getString(R.string.step_capture_notification_title))
            .setContentText(getString(R.string.step_capture_building, count))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
    }

    private fun cancelNotification() {
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    private fun requestTileUpdate() {
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, StepCaptureTileService::class.java))
        }
    }
}
