package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
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

class ScrollCaptureService : AccessibilityService() {

    companion object {
        private const val MAX_FRAMES = 10
        private const val MAX_CAPTURE_DURATION_MS = 18_000L
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
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var stopReason = "Reached capture limit"

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

                while (
                    isActive &&
                    frames.size < MAX_FRAMES &&
                    android.os.SystemClock.elapsedRealtime() - startedAt < MAX_CAPTURE_DURATION_MS
                ) {
                    val frame = captureCleanFrame()
                    if (frame == null) {
                        stopReason = "Screen capture failed"
                        Toast.makeText(
                            this@ScrollCaptureService,
                            "Screen capture failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        break
                    }

                    if (frames.lastOrNull()?.let { ScrollStitcher.looksSame(it, frame) } == true) {
                        frame.recycle()
                        stopReason = "Stopped at repeated content"
                        break
                    }

                    frames.add(frame)

                    if (frames.size >= MAX_FRAMES) {
                        stopReason = "Reached $MAX_FRAMES frame safety limit"
                        break
                    }
                    if (android.os.SystemClock.elapsedRealtime() - startedAt >= MAX_CAPTURE_DURATION_MS) {
                        stopReason = "Stopped after time safety limit"
                        break
                    }
                    if (!scrollForward()) {
                        stopReason = "Reached end of scrollable content"
                        break
                    }

                    delay(SCREENSHOT_INTERVAL_MS)
                }

                if (
                    stopReason == "Reached capture limit" &&
                    android.os.SystemClock.elapsedRealtime() - startedAt >= MAX_CAPTURE_DURATION_MS
                ) {
                    stopReason = "Stopped after time safety limit"
                }

                when {
                    frames.size >= 2 -> reviewLongScreenshot(frames, stopReason)
                    frames.size == 1 -> Toast.makeText(
                        this@ScrollCaptureService,
                        if (stopReason == "Screen capture failed") stopReason else "No new content after scrolling",
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

    private suspend fun reviewLongScreenshot(frames: List<Bitmap>, stopReason: String) {
        val stitched = try {
            withContext(Dispatchers.Default) {
                ScrollStitcher.stitch(frames)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Long screenshot stitch failed", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val reviewFile = withContext(Dispatchers.IO) {
                LongScreenshotStore.writeReviewFile(this@ScrollCaptureService, stitched)
            }
            if (reviewFile != null) {
                if (openReview(reviewFile.first, reviewFile.second, frames.size, stopReason)) {
                    Toast.makeText(
                        this,
                        "Review long screenshot before saving",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    LongScreenshotStore.deleteReviewFile(reviewFile.second)
                    val fallbackUri = withContext(Dispatchers.IO) {
                        LongScreenshotStore.saveToGallery(this@ScrollCaptureService, stitched)
                    }
                    if (fallbackUri != null) {
                        Toast.makeText(this, "Review unavailable; saved to SnapCrop", Toast.LENGTH_SHORT).show()
                        openEditor(fallbackUri)
                    } else {
                        Toast.makeText(this, "Long screenshot save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val fallbackUri = withContext(Dispatchers.IO) {
                    LongScreenshotStore.saveToGallery(this@ScrollCaptureService, stitched)
                }
                if (fallbackUri != null) {
                    Toast.makeText(this, "Review unavailable; saved to SnapCrop", Toast.LENGTH_SHORT).show()
                    openEditor(fallbackUri)
                } else {
                    Toast.makeText(this, "Long screenshot save failed", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            stitched.recycle()
        }
    }

    private fun openReview(uri: android.net.Uri, path: String, frameCount: Int, stopReason: String): Boolean =
        try {
            startActivity(
                Intent(this, LongScreenshotReviewActivity::class.java).apply {
                    putExtra(LongScreenshotReviewActivity.EXTRA_REVIEW_URI, uri.toString())
                    putExtra(LongScreenshotReviewActivity.EXTRA_REVIEW_PATH, path)
                    putExtra(LongScreenshotReviewActivity.EXTRA_FRAME_COUNT, frameCount)
                    putExtra(LongScreenshotReviewActivity.EXTRA_STOP_REASON, stopReason)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            true
        } catch (_: Exception) {
            false
        }

    private fun openEditor(uri: android.net.Uri) {
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
}

internal object ScrollStitcher {
    private const val OVERLAP_STEP = 8
    private const val START_STEP = 8
    private const val SAMPLE_COLUMNS = 24
    private const val SAMPLE_ROWS = 18
    private const val SAME_FRAME_THRESHOLD = 5
    private const val OVERLAP_THRESHOLD = 34f
    private const val STICKY_BAND_THRESHOLD = 9f

    private data class Transition(
        val nextCropTop: Int,
        val previousBottomTrim: Int
    )

    fun stitch(frames: List<Bitmap>): Bitmap {
        require(frames.isNotEmpty()) { "At least one frame is required" }

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
            val bottomTrims = IntArray(normalized.size)
            for (index in 1 until normalized.size) {
                val transition = findTransition(normalized[index - 1], normalized[index])
                cropTops[index] = transition.nextCropTop
                bottomTrims[index - 1] = transition.previousBottomTrim
            }

            val totalHeight = normalized.indices.sumOf { index ->
                val cropTop = cropTops[index].coerceIn(0, normalized[index].height - 1)
                val cropBottomTrim = bottomTrims[index].coerceIn(0, normalized[index].height - cropTop - 1)
                (normalized[index].height - cropTop - cropBottomTrim).coerceAtLeast(1)
            }
            val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var y = 0

            normalized.forEachIndexed { index, frame ->
                val cropTop = cropTops[index].coerceIn(0, frame.height - 1)
                val cropBottomTrim = bottomTrims[index].coerceIn(0, frame.height - cropTop - 1)
                val sourceBottom = frame.height - cropBottomTrim
                val drawHeight = (sourceBottom - cropTop).coerceAtLeast(1)
                canvas.drawBitmap(
                    frame,
                    Rect(0, cropTop, width, sourceBottom),
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

        val top = (height * 0.08f).toInt().coerceAtMost(height / 3)
        val bottom = (height * 0.92f).toInt().coerceAtLeast(top + 1)
        var total = 0L
        var count = 0
        val xStep = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
        val yStep = ((bottom - top) / SAMPLE_ROWS).coerceAtLeast(1)

        var y = top + yStep / 2
        while (y < bottom) {
            var x = xStep / 2
            while (x < width) {
                total += pixelDifference(first.getPixel(x, y), second.getPixel(x, y))
                count++
                x += xStep
            }
            y += yStep
        }

        return count > 0 && total / count < SAME_FRAME_THRESHOLD
    }

    private fun findTransition(previous: Bitmap, next: Bitmap): Transition {
        val repeatedTop = detectRepeatedBand(previous, next, top = true)
        val repeatedBottom = detectRepeatedBand(previous, next, top = false)
        val cropTop = findNextCropTop(previous, next, repeatedTop, repeatedBottom)
        return Transition(
            nextCropTop = maxOf(cropTop, repeatedTop).coerceIn(0, next.height - 1),
            previousBottomTrim = repeatedBottom.coerceIn(0, previous.height / 3)
        )
    }

    private fun findNextCropTop(
        previous: Bitmap,
        next: Bitmap,
        repeatedTopHint: Int,
        repeatedBottomHint: Int
    ): Int {
        val width = minOf(previous.width, next.width)
        val previousUsableHeight = (previous.height - repeatedBottomHint).coerceAtLeast(1)
        val searchHeight = minOf(previousUsableHeight, next.height)
        if (width <= 0 || searchHeight < 64) return repeatedTopHint
        val minOverlap = (searchHeight * 0.08f).toInt().coerceAtLeast(48)
        val maxOverlap = minOf((searchHeight * 0.72f).toInt(), searchHeight - 1)
            .coerceAtLeast(minOverlap + 1)
        val maxStart = minOf(
            next.height - minOverlap - 1,
            maxOf(160, (next.height * 0.25f).toInt())
        ).coerceAtLeast(0)

        var bestCropTop = repeatedTopHint
        var bestScore = Float.MAX_VALUE

        var start = 0
        while (start <= maxStart) {
            var overlap = minOverlap
            while (overlap <= maxOverlap && start + overlap < next.height) {
                val score = overlapScore(previous, next, width, previousUsableHeight, start, overlap)
                val adjustedScore = score + (start * 0.015f)
                if (adjustedScore < bestScore) {
                    bestScore = adjustedScore
                    bestCropTop = start + overlap
                }
                overlap += OVERLAP_STEP
            }
            start += START_STEP
        }

        return if (bestScore < OVERLAP_THRESHOLD) bestCropTop else repeatedTopHint
    }

    private fun overlapScore(
        previous: Bitmap,
        next: Bitmap,
        width: Int,
        previousUsableHeight: Int,
        nextStartY: Int,
        overlap: Int
    ): Float {
        var total = 0L
        var count = 0
        val xStep = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
        val yStep = (overlap / SAMPLE_ROWS).coerceAtLeast(1)
        val previousStartY = previousUsableHeight - overlap

        var row = 0
        var y = yStep / 2
        while (row < SAMPLE_ROWS && y < overlap) {
            var col = 0
            var x = xStep / 2
            while (col < SAMPLE_COLUMNS && x < width) {
                val previousY = previousStartY + y
                val nextY = nextStartY + y
                val base = pixelDifference(previous.getPixel(x, previousY), next.getPixel(x, nextY))
                val edge = if (y >= yStep) {
                    val previousEdge = pixelDifference(
                        previous.getPixel(x, previousY),
                        previous.getPixel(x, previousY - yStep)
                    )
                    val nextEdge = pixelDifference(
                        next.getPixel(x, nextY),
                        next.getPixel(x, nextY - yStep)
                    )
                    kotlin.math.abs(previousEdge - nextEdge)
                } else {
                    0
                }
                total += base + edge / 2
                count++
                x += xStep
                col++
            }
            y += yStep
            row++
        }

        return if (count == 0) Float.MAX_VALUE else total.toFloat() / count
    }

    private fun detectRepeatedBand(first: Bitmap, second: Bitmap, top: Boolean): Int {
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        val maxBand = minOf((height * 0.22f).toInt(), 220).coerceAtLeast(0)
        if (width <= 0 || maxBand < 32) return 0

        var repeated = 0
        var y = 0
        while (y < maxBand) {
            val firstY = if (top) y else first.height - 1 - y
            val secondY = if (top) y else second.height - 1 - y
            val score = rowDifference(first, second, width, firstY, secondY)
            if (score > STICKY_BAND_THRESHOLD) break
            repeated = y + 1
            y += OVERLAP_STEP
        }

        return if (repeated >= 32) repeated else 0
    }

    private fun rowDifference(first: Bitmap, second: Bitmap, width: Int, firstY: Int, secondY: Int): Float {
        val xStep = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
        var total = 0L
        var count = 0
        var x = xStep / 2
        while (x < width) {
            total += pixelDifference(first.getPixel(x, firstY), second.getPixel(x, secondY))
            count++
            x += xStep
        }
        return if (count == 0) Float.MAX_VALUE else total.toFloat() / count
    }

    private fun pixelDifference(first: Int, second: Int): Int {
        val red = kotlin.math.abs(android.graphics.Color.red(first) - android.graphics.Color.red(second))
        val green = kotlin.math.abs(android.graphics.Color.green(first) - android.graphics.Color.green(second))
        val blue = kotlin.math.abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))
        return (red + green + blue) / 3
    }
}
