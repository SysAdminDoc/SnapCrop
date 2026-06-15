package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class TextBlock(val text: String, val bounds: Rect)

enum class OcrScript(val key: String, val label: String) {
    LATIN("latin", "Latin (default)"),
    CHINESE("chinese", "Chinese"),
    JAPANESE("japanese", "Japanese"),
    KOREAN("korean", "Korean"),
    DEVANAGARI("devanagari", "Devanagari");

    companion object {
        const val PREF_KEY = "ocr_script"
        fun fromKey(key: String?): OcrScript = entries.firstOrNull { it.key == key } ?: LATIN
        fun fromContext(context: Context): OcrScript {
            val prefs = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
            return fromKey(prefs.getString(PREF_KEY, null))
        }
    }
}

object TextExtractor {

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    private fun recognizer(script: OcrScript): TextRecognizer {
        return recognizers.getOrPut(script) {
            TextRecognition.getClient(
                when (script) {
                    OcrScript.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
                    OcrScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
                    OcrScript.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
                    OcrScript.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
                    OcrScript.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
                }
            )
        }
    }

    suspend fun extract(bitmap: Bitmap, script: OcrScript = OcrScript.LATIN): List<TextBlock> {
        if (bitmap.isRecycled) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer(script).process(image)
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
