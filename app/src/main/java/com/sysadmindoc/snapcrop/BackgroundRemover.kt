package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class BackgroundRemovalResult(
    val bitmap: Bitmap,
    val changed: Boolean,
    val statusMessage: String?
)

object BackgroundRemover {

    private val segmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    /**
     * Removes the background from a bitmap using ML Kit Subject Segmentation.
     * Returns the subject on a transparent background, or the original bitmap on failure.
     */
    suspend fun remove(bitmap: Bitmap): Bitmap {
        return removeWithStatus(context = null, bitmap = bitmap).bitmap
    }

    suspend fun removeWithStatus(context: Context?, bitmap: Bitmap): BackgroundRemovalResult {
        if (bitmap.isRecycled) {
            return BackgroundRemovalResult(bitmap, changed = false, statusMessage = "Image is no longer available.")
        }
        context?.let { appContext ->
            MlKitStatus.playServicesIssue(appContext)?.let { message ->
                MlKitStatusStore.markSubjectSegmentationError(appContext, message)
                return BackgroundRemovalResult(bitmap, changed = false, statusMessage = message)
            }
        }
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        val foreground = result.foregroundBitmap
                        if (foreground != null) {
                            context?.let { MlKitStatusStore.markSubjectSegmentationReady(it) }
                            cont.resume(BackgroundRemovalResult(foreground, changed = true, statusMessage = null))
                        } else {
                            val message = "No clear foreground subject found. Try a screenshot with stronger subject/background separation."
                            context?.let { MlKitStatusStore.markSubjectSegmentationError(it, message) }
                            cont.resume(BackgroundRemovalResult(bitmap, changed = false, statusMessage = message))
                        }
                    }
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) {
                        val message = if (context != null) {
                            MlKitStatus.userMessage(context, MlKitFeature.SUBJECT_SEGMENTATION, error)
                        } else {
                            error.message?.trim().orEmpty().ifBlank { "Background removal unavailable." }
                        }
                        context?.let { MlKitStatusStore.markSubjectSegmentationError(it, message) }
                        cont.resume(BackgroundRemovalResult(bitmap, changed = false, statusMessage = message))
                    }
                }
        }
    }
}
