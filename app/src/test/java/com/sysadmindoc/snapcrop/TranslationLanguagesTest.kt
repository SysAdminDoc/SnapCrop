package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #4: translation offered 8 targets although ML Kit ships 59 on-device languages. */
class TranslationLanguagesTest {
    @Test
    fun everyOnDeviceLanguageIsATarget() {
        val codes = TextTranslator.targetLanguages.map { it.language }
        assertTrue("got ${codes.size}", codes.size >= 50)
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(listOf("el", "he", "uk", "pl", "th", "sw").all { it in codes })
    }

    @Test
    fun targetsAreNamedAndSortedByName() {
        val labels = TextTranslator.targetLanguages.map { it.label }
        assertEquals(labels.sorted(), labels)
        assertTrue("no bare codes: $labels", labels.none { it.length <= 3 && it == it.uppercase() })
        assertEquals("Greek", TextTranslator.labelFor("el"))
        assertEquals("Chinese", TextTranslator.labelFor("zh"))
        assertEquals("QQ", TextTranslator.labelFor("qq"))
    }

    @Test
    fun storedTargetFallsBackToEnglish() {
        assertEquals("el", TextTranslator.targetFor("el").language)
        assertEquals(TextTranslator.defaultTarget, TextTranslator.targetFor(null))
        assertEquals(TextTranslator.defaultTarget, TextTranslator.targetFor("not-a-language"))
        val codes = TextTranslator.targetLanguages.map { it.language }
        assertTrue(TextTranslator.quickTargets.all { it.language in codes })
    }

    @Test
    fun modelManagerListsQuickAndDownloadedLanguagesUntilExpanded() {
        val all = listOf("en", "el", "es", "fi", "pl").map(TextTranslator::target)
        val status = mapOf(
            "el" to ManagedModelStatus(ManagedModelState.INSTALLED),
            "fi" to ManagedModelStatus(ManagedModelState.NOT_INSTALLED),
            "pl" to ManagedModelStatus(ManagedModelState.DOWNLOADING),
        )
        val short = visibleTranslationRows(all, setOf("en", "es"), status, showAll = false)
        assertEquals(listOf("en", "el", "es", "pl"), short.map { it.language })
        assertEquals(all, visibleTranslationRows(all, setOf("en", "es"), status, showAll = true))
    }
}
