package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit-powered smart crop engine. Used as a fallback when the border-scan
 * algorithm finds no uniform borders to remove.
 *
 * Detects the dominant object(s) in the image and returns a bounding box
 * that encompasses them, providing content-aware cropping.
 */
object SmartCropEngine {

    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    /**
     * Runs ML Kit object detection and returns a crop rect encompassing
     * all detected objects, or the full image rect if nothing is detected.
     */
    suspend fun detect(bitmap: Bitmap): Rect {
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)

            detector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    if (detectedObjects.isEmpty()) {
                        cont.resume(Rect(0, 0, bitmap.width, bitmap.height))
                        return@addOnSuccessListener
                    }

                    // Find bounding box encompassing all detected objects
                    var minLeft = bitmap.width
                    var minTop = bitmap.height
                    var maxRight = 0
                    var maxBottom = 0

                    for (obj in detectedObjects) {
                        val box = obj.boundingBox
                        minLeft = minOf(minLeft, box.left)
                        minTop = minOf(minTop, box.top)
                        maxRight = maxOf(maxRight, box.right)
                        maxBottom = maxOf(maxBottom, box.bottom)
                    }

                    // Add 2% padding around detected content
                    val padX = (bitmap.width * 0.02f).toInt()
                    val padY = (bitmap.height * 0.02f).toInt()

                    val rect = Rect(
                        (minLeft - padX).coerceAtLeast(0),
                        (minTop - padY).coerceAtLeast(0),
                        (maxRight + padX).coerceAtMost(bitmap.width),
                        (maxBottom + padY).coerceAtMost(bitmap.height)
                    )

                    // Only use ML result if it crops at least 10% from any edge
                    val cropsSignificantly = rect.width() < bitmap.width * 0.9f ||
                            rect.height() < bitmap.height * 0.9f

                    if (cropsSignificantly) {
                        cont.resume(rect)
                    } else {
                        cont.resume(Rect(0, 0, bitmap.width, bitmap.height))
                    }
                }
                .addOnFailureListener {
                    cont.resume(Rect(0, 0, bitmap.width, bitmap.height))
                }
        }
    }
}
