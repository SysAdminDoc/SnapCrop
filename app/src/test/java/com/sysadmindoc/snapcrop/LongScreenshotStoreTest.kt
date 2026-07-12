package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LongScreenshotStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun reviewBundleRoundTripsFramesPlanAndLatestRender() {
        val frames = listOf(frame(Color.RED), frame(Color.BLUE))
        val plan = ScrollStitcher.createPlan(frames)
        val preview = ScrollStitcher.stitch(frames, plan)
        val review = LongScreenshotStore.writeReviewSession(context, preview, frames, plan)

        assertNotNull(review)
        val saved = requireNotNull(review)
        val loaded = LongScreenshotStore.loadPlan(context, saved.second)
        assertEquals(plan, loaded)

        val edited = plan.withCropTop(1, plan.frames[1].cropTop + 3)
        assertTrue(LongScreenshotStore.savePlan(context, saved.second, edited))
        assertEquals(edited, LongScreenshotStore.loadPlan(context, saved.second))
        val rendered = LongScreenshotStore.renderBundle(context, saved.second, edited)
        assertNotNull(rendered)
        assertEquals(
            edited.frames.sumOf { it.frameHeight - it.cropTop - it.bottomTrim },
            requireNotNull(rendered).height
        )

        rendered.recycle()
        preview.recycle()
        frames.forEach(Bitmap::recycle)
        LongScreenshotStore.deleteReviewBundle(context, saved.second)
        assertFalse(File(saved.second).exists())
    }

    @Test
    fun cleanupRefusesDirectoryOutsideReviewRootAndCorruptionFailsClosed() {
        val outside = File(context.cacheDir, "keep-review-test").apply { mkdirs() }
        File(outside, "keep.txt").writeText("keep")
        LongScreenshotStore.deleteReviewBundle(context, outside.absolutePath)
        assertTrue(outside.exists())

        val frames = listOf(frame(Color.BLACK), frame(Color.WHITE))
        val plan = ScrollStitcher.createPlan(frames)
        val preview = ScrollStitcher.stitch(frames, plan)
        val review = requireNotNull(LongScreenshotStore.writeReviewSession(context, preview, frames, plan))
        File(review.second, "stitch_plan.bin").writeBytes(byteArrayOf(0, 1, 2))
        assertEquals(null, LongScreenshotStore.loadPlan(context, review.second))

        preview.recycle()
        frames.forEach(Bitmap::recycle)
        LongScreenshotStore.deleteReviewBundle(context, review.second)
        outside.deleteRecursively()
    }

    private fun frame(color: Int): Bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }
}
