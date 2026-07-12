package com.sysadmindoc.snapcrop

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal object DuplicateHashing {
    const val HASH_VERSION = 1
    const val MAX_EXACT_BYTES = 64L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024

    fun sha256(input: InputStream, maxBytes: Long = MAX_EXACT_BYTES): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun perceptual(bitmap: Bitmap): Pair<Long, Int> {
        val scaled = if (bitmap.width == 9 && bitmap.height == 8) bitmap
            else Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        return try {
            val pixels = IntArray(72)
            scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            val luma = IntArray(72) { index -> compositeLuma(pixels[index]) }
            var differenceHash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    if (luma[y * 9 + x] > luma[y * 9 + x + 1]) {
                        differenceHash = differenceHash or (1L shl bit)
                    }
                    bit++
                }
            }
            differenceHash to (luma.sum() / luma.size)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun compositeLuma(color: Int): Int {
        val alpha = color ushr 24 and 0xff
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        val compositedRed = (red * alpha + 128 * (255 - alpha)) / 255
        val compositedGreen = (green * alpha + 128 * (255 - alpha)) / 255
        val compositedBlue = (blue * alpha + 128 * (255 - alpha)) / 255
        return (299 * compositedRed + 587 * compositedGreen + 114 * compositedBlue) / 1000
    }
}

internal data class DuplicateMedia(
    val uri: Uri,
    val dateAdded: Long,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
) {
    val identity: Pair<String, Long> get() = uri.toString() to dateAdded
}

class DuplicateAnalysisWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val screenSize = getScreenSize(applicationContext)
            val media = enumerateScreenshots(screenSize.first, screenSize.second)
            val store = ScreenshotIndexStore(applicationContext)
            val existing = store.storedDuplicateFingerprints().associateBy {
                it.mediaUri to it.mediaDateAdded
            }
            val pending = ArrayList<DuplicateFingerprintRow>(BATCH_SIZE)
            media.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val previous = existing[item.identity]
                val reusable = previous?.takeIf {
                    it.hashVersion == DuplicateHashing.HASH_VERSION &&
                        it.sizeBytes == item.sizeBytes && it.width == item.width && it.height == item.height
                }
                val row = reusable?.copy(
                    displayName = item.displayName,
                    updatedAt = System.currentTimeMillis()
                ) ?: try {
                    fingerprint(item)
                } catch (permission: SecurityException) {
                    throw permission
                } catch (_: Exception) {
                    null
                }
                if (row != null) pending += row
                if (pending.size >= BATCH_SIZE) {
                    store.upsertDuplicateFingerprints(pending.toList())
                    pending.clear()
                }
                // Throttle progress writes: one per item floods WorkManager's DB on large libraries.
                if (index % PROGRESS_STRIDE == 0 || index + 1 == media.size) {
                    setProgress(workDataOf(KEY_SCANNED to index + 1, KEY_TOTAL to media.size))
                }
            }
            store.upsertDuplicateFingerprints(pending)
            // Pruning is safe only after the complete MediaStore enumeration and analysis pass.
            store.pruneDuplicateFingerprints(media.mapTo(hashSetOf(), DuplicateMedia::identity))
            Result.success(workDataOf(KEY_SCANNED to media.size, KEY_TOTAL to media.size))
        } catch (_: SecurityException) {
            // Permission loss is a complete, non-retryable outcome; retain prior fingerprints.
            Result.success(workDataOf(KEY_PERMISSION_LOST to true))
        } catch (cancellation: java.util.concurrent.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun enumerateScreenshots(screenWidth: Int, screenHeight: Int): List<DuplicateMedia> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        return buildList {
            applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val name = cursor.getString(nameColumn).orEmpty()
                    if (!looksLikeScreenshot(width, height, name, screenWidth, screenHeight)) continue
                    val id = cursor.getLong(idColumn)
                    add(
                        DuplicateMedia(
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            cursor.getLong(dateColumn),
                            name.take(512),
                            width,
                            height,
                            cursor.getLong(sizeColumn).coerceAtLeast(0)
                        )
                    )
                }
            }
        }
    }

    private fun fingerprint(media: DuplicateMedia): DuplicateFingerprintRow? {
        val resolver = applicationContext.contentResolver
        val exact = if (media.sizeBytes <= DuplicateHashing.MAX_EXACT_BYTES) {
            resolver.openInputStream(media.uri)?.buffered()?.use(DuplicateHashing::sha256)
        } else null
        val bitmap = decodeSampledBitmap(media.uri) ?: return null
        val (differenceHash, averageLuma) = try {
            DuplicateHashing.perceptual(bitmap)
        } finally {
            bitmap.recycle()
        }
        return DuplicateFingerprintRow(
            media.uri.toString(), media.dateAdded, media.displayName, media.width, media.height,
            media.sizeBytes, exact, differenceHash, averageLuma, DuplicateHashing.HASH_VERSION,
            System.currentTimeMillis()
        )
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val resolver = applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 18 && bounds.outHeight / (sample * 2) >= 16) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        const val WORK_NAME = "snapcrop_duplicate_analysis"
        const val KEY_SCANNED = "scanned"
        const val KEY_TOTAL = "total"
        const val KEY_PERMISSION_LOST = "permission_lost"
        private const val BATCH_SIZE = 25
        private const val PROGRESS_STRIDE = 8

        fun start(context: Context): UUID {
            val request = OneTimeWorkRequestBuilder<DuplicateAnalysisWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            return request.id
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
