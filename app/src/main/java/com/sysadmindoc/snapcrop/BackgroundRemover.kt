package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result ->
                    val foreground = result.foregroundBitmap
                    if (foreground != null) {
                        cont.resume(foreground)
                    } else {
                        cont.resume(bitmap)
                    }
                }
                .addOnFailureListener {
                    cont.resume(bitmap)
                }
        }
    }
}
