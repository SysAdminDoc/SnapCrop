package com.sysadmindoc.snapcrop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One theme's palette. [SnapCropTheme] provides it through [LocalSnapCropColors], so two subtrees
 * under different themes each read their own colours.
 */
@Immutable
data class SnapCropColors(
    val isDark: Boolean,
    val black: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceElevated: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val warning: Color,
    val success: Color,
    val danger: Color,
    val favorite: Color,
    val ocrAccent: Color,
    val adjustAccent: Color,
    val channelRed: Color,
    val channelGreen: Color,
    val channelBlue: Color,
    val outline: Color,
    val cropHandle: Color,
    val cropBorder: Color,
)

val DarkSnapCropColors = SnapCropColors(
    isDark = true,
    black = Color(0xFF080A10),
    surface = Color(0xFF0D111B),
    surfaceVariant = Color(0xFF171D2B),
    surfaceContainer = Color(0xFF111725),
    surfaceElevated = Color(0xFF1C2434),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFFB5BECE),
    primary = Color(0xFFFF6B66),
    primaryContainer = Color(0xFF3B232B),
    onPrimary = Color(0xFF1B090B),
    secondary = Color(0xFF70E39A),
    tertiary = Color(0xFFA88CFF),
    warning = Color(0xFFFFB376),
    success = Color(0xFF70E39A),
    danger = Color(0xFFFF6F91),
    favorite = Color(0xFFFF8DB0),
    ocrAccent = Color(0xFFB99CFF),
    adjustAccent = Color(0xFFFFB376),
    channelRed = Color(0xFFFF6B6B),
    channelGreen = Color(0xFF51CF66),
    channelBlue = Color(0xFF339AF0),
    outline = Color(0xFF313A4C),
    cropHandle = Color(0xFFFF6B66),
    cropBorder = Color(0xFFF2F4F8),
)

val LightSnapCropColors = SnapCropColors(
    isDark = false,
    black = Color(0xFFF8F9FC),
    surface = Color(0xFFF0F2F7),
    surfaceVariant = Color(0xFFE4E8F0),
    surfaceContainer = Color(0xFFEDF0F6),
    surfaceElevated = Color(0xFFDCE2EC),
    onSurface = Color(0xFF232936),
    onSurfaceVariant = Color(0xFF4E596C),
    primary = Color(0xFFC83C43),
    primaryContainer = Color(0xFFFBDADC),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF16723B),
    tertiary = Color(0xFF6547C8),
    warning = Color(0xFFA83A00),
    success = Color(0xFF16723B),
    danger = Color(0xFFB42345),
    favorite = Color(0xFFB42361),
    ocrAccent = Color(0xFF6547C8),
    adjustAccent = Color(0xFFA83A00),
    channelRed = Color(0xFFD20F39),
    channelGreen = Color(0xFF40A02B),
    channelBlue = Color(0xFF1E66F5),
    outline = Color(0xFFC5CBD7),
    cropHandle = Color(0xFFC83C43),
    cropBorder = Color(0xFF232936),
)

fun snapCropColors(dark: Boolean): SnapCropColors = if (dark) DarkSnapCropColors else LightSnapCropColors

/** Dark until a [SnapCropTheme] provides the user's theme, matching the app's default. */
val LocalSnapCropColors = staticCompositionLocalOf { DarkSnapCropColors }

val Black: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.black
val Surface: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.surface
val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.surfaceVariant
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.surfaceContainer
val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.surfaceElevated
val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.onSurface
val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.onSurfaceVariant
val Primary: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.primary
val PrimaryContainer: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.primaryContainer
val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.onPrimary
val Secondary: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.secondary
val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.tertiary
val Warning: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.warning
val Success: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.success
val Danger: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.danger
val Favorite: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.favorite
// Editor mode accents keep each workspace legible while sharing the new studio palette.
val OcrAccent: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.ocrAccent
val AdjustAccent: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.adjustAccent
// Per-channel indicators for RGB curve/color controls.
val ChannelRed: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.channelRed
val ChannelGreen: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.channelGreen
val ChannelBlue: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.channelBlue
val Outline: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.outline
val CropHandle: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.cropHandle
val CropBorder: Color @Composable @ReadOnlyComposable get() = LocalSnapCropColors.current.cropBorder
val DimOverlay: Color = Color(0xAA000000)
// Image/video preview backdrops stay neutral in both app themes; pair them only with OnMediaSurface.
val MediaSurface: Color = Color(0xFF000000)
val OnMediaSurface: Color = Color(0xFFEDEDF2)
val OnMediaSurfaceVariant: Color = Color(0xFFB8B8C2)
