package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHelpTest {
    @Test
    fun catalogHasStableUniqueCompleteEntries() {
        val entries = LocalHelpCatalog.entries

        assertEquals(entries.size, entries.map(HelpEntry::id).toSet().size)
        assertTrue(entries.all { it.id.matches(Regex("[a-z0-9-]+")) })
        assertTrue(entries.all { it.title.isNotBlank() && it.summary.isNotBlank() && it.keywords.isNotEmpty() })
        assertEquals(HelpRoute.entries.toSet(), entries.map(HelpEntry::route).toSet())
    }

    @Test
    fun emptyQueryReturnsUsefulCatalogOrder() {
        assertEquals(LocalHelpCatalog.entries, LocalHelpCatalog.search(""))
        assertEquals(LocalHelpCatalog.entries, LocalHelpCatalog.search("  ---  "))
        assertEquals("recover-permissions", LocalHelpCatalog.search("").first().id)
    }

    @Test
    fun exactTaskTermsRankRelevantHelpFirst() {
        assertEquals("redact-safely", LocalHelpCatalog.search("redact secrets").first().id)
        assertEquals("save-replace-or-copy", LocalHelpCatalog.search("save copy").first().id)
        assertEquals("troubleshoot-static-web-capture", LocalHelpCatalog.search("static web").first().id)
        assertEquals("filter-gallery", LocalHelpCatalog.search("gallery filters").first().id)
    }

    @Test
    fun multiTokenSearchRequiresEveryToken() {
        val results = LocalHelpCatalog.search("share source privacy")

        assertEquals("share-metadata-and-source-link", results.first().id)
        assertTrue(results.none { "share" !in (it.title + it.summary + it.keywords.joinToString()).lowercase() })
    }

    @Test
    fun searchNormalizesCaseWidthAndPunctuationDeterministically() {
        val expected = LocalHelpCatalog.search("LONG screenshot")

        assertEquals(expected, LocalHelpCatalog.search("  ＬＯＮＧ---SCREENSHOT "))
        assertEquals(expected, LocalHelpCatalog.search("long screenshot"))
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        assertTrue(LocalHelpCatalog.search("quantum-frobnicator").isEmpty())
    }

    @Test
    fun settingsHelpUsesTheExactMatchingControl() {
        assertEquals(
            HelpRoute.SETTINGS_PROJECT_SIDECARS,
            LocalHelpCatalog.entries.single { it.id == "save-and-reopen-projects" }.route,
        )
        assertEquals(
            HelpRoute.SETTINGS_SECURE_EDITOR,
            LocalHelpCatalog.entries.single { it.id == "secure-preview-capture-conflict" }.route,
        )
        assertEquals(
            HelpRoute.HOME_PERMISSIONS,
            LocalHelpCatalog.entries.single { it.id == "troubleshoot-delayed-capture" }.route,
        )
    }
}
