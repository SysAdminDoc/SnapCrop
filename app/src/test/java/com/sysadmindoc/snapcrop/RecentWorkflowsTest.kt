package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentWorkflowsTest {
    @Test
    fun historyUsesAStoreSeparateFromExportedSettings() {
        assertFalse(RecentWorkflowStore.PREF_NAME == "snapcrop")
    }

    @Test
    fun storeIsEnabledAndEmptyByDefault() {
        val prefs = prefs("default")

        assertTrue(RecentWorkflowStore.isEnabled(prefs))
        assertTrue(RecentWorkflowStore.load(prefs).isEmpty())
    }

    @Test
    fun recordKeepsMostRecentFirstAndDeduplicates() {
        val prefs = prefs("mru")

        RecentWorkflowStore.record(prefs, WorkflowId.WEB_CAPTURE)
        RecentWorkflowStore.record(prefs, WorkflowId.LONG_SCREENSHOT)
        val result = RecentWorkflowStore.record(prefs, WorkflowId.WEB_CAPTURE)

        assertEquals(listOf(WorkflowId.WEB_CAPTURE, WorkflowId.LONG_SCREENSHOT), result)
        assertEquals(result, RecentWorkflowStore.load(prefs))
    }

    @Test
    fun recordRetainsAtMostSixAllowlistedIds() {
        val prefs = prefs("bounded")
        val recorded = WorkflowId.entries.take(8)

        recorded.forEach { RecentWorkflowStore.record(prefs, it) }

        assertEquals(recorded.takeLast(RecentWorkflowStore.MAX_ITEMS).reversed(), RecentWorkflowStore.load(prefs))
    }

    @Test
    fun disablingClearsHistoryAndBlocksRecording() {
        val prefs = prefs("disabled")
        RecentWorkflowStore.record(prefs, WorkflowId.COLLAGE)

        RecentWorkflowStore.setEnabled(prefs, false)

        assertFalse(RecentWorkflowStore.isEnabled(prefs))
        assertFalse(prefs.contains(RecentWorkflowStore.PREF_ITEMS))
        assertTrue(RecentWorkflowStore.record(prefs, WorkflowId.STITCH).isEmpty())
        assertFalse(prefs.contains(RecentWorkflowStore.PREF_ITEMS))
    }

    @Test
    fun clearRemovesItemsWithoutChangingEnablePreference() {
        val prefs = prefs("clear")
        RecentWorkflowStore.setEnabled(prefs, true)
        RecentWorkflowStore.record(prefs, WorkflowId.GALLERY)

        RecentWorkflowStore.clear(prefs)

        assertTrue(RecentWorkflowStore.isEnabled(prefs))
        assertTrue(RecentWorkflowStore.load(prefs).isEmpty())
    }

    @Test
    fun loadCullsUnknownDuplicateAndExcessIdsAndRewritesCanonicalValue() {
        val prefs = prefs("sanitize")
        prefs.edit().putString(
            RecentWorkflowStore.PREF_ITEMS,
            "WEB_CAPTURE,UNKNOWN,WEB_CAPTURE,LONG_SCREENSHOT,STITCH,COLLAGE,GALLERY,PDF_REPORT,VIDEO_CLIP",
        ).apply()

        val result = RecentWorkflowStore.load(prefs)

        assertEquals(
            listOf(
                WorkflowId.WEB_CAPTURE,
                WorkflowId.LONG_SCREENSHOT,
                WorkflowId.STITCH,
                WorkflowId.COLLAGE,
                WorkflowId.GALLERY,
                WorkflowId.PDF_REPORT,
            ),
            result,
        )
        assertEquals(result.joinToString(",", transform = WorkflowId::name), prefs.getString(RecentWorkflowStore.PREF_ITEMS, null))
    }

    @Test
    fun corruptTypeControlCharactersAndOversizedValuesAreRemoved() {
        val wrongType = prefs("wrong-type").apply {
            edit().putInt(RecentWorkflowStore.PREF_ITEMS, 42).apply()
        }
        val controls = prefs("controls").apply {
            edit().putString(RecentWorkflowStore.PREF_ITEMS, "WEB_CAPTURE\nGALLERY").apply()
        }
        val oversized = prefs("oversized").apply {
            edit().putString(RecentWorkflowStore.PREF_ITEMS, "X".repeat(1_025)).apply()
        }

        listOf(wrongType, controls, oversized).forEach { prefs ->
            assertTrue(RecentWorkflowStore.load(prefs).isEmpty())
            assertFalse(prefs.contains(RecentWorkflowStore.PREF_ITEMS))
        }
    }

    @Test
    fun persistedDataContainsOnlyBooleanAndAllowlistedEnumNames() {
        val prefs = prefs("privacy")
        RecentWorkflowStore.setEnabled(prefs, true)
        RecentWorkflowStore.record(prefs, WorkflowId.PROJECT_REOPEN)
        RecentWorkflowStore.record(prefs, WorkflowId.EDIT_IMAGE)

        assertEquals(setOf(RecentWorkflowStore.PREF_ENABLED, RecentWorkflowStore.PREF_ITEMS), prefs.all.keys)
        assertTrue(prefs.all[RecentWorkflowStore.PREF_ENABLED] is Boolean)
        val tokens = (prefs.all[RecentWorkflowStore.PREF_ITEMS] as String).split(',')
        assertTrue(tokens.all { token -> WorkflowId.entries.any { it.name == token } })
        assertTrue(tokens.none { it.contains('/') || it.contains(':') || it.contains(' ') })
    }

    private fun prefs(suffix: String) = RuntimeEnvironment.getApplication()
        .getSharedPreferences("recent_workflows_test_$suffix", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
}
