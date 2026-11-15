package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalHelpTest {
    private val text: (Int) -> String get() = RuntimeEnvironment.getApplication()::getString

    private fun search(query: String) = LocalHelpCatalog.search(query, text)

    @Test
    fun catalogHasStableUniqueCompleteEntries() {
        val entries = LocalHelpCatalog.entries

        assertEquals(entries.size, entries.map(HelpEntry::id).toSet().size)
        assertTrue(entries.all { it.id.matches(Regex("[a-z0-9-]+")) })
        assertTrue(entries.all { text(it.titleRes).isNotBlank() && text(it.summaryRes).isNotBlank() && it.keywords.isNotEmpty() })
        assertEquals(HelpRoute.entries.toSet(), entries.map(HelpEntry::route).toSet())
    }

    @Test
    fun emptyQueryReturnsUsefulCatalogOrder() {
        assertEquals(LocalHelpCatalog.entries, search(""))
        assertEquals(LocalHelpCatalog.entries, search("  ---  "))
        assertEquals("recover-permissions", search("").first().id)
    }

    @Test
    fun exactTaskTermsRankRelevantHelpFirst() {
        assertEquals("redact-safely", search("redact secrets").first().id)
        assertEquals("save-replace-or-copy", search("save copy").first().id)
        assertEquals("troubleshoot-static-web-capture", search("static web").first().id)
        assertEquals("filter-gallery", search("gallery filters").first().id)
    }

    @Test
    fun multiTokenSearchRequiresEveryToken() {
        val results = search("share source privacy")

        assertEquals("share-metadata-and-source-link", results.first().id)
        assertTrue(results.none { "share" !in (text(it.titleRes) + text(it.summaryRes) + it.keywords.joinToString()).lowercase() })
    }

    @Test
    fun searchNormalizesCaseWidthAndPunctuationDeterministically() {
        val expected = search("LONG screenshot")

        assertEquals(expected, search("  ＬＯＮＧ---SCREENSHOT "))
        assertEquals(expected, search("long screenshot"))
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        assertTrue(search("quantum-frobnicator").isEmpty())
    }

    @Test
    fun searchMatchesTheResolvedTextNotTheEnglishSource() {
        // A translated title must be searchable in that language.
        val translated = mapOf(
            R.string.help_entry_redact_safely_title to "Geheimnisse sicher schwärzen",
        )
        val resolve: (Int) -> String = { id -> translated[id] ?: text(id) }

        assertEquals("redact-safely", LocalHelpCatalog.search("schwärzen", resolve).first().id)
        assertTrue(LocalHelpCatalog.search("schwärzen", text).isEmpty())
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
