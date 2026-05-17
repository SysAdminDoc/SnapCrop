package com.sysadmindoc.snapcrop

internal enum class EditorLayoutClass {
    Phone,
    Wide
}

internal fun editorLayoutClass(widthDp: Float, heightDp: Float): EditorLayoutClass =
    if (widthDp >= 840f && heightDp >= 520f) EditorLayoutClass.Wide else EditorLayoutClass.Phone

internal fun editorSidePanelWidthDp(widthDp: Float): Float =
    when {
        widthDp >= 1200f -> 360f
        widthDp >= 1000f -> 336f
        else -> 312f
    }
