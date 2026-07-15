package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

internal data class OcrBackfillStatus(
    val queued: Int = 0,
    val indexed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val running: Boolean = false,
    val modelReady: Boolean = true,
    val updatedAt: Long = 0,
)

internal object OcrBackfillStatusStore {
    private const val PREFS = "ocr_backfill_status"
    private const val QUEUED = "queued"
    private const val INDEXED = "indexed"
    private const val SKIPPED = "skipped"
    private const val FAILED = "failed"
    private const val RUNNING = "running"
    private const val MODEL_READY = "model_ready"
    private const val UPDATED_AT = "updated_at"

    fun load(context: Context): OcrBackfillStatus = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { prefs ->
            OcrBackfillStatus(
                queued = prefs.getInt(QUEUED, 0).coerceAtLeast(0),
                indexed = prefs.getInt(INDEXED, 0).coerceAtLeast(0),
                skipped = prefs.getInt(SKIPPED, 0).coerceAtLeast(0),
                failed = prefs.getInt(FAILED, 0).coerceAtLeast(0),
                running = prefs.getBoolean(RUNNING, false),
                modelReady = prefs.getBoolean(MODEL_READY, true),
                updatedAt = prefs.getLong(UPDATED_AT, 0).coerceAtLeast(0),
            )
        }

    fun save(context: Context, status: OcrBackfillStatus) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(QUEUED, status.queued.coerceAtLeast(0))
            .putInt(INDEXED, status.indexed.coerceAtLeast(0))
            .putInt(SKIPPED, status.skipped.coerceAtLeast(0))
            .putInt(FAILED, status.failed.coerceAtLeast(0))
            .putBoolean(RUNNING, status.running)
            .putBoolean(MODEL_READY, status.modelReady)
            .putLong(UPDATED_AT, status.updatedAt.coerceAtLeast(0))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

internal data class OcrDecodePlan(val sampleSize: Int, val workingPixels: Long)

internal object OcrBackfillPolicy {
    const val MAX_ITEMS_PER_RUN = 12
    const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
    const val MAX_SOURCE_PIXELS = 48_000_000L
    const val MAX_WORKING_PIXELS_PER_ITEM = 4_000_000L
    const val MAX_WORKING_PIXELS_PER_RUN = 24_000_000L
    const val MAX_RUN_MILLIS = 3 * 60_000L
    const val MAX_ATTEMPTS = 3

    fun decodePlan(width: Int, height: Int, sizeBytes: Long): OcrDecodePlan? {
        if (width <= 0 || height <= 0 || sizeBytes !in 1..MAX_SOURCE_BYTES) return null
        val sourcePixels = width.toLong() * height.toLong()
        if (sourcePixels !in 1..MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (ceilDiv(width, sample).toLong() * ceilDiv(height, sample) > MAX_WORKING_PIXELS_PER_ITEM) {
            if (sample >= 128) return null
            sample *= 2
        }
        return OcrDecodePlan(
            sampleSize = sample,
            workingPixels = ceilDiv(width, sample).toLong() * ceilDiv(height, sample),
        )
    }

    fun canProcess(processedItems: Int, processedPixels: Long, nextPixels: Long, elapsedMillis: Long): Boolean =
        processedItems < MAX_ITEMS_PER_RUN &&
            processedPixels + nextPixels <= MAX_WORKING_PIXELS_PER_RUN &&
            elapsedMillis < MAX_RUN_MILLIS

    fun failureDisposition(runAttemptCount: Int): OcrBackfillFailureDisposition =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) OcrBackfillFailureDisposition.RETRY
        else OcrBackfillFailureDisposition.CHECKPOINT_FAILED

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
}

internal enum class OcrBackfillFailureDisposition { RETRY, CHECKPOINT_FAILED }

class OcrBackfillWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_ENABLED, false) ||
            !prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
        ) return Result.success()

        val store = ScreenshotIndexStore(applicationContext)
        val script = OcrScript.fromContext(applicationContext)
        val modelReady = runCatching { OcrModelManager.isInstalled(applicationContext, script) }
            .getOrDefault(false)
        suspend fun queuedCount(): Int = runCatching { store.countOcrBackfillCandidates() }.getOrDefault(0)
        val initialQueued = queuedCount()
        var indexed = 0
        var skipped = 0
        var failed = 0
        fun publish(running: Boolean, queued: Int = initialQueued) {
            if (!prefs.getBoolean(PREF_ENABLED, false)) {
                OcrBackfillStatusStore.clear(applicationContext)
                return
            }
            OcrBackfillStatusStore.save(
                applicationContext,
                OcrBackfillStatus(
                    queued = queued,
                    indexed = indexed,
                    skipped = skipped,
                    failed = failed,
                    running = running,
                    modelReady = modelReady,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        if (!modelReady) {
            publish(running = false)
            return Result.success()
        }
        publish(running = true)

        val startedAt = SystemClock.elapsedRealtime()
        var processedItems = 0
        var processedPixels = 0L
        try {
            val candidates = store.ocrBackfillCandidates(OcrBackfillPolicy.MAX_ITEMS_PER_RUN * 3)
            for (candidate in candidates) {
                currentCoroutineContext().ensureActive()
                if (isStopped) throw CancellationException("OCR backfill stopped")
                if (processedItems >= OcrBackfillPolicy.MAX_ITEMS_PER_RUN ||
                    SystemClock.elapsedRealtime() - startedAt >= OcrBackfillPolicy.MAX_RUN_MILLIS
                ) break
                val plan = inspect(candidate)
                if (plan == null) {
                    if (checkpointIfEnabled { store.skipOcrBackfill(candidate) }) skipped++
                    processedItems++
                    publish(running = true, queued = queuedCount())
                    continue
                }
                if (!OcrBackfillPolicy.canProcess(
                        processedItems,
                        processedPixels,
                        plan.workingPixels,
                        SystemClock.elapsedRealtime() - startedAt,
                    )
                ) break

                val bitmap = decode(candidate, plan)
                if (bitmap == null) {
                    if (checkpointIfEnabled { store.skipOcrBackfill(candidate) }) skipped++
                    processedItems++
                    publish(running = true, queued = queuedCount())
                    continue
                }
                try {
                    val blocks = withTimeout(20_000) { TextExtractor.extract(bitmap, script) }
                    if (checkpointIfEnabled {
                            store.checkpointOcrBackfill(candidate, blocks.joinToString("\n") { it.text })
                        }
                    ) indexed++
                    processedItems++
                    processedPixels += plan.workingPixels
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    failed++
                    when (OcrBackfillPolicy.failureDisposition(runAttemptCount)) {
                        OcrBackfillFailureDisposition.RETRY -> {
                            publish(running = false, queued = queuedCount())
                            return Result.retry()
                        }
                        OcrBackfillFailureDisposition.CHECKPOINT_FAILED -> {
                            checkpointIfEnabled { store.skipOcrBackfill(candidate) }
                            processedItems++
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                publish(running = true, queued = queuedCount())
            }
            publish(running = false, queued = queuedCount())
            return Result.success()
        } catch (cancelled: CancellationException) {
            publish(running = false, queued = queuedCount())
            throw cancelled
        } catch (_: SecurityException) {
            failed++
            publish(running = false, queued = queuedCount())
            return Result.success()
        } catch (_: Throwable) {
            failed++
            publish(running = false, queued = queuedCount())
            return if (OcrBackfillPolicy.failureDisposition(runAttemptCount) == OcrBackfillFailureDisposition.RETRY) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun checkpointIfEnabled(block: suspend () -> Boolean): Boolean =
        checkpointMutex.withLock {
            currentCoroutineContext().ensureActive()
            val enabled = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false)
            if (!enabled || isStopped) throw CancellationException("OCR backfill disabled")
            block()
    }

    private fun inspect(candidate: ScreenshotIndexRow): OcrDecodePlan? = runCatching {
        val uri = Uri.parse(candidate.uri)
        val descriptorSize = applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length.takeIf { length -> length >= 0 }
        }
        val sourceBytes = descriptorSize ?: candidate.size
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return@runCatching null
        OcrBackfillPolicy.decodePlan(options.outWidth, options.outHeight, sourceBytes)
    }.getOrNull()

    private fun decode(candidate: ScreenshotIndexRow, plan: OcrDecodePlan): Bitmap? = runCatching {
        val bitmap = applicationContext.contentResolver.openInputStream(Uri.parse(candidate.uri))?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = plan.sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: return@runCatching null
        val valid = bitmap.width.toLong() * bitmap.height <= OcrBackfillPolicy.MAX_WORKING_PIXELS_PER_ITEM &&
            bitmap.allocationByteCount.toLong() <= OcrBackfillPolicy.MAX_WORKING_PIXELS_PER_ITEM * 4
        if (valid) bitmap else {
            bitmap.recycle()
            null
        }
    }.getOrNull()

    companion object {
        const val PREF_ENABLED = "ocr_backfill_enabled"
        private const val PREFS_NAME = "snapcrop"
        internal const val WORK_NAME = "snapcrop_ocr_backfill"
        internal const val PERIODIC_WORK_NAME = "snapcrop_ocr_backfill_periodic"
        private val checkpointMutex = Mutex()

        private fun constraints() = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        fun schedule(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            OcrBackfillStatusStore.save(
                context,
                OcrBackfillStatusStore.load(context).copy(running = false),
            )
            if (prefs.getBoolean(PREF_ENABLED, false) &&
                prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            ) {
                schedulePeriodic(context)
            } else {
                WorkManager.getInstance(context).apply {
                    cancelUniqueWork(WORK_NAME)
                    cancelUniqueWork(PERIODIC_WORK_NAME)
                }
            }
        }

        fun sync(context: Context, enabled: Boolean) {
            if (!enabled) {
                cancelAll(context)
                return
            }
            request(context)
            schedulePeriodic(context)
        }

        fun request(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_ENABLED, false) ||
                !prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            ) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OcrBackfillWorker>()
                    .setConstraints(constraints())
                    .build(),
            )
        }

        internal suspend fun refreshStatus(context: Context, store: ScreenshotIndexStore): OcrBackfillStatus {
            val script = OcrScript.fromContext(context)
            val status = OcrBackfillStatusStore.load(context).copy(
                queued = runCatching { store.countOcrBackfillCandidates() }.getOrDefault(0),
                modelReady = runCatching { OcrModelManager.isInstalled(context, script) }.getOrDefault(false),
                updatedAt = System.currentTimeMillis(),
            )
            OcrBackfillStatusStore.save(context, status)
            return status
        }

        fun cancelCurrent(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(WORK_NAME)
            manager.cancelUniqueWork(PERIODIC_WORK_NAME)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(PREF_ENABLED, false) &&
                prefs.getBoolean(ScreenshotIndexStore.PREF_ENABLED, false)
            ) schedulePeriodic(context)
            OcrBackfillStatusStore.save(
                context,
                OcrBackfillStatusStore.load(context).copy(running = false, updatedAt = System.currentTimeMillis()),
            )
        }

        suspend fun clearIndex(context: Context, store: ScreenshotIndexStore): Int =
            withContext(Dispatchers.IO) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean(PREF_ENABLED, false)
                    .commit()
                cancelAll(context)
                checkpointMutex.withLock {
                    val cleared = store.clearOcrIndex()
                    OcrBackfillStatusStore.clear(context)
                    cleared
                }
            }

        private fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<OcrBackfillWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun cancelAll(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(PERIODIC_WORK_NAME)
            }
            OcrBackfillStatusStore.save(
                context,
                OcrBackfillStatusStore.load(context).copy(running = false, updatedAt = System.currentTimeMillis()),
            )
        }
    }
}
