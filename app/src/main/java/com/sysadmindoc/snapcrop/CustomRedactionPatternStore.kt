package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class CustomRedactionPattern(
    val id: String,
    val name: String,
    val expression: String,
    val caseSensitive: Boolean = true,
    val enabled: Boolean = true,
) {
    companion object {
        fun create(name: String, expression: String): CustomRedactionPattern =
            CustomRedactionPattern(UUID.randomUUID().toString(), name.trim(), expression.trim())
    }
}

internal enum class CustomPatternTestStatus { MATCH, NO_MATCH, INVALID, TIMED_OUT }

internal data class CustomPatternTestResult(
    val status: CustomPatternTestStatus,
    val matchCount: Int = 0,
    val validationMessage: String? = null,
)

internal data class CustomPatternScanResult(
    val matches: List<SensitiveTextMatch>,
    val timedOut: Boolean,
)

internal object CustomRedactionPatternStore {
    const val PREF_KEY = "custom_redaction_patterns"
    const val MAX_PATTERNS = 20
    const val MAX_NAME_LENGTH = 40
    const val MAX_EXPRESSION_LENGTH = 256
    const val MAX_TEST_TEXT_LENGTH = 4_000
    private const val MAX_SCAN_TEXT_LENGTH = 20_000
    private const val MAX_MATCHES_PER_PATTERN = 100
    private const val MAX_MATCHES_TOTAL = 256
    private const val MAX_SERIALIZED_LENGTH = 16_384
    private const val MATCH_TIMEOUT_MS = 150L
    private const val SCHEMA_VERSION = 1

    fun load(prefs: SharedPreferences): List<CustomRedactionPattern> {
        val serialized = prefs.getString(PREF_KEY, null) ?: return emptyList()
        if (serialized.isBlank()) return emptyList()
        return decode(serialized) ?: throw InvalidCustomPatternStoreException()
    }

    fun save(prefs: SharedPreferences, patterns: List<CustomRedactionPattern>): Boolean {
        if (patterns.size > MAX_PATTERNS || patterns.any { validate(it) != null }) return false
        val encoded = encode(patterns)
        if (encoded.length > MAX_SERIALIZED_LENGTH) return false
        prefs.edit().putString(PREF_KEY, encoded).apply()
        return true
    }

    fun export(patterns: List<CustomRedactionPattern>): String = encode(patterns)

    fun import(serialized: String): List<CustomRedactionPattern>? = decode(serialized)

    fun validate(pattern: CustomRedactionPattern): String? {
        if (pattern.id.isBlank() || pattern.id.length > 80) return "Invalid pattern identifier"
        if (pattern.name.isBlank() || pattern.name.length > MAX_NAME_LENGTH) {
            return "Name must be 1–$MAX_NAME_LENGTH characters"
        }
        val expression = pattern.expression
        if (expression.isBlank() || expression.length > MAX_EXPRESSION_LENGTH) {
            return "Expression must be 1–$MAX_EXPRESSION_LENGTH characters"
        }
        return try {
            val compiled = compile(pattern)
            val emptyMatcher = compiled.matcher("")
            if (emptyMatcher.find() && emptyMatcher.start() == emptyMatcher.end()) {
                "Expressions that match empty text are not allowed"
            } else {
                null
            }
        } catch (error: PatternSyntaxException) {
            (error.message ?: "Unsupported expression").take(120)
        }
    }

    fun test(pattern: CustomRedactionPattern, text: String): CustomPatternTestResult {
        val error = validate(pattern)
        if (error != null) return CustomPatternTestResult(CustomPatternTestStatus.INVALID, validationMessage = error)
        if (text.length > MAX_TEST_TEXT_LENGTH) {
            return CustomPatternTestResult(
                CustomPatternTestStatus.INVALID,
                validationMessage = "Test text is limited to $MAX_TEST_TEXT_LENGTH characters",
            )
        }
        val scan = scan(text, listOf(pattern.copy(enabled = true)))
        return when {
            scan.timedOut -> CustomPatternTestResult(CustomPatternTestStatus.TIMED_OUT)
            scan.matches.isEmpty() -> CustomPatternTestResult(CustomPatternTestStatus.NO_MATCH)
            else -> CustomPatternTestResult(CustomPatternTestStatus.MATCH, scan.matches.size)
        }
    }

    fun scan(text: String, patterns: List<CustomRedactionPattern>): CustomPatternScanResult {
        val active = patterns.filter { it.enabled && validate(it) == null }.take(MAX_PATTERNS)
        if (active.isEmpty()) return CustomPatternScanResult(emptyList(), timedOut = false)
        if (text.length > MAX_SCAN_TEXT_LENGTH) return CustomPatternScanResult(emptyList(), timedOut = true)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MATCH_TIMEOUT_MS)
        val matches = buildList {
            active.forEach { custom ->
                if (System.nanoTime() > deadline) return CustomPatternScanResult(emptyList(), timedOut = true)
                val matcher = compile(custom).matcher(text)
                var count = 0
                while (count < MAX_MATCHES_PER_PATTERN && matcher.find()) {
                    if (System.nanoTime() > deadline) {
                        return CustomPatternScanResult(emptyList(), timedOut = true)
                    }
                    if (matcher.start() < matcher.end()) {
                        add(
                            SensitiveTextMatch(
                                SensitiveTextCategory.CUSTOM,
                                matcher.start() until matcher.end(),
                                SensitiveTextDetectionSource.CUSTOM,
                            )
                        )
                        count++
                        if (size >= MAX_MATCHES_TOTAL) return CustomPatternScanResult(toList(), timedOut = false)
                    }
                }
            }
        }
        return CustomPatternScanResult(matches, timedOut = false)
    }

    private fun encode(patterns: List<CustomRedactionPattern>): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("patterns", JSONArray().apply {
            patterns.forEach { pattern ->
                put(
                    JSONObject()
                        .put("id", pattern.id)
                        .put("name", pattern.name)
                        .put("expression", pattern.expression)
                        .put("caseSensitive", pattern.caseSensitive)
                        .put("enabled", pattern.enabled)
                )
            }
        })
        .toString()

    private fun decode(serialized: String): List<CustomRedactionPattern>? {
        if (serialized.isBlank() || serialized.length > MAX_SERIALIZED_LENGTH) return null
        return try {
            val root = JSONObject(serialized)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
            val array = root.optJSONArray("patterns") ?: return null
            if (array.length() > MAX_PATTERNS) return null
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: return null
                    val pattern = CustomRedactionPattern(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        expression = item.optString("expression"),
                        caseSensitive = item.optBoolean("caseSensitive", true),
                        enabled = item.optBoolean("enabled", true),
                    )
                    if (validate(pattern) != null || any { it.id == pattern.id || it.name.equals(pattern.name, true) }) {
                        return null
                    }
                    add(pattern)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun compile(pattern: CustomRedactionPattern): Pattern = Pattern.compile(
        pattern.expression,
        if (pattern.caseSensitive) 0 else Pattern.CASE_INSENSITIVE,
    )
}

internal class InvalidCustomPatternStoreException : RuntimeException("Custom redaction patterns are invalid")
