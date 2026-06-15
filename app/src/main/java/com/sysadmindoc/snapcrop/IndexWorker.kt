package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class IndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)) return Result.success()

        val store = ScreenshotIndexStore(applicationContext)
        val screenSize = getScreenSize(applicationContext)
        val favIds = FavoritesStore.getAllIds(applicationContext)
        store.rebuildFromMediaStore(
            applicationContext.contentResolver,
            screenSize.first,
            screenSize.second,
            favIds
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "snapcrop_index"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
