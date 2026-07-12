package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepCapturePipelineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commonPhoneFramesNormalizeToBoundedWidth() {
        assertEquals(StepDimensions(720, 1600), StepCapturePolicy.normalizedDimensions(1080, 2400))
        assertEquals(StepDimensions(720, 1600), StepCapturePolicy.normalizedDimensions(1440, 3200))
        assertEquals(StepDimensions(600, 1200), StepCapturePolicy.normalizedDimensions(600, 1200))
        assertEquals(
            StepCaptureStopReason.PIXEL_LIMIT,
            StepCapturePolicy.frameViolation(StepCapturePolicy.normalizedDimensions(720, 20_000))
        )
    }

    @Test
    fun tenNormalizedFramesFitEveryBudgetAndDocumentedPeak() {
        val frames = (0 until 10).map { storedFrame(width = 720, height = 1600, encodedBytes = 2L * 1024L * 1024L) }

        frames.forEachIndexed { index, frame ->
            assertNull(StepCapturePolicy.violation(frames.take(index), frame))
        }
        assertTrue(frames.sumOf(StoredStepFrame::decodedBytes) <= StepCapturePolicy.MAX_DECODED_BYTES)
        assertTrue(frames.sumOf(StoredStepFrame::encodedBytes) <= StepCapturePolicy.MAX_CACHE_BYTES)
        assertTrue(StepGuideAssembler.layout(frames).pixels <= StepCapturePolicy.MAX_OUTPUT_PIXELS)
        assertTrue(StepGuideAssembler.estimatedPeakBytes(frames) < StepCapturePolicy.DOCUMENTED_PEAK_BYTES)
        assertEquals(StepCaptureStopReason.FRAME_LIMIT, StepCapturePolicy.violation(frames, frames.first()))
    }

    @Test
    fun eachSessionBudgetFailsClosed() {
        assertEquals(
            StepCaptureStopReason.PIXEL_LIMIT,
            StepCapturePolicy.violation(listOf(storedFrame(720, 1600)), storedFrame(720, 20_000))
        )
        assertEquals(
            StepCaptureStopReason.MEMORY_LIMIT,
            StepCapturePolicy.violation(emptyList(), storedFrame(100, 100, decodedBytes = StepCapturePolicy.MAX_DECODED_BYTES + 1))
        )
        assertEquals(
            StepCaptureStopReason.CACHE_LIMIT,
            StepCapturePolicy.violation(emptyList(), storedFrame(100, 100, encodedBytes = StepCapturePolicy.MAX_CACHE_BYTES + 1))
        )
    }

    @Test
    fun durationAndInactivityBoundariesAreExact() {
        assertNull(StepCapturePolicy.timeoutReason(0, 100, StepCapturePolicy.INACTIVITY_TIMEOUT_MS))
        assertEquals(
            StepCaptureStopReason.INACTIVITY,
            StepCapturePolicy.timeoutReason(0, 100, 100 + StepCapturePolicy.INACTIVITY_TIMEOUT_MS)
        )
        assertEquals(
            StepCaptureStopReason.DURATION,
            StepCapturePolicy.timeoutReason(0, StepCapturePolicy.MAX_DURATION_MS - 1, StepCapturePolicy.MAX_DURATION_MS)
        )
    }

    @Test
    fun cacheStorePublishesCompletePngAndRemovesSession() {
        val root = temporaryFolder.newFolder("step-cache")
        val store = StepCaptureStore(root)
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        store.startSession()

        val frame = try {
            store.persist(bitmap, 0.25f, 0.75f)
        } finally {
            bitmap.recycle()
        }

        assertTrue(frame.file.isFile)
        assertTrue(frame.encodedBytes > 0)
        assertFalse(frame.file.parentFile!!.listFiles().orEmpty().any { it.name.endsWith(".part") })
        store.deleteSession()
        assertFalse(frame.file.exists())
    }

    @Test
    fun assemblerDecodesOneCachedFrameAtATimeAndPreservesOrder() {
        val first = writeFixture("first.png", Color.RED)
        val second = writeFixture("second.png", Color.BLUE)
        val frames = listOf(
            storedFrame(first, 100, 200, tapX = 0.5f, tapY = 0.5f),
            storedFrame(second, 100, 200, tapX = 0.5f, tapY = 0.5f)
        )

        val guide = StepGuideAssembler.assemble(frames)
        try {
            val layout = StepGuideAssembler.layout(frames)
            assertEquals(layout.width, guide.width)
            assertEquals(layout.height, guide.height)
            assertEquals(Color.RED, guide.getPixel(5, layout.gap + 5))
            assertEquals(Color.BLUE, guide.getPixel(5, layout.gap * 2 + layout.frameHeights[0] + 5))
        } finally {
            guide.recycle()
        }
    }

    private fun storedFrame(
        width: Int,
        height: Int,
        encodedBytes: Long = 1024,
        decodedBytes: Long = width.toLong() * height * 4L
    ) = storedFrame(File("unused"), width, height, encodedBytes = encodedBytes, decodedBytes = decodedBytes)

    private fun storedFrame(
        file: File,
        width: Int,
        height: Int,
        tapX: Float = Float.NaN,
        tapY: Float = Float.NaN,
        encodedBytes: Long = file.length(),
        decodedBytes: Long = width.toLong() * height * 4L
    ) = StoredStepFrame(file, width, height, tapX, tapY, decodedBytes, encodedBytes)

    private fun writeFixture(name: String, color: Int): File {
        val file = temporaryFolder.newFile(name)
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            FileOutputStream(file).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
