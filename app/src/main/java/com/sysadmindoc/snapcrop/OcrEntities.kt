package com.sysadmindoc.snapcrop

/** Actionable entities extracted from recognized OCR text. */
internal enum class OcrEntityType(val actionable: Boolean) {
    PHONE(true), EMAIL(true), URL(true),   // launch dial/mail/browser
    IBAN(false), IPV4(false), MAC(false)   // copy to clipboard (no natural launch action)
}

internal data class OcrEntity(val type: OcrEntityType, val value: String, val display: String)

private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val URL_REGEX = Regex("https?://[^\\s)\"'>]+|www\\.[^\\s)\"'>]+")
private val IPV4_REGEX = Regex("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b")
private val MAC_REGEX = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
private val IBAN_REGEX = Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){3,7}[ ]?[A-Z0-9]{1,3}\\b")
private val PHONE_REGEX = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)")

/**
 * Detects copyable/actionable entities in [text]. Detection runs in priority order and consumes
 * character ranges so a match is never reinterpreted as a lower-priority type — e.g. an IPv4
 * address is not also reported as a phone number. Fully local; no ML Kit, no network.
 */
internal fun extractEntities(text: String, limit: Int = 12): List<OcrEntity> {
    val entities = mutableListOf<OcrEntity>()
    val seen = HashSet<String>()
    val consumed = mutableListOf<IntRange>()
    fun isFree(r: IntRange) = consumed.none { it.first <= r.last && r.first <= it.last }

    fun collect(regex: Regex, type: OcrEntityType, normalize: (String) -> Pair<String, String>?) {
        for (match in regex.findAll(text)) {
            if (!isFree(match.range)) continue
            val (value, display) = normalize(match.value.trim()) ?: continue
            if (seen.add("${type.name}:${value.lowercase()}")) {
                entities += OcrEntity(type, value, display)
                consumed += match.range
            }
        }
    }

    collect(EMAIL_REGEX, OcrEntityType.EMAIL) { it to it }
    collect(URL_REGEX, OcrEntityType.URL) { v -> (if (v.startsWith("www.")) "https://$v" else v) to v }
    collect(IPV4_REGEX, OcrEntityType.IPV4) { it to it }
    collect(MAC_REGEX, OcrEntityType.MAC) { it to it }
    collect(IBAN_REGEX, OcrEntityType.IBAN) { v -> v.replace(" ", "").let { it to v } }
    collect(PHONE_REGEX, OcrEntityType.PHONE) { raw ->
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7) digits to raw else null
    }

    return entities.take(limit)
}
