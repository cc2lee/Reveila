package com.reveila.aegin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AeginColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val subText: Color,
    val border: Color,
    val headerBg: Color,
    val tabBar: Color,
    val inputBg: Color,
    val primary: Color = Color(0xFFFF6600),
    val success: Color = Color(0xFF10B981),
    val accent: Color = Color(0xFF3B82F6),
    val warning: Color = Color(0xFFF59E0B),
    val danger: Color = Color(0xFFEF4444),
    val cyan: Color = Color(0xFF00E5FF)
)

val DarkAeginColors = AeginColors(
    isDark = true,
    bg = Color(0xFF090D16),
    card = Color(0xFF131B2E),
    cardAlt = Color(0xFF1E293B),
    text = Color(0xFFF8FAFC),
    subText = Color(0xFF94A3B8),
    border = Color(0xFF1E293B),
    headerBg = Color(0xFF090D16),
    tabBar = Color(0xFF0F172A),
    inputBg = Color(0xFF0F172A)
)

val LightAeginColors = AeginColors(
    isDark = false,
    bg = Color(0xFFF1F5F9),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A),
    subText = Color(0xFF64748B),
    border = Color(0xFFE2E8F0),
    headerBg = Color(0xFF090D16),
    tabBar = Color(0xFF1E293B),
    inputBg = Color(0xFFF1F5F9)
)

val LocalAeginColors = staticCompositionLocalOf { DarkAeginColors }
