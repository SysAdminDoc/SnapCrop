package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class DuplicateAnalysisWorkerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    @Test
    fun streamedSha256IsStableAndFailsClosedAtSizeLimit() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DuplicateHashing.sha256(ByteArrayInputStream("abc".toByteArray()))
        )
        assertNull(DuplicateHashing.sha256(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2))
    }

    @Test
    fun differenceHashAndAverageLumaUseBoundedNineByEightPixels() {
        val bitmap = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        for (y in 0 until 8) for (x in 0 until 9) bitmap.setPixel(x, y, Color.rgb(255 - x * 20, 255 - x * 20, 255 - x * 20))

        val (hash, luma) = DuplicateHashing.perceptual(bitmap)

        assertEquals(-1L, hash)
        assertTrue(luma in 170..180)
        bitmap.recycle()
    }

    @Test
    fun startsAsSingleConstrainedReplaceableWork() {
        DuplicateAnalysisWorker.start(context)
        DuplicateAnalysisWorker.start(context)

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DuplicateAnalysisWorker.WORK_NAME).get()
        assertEquals(1, work.count { !it.state.isFinished })
        val active = work.single { !it.state.isFinished }
        assertTrue(active.constraints.requiresBatteryNotLow())
        assertTrue(active.constraints.requiresStorageNotLow())

        DuplicateAnalysisWorker.cancel(context)
        assertTrue(
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(DuplicateAnalysisWorker.WORK_NAME)
                .get().all { it.state == WorkInfo.State.CANCELLED }
        )
    }
}
