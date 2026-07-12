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
import kotlin.coroutines.resumeWithException

/** OCR text and source-image geometry with a defensive boundary around mutable [Rect]. */
class TextBlock(val text: String, bounds: Rect, internal val separatorAfter: Char = '\n') {
    private val storedBounds = Rect(bounds)

    val bounds: Rect
        get() = Rect(storedBounds)

    fun deepCopy(): TextBlock = TextBlock(text, storedBounds, separatorAfter)

    fun copy(text: String = this.text, bounds: Rect = this.bounds): TextBlock =
        TextBlock(text, bounds, separatorAfter)

    override fun equals(other: Any?): Boolean =
        other is TextBlock && text == other.text && storedBounds == other.storedBounds &&
            separatorAfter == other.separatorAfter

    override fun hashCode(): Int = 31 * (31 * text.hashCode() + storedBounds.hashCode()) + separatorAfter.hashCode()

    override fun toString(): String = "TextBlock(text=$text, bounds=$storedBounds, separatorAfter=$separatorAfter)"
}

/** Pure OCR correction operations used by editor state, undo snapshots, and persisted projects. */
object OcrBlockEdits {
    fun edit(blocks: List<TextBlock>, index: Int, text: String): List<TextBlock> {
        require(index in blocks.indices) { "OCR block index is out of range" }
        require(text.isNotBlank()) { "OCR block text must not be blank" }
        return blocks.mapIndexed { blockIndex, block ->
            if (blockIndex == index) block.copy(text = text) else block.deepCopy()
        }
    }

    fun delete(blocks: List<TextBlock>, index: Int): List<TextBlock> {
        require(index in blocks.indices) { "OCR block index is out of range" }
        return blocks.mapIndexedNotNull { blockIndex, block ->
            if (blockIndex == index) null else block.deepCopy()
        }
    }

    /**
     * Merges two or more selected blocks at the first selected position. Text follows source-list
     * order and bounds become the union of the selected source rectangles.
     */
    fun merge(blocks: List<TextBlock>, indices: Collection<Int>): List<TextBlock> {
        val selected = indices.toSortedSet()
        require(selected.size >= 2) { "At least two OCR blocks are required to merge" }
        require(selected.all { it in blocks.indices }) { "OCR block index is out of range" }

        val mergedBounds = blocks[selected.first()].bounds
        selected.drop(1).forEach { mergedBounds.union(blocks[it].bounds) }
        val merged = TextBlock(
            text = selected.joinToString("\n") { blocks[it].text },
            bounds = mergedBounds
        )
        val insertionIndex = selected.first()
        return buildList {
            blocks.forEachIndexed { index, block ->
                when {
                    index == insertionIndex -> add(merged)
                    index !in selected -> add(block.deepCopy())
                }
            }
        }
    }
}

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

    /** Privacy-sensitive scans must distinguish recognition failure from a clean image. */
    internal suspend fun extractOrThrow(bitmap: Bitmap, script: OcrScript): List<TextBlock> {
        check(!bitmap.isRecycled) { "Cannot scan a recycled bitmap" }
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer(script).process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(
                            result.textBlocks.mapNotNull { block ->
                                val bounds = block.boundingBox ?: return@mapNotNull null
                                TextBlock(block.text, bounds)
                            }
                        )
                    }
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
                .addOnCanceledListener { if (cont.isActive) cont.cancel() }
        }
    }

    /**
     * Returns the smallest OCR elements with geometry so separate secrets in one terminal/code
     * block remain independently reviewable. Lines and blocks are retained only as fallbacks for
     * OCR engines that do not expose element boxes.
     */
    internal suspend fun extractRedactionSpans(
        bitmap: Bitmap,
        script: OcrScript,
        failOnError: Boolean,
    ): List<TextBlock> {
        if (bitmap.isRecycled) {
            if (failOnError) error("Cannot scan a recycled bitmap")
            return emptyList()
        }
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer(script).process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(
                            result.textBlocks.flatMap { block ->
                                val elements = block.lines.flatMap { line ->
                                    val boxed = line.elements.mapNotNull { element ->
                                        element.boundingBox?.let { element.text to it }
                                    }
                                    boxed.mapIndexed { index, (text, bounds) ->
                                        TextBlock(text, bounds, if (index == boxed.lastIndex) '\n' else ' ')
                                    }
                                }
                                if (elements.isNotEmpty()) {
                                    elements
                                } else {
                                    val lines = block.lines.mapNotNull { line ->
                                        line.boundingBox?.let { TextBlock(line.text, it) }
                                    }
                                    if (lines.isNotEmpty()) lines else listOfNotNull(
                                        block.boundingBox?.let { TextBlock(block.text, it) }
                                    )
                                }
                            }
                        )
                    }
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) {
                        if (failOnError) cont.resumeWithException(error) else cont.resume(emptyList())
                    }
                }
                .addOnCanceledListener { if (cont.isActive) cont.cancel() }
        }
    }
}
