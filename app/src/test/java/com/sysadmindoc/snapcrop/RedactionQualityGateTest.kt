package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class RedactionQualityGateTest {
    @Test
    fun syntheticCorpusMeetsPerCategoryPrecisionRecallAndCoverageGates() {
        val expected = mutableListOf<ExpectedSensitiveRegion>()
        val detected = mutableListOf<SensitiveTextDetection>()
        var visibleSecrets = 0

        fixtures().forEach { fixture ->
            val source = renderFixture(fixture)
            val block = TextBlock(fixture.text, Rect(FIXTURE_BOUNDS))
            val entityMatches = if (fixture.category == SensitiveTextCategory.POSTAL_ADDRESS) {
                val start = fixture.text.indexOf(fixture.secret)
                listOf(
                    SensitiveTextMatch(
                        fixture.category,
                        start until start + fixture.secret.length,
                        SensitiveTextDetectionSource.ENTITY
                    )
                )
            } else {
                emptyList()
            }
            val result = SensitiveTextDetector.detectBlocks(
                listOf(block, TextBlock(NEGATIVE_TEXT, Rect(40, 160, 680, 200))),
                source.width,
                source.height,
                entityMatches
            )

            expected.add(ExpectedSensitiveRegion(fixture.category, Rect(FIXTURE_BOUNDS)))
            detected.addAll(result.detections)

            val redacted = ImageRedactor.redact(source, result.rects, RedactionStyle.SOLID)
            val roundTripped = pngRoundTrip(redacted)
            visibleSecrets += countNonBlackPixels(roundTripped, FIXTURE_BOUNDS)

            source.recycle()
            redacted.recycle()
            roundTripped.recycle()
        }

        val report = RedactionQualityGate.evaluate(expected, detected, visibleSecrets)
        println(report.summary())

        assertEquals(OcrScript.entries.toSet(), fixtures().map { it.script }.toSet())
        assertEquals(Theme.entries.toSet(), fixtures().map { it.theme }.toSet())
        assertEquals(SensitiveTextCategory.entries.toSet(), fixtures().map { it.category }.toSet())
        assertTrue(report.summary(), report.passes)
    }

    @Test
    fun missingOrVisibleSecretFailsTheReleaseThresholds() {
        val expected = listOf(
            ExpectedSensitiveRegion(SensitiveTextCategory.EMAIL, Rect(10, 10, 110, 40))
        )

        val missing = RedactionQualityGate.evaluate(expected, emptyList())
        val visible = RedactionQualityGate.evaluate(
            expected,
            listOf(
                SensitiveTextDetection(
                    SensitiveTextCategory.EMAIL,
                    Rect(10, 10, 110, 40),
                    SensitiveTextDetectionSource.REGEX
                )
            ),
            visibleSecretCount = 1
        )

        assertFalse(missing.passes)
        assertFalse(visible.passes)
    }

    @Test
    fun corpusUsesReservedSyntheticValuesOnly() {
        val byCategory = fixtures().associateBy { it.category }
        assertTrue(byCategory.getValue(SensitiveTextCategory.EMAIL).secret.endsWith(".test"))
        assertTrue(byCategory.getValue(SensitiveTextCategory.PHONE).secret.contains("555"))
        assertTrue(byCategory.getValue(SensitiveTextCategory.IPV4).secret.startsWith("192.0.2."))
        assertTrue(byCategory.getValue(SensitiveTextCategory.IPV6).secret.startsWith("2001:db8:"))
        assertTrue(byCategory.getValue(SensitiveTextCategory.MAC_ADDRESS).secret.startsWith("02:"))
        assertTrue(byCategory.getValue(SensitiveTextCategory.POSTAL_ADDRESS).secret.contains("Example"))
    }

    private fun fixtures(): List<Fixture> = buildList {
        OcrScript.entries.forEach { script ->
            Theme.entries.forEach { theme ->
                SECRETS.forEach { (category, secret) ->
                    add(
                        Fixture(
                            id = "${script.key}-${theme.name.lowercase()}-${category.name.lowercase()}",
                            script = script,
                            theme = theme,
                            category = category,
                            secret = secret,
                            text = "${SCRIPT_PREFIX.getValue(script)} $secret"
                        )
                    )
                }
            }
        }
    }

    private fun renderFixture(fixture: Fixture): Bitmap {
        val background = if (fixture.theme == Theme.LIGHT) Color.WHITE else Color.rgb(18, 18, 22)
        val foreground = if (fixture.theme == Theme.LIGHT) Color.rgb(20, 20, 24) else Color.rgb(240, 240, 245)
        return Bitmap.createBitmap(720, 220, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(background)
            val chrome = Paint().apply { color = if (fixture.theme == Theme.LIGHT) 0xffe8e8ee.toInt() else 0xff303038.toInt() }
            canvas.drawRect(0f, 0f, width.toFloat(), 48f, chrome)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = foreground
                textSize = 25f
            }
            canvas.drawText(fixture.text, 48f, 122f, textPaint)
            textPaint.textSize = 18f
            canvas.drawText(NEGATIVE_TEXT, 48f, 188f, textPaint)
        }
    }

    private fun pngRoundTrip(bitmap: Bitmap): Bitmap {
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun countNonBlackPixels(bitmap: Bitmap, bounds: Rect): Int {
        var count = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                if (bitmap.getPixel(x, y) != Color.BLACK) count++
            }
        }
        return count
    }

    private enum class Theme { LIGHT, DARK }

    private data class Fixture(
        val id: String,
        val script: OcrScript,
        val theme: Theme,
        val category: SensitiveTextCategory,
        val secret: String,
        val text: String
    )

    companion object {
        private val FIXTURE_BOUNDS = Rect(40, 72, 680, 146)
        private const val NEGATIVE_TEXT = "Build 2026-07-12 · 1080x2400 · room 42 · 999.999.999.999"
        private val SCRIPT_PREFIX = mapOf(
            OcrScript.LATIN to "Synthetic account",
            OcrScript.CHINESE to "合成测试",
            OcrScript.JAPANESE to "合成テスト",
            OcrScript.KOREAN to "합성 테스트",
            OcrScript.DEVANAGARI to "कृत्रिम परीक्षण"
        )
        private val SECRETS = linkedMapOf(
            SensitiveTextCategory.EMAIL to "qa.fixture@example.test",
            SensitiveTextCategory.PHONE to "+1 202-555-0100",
            SensitiveTextCategory.PAYMENT_CARD to "4111 1111 1111 1111",
            SensitiveTextCategory.IPV4 to "192.0.2.42",
            SensitiveTextCategory.IPV6 to "2001:db8::42",
            SensitiveTextCategory.MAC_ADDRESS to "02:00:00:00:00:01",
            SensitiveTextCategory.IBAN to "GB82 WEST 1234 5698 7654 32",
            SensitiveTextCategory.POSTAL_ADDRESS to "123 Example Street, Testville"
        )
    }
}
