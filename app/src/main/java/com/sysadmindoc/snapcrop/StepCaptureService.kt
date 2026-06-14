package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** One captured step: the full-screen frame plus where the user tapped (screen coords). */
internal data class StepFrame(val bitmap: Bitmap, val clickX: Int, val clickY: Int)

/**
 * Snagit-style step capture. While active (toggled on by the user), each tap captures the current
 * screen and marks where the tap landed. Stopping assembles the frames into a single numbered guide
 * image. Privacy: nothing is captured unless the user has explicitly started a step-capture session.
 */
class StepCaptureService : AccessibilityService() {

    companion object {
        private const val MAX_STEPS = 24
        private const val CAPTURE_THROTTLE_MS = 450L
        private const val POST_CLICK_DELAY_MS = 130L

        private var activeService: WeakReference<StepCaptureService>? = null

        fun isReady(): Boolean = activeService?.get() != null

        fun isCapturing(): Boolean = activeService?.get()?.capturing == true

        /** Toggles a capture session. Returns false when the service is not enabled. */
        fun toggleCapture(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Toast.makeText(context, context.getString(R.string.toast_long_requires_11), Toast.LENGTH_LONG).show()
                return false
            }
            val service = activeService?.get() ?: return false
            if (service.capturing) service.stopCapture() else service.startCapture()
            return true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val frames = mutableListOf<StepFrame>()
    @Volatile private var capturing = false
    private var lastCaptureAt = 0L
    private var captureInFlight = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!capturing || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) return
        // Ignore taps inside SnapCrop itself (e.g. the QS tile shade interactions).
        if (event.packageName == packageName) return

        val now = SystemClock.elapsedRealtime()
        if (captureInFlight || now - lastCaptureAt < CAPTURE_THROTTLE_MS) return
        if (frames.size >= MAX_STEPS) return
        lastCaptureAt = now

        val bounds = Rect()
        event.source?.let { node ->
            try { node.getBoundsInScreen(bounds) } catch (_: Exception) {}
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
        recycleFrames()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture() {
        recycleFrames()
        capturing = true
        lastCaptureAt = 0L
        Toast.makeText(this, getString(R.string.step_capture_started), Toast.LENGTH_LONG).show()
    }

    private fun stopCapture() {
        capturing = false
        val captured = frames.toList()
        frames.clear()
        if (captured.isEmpty()) {
            Toast.makeText(this, getString(R.string.step_capture_none), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.step_capture_building, captured.size), Toast.LENGTH_SHORT).show()
        scope.launch {
            val guide = try {
                withContext(Dispatchers.Default) { StepGuideAssembler.assemble(captured) }
            } catch (_: Exception) {
                null
            } finally {
                captured.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            }
            if (guide == null) {
                Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = try {
                withContext(Dispatchers.IO) { saveGuide(guide) }
            } finally {
                guide.recycle()
            }
            if (uri != null) {
                Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_saved), Toast.LENGTH_SHORT).show()
                openEditor(uri)
            } else {
                Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureStep(clickX: Int, clickY: Int) {
        captureInFlight = true
        scope.launch {
            try {
                delay(POST_CLICK_DELAY_MS)
                if (!capturing) return@launch
                val frame = captureScreen() ?: return@launch
                frames.add(StepFrame(frame, clickX, clickY))
                Toast.makeText(this@StepCaptureService, getString(R.string.step_capture_step, frames.size), Toast.LENGTH_SHORT).show()
            } finally {
                captureInFlight = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreen(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            try {
                                val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                val copy = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBitmap?.recycle()
                                copy
                            } finally {
                                hardwareBuffer.close()
                            }
                        } catch (_: Exception) {
                            null
                        }
                        if (continuation.isActive) continuation.resume(bitmap)
                        else bitmap?.recycle()
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
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
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IllegalStateException("Output stream unavailable")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
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

    private fun recycleFrames() {
        frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        frames.clear()
    }
}

/** Stacks step frames vertically into a single guide image, marking each tap with a numbered badge. */
internal object StepGuideAssembler {
    private const val BG_COLOR = 0xFF1E1E2E.toInt()
    private const val ACCENT = 0xFF89B4FA.toInt()

    fun assemble(frames: List<StepFrame>): Bitmap {
        require(frames.isNotEmpty()) { "At least one step is required" }
        val width = frames.minOf { it.bitmap.width }.coerceAtLeast(1)
        val gap = (width * 0.025f).toInt().coerceAtLeast(12)

        // Scale each frame to the common width and draw its tap marker + step badge.
        val rendered = ArrayList<Bitmap>(frames.size)
        try {
            frames.forEachIndexed { index, step ->
                val src = step.bitmap
                val scale = width.toFloat() / src.width
                val scaledHeight = (src.height * scale).toInt().coerceAtLeast(1)
                val canvasBmp = Bitmap.createBitmap(width, scaledHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canvasBmp)
                canvas.drawBitmap(
                    src,
                    Rect(0, 0, src.width, src.height),
                    Rect(0, 0, width, scaledHeight),
                    null
                )
                drawMarker(canvas, width, scaledHeight, index + 1, step, scale)
                rendered.add(canvasBmp)
            }

            val totalHeight = rendered.sumOf { it.height } + gap * (rendered.size + 1)
            val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(BG_COLOR)
            var y = gap
            for (bmp in rendered) {
                canvas.drawBitmap(bmp, 0f, y.toFloat(), null)
                y += bmp.height + gap
            }
            return result
        } finally {
            rendered.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun drawMarker(canvas: Canvas, width: Int, height: Int, number: Int, step: StepFrame, scale: Float) {
        val radius = (width * 0.045f).coerceAtLeast(28f)
        // Tap location in scaled coords; fall back to top-left corner when unknown.
        val hasPoint = step.clickX >= 0 && step.clickY >= 0
        val cx = if (hasPoint) (step.clickX * scale).coerceIn(radius, width - radius) else radius + width * 0.02f
        val cy = if (hasPoint) (step.clickY * scale).coerceIn(radius, height - radius) else radius + height * 0.02f

        if (hasPoint) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ACCENT; style = Paint.Style.STROKE; strokeWidth = radius * 0.18f
            }
            canvas.drawCircle(cx, cy, radius * 1.4f, ring)
        }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, radius, badge)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = radius * 1.1f; isFakeBoldText = true
        }
        val fm = text.fontMetrics
        canvas.drawText(number.toString(), cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }
}
