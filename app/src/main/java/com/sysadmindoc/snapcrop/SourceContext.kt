package com.sysadmindoc.snapcrop

import android.content.Intent
import android.net.Uri
import java.net.IDN
import java.text.Normalizer
import java.util.Locale

/** User-visible source provenance. Only explicit Intent/user signals may populate this model. */
data class ExplicitSourceContext(
    val url: String? = null,
    val label: String? = null,
    val packageName: String? = null,
) {
    val openUri: Uri?
        get() = normalizedOrNull()?.url?.let(Uri::parse)

    val shareText: String?
        get() = normalizedOrNull()?.url

    fun normalizedOrNull(): ExplicitSourceContext? {
        val normalized = ExplicitSourceContext(
            url = normalizeHttpUrl(url),
            label = normalizeLabel(label),
            packageName = normalizePackageName(packageName),
        )
        return normalized.takeIf { it.url != null || it.label != null || it.packageName != null }
    }

    /** Returns the receiver with valid non-null fields from [higherPriority] applied over it. */
    fun mergedWith(higherPriority: ExplicitSourceContext?): ExplicitSourceContext? {
        val lower = normalizedOrNull()
        val higher = higherPriority?.normalizedOrNull()
        return ExplicitSourceContext(
            url = higher?.url ?: lower?.url,
            label = higher?.label ?: lower?.label,
            packageName = higher?.packageName ?: lower?.packageName,
        ).normalizedOrNull()
    }

    /** User edits win field-by-field; an explicit blank/invalid edited value clears that field. */
    fun mergeUserEdits(userEdits: ExplicitSourceContext): ExplicitSourceContext? {
        val lower = normalizedOrNull()
        return ExplicitSourceContext(
            url = if (userEdits.url != null) normalizeHttpUrl(userEdits.url) else lower?.url,
            label = if (userEdits.label != null) normalizeLabel(userEdits.label) else lower?.label,
            packageName = if (userEdits.packageName != null) {
                normalizePackageName(userEdits.packageName)
            } else {
                lower?.packageName
            },
        ).normalizedOrNull()
    }

    fun putInto(intent: Intent): Intent = intent.apply {
        normalizedOrNull()?.let { context ->
            context.url?.let { putExtra(EXTRA_CONTEXT_URL, it) }
            context.label?.let { putExtra(EXTRA_CONTEXT_LABEL, it) }
            context.packageName?.let { putExtra(EXTRA_CONTEXT_PACKAGE, it) }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"
        private const val EXTRA_CONTEXT_URL = "com.sysadmindoc.snapcrop.SOURCE_CONTEXT_URL"
        private const val EXTRA_CONTEXT_LABEL = "com.sysadmindoc.snapcrop.SOURCE_CONTEXT_LABEL"
        private const val EXTRA_CONTEXT_PACKAGE = "com.sysadmindoc.snapcrop.SOURCE_CONTEXT_PACKAGE"

        private const val MAX_URL_LENGTH = 2_048
        private const val MAX_LABEL_LENGTH = 160
        private const val MAX_PACKAGE_LENGTH = 255
        private val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*")
        private val ENCODED_CONTROL_PATTERN = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)

        /**
         * Reads explicit provenance only. Intent.data, ClipData, media metadata, OCR, and
         * Accessibility content are deliberately ignored.
         */
        fun fromIntent(intent: Intent, activityReferrer: Uri? = null): ExplicitSourceContext? {
            val packageSignal = ExplicitSourceContext(
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
            ).normalizedOrNull()

            val activityReferrerSignal = contextFromReferrer(activityReferrer?.toString())
            val referrerNameSignal = contextFromReferrer(intent.getStringExtra(Intent.EXTRA_REFERRER_NAME))
            @Suppress("DEPRECATION")
            val referrerUriSignal = contextFromReferrer(
                intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)?.toString(),
            )
            val referrerSignal = activityReferrerSignal
                .mergeLowToHigh(referrerNameSignal)
                .mergeLowToHigh(referrerUriSignal)

            val sharedUrlSignal = ExplicitSourceContext(
                url = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            ).normalizedOrNull()
            val webUrlSignal = ExplicitSourceContext(
                url = intent.getStringExtra(WebCaptureActivity.EXTRA_SOURCE_URL),
            ).normalizedOrNull()
            val forwardedSignal = ExplicitSourceContext(
                url = intent.getStringExtra(EXTRA_CONTEXT_URL),
                label = intent.getStringExtra(EXTRA_CONTEXT_LABEL),
                packageName = intent.getStringExtra(EXTRA_CONTEXT_PACKAGE),
            ).normalizedOrNull()

            return packageSignal
                .mergeLowToHigh(referrerSignal)
                .mergeLowToHigh(sharedUrlSignal)
                .mergeLowToHigh(webUrlSignal)
                .mergeLowToHigh(forwardedSignal)
        }

        private fun contextFromReferrer(raw: String?): ExplicitSourceContext? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            normalizeHttpUrl(value)?.let { return ExplicitSourceContext(url = it) }
            normalizePackageName(value)?.let { return ExplicitSourceContext(packageName = it) }

            if (value.length > MAX_URL_LENGTH || value.any(Char::isISOControl)) return null
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
            if (!uri.scheme.equals("android-app", ignoreCase = true)) return null
            return normalizePackageName(uri.host)?.let { ExplicitSourceContext(packageName = it) }
        }

        private fun ExplicitSourceContext?.mergeLowToHigh(
            higherPriority: ExplicitSourceContext?,
        ): ExplicitSourceContext? = when {
            this == null -> higherPriority?.normalizedOrNull()
            else -> mergedWith(higherPriority)
        }

        private fun normalizeHttpUrl(raw: String?): String? {
            val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (candidate.length > MAX_URL_LENGTH ||
                candidate.any { it.isISOControl() || it.isWhitespace() } ||
                '\\' in candidate || ENCODED_CONTROL_PATTERN.containsMatchIn(candidate)
            ) return null

            val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            if (uri.userInfo != null) return null

            val rawHost = uri.host?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
            val hostWithoutBrackets = rawHost.removePrefix("[").removeSuffix("]")
            val normalizedHost = if (':' in hostWithoutBrackets) {
                hostWithoutBrackets.lowercase(Locale.ROOT)
            } else {
                runCatching {
                    IDN.toASCII(hostWithoutBrackets, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
                }.getOrNull()
                    ?.takeIf { it.isNotEmpty() && it.length <= 253 }
                    ?: return null
            }
            val port = runCatching { uri.port }.getOrNull() ?: return null
            if (port !in -1..65_535) return null
            val normalizedPort = when {
                scheme == "http" && port == 80 -> -1
                scheme == "https" && port == 443 -> -1
                else -> port
            }
            val authorityHost = if (':' in normalizedHost) "[$normalizedHost]" else normalizedHost
            val authority = if (normalizedPort >= 0) "$authorityHost:$normalizedPort" else authorityHost
            return uri.buildUpon()
                .scheme(scheme)
                .encodedAuthority(authority)
                .build()
                .toString()
                .takeIf { it.length <= MAX_URL_LENGTH }
        }

        private fun normalizeLabel(raw: String?): String? {
            val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (candidate.length > MAX_LABEL_LENGTH || candidate.any(Char::isISOControl)) return null
            return Normalizer.normalize(candidate, Normalizer.Form.NFKC)
                .replace(Regex(" {2,}"), " ")
                .takeIf { it.isNotEmpty() && it.length <= MAX_LABEL_LENGTH }
        }

        private fun normalizePackageName(raw: String?): String? {
            val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (candidate.length > MAX_PACKAGE_LENGTH || candidate.any(Char::isISOControl)) return null
            return candidate.takeIf(PACKAGE_PATTERN::matches)?.lowercase(Locale.ROOT)
        }
    }
}
