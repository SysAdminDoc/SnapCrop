package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FaceDetector {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Detect all faces and return their bounding boxes.
     * Boxes are padded by 15% for better coverage when pixelating.
     */
    suspend fun detect(bitmap: Bitmap): List<Rect> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (cont.isActive) {
                        val rects = faces.mapNotNull { face ->
                            val box = face.boundingBox
                            val padX = (box.width() * 0.15f).toInt()
                            val padY = (box.height() * 0.15f).toInt()
                            Rect(
                                (box.left - padX).coerceAtLeast(0),
                                (box.top - padY).coerceAtLeast(0),
                                (box.right + padX).coerceAtMost(bitmap.width),
                                (box.bottom + padY).coerceAtMost(bitmap.height)
                            )
                        }
                        cont.resume(rects)
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
        }
    }
}
