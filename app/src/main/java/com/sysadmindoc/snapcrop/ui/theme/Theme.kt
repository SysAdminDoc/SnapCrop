package com.sysadmindoc.snapcrop.ui.theme

import android.content.Context
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
    val themePref = context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        .getString("theme", "dark") ?: "dark"
    val dark = darkOverride ?: resolveDarkTheme(themePref, isSystemInDarkTheme())
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
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnSurface,
            secondary = Secondary,
            onSecondary = OnPrimary,
            tertiary = Tertiary,
            onTertiary = OnPrimary,
            background = Black,
            surface = Surface,
            surfaceVariant = SurfaceVariant,
            surfaceContainerLow = Surface,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceElevated,
            surfaceContainerHighest = SurfaceElevated,
            surfaceTint = Color.Transparent,
            onBackground = OnSurface,
            onSurface = OnSurface,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
            outlineVariant = Outline.copy(alpha = 0.72f),
            error = Danger,
            onError = OnPrimary,
        )
    } else {
        lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnSurface,
            secondary = Secondary,
            onSecondary = OnPrimary,
            tertiary = Tertiary,
            onTertiary = OnPrimary,
            background = Black,
            surface = Surface,
            surfaceVariant = SurfaceVariant,
            surfaceContainerLow = Surface,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceElevated,
            surfaceContainerHighest = SurfaceElevated,
            surfaceTint = Color.Transparent,
            onBackground = OnSurface,
            onSurface = OnSurface,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
            outlineVariant = Outline.copy(alpha = 0.72f),
            error = Danger,
            onError = OnPrimary,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnapCropTypography,
        shapes = SnapCropShapes,
        content = content
    )
}

internal fun resolveDarkTheme(themePreference: String?, systemDark: Boolean): Boolean =
    when (themePreference) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }
