package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class ScrollCaptureService : AccessibilityService() {

    private enum class ScrollForwardResult { SCROLLED, END, WINDOW_LOST }

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
                    context.getString(R.string.toast_long_requires_11),
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
    private var lastCaptureFailure = AccessibilityScreenshotFailure.INTERNAL

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
            Toast.makeText(this, getString(R.string.long_screenshot_already_running), Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isCapturing = true
            val frames = mutableListOf<Bitmap>()
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val captureLimitMsg = getString(R.string.scroll_capture_limit)
            val timeLimitMsg = getString(R.string.long_screenshot_time_limit)
            var stopReason = captureLimitMsg
            var captureFailed = false
            var failureCode = DiagnosticCode.NONE

            try {
                val startMessage = if (startDelayMs >= 1000L) {
                    val seconds = ((startDelayMs + 999L) / 1000L).toInt()
                    getString(R.string.long_screenshot_starts_in, seconds)
                } else {
                    getString(R.string.long_screenshot_starts_now)
                }
                Toast.makeText(
                    this@ScrollCaptureService,
                    startMessage,
                    Toast.LENGTH_SHORT
                ).show()
                delay(startDelayMs.coerceAtLeast(0L))
                val targetWindowId = if (Build.VERSION.SDK_INT >= 34) currentActiveWindowTarget()?.id else null
                if (Build.VERSION.SDK_INT >= 34 && targetWindowId == null) {
                    captureFailed = true
                    failureCode = DiagnosticCode.WINDOW_UNAVAILABLE
                    stopReason = captureFailureMessage(AccessibilityScreenshotFailure.WINDOW_UNAVAILABLE)
                    Toast.makeText(this@ScrollCaptureService, stopReason, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                while (
                    isActive &&
                    frames.size < MAX_FRAMES &&
                    android.os.SystemClock.elapsedRealtime() - startedAt < MAX_CAPTURE_DURATION_MS
                ) {
                    val frame = captureCleanFrame(targetWindowId)
                    if (frame == null) {
                        captureFailed = true
                        failureCode = diagnosticCode(lastCaptureFailure)
                        stopReason = captureFailureMessage(lastCaptureFailure)
                        Toast.makeText(
                            this@ScrollCaptureService,
                            stopReason,
                            Toast.LENGTH_SHORT
                        ).show()
                        break
                    }

                    if (frames.lastOrNull()?.let { ScrollStitcher.looksSame(it, frame) } == true) {
                        frame.recycle()
                        stopReason = getString(R.string.long_screenshot_stopped_repeat)
                        break
                    }

                    frames.add(frame)

                    if (frames.size >= MAX_FRAMES) {
                        stopReason = getString(R.string.long_screenshot_frame_limit, MAX_FRAMES)
                        break
                    }
                    if (android.os.SystemClock.elapsedRealtime() - startedAt >= MAX_CAPTURE_DURATION_MS) {
                        stopReason = timeLimitMsg
                        break
                    }
                    when (scrollForward(targetWindowId)) {
                        ScrollForwardResult.SCROLLED -> Unit
                        ScrollForwardResult.END -> {
                            stopReason = getString(R.string.long_screenshot_end_scroll)
                            break
                        }
                        ScrollForwardResult.WINDOW_LOST -> {
                            captureFailed = true
                            stopReason = captureFailureMessage(AccessibilityScreenshotFailure.WINDOW_LOST)
                            break
                        }
                    }

                    delay(SCREENSHOT_INTERVAL_MS)
                }

                if (
                    stopReason == captureLimitMsg &&
                    android.os.SystemClock.elapsedRealtime() - startedAt >= MAX_CAPTURE_DURATION_MS
                ) {
                    stopReason = timeLimitMsg
                }

                when {
                    frames.size >= 2 -> {
                        val successful = reviewLongScreenshot(frames, stopReason)
                        OperationJournal.record(
                            this@ScrollCaptureService,
                            DiagnosticOperation.LONG_SCREENSHOT,
                            DiagnosticStage.COMPLETE,
                            if (successful) DiagnosticResult.SUCCESS else DiagnosticResult.FAILED,
                            startedAt,
                            if (successful) DiagnosticCode.NONE else DiagnosticCode.STORAGE_FAILURE
                        )
                    }
                    frames.size == 1 -> {
                        Toast.makeText(
                            this@ScrollCaptureService,
                            if (captureFailed) stopReason else getString(R.string.long_screenshot_no_content),
                            Toast.LENGTH_SHORT
                        ).show()
                        OperationJournal.record(
                            this@ScrollCaptureService,
                            DiagnosticOperation.LONG_SCREENSHOT,
                            DiagnosticStage.CAPTURE,
                            if (captureFailed) DiagnosticResult.FAILED else DiagnosticResult.BLOCKED,
                            startedAt,
                            if (captureFailed) failureCode else DiagnosticCode.NO_SOURCE
                        )
                    }
                    else -> OperationJournal.record(
                        this@ScrollCaptureService,
                        DiagnosticOperation.LONG_SCREENSHOT,
                        DiagnosticStage.CAPTURE,
                        DiagnosticResult.FAILED,
                        startedAt,
                        failureCode.takeUnless { it == DiagnosticCode.NONE } ?: DiagnosticCode.NO_SOURCE
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                OperationJournal.record(
                    this@ScrollCaptureService, DiagnosticOperation.LONG_SCREENSHOT,
                    DiagnosticStage.CAPTURE, DiagnosticResult.CANCELLED, startedAt,
                    DiagnosticCode.USER_CANCELLED
                )
                throw cancelled
            } catch (error: Throwable) {
                OperationJournal.record(
                    this@ScrollCaptureService, DiagnosticOperation.LONG_SCREENSHOT,
                    DiagnosticStage.PROCESS, DiagnosticResult.FAILED, startedAt,
                    DiagnosticCode.INTERNAL, error
                )
            } finally {
                frames.forEach { if (!it.isRecycled) it.recycle() }
                isCapturing = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureCleanFrame(targetWindowId: Int?): Bitmap? {
        val capture = captureAccessibilityScreenshot(targetWindowId)
        if (capture is AccessibilityScreenshotResult.Failure) {
            lastCaptureFailure = capture.reason
            return null
        }
        val success = capture as AccessibilityScreenshotResult.Success
        val raw = success.bitmap
        if (!AccessibilityScreenshotPolicy.shouldCropSystemInsets(success.windowScoped)) return raw
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

    private fun captureFailureMessage(reason: AccessibilityScreenshotFailure): String = getString(
        when (reason) {
            AccessibilityScreenshotFailure.SECURE_WINDOW -> R.string.accessibility_capture_secure
            AccessibilityScreenshotFailure.INVALID_WINDOW -> R.string.accessibility_capture_invalid_window
            AccessibilityScreenshotFailure.WINDOW_LOST -> R.string.accessibility_capture_window_lost
            AccessibilityScreenshotFailure.WINDOW_UNAVAILABLE -> R.string.accessibility_capture_window_unavailable
            AccessibilityScreenshotFailure.THROTTLED -> R.string.accessibility_capture_throttled
            AccessibilityScreenshotFailure.ACCESS_REVOKED -> R.string.accessibility_capture_access_revoked
            AccessibilityScreenshotFailure.INVALID_DISPLAY -> R.string.accessibility_capture_invalid_display
            AccessibilityScreenshotFailure.INTERNAL -> R.string.long_screenshot_capture_failed
        }
    )

    private fun diagnosticCode(reason: AccessibilityScreenshotFailure): DiagnosticCode = when (reason) {
        AccessibilityScreenshotFailure.SECURE_WINDOW -> DiagnosticCode.SECURE_WINDOW
        AccessibilityScreenshotFailure.INVALID_WINDOW -> DiagnosticCode.INVALID_WINDOW
        AccessibilityScreenshotFailure.WINDOW_LOST -> DiagnosticCode.WINDOW_LOST
        AccessibilityScreenshotFailure.WINDOW_UNAVAILABLE -> DiagnosticCode.WINDOW_UNAVAILABLE
        AccessibilityScreenshotFailure.THROTTLED -> DiagnosticCode.THROTTLED
        AccessibilityScreenshotFailure.ACCESS_REVOKED -> DiagnosticCode.ACCESS_REVOKED
        AccessibilityScreenshotFailure.INVALID_DISPLAY -> DiagnosticCode.INVALID_DISPLAY
        AccessibilityScreenshotFailure.INTERNAL -> DiagnosticCode.INTERNAL
    }

    private fun scrollForward(targetWindowId: Int?): ScrollForwardResult {
        val root = rootInActiveWindow
            ?: return if (targetWindowId != null) ScrollForwardResult.WINDOW_LOST else ScrollForwardResult.END
        return try {
            if (targetWindowId != null && root.windowId != targetWindowId) {
                ScrollForwardResult.WINDOW_LOST
            } else if (performScrollForward(root)) {
                ScrollForwardResult.SCROLLED
            } else {
                ScrollForwardResult.END
            }
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

    private suspend fun reviewLongScreenshot(frames: List<Bitmap>, stopReason: String): Boolean {
        val (plan, stitched) = try {
            withContext(Dispatchers.Default) {
                val detectedPlan = ScrollStitcher.createPlan(frames)
                detectedPlan to ScrollStitcher.stitch(frames, detectedPlan)
            }
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.long_screenshot_stitch_failed), Toast.LENGTH_SHORT).show()
            return false
        }

        var successful = false
        try {
            val reviewFile = withContext(Dispatchers.IO) {
                LongScreenshotStore.writeReviewFile(this@ScrollCaptureService, stitched, frames, plan)
            }
            if (reviewFile != null) {
                if (openReview(reviewFile, frames.size, stopReason)) {
                    successful = true
                    Toast.makeText(
                        this,
                        getString(R.string.long_screenshot_review_body),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    LongScreenshotStore.deleteReviewFile(reviewFile.previewPath)
                    LongScreenshotStore.deleteReviewBundle(this@ScrollCaptureService, reviewFile.bundlePath)
                    val fallbackUri = withContext(Dispatchers.IO) {
                        LongScreenshotStore.saveToGallery(this@ScrollCaptureService, stitched)
                    }
                    if (fallbackUri != null) {
                        successful = true
                        Toast.makeText(this, getString(R.string.long_screenshot_review_fallback), Toast.LENGTH_SHORT).show()
                        openEditor(fallbackUri)
                    } else {
                        Toast.makeText(this, getString(R.string.long_screenshot_save_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val fallbackUri = withContext(Dispatchers.IO) {
                    LongScreenshotStore.saveToGallery(this@ScrollCaptureService, stitched)
                }
                if (fallbackUri != null) {
                    successful = true
                    Toast.makeText(this, getString(R.string.long_screenshot_review_fallback), Toast.LENGTH_SHORT).show()
                    openEditor(fallbackUri)
                } else {
                    Toast.makeText(this, getString(R.string.long_screenshot_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            stitched.recycle()
        }
        return successful
    }

    private fun openReview(reviewFile: LongScreenshotReviewFile, frameCount: Int, stopReason: String): Boolean =
        try {
            startActivity(
                Intent(this, LongScreenshotReviewActivity::class.java).apply {
                    putExtra(LongScreenshotReviewActivity.EXTRA_REVIEW_URI, reviewFile.uri.toString())
                    putExtra(LongScreenshotReviewActivity.EXTRA_REVIEW_PATH, reviewFile.previewPath)
                    putExtra(LongScreenshotReviewActivity.EXTRA_BUNDLE_PATH, reviewFile.bundlePath)
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
            Toast.makeText(this, getString(R.string.long_screenshot_saved_folder), Toast.LENGTH_SHORT).show()
        }
    }
}

internal data class StitchFrameCrop(
    val cropTop: Int,
    val detectedCropTop: Int,
    val bottomTrim: Int,
    val detectedBottomTrim: Int,
    val frameHeight: Int
)

internal data class ScrollStitchPlan(
    val width: Int,
    val frames: List<StitchFrameCrop>
) {
    init {
        require(width > 0)
        require(frames.isNotEmpty())
        frames.forEachIndexed { index, frame ->
            require(frame.frameHeight > 0)
            require(frame.cropTop in 0 until frame.frameHeight)
            require(frame.detectedCropTop in 0 until frame.frameHeight)
            require(frame.bottomTrim in 0 until frame.frameHeight - frame.cropTop)
            require(frame.detectedBottomTrim in 0 until frame.frameHeight - frame.detectedCropTop)
            if (index == 0) require(frame.cropTop == 0 && frame.detectedCropTop == 0)
        }
    }

    val seamCount: Int get() = (frames.size - 1).coerceAtLeast(0)

    fun withCropTop(frameIndex: Int, cropTop: Int): ScrollStitchPlan {
        require(frameIndex in 1 until frames.size)
        val frame = frames[frameIndex]
        val maxTop = (frame.frameHeight - frame.bottomTrim - 1).coerceAtLeast(0)
        return copy(frames = frames.mapIndexed { index, current ->
            if (index == frameIndex) current.copy(cropTop = cropTop.coerceIn(0, maxTop)) else current
        })
    }

    fun resetCropTop(frameIndex: Int): ScrollStitchPlan =
        withCropTop(frameIndex, frames[frameIndex].detectedCropTop)

    fun withBottomTrim(frameIndex: Int, bottomTrim: Int): ScrollStitchPlan {
        require(frameIndex in 0 until frames.lastIndex)
        val frame = frames[frameIndex]
        val maxTrim = (frame.frameHeight - frame.cropTop - 1).coerceAtLeast(0)
        return copy(frames = frames.mapIndexed { index, current ->
            if (index == frameIndex) current.copy(bottomTrim = bottomTrim.coerceIn(0, maxTrim)) else current
        })
    }

    fun resetJoin(nextFrameIndex: Int): ScrollStitchPlan {
        require(nextFrameIndex in 1 until frames.size)
        val previousIndex = nextFrameIndex - 1
        return withCropTop(nextFrameIndex, frames[nextFrameIndex].detectedCropTop)
            .withBottomTrim(previousIndex, frames[previousIndex].detectedBottomTrim)
    }

    fun outputJoinY(frameIndex: Int): Int {
        require(frameIndex in 1 until frames.size)
        return frames.take(frameIndex).sumOf { frame ->
            (frame.frameHeight - frame.cropTop - frame.bottomTrim).coerceAtLeast(1)
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
    private const val MAX_OUTPUT_PIXELS = 64_000_000L

    private data class Transition(
        val nextCropTop: Int,
        val previousBottomTrim: Int
    )

    fun createPlan(frames: List<Bitmap>): ScrollStitchPlan {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        val width = frames.minOf { it.width }
        val normalized = normalize(frames, width)

        try {
            val cropTops = IntArray(normalized.size)
            val bottomTrims = IntArray(normalized.size)
            for (index in 1 until normalized.size) {
                val transition = findTransition(normalized[index - 1], normalized[index])
                cropTops[index] = transition.nextCropTop
                bottomTrims[index - 1] = transition.previousBottomTrim
            }

            return ScrollStitchPlan(
                width = width,
                frames = normalized.indices.map { index ->
                    val frame = normalized[index]
                    val top = cropTops[index].coerceIn(0, frame.height - 1)
                    val trim = bottomTrims[index].coerceIn(0, frame.height - top - 1)
                    StitchFrameCrop(top, top, trim, trim, frame.height)
                }
            )
        } finally {
            recycleNormalized(normalized, frames)
        }
    }

    fun stitch(frames: List<Bitmap>): Bitmap = stitch(frames, createPlan(frames))

    fun stitch(frames: List<Bitmap>, plan: ScrollStitchPlan): Bitmap {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        require(plan.frames.size == frames.size) { "Plan/frame count mismatch" }
        val width = plan.width
        val normalized = normalize(frames, width)

        try {
            require(normalized.indices.all { normalized[it].height == plan.frames[it].frameHeight }) {
                "Plan/frame geometry mismatch"
            }

            val totalHeightLong = normalized.indices.sumOf { index ->
                val cropTop = plan.frames[index].cropTop
                val cropBottomTrim = plan.frames[index].bottomTrim
                (normalized[index].height - cropTop - cropBottomTrim).coerceAtLeast(1).toLong()
            }
            require(totalHeightLong in 1..Int.MAX_VALUE.toLong()) { "Stitched output height is invalid" }
            require(plan.width.toLong() * totalHeightLong <= MAX_OUTPUT_PIXELS) { "Stitched output is too large" }
            val totalHeight = totalHeightLong.toInt()
            val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var y = 0

            normalized.forEachIndexed { index, frame ->
                val cropTop = plan.frames[index].cropTop
                val cropBottomTrim = plan.frames[index].bottomTrim
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
            recycleNormalized(normalized, frames)
        }
    }

    private fun normalize(frames: List<Bitmap>, width: Int): List<Bitmap> = frames.map { frame ->
        if (frame.width == width) frame else {
            val scaledHeight = (frame.height * (width.toFloat() / frame.width)).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, width, scaledHeight, true)
        }
    }

    private fun recycleNormalized(normalized: List<Bitmap>, source: List<Bitmap>) {
        normalized.forEachIndexed { index, bitmap ->
            if (bitmap !== source[index]) bitmap.recycle()
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
