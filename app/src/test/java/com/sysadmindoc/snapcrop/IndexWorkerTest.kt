package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class IndexWorkerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun uniquePeriodicWorkUpdatesAndCancelsWithIndexPreference() {
        IndexWorker.sync(context, true)
        IndexWorker.sync(context, true)

        val active = WorkManager.getInstance(context).getWorkInfosForUniqueWork(IndexWorker.WORK_NAME).get()
        assertEquals(1, active.count { !it.state.isFinished })
        val request = active.single { !it.state.isFinished }
        assertTrue(request.constraints.requiresCharging())
        assertTrue(request.constraints.requiresDeviceIdle())
        assertEquals(androidx.work.NetworkType.NOT_REQUIRED, request.constraints.requiredNetworkType)

        IndexWorker.sync(context, false)
        val cancelled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(IndexWorker.WORK_NAME).get()
        assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun failurePolicyCompletesPermissionLossAndBoundsRetries() {
        assertEquals(IndexFailureDisposition.COMPLETE, IndexWorker.failureDisposition(SecurityException(), 0))
        assertEquals(IndexFailureDisposition.RETRY, IndexWorker.failureDisposition(IllegalStateException(), 0))
        assertEquals(IndexFailureDisposition.RETRY, IndexWorker.failureDisposition(IllegalStateException(), 1))
        assertEquals(IndexFailureDisposition.FAIL, IndexWorker.failureDisposition(IllegalStateException(), 2))
    }
}
