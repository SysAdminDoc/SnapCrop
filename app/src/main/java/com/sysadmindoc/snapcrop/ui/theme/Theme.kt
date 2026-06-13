package com.sysadmindoc.snapcrop.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun SnapCropTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themePref = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        .getString("theme", "dark") ?: "dark"
    val dark = when (themePref) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    isDarkTheme = dark

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = Primary,
            primaryContainer = PrimaryContainer,
            secondary = Secondary,
            tertiary = Tertiary,
            background = Black,
            surface = Surface,
            surfaceVariant = SurfaceVariant,
            surfaceContainer = SurfaceContainer,
            onBackground = OnSurface,
            onSurface = OnSurface,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
        )
    } else {
        lightColorScheme(
            primary = Primary,
            primaryContainer = PrimaryContainer,
            secondary = Secondary,
            tertiary = Tertiary,
            background = Black,
            surface = Surface,
            surfaceVariant = SurfaceVariant,
            surfaceContainer = SurfaceContainer,
            onBackground = OnSurface,
            onSurface = OnSurface,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
