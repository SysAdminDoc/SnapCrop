package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

data class EvalMetrics(
    val suite: String,
    val passed: Int,
    val total: Int,
    val details: List<String>
) {
    val accuracy: Float get() = if (total > 0) passed.toFloat() / total else 0f
}

data class EvalReport(
    val suites: List<EvalMetrics>,
    val elapsedMs: Long
) {
    fun summary(): String = buildString {
        for (m in suites) {
            append("${m.suite}: ${m.passed}/${m.total} (${(m.accuracy * 100).toInt()}%)\n")
            for (d in m.details) append("  $d\n")
            append("\n")
        }
        append("Total: ${suites.sumOf { it.passed }}/${suites.sumOf { it.total }} in ${elapsedMs}ms")
    }
}

object EvalHarness {

    private const val CROP_TOLERANCE_PX = 6

    fun runAll(): EvalReport {
        val start = System.nanoTime()
        val suites = listOf(
            evalCropWhiteBorders(),
            evalCropBlackBorders(),
            evalCropSystemBars(),
            evalCropDarkMode(),
            evalCropNoBorder(),
            evalCropSmallImage(),
            evalSensitivePatterns(),
            evalSensitiveLuhn(),
            evalProfileHintMatching()
        )
        val elapsed = (System.nanoTime() - start) / 1_000_000
        return EvalReport(suites, elapsed)
    }

    // -- AutoCrop: white borders --

    private fun evalCropWhiteBorders(): EvalMetrics {
        data class Case(val label: String, val borderW: Int, val w: Int, val h: Int)
        val cases = listOf(
            Case("50px", 50, 1080, 1920),
            Case("20px", 20, 1080, 1920),
            Case("10px", 10, 720, 1280),
            Case("80px wide", 80, 1440, 2560)
        )
        return runCropCases("Crop white borders", cases.map { c ->
            CropCase(c.label, 0xFFFFFFFF.toInt(), c.borderW, c.w, c.h)
        })
    }

    // -- AutoCrop: black borders --

    private fun evalCropBlackBorders(): EvalMetrics {
        val cases = listOf(
            CropCase("30px black", 0xFF000000.toInt(), 30, 1080, 1920),
            CropCase("60px black", 0xFF000000.toInt(), 60, 1440, 2560),
            CropCase("15px black", 0xFF000000.toInt(), 15, 720, 1280)
        )
        return runCropCases("Crop black borders", cases)
    }

    // -- AutoCrop: system bars only --

    private fun evalCropSystemBars(): EvalMetrics {
        val details = mutableListOf<String>()
        var passed = 0
        var total = 0

        val bitmap = createContentBitmap(1080, 2400)
        val statusH = 66
        val navH = 48
        val result = AutoCrop.detectWithMethod(bitmap, statusBarPx = statusH, navBarPx = navH)
        val expected = Rect(0, statusH, 1080, 2400 - navH)
        total++
        val ok = rectsMatch(result.rect, expected)
        if (ok) passed++
        details.add("${mark(ok)} bars only: ${result.method} rect=${result.rect}")
        bitmap.recycle()

        return EvalMetrics("Crop system bars", passed, total, details)
    }

    // -- AutoCrop: dark mode (dark border on dark content) --

    private fun evalCropDarkMode(): EvalMetrics {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val borderPaint = Paint().apply { color = 0xFF1A1A1A.toInt() }
        canvas.drawRect(0f, 0f, 1080f, 1920f, borderPaint)
        val contentPaint = Paint().apply { color = 0xFF2D2D2D.toInt() }
        canvas.drawRect(40f, 40f, 1040f, 1880f, contentPaint)
        val accentPaint = Paint().apply { color = 0xFF4488CC.toInt() }
        canvas.drawRect(100f, 100f, 500f, 400f, accentPaint)

        val result = AutoCrop.detectWithMethod(bitmap)
        val expected = Rect(40, 40, 1040, 1880)
        val ok = rectsMatch(result.rect, expected)
        bitmap.recycle()

        return EvalMetrics("Crop dark mode", if (ok) 1 else 0, 1, listOf(
            "${mark(ok)} dark border: ${result.method} rect=${result.rect}"
        ))
    }

    // -- AutoCrop: no border should return full rect --

    private fun evalCropNoBorder(): EvalMetrics {
        val bitmap = createContentBitmap(1080, 1920)
        val result = AutoCrop.detectWithMethod(bitmap)
        val expected = Rect(0, 0, 1080, 1920)
        val ok = result.rect == expected
        bitmap.recycle()

        return EvalMetrics("Crop no border", if (ok) 1 else 0, 1, listOf(
            "${mark(ok)} full image: method=${result.method} rect=${result.rect}"
        ))
    }

    // -- AutoCrop: small image guard --

    private fun evalCropSmallImage(): EvalMetrics {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        Paint().apply { color = 0xFF0000FF.toInt() }.also {
            canvas.drawRect(10f, 10f, 40f, 40f, it)
        }
        val result = AutoCrop.detectWithMethod(bitmap)
        val ok = result.method == "too_small" && result.rect == Rect(0, 0, 50, 50)
        bitmap.recycle()

        return EvalMetrics("Crop small guard", if (ok) 1 else 0, 1, listOf(
            "${mark(ok)} <100px: method=${result.method}"
        ))
    }

    // -- SensitiveTextPatterns: regex detection --

    private fun evalSensitivePatterns(): EvalMetrics {
        data class Case(val label: String, val text: String, val expected: Boolean)
        val cases = listOf(
            Case("email", "Contact us at admin@example.com for help", true),
            Case("phone US", "Call 555-123-4567 today", true),
            Case("phone intl", "Reach us at +44 20 7946 0958", true),
            Case("ipv4", "Server at 192.168.1.100 is down", true),
            Case("mac", "Device MAC: AA:BB:CC:DD:EE:FF", true),
            Case("ipv6", "Address fe80::1:2:3:4 assigned", true),
            Case("clean text", "The quick brown fox jumps over the lazy dog", false),
            Case("short number", "Room 42", false),
            Case("date-like", "2026-06-13 meeting", false)
        )

        val details = mutableListOf<String>()
        var passed = 0
        for (c in cases) {
            val detected = SensitiveTextPatterns.containsSensitivePattern(c.text)
            val ok = detected == c.expected
            if (ok) passed++
            details.add("${mark(ok)} ${c.label}: expected=${c.expected} got=$detected")
        }
        return EvalMetrics("Sensitive patterns", passed, cases.size, details)
    }

    // -- SensitiveTextPatterns: Luhn card validation --

    private fun evalSensitiveLuhn(): EvalMetrics {
        data class Case(val label: String, val digits: String, val expected: Boolean)
        val cases = listOf(
            Case("Visa test", "4111111111111111", true),
            Case("MC test", "5500000000000004", true),
            Case("invalid", "1234567890123456", false),
            Case("short", "41111111", false),
            Case("Amex test", "378282246310005", true)
        )

        val details = mutableListOf<String>()
        var passed = 0
        for (c in cases) {
            val valid = c.digits.length in 13..19 && SensitiveTextPatterns.passesLuhn(c.digits)
            val ok = valid == c.expected
            if (ok) passed++
            details.add("${mark(ok)} ${c.label}: expected=${c.expected} got=$valid")
        }
        return EvalMetrics("Luhn validation", passed, cases.size, details)
    }

    // -- AppCropProfiles: hint-based matching --

    private fun evalProfileHintMatching(): EvalMetrics {
        val bitmap = createProfileBitmap(1080, 1920, 0xFFFF4500.toInt())
        val base = AutoCrop.CropResult(Rect(0, 0, 1080, 1920), "full")

        val details = mutableListOf<String>()
        var passed = 0
        var total = 0

        val redditHints = listOf("com.reddit.frontpage", "screenshot_reddit")
        val redditResult = AppCropProfiles.evaluate(
            bitmap, base, 66, 48, redditHints, enabled = true
        )
        total++
        val redditOk = redditResult != null && redditResult.id == "reddit"
        if (redditOk) passed++
        details.add("${mark(redditOk)} Reddit hint: matched=${redditResult?.id}")

        val twitterHints = listOf("com.twitter.android")
        val twitterResult = AppCropProfiles.evaluate(
            bitmap, base, 66, 48, twitterHints, enabled = true
        )
        total++
        val twitterOk = twitterResult != null && twitterResult.id == "twitter"
        if (twitterOk) passed++
        details.add("${mark(twitterOk)} Twitter hint: matched=${twitterResult?.id}")

        val noHints = listOf("com.example.randomapp")
        val noResult = AppCropProfiles.evaluate(
            bitmap, base, 66, 48, noHints, enabled = true
        )
        total++
        val noOk = noResult == null || noResult.confidence < 0.78f
        if (noOk) passed++
        details.add("${mark(noOk)} no match: result=${noResult?.id ?: "none"}")

        val disabledResult = AppCropProfiles.evaluate(
            bitmap, base, 66, 48, redditHints, enabled = false
        )
        total++
        val disabledOk = disabledResult == null
        if (disabledOk) passed++
        details.add("${mark(disabledOk)} disabled: result=${disabledResult?.id ?: "none"}")

        bitmap.recycle()
        return EvalMetrics("Profile matching", passed, total, details)
    }

    // -- Helpers --

    private data class CropCase(
        val label: String,
        val borderColor: Int,
        val borderWidth: Int,
        val w: Int,
        val h: Int
    )

    private fun runCropCases(suite: String, cases: List<CropCase>): EvalMetrics {
        val details = mutableListOf<String>()
        var passed = 0

        for (c in cases) {
            val bitmap = createBorderedBitmap(c.w, c.h, c.borderColor, c.borderWidth)
            val result = AutoCrop.detectWithMethod(bitmap)
            val expected = Rect(c.borderWidth, c.borderWidth, c.w - c.borderWidth, c.h - c.borderWidth)
            val ok = rectsMatch(result.rect, expected)
            if (ok) passed++
            val iou = computeIoU(result.rect, expected)
            details.add("${mark(ok)} ${c.label}: IoU=${String.format("%.3f", iou)} method=${result.method}")
            bitmap.recycle()
        }
        return EvalMetrics(suite, passed, cases.size, details)
    }

    private fun createBorderedBitmap(w: Int, h: Int, borderColor: Int, borderWidth: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(borderColor)
        val content = Paint().apply { color = 0xFF3366CC.toInt() }
        canvas.drawRect(
            borderWidth.toFloat(), borderWidth.toFloat(),
            (w - borderWidth).toFloat(), (h - borderWidth).toFloat(), content
        )
        val accent = Paint().apply { color = 0xFFCC3344.toInt() }
        canvas.drawRect(
            (borderWidth + 30).toFloat(), (borderWidth + 30).toFloat(),
            (w / 2).toFloat(), (h / 2).toFloat(), accent
        )
        return bitmap
    }

    private fun createContentBitmap(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint().apply { color = 0xFF2244AA.toInt() }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val accent = Paint().apply { color = 0xFFDD5533.toInt() }
        canvas.drawRect(50f, 50f, (w - 50).toFloat(), (h / 3).toFloat(), accent)
        val text = Paint().apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawRect((w / 4).toFloat(), (h / 2).toFloat(), (3 * w / 4).toFloat(), (2 * h / 3).toFloat(), text)
        return bitmap
    }

    private fun createProfileBitmap(w: Int, h: Int, accentColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val toolbar = Paint().apply { color = accentColor }
        canvas.drawRect(0f, 0f, w.toFloat(), 160f, toolbar)
        val content = Paint().apply { color = 0xFF333333.toInt() }
        canvas.drawRect(20f, 200f, (w - 20).toFloat(), (h - 160).toFloat(), content)
        val bottomBar = Paint().apply { color = 0xFFEEEEEE.toInt() }
        canvas.drawRect(0f, (h - 160).toFloat(), w.toFloat(), h.toFloat(), bottomBar)
        return bitmap
    }

    private fun rectsMatch(actual: Rect, expected: Rect): Boolean {
        val t = CROP_TOLERANCE_PX
        return kotlin.math.abs(actual.left - expected.left) <= t &&
                kotlin.math.abs(actual.top - expected.top) <= t &&
                kotlin.math.abs(actual.right - expected.right) <= t &&
                kotlin.math.abs(actual.bottom - expected.bottom) <= t
    }

    private fun computeIoU(a: Rect, b: Rect): Float {
        val iLeft = max(a.left, b.left)
        val iTop = max(a.top, b.top)
        val iRight = min(a.right, b.right)
        val iBottom = min(a.bottom, b.bottom)
        if (iRight <= iLeft || iBottom <= iTop) return 0f
        val inter = (iRight - iLeft).toLong() * (iBottom - iTop)
        val areaA = a.width().toLong() * a.height()
        val areaB = b.width().toLong() * b.height()
        val union = areaA + areaB - inter
        return if (union == 0L) 0f else inter.toFloat() / union
    }

    private fun mark(ok: Boolean) = if (ok) "PASS" else "FAIL"
}
