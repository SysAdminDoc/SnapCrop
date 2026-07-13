package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = syncMutex.withLock {
        val prefs = applicationContext.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) return@withLock Result.success()

        val journalStarted = OperationJournal.start()
        IndexHealthStore.markStarted(applicationContext)
        try {
            val store = ScreenshotIndexStore(applicationContext)
            val screenSize = getScreenSize(applicationContext)
            val favoriteKeys = FavoritesStore.getAllKeys(applicationContext)
            val indexed = store.syncFromMediaStore(
                applicationContext.contentResolver,
                screenSize.first,
                screenSize.second,
                favoriteKeys
            )
            IndexHealthStore.markSuccess(applicationContext, indexed)
            OperationJournal.record(
                applicationContext,
                DiagnosticOperation.INDEX,
                DiagnosticStage.COMPLETE,
                DiagnosticResult.SUCCESS,
                journalStarted,
            )
            Result.success()
        } catch (error: Throwable) {
            IndexHealthStore.markFailure(applicationContext)
            val disposition = failureDisposition(error, runAttemptCount)
            OperationJournal.record(
                applicationContext,
                DiagnosticOperation.INDEX,
                DiagnosticStage.OBSERVE,
                if (disposition == IndexFailureDisposition.RETRY) DiagnosticResult.RETRY else DiagnosticResult.FAILED,
                journalStarted,
                if (error is SecurityException) DiagnosticCode.PERMISSION_DENIED else DiagnosticCode.INTERNAL,
                error,
            )
            when (disposition) {
                IndexFailureDisposition.COMPLETE -> Result.success()
                IndexFailureDisposition.RETRY -> Result.retry()
                IndexFailureDisposition.FAIL -> Result.failure()
            }
        }
    }

    companion object {
        internal const val WORK_NAME = "snapcrop_index"
        internal const val IMMEDIATE_WORK_NAME = "snapcrop_index_immediate"
        private const val MAX_ATTEMPTS = 3
        private val syncMutex = Mutex()

        fun schedule(context: Context) {
            val enabled = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
                .getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            if (enabled) schedulePeriodic(context) else cancelAll(context)
        }

        fun sync(context: Context, enabled: Boolean) {
            if (!enabled) {
                cancelAll(context)
                return
            }
            requestImmediate(context)
            schedulePeriodic(context)
        }

        fun requestImmediate(context: Context) {
            val enabled = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
                .getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            if (!enabled) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>().build(),
            )
        }

        private fun schedulePeriodic(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()
            val request = PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun cancelAll(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
        }

        internal fun failureDisposition(error: Throwable, runAttemptCount: Int): IndexFailureDisposition = when {
            error is SecurityException -> IndexFailureDisposition.COMPLETE
            runAttemptCount + 1 < MAX_ATTEMPTS -> IndexFailureDisposition.RETRY
            else -> IndexFailureDisposition.FAIL
        }
    }
}

internal enum class IndexFailureDisposition { COMPLETE, RETRY, FAIL }
