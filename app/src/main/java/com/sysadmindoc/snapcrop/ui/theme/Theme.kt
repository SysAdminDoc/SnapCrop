package com.sysadmindoc.snapcrop.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

private val SnapCropShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

private val SnapCropTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SnapCropTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE) }
    var themePref by remember(prefs) { mutableStateOf(prefs.getString(THEME_PREF_KEY, "dark")) }
    // Settings writes the preference, and every open screen re-themes from it.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            // A null key is clear() on API 30+, which also drops the theme back to its default.
            if (key == null || key == THEME_PREF_KEY) themePref = changed.getString(THEME_PREF_KEY, "dark")
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = darkOverride ?: resolveDarkTheme(themePref, isSystemInDarkTheme())
    val colors = snapCropColors(dark)
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

    CompositionLocalProvider(LocalSnapCropColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme(colors),
            typography = SnapCropTypography,
            shapes = SnapCropShapes,
            content = content
        )
    }
}

private const val THEME_PREF_KEY = "theme"

private fun materialColorScheme(colors: SnapCropColors): ColorScheme =
    if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.secondary,
            onSecondary = colors.onPrimary,
            tertiary = colors.tertiary,
            onTertiary = colors.onPrimary,
            background = colors.black,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surfaceContainer,
            surfaceContainerHigh = colors.surfaceElevated,
            surfaceContainerHighest = colors.surfaceElevated,
            surfaceTint = Color.Transparent,
            onBackground = colors.onSurface,
            onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            outlineVariant = colors.outline.copy(alpha = 0.72f),
            error = colors.danger,
            onError = colors.onPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.secondary,
            onSecondary = colors.onPrimary,
            tertiary = colors.tertiary,
            onTertiary = colors.onPrimary,
            background = colors.black,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surfaceContainer,
            surfaceContainerHigh = colors.surfaceElevated,
            surfaceContainerHighest = colors.surfaceElevated,
            surfaceTint = Color.Transparent,
            onBackground = colors.onSurface,
            onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            outlineVariant = colors.outline.copy(alpha = 0.72f),
            error = colors.danger,
            onError = colors.onPrimary,
        )
    }

internal fun resolveDarkTheme(themePreference: String?, systemDark: Boolean): Boolean =
    when (themePreference) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }
