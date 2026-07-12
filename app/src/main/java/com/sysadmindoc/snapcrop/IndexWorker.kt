package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class IndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) return Result.success()

        return try {
            val store = ScreenshotIndexStore(applicationContext)
            val screenSize = getScreenSize(applicationContext)
            val favIds = FavoritesStore.getAllIds(applicationContext)
            store.rebuildFromMediaStore(
                applicationContext.contentResolver,
                screenSize.first,
                screenSize.second,
                favIds
            )
            Result.success()
        } catch (error: Throwable) {
            when (failureDisposition(error, runAttemptCount)) {
                IndexFailureDisposition.COMPLETE -> Result.success()
                IndexFailureDisposition.RETRY -> Result.retry()
                IndexFailureDisposition.FAIL -> Result.failure()
            }
        }
    }

    companion object {
        internal const val WORK_NAME = "snapcrop_index"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context) {
            val enabled = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
                .getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            sync(context, enabled)
        }

        fun sync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
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

        internal fun failureDisposition(error: Throwable, runAttemptCount: Int): IndexFailureDisposition = when {
            error is SecurityException -> IndexFailureDisposition.COMPLETE
            runAttemptCount + 1 < MAX_ATTEMPTS -> IndexFailureDisposition.RETRY
            else -> IndexFailureDisposition.FAIL
        }
    }
}

internal enum class IndexFailureDisposition { COMPLETE, RETRY, FAIL }
