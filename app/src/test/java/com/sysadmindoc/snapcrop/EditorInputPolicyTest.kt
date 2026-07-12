package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorInputPolicyTest {
    @Test
    fun editorHasNoShortcutInterceptionAndKeepsVisibleAccessibleActions() {
        val source = File("src/main/java/com/sysadmindoc/snapcrop/CropEditorScreen.kt").readText()

        listOf("onPreviewKeyEvent", "handleEditorShortcut", "androidx.compose.ui.input.key")
            .forEach { forbidden -> assertFalse(forbidden, source.contains(forbidden)) }
        listOf(
            "onClick = { undo() }",
            "onClick = { redo() }",
            "Button(onClick = { onSave(",
            "CustomAccessibilityAction(nudgeCropLeftLabel)",
            "CustomAccessibilityAction(zoomInLabel)",
            "CustomAccessibilityAction(previewLabel)"
        ).forEach { required -> assertTrue(required, source.contains(required)) }
    }
}
