package com.sysadmindoc.snapcrop

import androidx.compose.runtime.mutableStateListOf

internal class EditorHistoryController(
    private val maxDepth: Int = 30,
) {
    private val undo = mutableStateListOf<EditorSnapshot>()
    private val redo = mutableStateListOf<EditorSnapshot>()

    val undoSnapshots: List<EditorSnapshot> get() = undo
    val redoSnapshots: List<EditorSnapshot> get() = redo

    fun record(snapshot: EditorSnapshot) {
        undo += snapshot
        redo.clear()
        while (undo.size > maxDepth) undo.removeAt(0)
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        if (undo.isEmpty()) return null
        redo += current
        return undo.removeAt(undo.lastIndex)
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        if (redo.isEmpty()) return null
        undo += current
        return redo.removeAt(redo.lastIndex)
    }

    /** Rewrites every stored snapshot, for when the image under them changes shape. */
    fun remap(transform: (EditorSnapshot) -> EditorSnapshot) {
        val mappedUndo = undo.map(transform)
        val mappedRedo = redo.map(transform)
        undo.clear(); undo.addAll(mappedUndo)
        redo.clear(); redo.addAll(mappedRedo)
    }

    fun jumpTo(index: Int, current: EditorSnapshot): EditorSnapshot? {
        if (index !in undo.indices) return null
        redo += current
        for (position in undo.lastIndex downTo index + 1) {
            redo += undo.removeAt(position)
        }
        return undo.removeAt(index)
    }
}
