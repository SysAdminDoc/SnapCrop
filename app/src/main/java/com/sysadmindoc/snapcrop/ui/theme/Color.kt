package com.sysadmindoc.snapcrop.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

private val _isDark = mutableStateOf(true)
var isDarkTheme: Boolean
    get() = _isDark.value
    set(value) { _isDark.value = value }

val Black: Color get() = if (_isDark.value) Color(0xFF000000) else Color(0xFFFFFFFF)
val Surface: Color get() = if (_isDark.value) Color(0xFF0A0A0A) else Color(0xFFF2F4F8)
val SurfaceVariant: Color get() = if (_isDark.value) Color(0xFF1A1A1A) else Color(0xFFE6E9EF)
val SurfaceContainer: Color get() = if (_isDark.value) Color(0xFF141414) else Color(0xFFEFF1F5)
val SurfaceElevated: Color get() = if (_isDark.value) Color(0xFF202020) else Color(0xFFDCE0E8)
val OnSurface: Color get() = if (_isDark.value) Color(0xFFE0E0E0) else Color(0xFF4C4F69)
val OnSurfaceVariant: Color get() = if (_isDark.value) Color(0xFF9E9E9E) else Color(0xFF6C6F85)
val Primary: Color get() = if (_isDark.value) Color(0xFF89B4FA) else Color(0xFF1E66F5)
val PrimaryContainer: Color get() = if (_isDark.value) Color(0xFF1E3A5F) else Color(0xFFD5E2FA)
val OnPrimary: Color get() = if (_isDark.value) Color(0xFF000000) else Color(0xFFFFFFFF)
val Secondary: Color get() = if (_isDark.value) Color(0xFFA6E3A1) else Color(0xFF40A02B)
val Tertiary: Color get() = if (_isDark.value) Color(0xFFF38BA8) else Color(0xFFD20F39)
val Warning: Color get() = if (_isDark.value) Color(0xFFFAB387) else Color(0xFFFE640B)
// Editor mode accents — Catppuccin Mocha (dark) / Latte (light) so they adapt across themes.
val OcrAccent: Color get() = if (_isDark.value) Color(0xFFCBA6F7) else Color(0xFF8839EF) // lavender / mauve
val AdjustAccent: Color get() = if (_isDark.value) Color(0xFFFAB387) else Color(0xFFFE640B) // peach
// Per-channel indicators for RGB curve/color controls.
val ChannelRed: Color get() = if (_isDark.value) Color(0xFFFF6B6B) else Color(0xFFD20F39)
val ChannelGreen: Color get() = if (_isDark.value) Color(0xFF51CF66) else Color(0xFF40A02B)
val ChannelBlue: Color get() = if (_isDark.value) Color(0xFF339AF0) else Color(0xFF1E66F5)
val Outline: Color get() = if (_isDark.value) Color(0xFF333333) else Color(0xFFCCD0DA)
val CropHandle: Color get() = if (_isDark.value) Color(0xFF89B4FA) else Color(0xFF1E66F5)
val CropBorder: Color get() = if (_isDark.value) Color(0xFFCDD6F4) else Color(0xFF4C4F69)
val DimOverlay: Color get() = Color(0xAA000000)
