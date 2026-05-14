package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private data class ScrollSaveFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String
)

class ScrollCaptureService : AccessibilityService() {

    companion object {
        private const val MAX_FRAMES = 5
        private const val SCREENSHOT_INTERVAL_MS = 1150L
        private const val DEFAULT_START_DELAY_MS = 700L

        private var activeService: WeakReference<ScrollCaptureService>? = null

        fun isReady(): Boolean = activeService?.get() != null

        fun requestLongScreenshot(
            context: Context,
            startDelayMs: Long = DEFAULT_START_DELAY_MS
        ): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Toast.makeText(
                    context,
                    "Long screenshot requires Android 11 or newer",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            val service = activeService?.get() ?: return false
            service.startLongScreenshot(startDelayMs)
            return true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isCapturing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        scope.cancel()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startLongScreenshot(startDelayMs: Long) {
        if (isCapturing) {
            Toast.makeText(this, "Long screenshot is already running", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isCapturing = true
            val frames = mutableListOf<Bitmap>()

            try {
                val startMessage = if (startDelayMs >= 1000L) {
                    val seconds = ((startDelayMs + 999L) / 1000L).toInt()
                    "Long screenshot starts in ${seconds}s. Open the target screen."
                } else {
                    "Long screenshot starts now. Keep the screen still."
                }
                Toast.makeText(
                    this@ScrollCaptureService,
                    startMessage,
                    Toast.LENGTH_SHORT
                ).show()
                delay(startDelayMs.coerceAtLeast(0L))

                while (isActive && frames.size < MAX_FRAMES) {
                    val frame = captureCleanFrame()
                    if (frame == null) {
                        Toast.makeText(
                            this@ScrollCaptureService,
                            "Screen capture failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        break
                    }

                    if (frames.lastOrNull()?.let { ScrollStitcher.looksSame(it, frame) } == true) {
                        frame.recycle()
                        break
                    }

                    frames.add(frame)

                    if (frames.size >= MAX_FRAMES) break
                    if (!scrollForward()) break

                    delay(SCREENSHOT_INTERVAL_MS)
                }

                when {
                    frames.size >= 2 -> saveAndOpenLongScreenshot(frames)
                    frames.size == 1 -> Toast.makeText(
                        this@ScrollCaptureService,
                        "No scrollable content found",
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> Unit
                }
            } finally {
                frames.forEach { if (!it.isRecycled) it.recycle() }
                isCapturing = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureCleanFrame(): Bitmap? {
        val raw = captureScreen() ?: return null
        val topInset = SystemBars.statusBarHeight(resources)
            .coerceIn(0, raw.height / 4)
        val bottomInset = SystemBars.navigationBarHeight(resources)
            .coerceIn(0, raw.height / 4)
        val cleanHeight = (raw.height - topInset - bottomInset).coerceAtLeast(1)

        return try {
            Bitmap.createBitmap(raw, 0, topInset, raw.width, cleanHeight)
        } catch (_: Exception) {
            raw.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            raw.recycle()
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
                                val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                    hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                val copy = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBitmap?.recycle()
                                copy
                            } finally {
                                hardwareBuffer.close()
                            }
                        } catch (_: Exception) {
                            null
                        }

                        if (continuation.isActive) {
                            continuation.resume(bitmap)
                        } else {
                            bitmap?.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }

    private fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            performScrollForward(root)
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun performScrollForward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (performScrollForward(child)) return true
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }

        return false
    }

    private suspend fun saveAndOpenLongScreenshot(frames: List<Bitmap>) {
        val stitched = try {
            withContext(Dispatchers.Default) {
                ScrollStitcher.stitch(frames)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Long screenshot stitch failed", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = withContext(Dispatchers.IO) { saveToGallery(stitched) }
            if (uri != null) {
                Toast.makeText(
                    this,
                    "Long screenshot saved",
                    Toast.LENGTH_SHORT
                ).show()
                openEditor(uri)
            } else {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        } finally {
            stitched.recycle()
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
            Toast.makeText(this, "Saved to your SnapCrop folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSaveFormat(): ScrollSaveFormat {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                ScrollSaveFormat(format, quality, "webp", "image/webp")
            }
            prefs.getBoolean("use_jpeg", false) ->
                ScrollSaveFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
            else -> ScrollSaveFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
        }
    }

    private fun saveToGallery(bitmap: Bitmap): Uri? {
        val saveFormat = getSaveFormat()
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "SnapCrop_Long_${System.currentTimeMillis()}.${saveFormat.ext}"
            )
            put(MediaStore.Images.Media.MIME_TYPE, saveFormat.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Output stream unavailable")
            output.use { bitmap.compress(saveFormat.format, saveFormat.quality, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }
}

private object ScrollStitcher {
    private const val OVERLAP_STEP = 8
    private const val SAMPLE_COLUMNS = 18
    private const val SAMPLE_ROWS = 18

    fun stitch(frames: List<Bitmap>): Bitmap {
        val width = frames.minOf { it.width }
        val normalized = frames.map { frame ->
            if (frame.width == width) frame else {
                val scaledHeight = (frame.height * (width.toFloat() / frame.width)).toInt()
                    .coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, width, scaledHeight, true)
            }
        }

        try {
            val cropTops = IntArray(normalized.size)
            for (index in 1 until normalized.size) {
                cropTops[index] = findOverlap(normalized[index - 1], normalized[index])
            }

            val totalHeight = normalized.first().height +
                    (1 until normalized.size).sumOf { index ->
                        (normalized[index].height - cropTops[index]).coerceAtLeast(1)
                    }
            val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var y = 0

            normalized.forEachIndexed { index, frame ->
                val cropTop = cropTops[index].coerceIn(0, frame.height - 1)
                val drawHeight = frame.height - cropTop
                canvas.drawBitmap(
                    frame,
                    Rect(0, cropTop, width, frame.height),
                    Rect(0, y, width, y + drawHeight),
                    null
                )
                y += drawHeight
            }

            return result
        } finally {
            normalized.forEachIndexed { index, bitmap ->
                if (bitmap !== frames[index]) bitmap.recycle()
            }
        }
    }

    fun looksSame(first: Bitmap, second: Bitmap): Boolean {
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        if (width <= 0 || height <= 0) return false

        var total = 0L
        var count = 0
        val xStep = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
        val yStep = (height / SAMPLE_ROWS).coerceAtLeast(1)

        var y = yStep / 2
        while (y < height) {
            var x = xStep / 2
            while (x < width) {
                total += pixelDifference(first.getPixel(x, y), second.getPixel(x, y))
                count++
                x += xStep
            }
            y += yStep
        }

        return count > 0 && total / count < 3
    }

    private fun findOverlap(previous: Bitmap, next: Bitmap): Int {
        val width = minOf(previous.width, next.width)
        val searchHeight = minOf(previous.height, next.height)
        val minOverlap = (searchHeight * 0.08f).toInt().coerceAtLeast(48)
        val maxOverlap = (searchHeight * 0.72f).toInt().coerceAtLeast(minOverlap + 1)

        var bestOverlap = 0
        var bestScore = Long.MAX_VALUE

        var overlap = minOverlap
        while (overlap <= maxOverlap) {
            val score = overlapScore(previous, next, width, overlap)
            if (score < bestScore) {
                bestScore = score
                bestOverlap = overlap
            }
            overlap += OVERLAP_STEP
        }

        val averageScore = bestScore / (SAMPLE_COLUMNS * SAMPLE_ROWS).coerceAtLeast(1)
        return if (averageScore < 28) bestOverlap else 0
    }

    private fun overlapScore(
        previous: Bitmap,
        next: Bitmap,
        width: Int,
        overlap: Int
    ): Long {
        var total = 0L
        val xStep = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
        val yStep = (overlap / SAMPLE_ROWS).coerceAtLeast(1)
        val previousStartY = previous.height - overlap

        var row = 0
        var y = yStep / 2
        while (row < SAMPLE_ROWS && y < overlap) {
            var col = 0
            var x = xStep / 2
            while (col < SAMPLE_COLUMNS && x < width) {
                total += pixelDifference(
                    previous.getPixel(x, previousStartY + y),
                    next.getPixel(x, y)
                )
                x += xStep
                col++
            }
            y += yStep
            row++
        }

        return total
    }

    private fun pixelDifference(first: Int, second: Int): Int {
        val red = kotlin.math.abs(android.graphics.Color.red(first) - android.graphics.Color.red(second))
        val green = kotlin.math.abs(android.graphics.Color.green(first) - android.graphics.Color.green(second))
        val blue = kotlin.math.abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))
        return (red + green + blue) / 3
    }
}
