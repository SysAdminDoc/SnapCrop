package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OcrBackfillWorkerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        OcrBackfillStatusStore.clear(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        prefs.edit().clear().commit()
        OcrBackfillStatusStore.clear(context)
    }

    @Test
    fun schedulingIsOptInOnlyAndAlwaysRequiresChargingAndIdle() {
        OcrBackfillWorker.request(context)
        assertTrue(work(OcrBackfillWorker.WORK_NAME).isEmpty())

        prefs.edit()
            .putBoolean(ScreenshotIndexStore.PREF_ENABLED, true)
            .putBoolean(OcrBackfillWorker.PREF_ENABLED, true)
            .commit()
        OcrBackfillWorker.sync(context, true)

        val immediate = work(OcrBackfillWorker.WORK_NAME).single { !it.state.isFinished }
        val periodic = work(OcrBackfillWorker.PERIODIC_WORK_NAME).single { !it.state.isFinished }
        listOf(immediate, periodic).forEach { info ->
            assertTrue(info.constraints.requiresCharging())
            assertTrue(info.constraints.requiresDeviceIdle())
            assertEquals(androidx.work.NetworkType.NOT_REQUIRED, info.constraints.requiredNetworkType)
        }
    }

    @Test
    fun disablingCancelsQueuedAndPeriodicWork() {
        prefs.edit()
            .putBoolean(ScreenshotIndexStore.PREF_ENABLED, true)
            .putBoolean(OcrBackfillWorker.PREF_ENABLED, true)
            .commit()
        OcrBackfillWorker.sync(context, true)

        prefs.edit().putBoolean(OcrBackfillWorker.PREF_ENABLED, false).commit()
        OcrBackfillWorker.sync(context, false)

        assertTrue(work(OcrBackfillWorker.WORK_NAME).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(work(OcrBackfillWorker.PERIODIC_WORK_NAME).all { it.state == WorkInfo.State.CANCELLED })
        assertFalse(OcrBackfillStatusStore.load(context).running)
    }

    @Test
    fun cancelCurrentStopsTheRunButKeepsAConstrainedFutureSchedule() {
        prefs.edit()
            .putBoolean(ScreenshotIndexStore.PREF_ENABLED, true)
            .putBoolean(OcrBackfillWorker.PREF_ENABLED, true)
            .commit()
        OcrBackfillWorker.sync(context, true)
        OcrBackfillStatusStore.save(context, OcrBackfillStatus(running = true))

        OcrBackfillWorker.cancelCurrent(context)

        assertTrue(work(OcrBackfillWorker.WORK_NAME).all { it.state == WorkInfo.State.CANCELLED })
        val future = work(OcrBackfillWorker.PERIODIC_WORK_NAME).filterNot { it.state.isFinished }
        assertEquals(1, future.size)
        assertTrue(future.single().constraints.requiresCharging())
        assertTrue(future.single().constraints.requiresDeviceIdle())
        assertFalse(OcrBackfillStatusStore.load(context).running)
    }

    @Test
    fun decodeAndRunBudgetsBoundItemsPixelsAndTime() {
        val plan = OcrBackfillPolicy.decodePlan(4000, 3000, 8L * 1024 * 1024)
        requireNotNull(plan)
        assertTrue(plan.sampleSize > 1)
        assertTrue(plan.workingPixels <= OcrBackfillPolicy.MAX_WORKING_PIXELS_PER_ITEM)
        assertEquals(null, OcrBackfillPolicy.decodePlan(20_000, 20_000, 1_000))
        assertEquals(null, OcrBackfillPolicy.decodePlan(100, 100, OcrBackfillPolicy.MAX_SOURCE_BYTES + 1))
        assertTrue(OcrBackfillPolicy.canProcess(0, 0, plan.workingPixels, 0))
        assertFalse(OcrBackfillPolicy.canProcess(OcrBackfillPolicy.MAX_ITEMS_PER_RUN, 0, 1, 0))
        assertFalse(OcrBackfillPolicy.canProcess(0, OcrBackfillPolicy.MAX_WORKING_PIXELS_PER_RUN, 1, 0))
        assertFalse(OcrBackfillPolicy.canProcess(0, 0, 1, OcrBackfillPolicy.MAX_RUN_MILLIS))
    }

    @Test
    fun retryPolicyIsBoundedAndWorkerHasExplicitCancellationCheckpoints() {
        assertEquals(OcrBackfillFailureDisposition.RETRY, OcrBackfillPolicy.failureDisposition(0))
        assertEquals(OcrBackfillFailureDisposition.RETRY, OcrBackfillPolicy.failureDisposition(1))
        assertEquals(OcrBackfillFailureDisposition.CHECKPOINT_FAILED, OcrBackfillPolicy.failureDisposition(2))

        val source = File("src/main/java/com/sysadmindoc/snapcrop/OcrBackfillWorker.kt").readText()
        assertTrue(source.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(source.contains("if (isStopped) throw CancellationException"))
        assertTrue(source.contains("store.checkpointOcrBackfill"))
        assertTrue(source.contains("store.skipOcrBackfill"))
        assertFalse(source.contains("MediaStore.createWriteRequest"))
        assertFalse(source.contains("MediaStore.createTrashRequest"))
    }

    @Test
    fun statusCountsRoundTripAndClearLocally() {
        val status = OcrBackfillStatus(queued = 7, indexed = 4, skipped = 2, failed = 1, running = true)
        OcrBackfillStatusStore.save(context, status)
        assertEquals(status.copy(updatedAt = 0), OcrBackfillStatusStore.load(context))
        OcrBackfillStatusStore.clear(context)
        assertEquals(OcrBackfillStatus(), OcrBackfillStatusStore.load(context))
    }

    private fun work(name: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
}
