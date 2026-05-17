package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class CropProfileTemplate(
    val id: String,
    val label: String,
    val aliases: List<String>,
    val accentColors: List<Int>,
    val topDp: Int = 56,
    val bottomDp: Int = 56,
    val automationSummary: String
)

private data class CropProfileMatch(
    val template: CropProfileTemplate,
    val confidence: Float,
    val hasHint: Boolean
)

private data class ProfileCropCandidate(
    val id: String,
    val label: String,
    val type: String,
    val confidence: Float,
    val reason: String,
    val rect: Rect,
    val method: String,
    val actionSummary: String
)

data class BuiltInAppCropProfileInfo(
    val id: String,
    val label: String,
    val aliases: List<String>,
    val cropPreview: String,
    val confidencePreview: String,
    val automationSummary: String
)

data class AppCropProfilePreview(
    val id: String,
    val label: String,
    val type: String,
    val confidence: Float,
    val reason: String,
    val rect: Rect,
    val method: String,
    val actionSummary: String
)

object AppCropProfiles {
    private val templates = listOf(
        CropProfileTemplate(
            id = "reddit",
            label = "Reddit",
            aliases = listOf("reddit", "com.reddit.frontpage"),
            accentColors = listOf(0xFFFF4500.toInt(), 0xFFFF5700.toInt(), 0xFFD93A00.toInt()),
            automationSummary = "Quick Crop can redact sensitive text and save to the Reddit album."
        ),
        CropProfileTemplate(
            id = "twitter",
            label = "X/Twitter",
            aliases = listOf("twitter", "x.com", "com.twitter.android"),
            accentColors = listOf(0xFF1D9BF0.toInt(), 0xFF1DA1F2.toInt(), 0xFF0F8CEB.toInt()),
            automationSummary = "Quick Crop can redact sensitive text and save to the X-Twitter album."
        )
    )

    fun builtInProfiles(): List<BuiltInAppCropProfileInfo> =
        templates.map { template ->
            BuiltInAppCropProfileInfo(
                id = template.id,
                label = template.label,
                aliases = template.aliases,
                cropPreview = "Trims about ${template.topDp}dp from the top and ${template.bottomDp}dp from the bottom when app chrome is detected.",
                confidencePreview = "Source/package hints are accepted at high confidence; visual-only matches require at least 78% confidence.",
                automationSummary = template.automationSummary
            )
        }

    fun apply(
        bitmap: Bitmap,
        baseResult: AutoCrop.CropResult,
        statusBarPx: Int,
        navBarPx: Int,
        sourceHints: List<String>,
        profileTextHints: List<String> = emptyList(),
        userProfiles: List<UserAppCropProfile> = emptyList(),
        enabled: Boolean
    ): AutoCrop.CropResult {
        val preview = evaluate(
            bitmap = bitmap,
            baseResult = baseResult,
            statusBarPx = statusBarPx,
            navBarPx = navBarPx,
            sourceHints = sourceHints,
            profileTextHints = profileTextHints,
            userProfiles = userProfiles,
            enabled = enabled
        ) ?: return baseResult

        return AutoCrop.CropResult(preview.rect, preview.method)
    }

    fun evaluate(
        bitmap: Bitmap,
        baseResult: AutoCrop.CropResult,
        statusBarPx: Int,
        navBarPx: Int,
        sourceHints: List<String>,
        profileTextHints: List<String> = emptyList(),
        userProfiles: List<UserAppCropProfile> = emptyList(),
        enabled: Boolean
    ): AppCropProfilePreview? {
        if (!enabled || bitmap.width < 240 || bitmap.height < 320) return null
        val base = baseResult.rect
        if (base.height() < bitmap.height * 0.45f || base.width() < bitmap.width * 0.6f) {
            return null
        }

        val density = estimateDensity(bitmap, statusBarPx, navBarPx)
        val normalizedHints = sourceHints.map { it.lowercase() }
        val builtInCandidates = templates.mapNotNull { template ->
            builtInCandidate(bitmap, base, template, density, normalizedHints)
        }
        val userCandidate = userCandidate(base, userProfiles, sourceHints, profileTextHints)

        val best = (builtInCandidates + listOfNotNull(userCandidate))
            .maxByOrNull { it.confidence }
            ?: return null

        return AppCropProfilePreview(
            id = best.id,
            label = best.label,
            type = best.type,
            confidence = best.confidence,
            reason = best.reason,
            rect = best.rect,
            method = best.method,
            actionSummary = best.actionSummary
        )
    }

    private fun builtInCandidate(
        bitmap: Bitmap,
        base: Rect,
        template: CropProfileTemplate,
        density: Float,
        hints: List<String>
    ): ProfileCropCandidate? {
        val match = matchTemplate(bitmap, base, template, density, hints) ?: return null
        val accepted = match.hasHint || match.confidence >= 0.78f
        if (!accepted) return null

        val topTrim = (template.topDp * density).toInt().coerceIn(32, base.height() / 4)
        val bottomTrim = (template.bottomDp * density).toInt().coerceIn(32, base.height() / 4)
        val nextTop = (base.top + topTrim).coerceAtMost(base.bottom - 1)
        val nextBottom = (base.bottom - bottomTrim).coerceAtLeast(nextTop + 1)
        if (nextBottom - nextTop < bitmap.height * 0.35f) return null

        val reason = if (match.hasHint) {
            "source/package hint matched; trimmed ${template.topDp}dp top and ${template.bottomDp}dp bottom"
        } else {
            "visual chrome matched at ${(match.confidence * 100).roundToInt()}% confidence"
        }
        return ProfileCropCandidate(
            id = template.id,
            label = template.label,
            type = "Built-in",
            confidence = match.confidence,
            reason = reason,
            rect = Rect(base.left, nextTop, base.right, nextBottom),
            method = "profile:${template.label}",
            actionSummary = template.automationSummary
        )
    }

    private fun userCandidate(
        base: Rect,
        userProfiles: List<UserAppCropProfile>,
        sourceHints: List<String>,
        profileTextHints: List<String>
    ): ProfileCropCandidate? {
        val match = UserAppProfileStore.match(userProfiles, sourceHints, profileTextHints) ?: return null
        val profile = match.profile
        val leftTrim = (base.width() * profile.cropLeftFraction).roundToInt()
        val topTrim = (base.height() * profile.cropTopFraction).roundToInt()
        val rightTrim = (base.width() * profile.cropRightFraction).roundToInt()
        val bottomTrim = (base.height() * profile.cropBottomFraction).roundToInt()
        val nextLeft = (base.left + leftTrim).coerceAtMost(base.right - 1)
        val nextTop = (base.top + topTrim).coerceAtMost(base.bottom - 1)
        val nextRight = (base.right - rightTrim).coerceAtLeast(nextLeft + 1)
        val nextBottom = (base.bottom - bottomTrim).coerceAtLeast(nextTop + 1)
        if (nextRight - nextLeft < base.width() * 0.25f) return null
        if (nextBottom - nextTop < base.height() * 0.25f) return null

        val actionSummary = buildString {
            append("Quick Crop saves to ${profile.albumName}")
            if (profile.redactSensitiveText) append(" with sensitive-text redaction")
            val format = profile.normalizedExportFormat()
            if (format != "default") append(" as ${format.uppercase()}")
            append(".")
        }
        return ProfileCropCandidate(
            id = profile.id,
            label = profile.label,
            type = "User",
            confidence = match.confidence,
            reason = "${match.reason}; crop ${profile.cropSummary()}",
            rect = Rect(nextLeft, nextTop, nextRight, nextBottom),
            method = "profile:${profile.label}",
            actionSummary = actionSummary
        )
    }

    private fun matchTemplate(
        bitmap: Bitmap,
        base: Rect,
        template: CropProfileTemplate,
        density: Float,
        hints: List<String>
    ): CropProfileMatch? {
        val hasHint = hints.any { hint -> template.aliases.any { alias -> hint.contains(alias) } }
        val topHeight = (template.topDp * density).toInt().coerceIn(32, base.height() / 4)
        val bottomHeight = (template.bottomDp * density).toInt().coerceIn(32, base.height() / 4)
        val topBand = Rect(base.left, base.top, base.right, min(base.bottom, base.top + topHeight))
        val bottomBand = Rect(base.left, max(base.top, base.bottom - bottomHeight), base.right, base.bottom)

        val topChrome = bandBackgroundCoverage(bitmap, topBand)
        val bottomChrome = bandBackgroundCoverage(bitmap, bottomBand)
        val accent = max(
            accentCoverage(bitmap, topBand, template.accentColors),
            accentCoverage(bitmap, bottomBand, template.accentColors)
        )
        val divider = max(
            boundaryContrast(bitmap, base.top + topHeight),
            boundaryContrast(bitmap, base.bottom - bottomHeight)
        )
        if (hasHint && topChrome < 0.35f && bottomChrome < 0.35f && accent < 0.002f) {
            return null
        }
        val visualConfidence = (topChrome * 0.26f) + (bottomChrome * 0.26f) +
                (accent * 12.0f).coerceAtMost(0.34f) + (divider * 0.14f)
        val confidence = if (hasHint) max(0.84f, visualConfidence) else visualConfidence

        return CropProfileMatch(template, confidence.coerceIn(0f, 1f), hasHint)
    }

    private fun estimateDensity(bitmap: Bitmap, statusBarPx: Int, navBarPx: Int): Float {
        val fromStatus = if (statusBarPx > 0) statusBarPx / 24f else 0f
        val fromNav = if (navBarPx > 0) navBarPx / 48f else 0f
        val fromWidth = bitmap.width / 360f
        return listOf(fromStatus, fromNav, fromWidth)
            .filter { it > 0f }
            .average()
            .toFloat()
            .coerceIn(1.0f, 4.5f)
    }

    private fun bandBackgroundCoverage(bitmap: Bitmap, rect: Rect): Float {
        if (rect.width() <= 0 || rect.height() <= 0) return 0f
        val background = dominantBandColor(bitmap, rect)
        var matches = 0
        var samples = 0
        val xStep = max(1, rect.width() / 28)
        val yStep = max(1, rect.height() / 8)

        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                if (colorDistance(bitmap.getPixel(x, y), background) < 34) matches++
                samples++
                x += xStep
            }
            y += yStep
        }

        return if (samples == 0) 0f else matches.toFloat() / samples
    }

    private fun dominantBandColor(bitmap: Bitmap, rect: Rect): Int {
        val samplePoints = listOf(
            rect.left to rect.top,
            rect.centerX() to rect.top,
            (rect.right - 1) to rect.top,
            rect.left to rect.centerY(),
            rect.centerX() to rect.centerY(),
            (rect.right - 1) to rect.centerY(),
            rect.left to (rect.bottom - 1),
            rect.centerX() to (rect.bottom - 1),
            (rect.right - 1) to (rect.bottom - 1)
        )
        return samplePoints
            .map { (x, y) ->
                bitmap.getPixel(
                    x.coerceIn(0, bitmap.width - 1),
                    y.coerceIn(0, bitmap.height - 1)
                )
            }
            .groupBy { quantizeColor(it) }
            .maxByOrNull { it.value.size }
            ?.value
            ?.first()
            ?: bitmap.getPixel(rect.centerX().coerceIn(0, bitmap.width - 1), rect.centerY().coerceIn(0, bitmap.height - 1))
    }

    private fun accentCoverage(bitmap: Bitmap, rect: Rect, accentColors: List<Int>): Float {
        if (rect.width() <= 0 || rect.height() <= 0 || accentColors.isEmpty()) return 0f
        var matches = 0
        var samples = 0
        val xStep = max(1, rect.width() / 36)
        val yStep = max(1, rect.height() / 10)

        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val pixel = bitmap.getPixel(x, y)
                if (accentColors.any { colorDistance(pixel, it) < 42 }) matches++
                samples++
                x += xStep
            }
            y += yStep
        }

        return if (samples == 0) 0f else matches.toFloat() / samples
    }

    private fun boundaryContrast(bitmap: Bitmap, boundaryY: Int): Float {
        if (boundaryY <= 2 || boundaryY >= bitmap.height - 3) return 0f
        var total = 0
        var samples = 0
        val xStep = max(1, bitmap.width / 32)
        var x = 0
        while (x < bitmap.width) {
            total += colorDistance(bitmap.getPixel(x, boundaryY - 2), bitmap.getPixel(x, boundaryY + 2))
            samples++
            x += xStep
        }
        return if (samples == 0) 0f else (total / samples).coerceAtMost(80) / 80f
    }

    private fun quantizeColor(color: Int): Int {
        val red = Color.red(color) / 24
        val green = Color.green(color) / 24
        val blue = Color.blue(color) / 24
        return (red shl 16) or (green shl 8) or blue
    }

    private fun colorDistance(first: Int, second: Int): Int {
        val red = abs(Color.red(first) - Color.red(second))
        val green = abs(Color.green(first) - Color.green(second))
        val blue = abs(Color.blue(first) - Color.blue(second))
        return (red + green + blue) / 3
    }
}
