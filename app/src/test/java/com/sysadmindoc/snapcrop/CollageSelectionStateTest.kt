package com.sysadmindoc.snapcrop

import android.net.Uri
import android.os.Bundle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollageSelectionStateTest {
    private val selected = (1..4).map { Uri.parse("content://media/image/$it") }
    private val twoByTwo = collageLayouts.single { it.name == "2x2" }
    private val twoByOne = collageLayouts.single { it.name == "2x1" }

    @Test
    fun shrinkingRequiresConfirmationAndCancelPreservesExactOrder() {
        val initial = CollageSelectionState(twoByTwo, selected)
        val pending = CollageSelectionReducer.requestLayout(initial, twoByOne)

        assertEquals(twoByTwo, pending.layout)
        assertEquals(twoByOne, pending.pendingLayout)
        assertEquals(selected, pending.uris)

        val cancelled = CollageSelectionReducer.cancelPending(pending)
        assertEquals(initial, cancelled)
    }

    @Test
    fun oneConfirmationAndUndoRestoreSelectionAndLayoutExactly() {
        val initial = CollageSelectionState(twoByTwo, selected)
        val confirmed = CollageSelectionReducer.confirmPending(
            CollageSelectionReducer.requestLayout(initial, twoByOne)
        )

        assertEquals(twoByOne, confirmed.layout)
        assertEquals(selected.take(2), confirmed.uris)
        assertEquals(selected.drop(2), confirmed.undo?.removedUris)

        val restored = CollageSelectionReducer.undo(confirmed)
        assertEquals(initial, restored)
        assertNull(restored.undo)
    }

    @Test
    fun pendingConfirmationAndUndoSurviveSavedState() {
        val pending = CollageSelectionReducer.requestLayout(
            CollageSelectionState(twoByTwo, selected),
            twoByOne,
        )
        val pendingBundle = Bundle()
        CollageSelectionPersistence.save(pendingBundle, pending)
        assertEquals(pending, CollageSelectionPersistence.restore(pendingBundle))

        val confirmed = CollageSelectionReducer.confirmPending(pending)
        val undoBundle = Bundle()
        CollageSelectionPersistence.save(undoBundle, confirmed)
        val recreated = CollageSelectionPersistence.restore(undoBundle)
        assertEquals(confirmed, recreated)
        assertEquals(CollageSelectionState(twoByTwo, selected), CollageSelectionReducer.undo(recreated))
    }

    @Test
    fun replacementExpandsCapacityInsteadOfTruncatingPickedOrder() {
        val compact = CollageSelectionState(twoByOne, selected.take(2))
        val replaced = CollageSelectionReducer.replace(compact, selected, maxItems = 25)

        assertEquals(twoByTwo, replaced.layout)
        assertEquals(selected, replaced.uris)
        assertFalse(replaced.uris.size > replaced.layout.slots)
    }

    @Test
    fun activityRoutesDestructiveLayoutChangesThroughReducerAndUndoUi() {
        val source = File("src/main/java/com/sysadmindoc/snapcrop/CollageActivity.kt").readText()

        assertTrue(source.contains("CollageSelectionReducer.requestLayout"))
        assertTrue(source.contains("CollageSelectionReducer.confirmPending"))
        assertTrue(source.contains("CollageSelectionReducer.cancelPending"))
        assertTrue(source.contains("CollageSelectionReducer.undo"))
        assertTrue(source.contains("R.string.collage_layout_reduce_body"))
        assertFalse(source.contains("Drop overflow selections silently"))
    }
}
