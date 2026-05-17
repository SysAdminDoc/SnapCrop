package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

internal data class ScrollSaveFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String
)

internal object LongScreenshotStore {
    private const val REVIEW_DIR = "long_screenshots"

    fun getSaveFormat(context: Context): ScrollSaveFormat {
        val prefs = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                ScrollSaveFormat(format, quality, "webp", "image/webp")
            }
            prefs.getBoolean("use_jpeg", false) ->
                ScrollSaveFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
            else -> ScrollSaveFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
        }
    }

    fun writeReviewFile(context: Context, bitmap: Bitmap): Pair<Uri, String>? {
        val dir = File(context.cacheDir, REVIEW_DIR).apply { mkdirs() }
        val file = File(dir, "SnapCrop_Long_Review_${System.currentTimeMillis()}.png")
        return try {
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Unable to encode review bitmap")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            uri to file.absolutePath
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val saveFormat = getSaveFormat(context)
        val prefs = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "SnapCrop_Long_${System.currentTimeMillis()}.${saveFormat.ext}"
            )
            put(MediaStore.Images.Media.MIME_TYPE, saveFormat.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Output stream unavailable")
            output.use { bitmap.compress(saveFormat.format, saveFormat.quality, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    fun deleteReviewFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}
