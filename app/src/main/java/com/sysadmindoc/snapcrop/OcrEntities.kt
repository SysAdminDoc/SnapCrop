package com.sysadmindoc.snapcrop

/** Actionable entities extracted from recognized OCR text. */
internal enum class OcrEntityType(val actionable: Boolean) {
    PHONE(true), EMAIL(true), URL(true),   // launch dial/mail/browser
    IBAN(false), IPV4(false), MAC(false)   // copy to clipboard (no natural launch action)
}

internal data class OcrEntity(val type: OcrEntityType, val value: String, val display: String)

private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val URL_REGEX = Regex("(?i:https?://)[^\\s\"'<>]+|\\b(?i:www)\\.[^\\s\"'<>]+")
// Links typed without a scheme ("github.com/user/repo"). The TLD is checked against BARE_DOMAIN_TLDS.
private val BARE_DOMAIN_REGEX = Regex(
    "(?<![\\w@./:-])(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+([a-z]{2,24})(?![A-Za-z0-9-])" +
        "(?::\\d{2,5})?(?:[/?#][^\\s\"'<>]*)?"
)
private val IPV4_REGEX = Regex("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b")
private val MAC_REGEX = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
private val IBAN_REGEX = Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){3,7}[ ]?[A-Z0-9]{1,3}\\b")
private val PHONE_REGEX = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)")

/**
 * TLDs accepted for a domain written without "http" or "www". Lowercase only, so OCR'd sentences
 * with a dropped space ("done.It works") stay text. Extensions that double as TLDs and mostly show
 * up as file names (md, sh, py, zip, mov, rs, pl) are left out.
 */
private val BARE_DOMAIN_TLDS = setOf(
    "com", "org", "net", "edu", "gov", "mil", "info", "biz", "io", "dev", "app", "ai", "co", "me",
    "tv", "xyz", "site", "online", "store", "tech", "blog", "page", "link", "news", "shop", "cloud",
    "gg", "ly", "fm", "gl", "be", "us", "uk", "ca", "au", "nz", "ie", "de", "fr", "es", "it", "nl",
    "se", "no", "fi", "dk", "ch", "at", "eu", "ru", "jp", "cn", "kr", "in", "br", "mx",
)

private const val URL_TRAILING_PUNCTUATION = ".,;:!?*"

/**
 * Drops sentence punctuation and unbalanced closing brackets OCR picks up after a link
 * ("see example.com/a)." keeps "example.com/a"), while "wiki/Foo_(bar)" keeps its bracket.
 */
internal fun trimUrlTail(raw: String): String {
    var end = raw.length
    while (end > 0) {
        val last = raw[end - 1]
        val open = when (last) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> null }
        end = when {
            last in URL_TRAILING_PUNCTUATION -> end - 1
            open != null && raw.subSequence(0, end).count { it == last } > raw.subSequence(0, end).count { it == open } -> end - 1
            else -> break
        }
    }
    return raw.substring(0, end)
}

private fun urlEntity(raw: String, requireKnownTld: Boolean): Pair<String, String>? {
    val display = trimUrlTail(raw)
    if (display.isEmpty()) return null
    val hasScheme = display.startsWith("http://", ignoreCase = true) || display.startsWith("https://", ignoreCase = true)
    val withScheme = if (hasScheme) display.substringBefore("://").lowercase() + "://" + display.substringAfter("://") else "https://$display"
    val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    if (host.isEmpty() || '.' !in host) return null
    if (requireKnownTld && host.substringAfterLast('.') !in BARE_DOMAIN_TLDS) return null
    return withScheme to display
}

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
    collect(URL_REGEX, OcrEntityType.URL) { urlEntity(it, requireKnownTld = false) }
    collect(BARE_DOMAIN_REGEX, OcrEntityType.URL) { urlEntity(it, requireKnownTld = true) }
    collect(IPV4_REGEX, OcrEntityType.IPV4) { it to it }
    collect(MAC_REGEX, OcrEntityType.MAC) { it to it }
    collect(IBAN_REGEX, OcrEntityType.IBAN) { v -> v.replace(" ", "").let { it to v } }
    collect(PHONE_REGEX, OcrEntityType.PHONE) { raw ->
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7) digits to raw else null
    }

    return entities.take(limit)
}

/** Short chip label for a link: the host and path without the scheme, capped so the chip stays on screen. */
internal fun urlChipText(display: String, maxChars: Int = 32): String {
    val bare = display.replaceFirst(Regex("^(?i)https?://"), "").removePrefix("www.").removeSuffix("/")
    return if (bare.length <= maxChars) bare else bare.take(maxChars - 1) + "…"
}
