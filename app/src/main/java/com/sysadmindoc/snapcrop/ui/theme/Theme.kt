package com.sysadmindoc.snapcrop.ui.theme

import android.content.Context
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SnapCropTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themePref = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        .getString("theme", "dark") ?: "dark"
    val dark = resolveDarkTheme(themePref, isSystemInDarkTheme())
    isDarkTheme = dark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

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

internal fun resolveDarkTheme(themePreference: String?, systemDark: Boolean): Boolean =
    when (themePreference) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }
