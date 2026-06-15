package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object ScreenshotSummarizer {

    data class Summary(
        val description: String,
        val textContent: String,
        val entityLabels: List<String>,
        val barcodeLabels: List<String>,
        val dominantColors: List<String>,
        val dimensions: String
    )

    suspend fun summarize(context: Context, uri: Uri): Summary = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream, null,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            )
        } ?: return@withContext Summary(
            "Image could not be loaded", "", emptyList(), emptyList(), emptyList(), ""
        )

        try {
            val w = bitmap.width * 2
            val h = bitmap.height * 2
            val orientation = if (h > w) "portrait" else if (w > h) "landscape" else "square"
            val dims = "${w}×${h}"

            val ocrScript = OcrScript.fromContext(context)
            val textBlocks = withTimeoutOrNull(5_000) { TextExtractor.extract(bitmap, ocrScript) }
                ?: emptyList()
            val allText = textBlocks.joinToString(" ") { it.text }
            val words = allText.split("\\s+".toRegex()).filter { it.isNotBlank() }

            val barcodes = withTimeoutOrNull(3_000) { BarcodeScanner.scan(bitmap) }
                ?: emptyList()

            val colors = ColorPaletteExtractor.extract(bitmap, 3)

            val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
            val phonePattern = Regex("(?:\\+?\\d[\\d\\s().-]{7,}\\d)")
            val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

            val entities = mutableListOf<String>()
            val emails = emailPattern.findAll(allText).toList()
            val phones = phonePattern.findAll(allText).toList()
            val urls = urlPattern.findAll(allText).toList()
            if (emails.isNotEmpty()) entities.add("${emails.size} email address${if (emails.size > 1) "es" else ""}")
            if (phones.isNotEmpty()) entities.add("${phones.size} phone number${if (phones.size > 1) "s" else ""}")
            if (urls.isNotEmpty()) entities.add("${urls.size} URL${if (urls.size > 1) "s" else ""}")

            val barcodeLabels = barcodes.map { code ->
                when (code.type) {
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_URL -> "URL: ${code.displayValue}"
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_WIFI -> code.displayValue
                    com.google.mlkit.vision.barcode.common.Barcode.TYPE_EMAIL -> "Email: ${code.displayValue}"
                    else -> "Code: ${code.displayValue.take(40)}"
                }
            }

            val colorLabels = colors.map { "${it.hex} (${String.format("%.0f", it.percentage)}%)" }

            val desc = buildString {
                append("$dims $orientation screenshot")
                if (words.isNotEmpty()) {
                    append(". Contains ${words.size} word${if (words.size != 1) "s" else ""} of text")
                    val preview = words.take(12).joinToString(" ")
                    append(": \"$preview${if (words.size > 12) "…" else ""}\"")
                }
                if (entities.isNotEmpty()) {
                    append(". Detected ${entities.joinToString(", ")}")
                }
                if (barcodes.isNotEmpty()) {
                    append(". ${barcodes.size} barcode${if (barcodes.size > 1) "s" else ""} found")
                }
                if (colors.isNotEmpty()) {
                    append(". Dominant colors: ${colors.take(3).joinToString(", ") { it.hex }}")
                }
                append(".")
            }

            Summary(
                description = desc,
                textContent = allText,
                entityLabels = entities,
                barcodeLabels = barcodeLabels,
                dominantColors = colorLabels,
                dimensions = dims
            )
        } finally {
            bitmap.recycle()
        }
    }
}
