package com.sysadmindoc.snapcrop

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorHistoryControllerTest {
    @Test
    fun recordCapsOldestSnapshotsAndClearsRedo() {
        val history = EditorHistoryController(maxDepth = 30)
        repeat(35) { history.record(snapshot(it)) }

        assertEquals(30, history.undoSnapshots.size)
        assertEquals((5 until 35).map(Int::toFloat), history.undoSnapshots.map(EditorSnapshot::bright))

        assertEquals(snapshot(34), history.undo(snapshot(99)))
        assertEquals(1, history.redoSnapshots.size)
        history.record(snapshot(100))
        assertEquals(0, history.redoSnapshots.size)
    }

    @Test
    fun undoAndRedoRoundTripTheCurrentSnapshot() {
        val history = EditorHistoryController()
        history.record(snapshot(1))
        history.record(snapshot(2))

        val restored = history.undo(snapshot(3))
        assertEquals(snapshot(2), restored)
        assertEquals(snapshot(3), history.redo(snapshot(2)))
        assertEquals(listOf(snapshot(1), snapshot(2)), history.undoSnapshots)
        assertNull(history.redo(snapshot(3)))
    }

    @Test
    fun jumpPreservesForwardOrderingAndRejectsInvalidIndex() {
        val history = EditorHistoryController()
        history.record(snapshot(1))
        history.record(snapshot(2))
        history.record(snapshot(3))

        assertNull(history.jumpTo(4, snapshot(4)))
        assertEquals(snapshot(1), history.jumpTo(0, snapshot(4)))
        assertEquals(snapshot(2), history.redo(snapshot(1)))
        assertEquals(snapshot(3), history.redo(snapshot(2)))
        assertEquals(snapshot(4), history.redo(snapshot(3)))
    }

    private fun snapshot(id: Int) = EditorSnapshot(
        crop = Rect(id, id, id + 10, id + 10),
        bright = id.toFloat(),
        contr = 1f,
        sat = 1f,
        warm = 0f,
        vig = 0f,
        sharp = 0f,
        rotAngle = 0f,
        hi = 0f,
        sh = 0f,
        tilt = 0f,
        dn = 0f,
        gradBg = 0,
        filter = ImageFilter.NONE,
        redactions = emptyList(),
        draws = emptyList(),
    )
}
