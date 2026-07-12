package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

internal data class StepDimensions(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
    val decodedBytes: Long get() = pixels * 4L
}

internal data class StoredStepFrame(
    val file: File,
    val width: Int,
    val height: Int,
    val tapXFraction: Float,
    val tapYFraction: Float,
    val decodedBytes: Long,
    val encodedBytes: Long
)

internal enum class StepCaptureStopReason { MANUAL, FRAME_LIMIT, PIXEL_LIMIT, MEMORY_LIMIT, CACHE_LIMIT, DURATION, INACTIVITY, STORAGE_FAILURE }

internal class StepCaptureLimitException(val reason: StepCaptureStopReason) : Exception(reason.name)

internal object StepCapturePolicy {
    const val MAX_FRAME_WIDTH = 720
    const val MAX_FRAMES = 10
    const val MAX_SESSION_PIXELS = 12_000_000L
    const val MAX_DECODED_BYTES = 48L * 1024L * 1024L
    const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    const val MAX_OUTPUT_PIXELS = 12_000_000L
    const val MAX_DURATION_MS = 10L * 60L * 1000L
    const val INACTIVITY_TIMEOUT_MS = 2L * 60L * 1000L
    const val DOCUMENTED_PEAK_BYTES = 80L * 1024L * 1024L

    fun normalizedDimensions(width: Int, height: Int): StepDimensions {
        require(width > 0 && height > 0)
        if (width <= MAX_FRAME_WIDTH) return StepDimensions(width, height)
        val scale = MAX_FRAME_WIDTH.toDouble() / width
        return StepDimensions(MAX_FRAME_WIDTH, (height * scale).roundToInt().coerceAtLeast(1))
    }

    fun violation(existing: List<StoredStepFrame>, candidate: StoredStepFrame): StepCaptureStopReason? {
        if (existing.size + 1 > MAX_FRAMES) return StepCaptureStopReason.FRAME_LIMIT
        if (existing.sumOf { it.width.toLong() * it.height } + candidate.width.toLong() * candidate.height > MAX_SESSION_PIXELS) {
            return StepCaptureStopReason.PIXEL_LIMIT
        }
        if (existing.sumOf(StoredStepFrame::decodedBytes) + candidate.decodedBytes > MAX_DECODED_BYTES) {
            return StepCaptureStopReason.MEMORY_LIMIT
        }
        if (existing.sumOf(StoredStepFrame::encodedBytes) + candidate.encodedBytes > MAX_CACHE_BYTES) {
            return StepCaptureStopReason.CACHE_LIMIT
        }
        return null
    }

    fun frameViolation(dimensions: StepDimensions): StepCaptureStopReason? = when {
        dimensions.pixels > MAX_SESSION_PIXELS -> StepCaptureStopReason.PIXEL_LIMIT
        dimensions.decodedBytes > MAX_DECODED_BYTES -> StepCaptureStopReason.MEMORY_LIMIT
        else -> null
    }

    fun timeoutReason(startedAt: Long, lastActivityAt: Long, now: Long): StepCaptureStopReason? = when {
        now - startedAt >= MAX_DURATION_MS -> StepCaptureStopReason.DURATION
        now - lastActivityAt >= INACTIVITY_TIMEOUT_MS -> StepCaptureStopReason.INACTIVITY
        else -> null
    }

    fun normalizedBitmap(source: Bitmap): Bitmap {
        val target = normalizedDimensions(source.width, source.height)
        frameViolation(target)?.let { throw StepCaptureLimitException(it) }
        return if (target.width == source.width && target.height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, target.width, target.height, true)
        }
    }
}

internal class StepCaptureStore(private val root: File) {
    private var sessionDirectory: File? = null
    private var nextIndex = 0

    fun startSession(): File {
        purgeStaleSessions()
        return root.resolve("session-${UUID.randomUUID()}").also {
            check(it.mkdirs()) { "Could not create step capture session" }
            sessionDirectory = it
            nextIndex = 0
        }
    }

    fun persist(bitmap: Bitmap, tapXFraction: Float, tapYFraction: Float): StoredStepFrame {
        val directory = sessionDirectory ?: error("Step capture session is not active")
        val index = nextIndex++
        val completed = directory.resolve("step-${index.toString().padStart(2, '0')}.png")
        val partial = directory.resolve("${completed.name}.part")
        try {
            FileOutputStream(partial).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode step frame" }
                output.fd.sync()
            }
            check(partial.length() > 0) { "Encoded frame is empty" }
            if (partial.length() > StepCapturePolicy.MAX_CACHE_BYTES) {
                throw StepCaptureLimitException(StepCaptureStopReason.CACHE_LIMIT)
            }
            check(partial.renameTo(completed)) { "Could not publish step frame" }
            return StoredStepFrame(
                file = completed,
                width = bitmap.width,
                height = bitmap.height,
                tapXFraction = tapXFraction,
                tapYFraction = tapYFraction,
                decodedBytes = bitmap.width.toLong() * bitmap.height * 4L,
                encodedBytes = completed.length()
            )
        } catch (e: Exception) {
            partial.delete()
            completed.delete()
            throw e
        }
    }

    fun deleteFrame(frame: StoredStepFrame) {
        frame.file.delete()
    }

    fun deleteSession() {
        sessionDirectory?.deleteRecursively()
        sessionDirectory = null
        nextIndex = 0
    }

    fun purgeStaleSessions(now: Long = System.currentTimeMillis()) {
        root.mkdirs()
        root.listFiles()?.filter { it.isDirectory && now - it.lastModified() >= STALE_SESSION_MS }
            ?.forEach(File::deleteRecursively)
    }

    companion object {
        private const val STALE_SESSION_MS = 24L * 60L * 60L * 1000L
    }
}

/** Allocates only the final guide and one decoded cached frame at a time. */
internal object StepGuideAssembler {
    private const val BG_COLOR = 0xFF1E1E2E.toInt()
    private const val ACCENT = 0xFF89B4FA.toInt()

    data class Layout(val width: Int, val height: Int, val gap: Int, val frameHeights: List<Int>) {
        val pixels: Long get() = width.toLong() * height
    }

    fun layout(frames: List<StoredStepFrame>): Layout {
        require(frames.isNotEmpty()) { "At least one step is required" }
        require(frames.all { it.width > 0 && it.height > 0 }) { "Step dimensions must be positive" }
        val width = frames.minOf(StoredStepFrame::width)
        val gap = (width * 0.025f).toInt().coerceAtLeast(12)
        val heights = frames.map { frame ->
            (frame.height.toDouble() * width / frame.width).roundToInt().coerceAtLeast(1)
        }
        val totalHeight = heights.sumOf(Int::toLong) + gap.toLong() * (frames.size + 1)
        require(totalHeight in 1..Int.MAX_VALUE) { "Step guide height is too large" }
        val layout = Layout(width, totalHeight.toInt(), gap, heights)
        require(layout.pixels <= StepCapturePolicy.MAX_OUTPUT_PIXELS) { "Step guide exceeds output pixel budget" }
        return layout
    }

    fun estimatedPeakBytes(frames: List<StoredStepFrame>): Long {
        val layout = layout(frames)
        return layout.pixels * 4L + frames.maxOf(StoredStepFrame::decodedBytes)
    }

    fun assemble(frames: List<StoredStepFrame>): Bitmap {
        val layout = layout(frames)
        val result = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            canvas.drawColor(BG_COLOR)
            var y = layout.gap
            frames.forEachIndexed { index, frame ->
                val decoded = BitmapFactory.decodeFile(frame.file.absolutePath)
                    ?: error("Could not decode cached step")
                try {
                    check(decoded.width == frame.width && decoded.height == frame.height) { "Cached step dimensions changed" }
                    val height = layout.frameHeights[index]
                    canvas.drawBitmap(
                        decoded,
                        Rect(0, 0, decoded.width, decoded.height),
                        Rect(0, y, layout.width, y + height),
                        null
                    )
                    drawMarker(canvas, layout.width, height, y, index + 1, frame)
                    y += height + layout.gap
                } finally {
                    decoded.recycle()
                }
            }
            return result
        } catch (e: Throwable) {
            result.recycle()
            throw e
        }
    }

    private fun drawMarker(canvas: Canvas, width: Int, height: Int, top: Int, number: Int, frame: StoredStepFrame) {
        val radius = (width * 0.045f).coerceAtLeast(28f)
        val hasPoint = frame.tapXFraction.isFinite() && frame.tapYFraction.isFinite()
        val cx = if (hasPoint) (frame.tapXFraction * width).coerceIn(radius, width - radius) else radius + width * 0.02f
        val localY = if (hasPoint) (frame.tapYFraction * height).coerceIn(radius, height - radius) else radius + height * 0.02f
        val cy = top + localY
        if (hasPoint) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ACCENT
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.18f
            }
            canvas.drawCircle(cx, cy, radius * 1.4f, ring)
        }
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.1f
            isFakeBoldText = true
        }
        val metrics = text.fontMetrics
        canvas.drawText(number.toString(), cx, cy - (metrics.ascent + metrics.descent) / 2f, text)
    }
}
