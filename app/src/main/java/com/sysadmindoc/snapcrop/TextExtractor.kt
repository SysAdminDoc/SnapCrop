package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class TextBlock(val text: String, val bounds: Rect)

object TextExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extract(bitmap: Bitmap): List<TextBlock> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        val blocks = result.textBlocks.mapNotNull { block ->
                            val bounds = block.boundingBox ?: return@mapNotNull null
                            TextBlock(block.text, bounds)
                        }
                        cont.resume(blocks)
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
        }
    }
}
