package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CustomRedactionPatternStoreTest {
    private val prefs get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("custom-pattern-test", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun boundedStoreRoundTripsAndImports() {
        val patterns = listOf(
            CustomRedactionPattern("ticket", "Ticket", "TKT-[0-9]{6}"),
            CustomRedactionPattern("disabled", "Disabled", "LOCAL-[A-Z]{4}", enabled = false),
        )

        assertTrue(CustomRedactionPatternStore.save(prefs, patterns))
        assertEquals(patterns, CustomRedactionPatternStore.load(prefs))
        assertEquals(patterns, CustomRedactionPatternStore.import(CustomRedactionPatternStore.export(patterns)))
    }

    @Test
    fun invalidOrDangerousDefinitionsFailClosed() {
        val dangerous = listOf(
            "(?<=secret)[0-9]+",
            "(secret)\\1",
            "a*",
        )
        dangerous.forEach { expression ->
            assertNotNull(
                expression,
                CustomRedactionPatternStore.validate(
                    CustomRedactionPattern("id", "Unsafe", expression)
                )
            )
        }
        assertFalse(
            CustomRedactionPatternStore.save(
                prefs,
                List(CustomRedactionPatternStore.MAX_PATTERNS + 1) { index ->
                    CustomRedactionPattern("id-$index", "Pattern $index", "SAFE-$index-[0-9]+")
                }
            )
        )
        assertNull(CustomRedactionPatternStore.import("{\"schemaVersion\":99,\"patterns\":[]}"))
    }

    @Test
    fun re2HandlesNestedRepetitionWithoutBacktracking() {
        val pattern = CustomRedactionPattern("nested", "Nested", "(a+)+$")
        val started = System.nanoTime()
        val result = CustomRedactionPatternStore.test(pattern, "a".repeat(3_999) + "!")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertEquals(CustomPatternTestStatus.NO_MATCH, result.status)
        assertTrue("RE2 scan took ${elapsedMillis}ms", elapsedMillis < 1_000)
    }

    @Test
    fun testAndDetectorReturnIndividuallyReviewableCustomRegions() {
        val pattern = CustomRedactionPattern("ticket", "Ticket", "TKT-[0-9]{6}")
        val sample = "Public text TKT-123456 and TKT-654321"

        val test = CustomRedactionPatternStore.test(pattern, sample)
        assertEquals(CustomPatternTestStatus.MATCH, test.status)
        assertEquals(2, test.matchCount)

        val result = SensitiveTextDetector.detectBlocks(
            listOf(
                TextBlock("TKT-123456", Rect(10, 10, 90, 40), ' '),
                TextBlock("and", Rect(95, 10, 120, 40), ' '),
                TextBlock("TKT-654321", Rect(125, 10, 205, 40)),
            ),
            300,
            100,
            customPatterns = listOf(pattern),
        )
        assertEquals(2, result.rects.size)
        assertTrue(result.detections.all { it.category == SensitiveTextCategory.CUSTOM })
        assertTrue(result.detections.all { it.source == SensitiveTextDetectionSource.CUSTOM })
    }

    @Test
    fun disabledPatternsAndOversizedInputsAreIgnored() {
        val pattern = CustomRedactionPattern("ticket", "Ticket", "TKT-[0-9]{6}", enabled = false)
        assertTrue(CustomRedactionPatternStore.scan("TKT-123456", listOf(pattern)).matches.isEmpty())
        assertEquals(
            CustomPatternTestStatus.INVALID,
            CustomRedactionPatternStore.test(
                pattern,
                "x".repeat(CustomRedactionPatternStore.MAX_TEST_TEXT_LENGTH + 1),
            ).status,
        )
    }

    @Test
    fun caseSensitivityAndCorruptStorageFailClosed() {
        val exact = CustomRedactionPattern("exact", "Exact", "ticket-[0-9]+", caseSensitive = true)
        val folded = exact.copy(id = "folded", name = "Folded", caseSensitive = false)
        assertEquals(CustomPatternTestStatus.NO_MATCH, CustomRedactionPatternStore.test(exact, "TICKET-42").status)
        assertEquals(CustomPatternTestStatus.MATCH, CustomRedactionPatternStore.test(folded, "TICKET-42").status)

        prefs.edit().putString(CustomRedactionPatternStore.PREF_KEY, "not-json").commit()
        assertThrows(InvalidCustomPatternStoreException::class.java) {
            CustomRedactionPatternStore.load(prefs)
        }
    }
}
