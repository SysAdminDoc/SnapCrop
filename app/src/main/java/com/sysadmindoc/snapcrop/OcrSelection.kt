package com.sysadmindoc.snapcrop

import androidx.compose.ui.text.TextRange

/** Text the reader picked in the OCR dialog, trimmed, or null when the pick is only whitespace. */
internal fun ocrSelectionText(text: CharSequence, selection: TextRange): String? {
    if (selection.collapsed) return null
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    return text.subSequence(start, end).toString().trim().ifEmpty { null }
}

/** One-line preview of a pick for the hint under the OCR text, capped so it fits the dialog. */
internal fun ocrSelectionPreview(pick: String, maxChars: Int = 60): String {
    val flat = pick.replace(Regex("\\s+"), " ")
    return if (flat.length <= maxChars) flat else flat.take(maxChars - 1).trimEnd() + Char(0x2026)
}
