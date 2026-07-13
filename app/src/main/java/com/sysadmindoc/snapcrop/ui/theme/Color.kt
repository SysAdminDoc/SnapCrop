package com.sysadmindoc.snapcrop.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

private val _isDark = mutableStateOf(true)
var isDarkTheme: Boolean
    get() = _isDark.value
    set(value) { _isDark.value = value }

val Black: Color get() = if (_isDark.value) Color(0xFF080A10) else Color(0xFFF8F9FC)
val Surface: Color get() = if (_isDark.value) Color(0xFF0D111B) else Color(0xFFF0F2F7)
val SurfaceVariant: Color get() = if (_isDark.value) Color(0xFF171D2B) else Color(0xFFE4E8F0)
val SurfaceContainer: Color get() = if (_isDark.value) Color(0xFF111725) else Color(0xFFEDF0F6)
val SurfaceElevated: Color get() = if (_isDark.value) Color(0xFF1C2434) else Color(0xFFDCE2EC)
val OnSurface: Color get() = if (_isDark.value) Color(0xFFF2F4F8) else Color(0xFF232936)
val OnSurfaceVariant: Color get() = if (_isDark.value) Color(0xFFB5BECE) else Color(0xFF4E596C)
val Primary: Color get() = if (_isDark.value) Color(0xFFFF6B66) else Color(0xFFC83C43)
val PrimaryContainer: Color get() = if (_isDark.value) Color(0xFF3B232B) else Color(0xFFFBDADC)
val OnPrimary: Color get() = if (_isDark.value) Color(0xFF1B090B) else Color(0xFFFFFFFF)
val Secondary: Color get() = if (_isDark.value) Color(0xFF70E39A) else Color(0xFF16723B)
val Tertiary: Color get() = if (_isDark.value) Color(0xFFA88CFF) else Color(0xFF6547C8)
val Warning: Color get() = if (_isDark.value) Color(0xFFFFB376) else Color(0xFFA83A00)
val Success: Color get() = if (_isDark.value) Color(0xFF70E39A) else Color(0xFF16723B)
val Danger: Color get() = if (_isDark.value) Color(0xFFFF6F91) else Color(0xFFB42345)
val Favorite: Color get() = if (_isDark.value) Color(0xFFFF8DB0) else Color(0xFFB42361)
// Editor mode accents keep each workspace legible while sharing the new studio palette.
val OcrAccent: Color get() = if (_isDark.value) Color(0xFFB99CFF) else Color(0xFF6547C8)
val AdjustAccent: Color get() = if (_isDark.value) Color(0xFFFFB376) else Color(0xFFA83A00)
// Per-channel indicators for RGB curve/color controls.
val ChannelRed: Color get() = if (_isDark.value) Color(0xFFFF6B6B) else Color(0xFFD20F39)
val ChannelGreen: Color get() = if (_isDark.value) Color(0xFF51CF66) else Color(0xFF40A02B)
val ChannelBlue: Color get() = if (_isDark.value) Color(0xFF339AF0) else Color(0xFF1E66F5)
val Outline: Color get() = if (_isDark.value) Color(0xFF313A4C) else Color(0xFFC5CBD7)
val CropHandle: Color get() = if (_isDark.value) Color(0xFFFF6B66) else Color(0xFFC83C43)
val CropBorder: Color get() = if (_isDark.value) Color(0xFFF2F4F8) else Color(0xFF232936)
val DimOverlay: Color get() = Color(0xAA000000)
// Image/video preview backdrops stay neutral in both app themes; pair them only with OnMediaSurface.
val MediaSurface: Color get() = Color(0xFF000000)
val OnMediaSurface: Color get() = Color(0xFFEDEDF2)
val OnMediaSurfaceVariant: Color get() = Color(0xFFB8B8C2)
