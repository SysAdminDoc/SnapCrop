package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

internal data class ScrollSaveFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String
)

internal data class LongScreenshotReviewFile(
    val uri: Uri,
    val previewPath: String,
    val bundlePath: String
)

internal object LongScreenshotStore {
    private const val REVIEW_DIR = "long_screenshots"
    private const val PLAN_FILE = "stitch_plan.bin"
    private const val PLAN_VERSION = 1
    private const val MAX_FRAMES = 10

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

    fun writeReviewFile(
        context: Context,
        bitmap: Bitmap,
        frames: List<Bitmap>,
        plan: ScrollStitchPlan
    ): LongScreenshotReviewFile? {
        val session = writeReviewSession(context, bitmap, frames, plan) ?: return null
        val file = File(session.first)
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            LongScreenshotReviewFile(uri, file.absolutePath, session.second)
        } catch (_: Exception) {
            File(session.second).deleteRecursively()
            null
        }
    }

    internal fun writeReviewSession(
        context: Context,
        bitmap: Bitmap,
        frames: List<Bitmap>,
        plan: ScrollStitchPlan
    ): Pair<String, String>? {
        if (frames.isEmpty() || frames.size > MAX_FRAMES || plan.frames.size != frames.size) return null
        val root = File(context.cacheDir, REVIEW_DIR).apply { mkdirs() }
        val dir = File(root, "review_${System.currentTimeMillis()}").apply { mkdirs() }
        val file = File(dir, "preview.png")
        return try {
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Unable to encode review bitmap")
                }
            }
            frames.forEachIndexed { index, frame ->
                File(dir, frameName(index)).outputStream().use { output ->
                    if (!frame.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IllegalStateException("Unable to encode review frame")
                    }
                }
            }
            writePlan(File(dir, PLAN_FILE), plan)
            file.absolutePath to dir.absolutePath
        } catch (_: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    fun loadPlan(context: Context, bundlePath: String?): ScrollStitchPlan? {
        val dir = resolveBundleDir(context, bundlePath) ?: return null
        return try {
            DataInputStream(File(dir, PLAN_FILE).inputStream().buffered()).use { input ->
                if (input.readInt() != PLAN_VERSION) return null
                val width = input.readInt()
                val count = input.readInt()
                if (width <= 0 || count !in 1..MAX_FRAMES) return null
                val frames = List(count) {
                    StitchFrameCrop(
                        cropTop = input.readInt(),
                        detectedCropTop = input.readInt(),
                        bottomTrim = input.readInt(),
                        detectedBottomTrim = input.readInt(),
                        frameHeight = input.readInt()
                    )
                }
                ScrollStitchPlan(width, frames)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun savePlan(context: Context, bundlePath: String?, plan: ScrollStitchPlan): Boolean {
        val dir = resolveBundleDir(context, bundlePath) ?: return false
        val temp = File(dir, "$PLAN_FILE.tmp")
        return try {
            writePlan(temp, plan)
            replaceFile(temp, File(dir, PLAN_FILE))
            true
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    fun renderBundle(context: Context, bundlePath: String?, plan: ScrollStitchPlan): Bitmap? {
        val dir = resolveBundleDir(context, bundlePath) ?: return null
        if (plan.frames.size !in 1..MAX_FRAMES) return null
        val frames = mutableListOf<Bitmap>()
        return try {
            plan.frames.indices.forEach { index ->
                val frame = BitmapFactory.decodeFile(File(dir, frameName(index)).absolutePath)
                    ?: throw IllegalStateException("Review frame unavailable")
                frames += frame
            }
            ScrollStitcher.stitch(frames, plan)
        } catch (_: Exception) {
            null
        } finally {
            frames.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    fun writePreview(previewPath: String?, bitmap: Bitmap): Boolean {
        if (previewPath.isNullOrBlank()) return false
        val file = File(previewPath)
        val temp = File(file.parentFile, "preview.tmp")
        return try {
            temp.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Unable to encode preview")
                }
            }
            replaceFile(temp, file)
            true
        } catch (_: Exception) {
            temp.delete()
            false
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

    fun deleteReviewBundle(context: Context, bundlePath: String?) {
        resolveBundleDir(context, bundlePath)?.deleteRecursively()
    }

    private fun writePlan(file: File, plan: ScrollStitchPlan) {
        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeInt(PLAN_VERSION)
            output.writeInt(plan.width)
            output.writeInt(plan.frames.size)
            plan.frames.forEach { frame ->
                output.writeInt(frame.cropTop)
                output.writeInt(frame.detectedCropTop)
                output.writeInt(frame.bottomTrim)
                output.writeInt(frame.detectedBottomTrim)
                output.writeInt(frame.frameHeight)
            }
        }
    }

    private fun resolveBundleDir(context: Context, bundlePath: String?): File? {
        if (bundlePath.isNullOrBlank()) return null
        return try {
            val root = File(context.cacheDir, REVIEW_DIR).canonicalFile
            val dir = File(bundlePath).canonicalFile
            if (!dir.path.startsWith(root.path + File.separator) || !dir.isDirectory) null else dir
        } catch (_: Exception) {
            null
        }
    }

    private fun frameName(index: Int): String = "frame_${index.toString().padStart(2, '0')}.png"

    private fun replaceFile(temp: File, destination: File) {
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temp.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
