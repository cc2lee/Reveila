package com.reveila.aegin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AeginColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val surfaceElevated: Color,
    val text: Color,
    val subText: Color,
    val mutedText: Color,
    val border: Color,
    val glassBorder: Color,
    val headerBg: Color,
    val tabBar: Color,
    val navBarBg: Color,
    val inputBg: Color,
    val primary: Color = Color(0xFFFF6B2C),
    val primaryEnd: Color = Color(0xFFFF9F43),
    val success: Color = Color(0xFF10B981),
    val accent: Color = Color(0xFF4F46E5),
    val accentEnd: Color = Color(0xFF06B6D4),
    val violet: Color = Color(0xFF8B5CF6),
    val warning: Color = Color(0xFFF59E0B),
    val danger: Color = Color(0xFFEF4444),
    val cyan: Color = Color(0xFF06B6D4)
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(listOf(primary, primaryEnd))

    val cloudGradient: Brush
        get() = Brush.linearGradient(listOf(accent, cyan))

    val museHeroGradient: Brush
        get() = Brush.linearGradient(
            listOf(
                Color(0xFFFF6B2C),
                Color(0xFFEC4899),
                Color(0xFF6366F1),
                Color(0xFF06B6D4)
            )
        )

    val subtleSurfaceGradient: Brush
        get() = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF131B2E),
                    Color(0xFF0D1322)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF8FAFC)
                )
            )
        }
}

val DarkAeginColors = AeginColors(
    isDark = true,
    bg = Color(0xFF060911),
    card = Color(0xFF101726),
    cardAlt = Color(0xFF182236),
    surfaceElevated = Color(0xFF1E293B),
    text = Color(0xFFF8FAFC),
    subText = Color(0xFF94A3B8),
    mutedText = Color(0xFF64748B),
    border = Color(0xFF1E293B),
    glassBorder = Color(0x24FFFFFF),
    headerBg = Color(0xFF060911),
    tabBar = Color(0xFF0D1322),
    navBarBg = Color(0xFF0B101D),
    inputBg = Color(0xFF0D1424)
)

val LightAeginColors = AeginColors(
    isDark = false,
    bg = Color(0xFFF8FAFC),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF1F5F9),
    surfaceElevated = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A),
    subText = Color(0xFF475569),
    mutedText = Color(0xFF94A3B8),
    border = Color(0xFFE2E8F0),
    glassBorder = Color(0x140F172A),
    headerBg = Color(0xFFF8FAFC),
    tabBar = Color(0xFFF1F5F9),
    navBarBg = Color(0xFFFFFFFF),
    inputBg = Color(0xFFF1F5F9)
)

val LocalAeginColors = staticCompositionLocalOf { DarkAeginColors }

/**
 * Luminous Muse-style AI Presence Orb Avatar.
 * Shifts its core aura subtly based on whether Aegin is running in Sovereign On-Device mode or Cloud mode.
 */
@Composable
fun AeginOrbAvatar(
    size: Dp = 36.dp,
    isLocalMode: Boolean = true,
    isThinking: Boolean = false,
    showStatusDot: Boolean = false
) {
    val theme = LocalAeginColors.current
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isLocalMode) {
                        listOf(
                            Color(0xFFFF6B2C).copy(alpha = if (isThinking) 0.55f else 0.35f),
                            Color(0xFFEC4899).copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0xFF6366F1).copy(alpha = if (isThinking) 0.55f else 0.35f),
                            Color(0xFF06B6D4).copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    },
                    center = center,
                    radius = radius * 1.05f
                ),
                radius = radius,
                center = center
            )

            // Core orb sphere
            val orbRadius = radius * 0.78f
            drawCircle(
                brush = Brush.linearGradient(
                    colors = if (isLocalMode) {
                        listOf(
                            Color(0xFFFF8A4C),
                            Color(0xFFFF5E3A),
                            Color(0xFFD946EF)
                        )
                    } else {
                        listOf(
                            Color(0xFF38BDF8),
                            Color(0xFF6366F1),
                            Color(0xFFA855F7)
                        )
                    },
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height)
                ),
                radius = orbRadius,
                center = center
            )

            // Inner specular highlight (glassmorphic depth)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - orbRadius * 0.28f, center.y - orbRadius * 0.32f),
                    radius = orbRadius * 0.65f
                ),
                radius = orbRadius * 0.65f,
                center = Offset(center.x - orbRadius * 0.2f, center.y - orbRadius * 0.2f)
            )

            // Subtle inner geometric ring
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = orbRadius * 0.88f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        if (showStatusDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).dp.coerceAtLeast(8.dp))
                    .clip(CircleShape)
                    .background(if (isLocalMode) theme.success else theme.cyan)
                    .border(1.5.dp, theme.bg, CircleShape)
            )
        }
    }
}

enum class AeginIconType {
    CHAT,
    SPACES,
    SETTINGS,
    SEND,
    SPARKLES,
    VAULT,
    SHIELD,
    PLUS,
    THREAD,
    SUN,
    MOON,
    BOLT,
    CHECK,
    CLOSE
}

/**
 * Crisp geometric vector icons drawn on Canvas so Aegin never relies on external icon font artifacts.
 */
@Composable
fun AeginVectorIcon(
    type: AeginIconType,
    tint: Color,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            AeginIconType.CHAT -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.22f)
                    lineTo(w * 0.82f, h * 0.22f)
                    quadraticTo(w * 0.92f, h * 0.22f, w * 0.92f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.64f)
                    quadraticTo(w * 0.92f, h * 0.76f, w * 0.80f, h * 0.76f)
                    lineTo(w * 0.42f, h * 0.76f)
                    lineTo(w * 0.20f, h * 0.90f)
                    lineTo(w * 0.24f, h * 0.76f)
                    lineTo(w * 0.18f, h * 0.76f)
                    quadraticTo(w * 0.08f, h * 0.76f, w * 0.08f, h * 0.64f)
                    lineTo(w * 0.08f, h * 0.34f)
                    quadraticTo(w * 0.08f, h * 0.22f, w * 0.18f, h * 0.22f)
                    close()
                }
                drawPath(path, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.28f, h * 0.44f), Offset(w * 0.72f, h * 0.44f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.58f), Offset(w * 0.56f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.SPACES -> {
                val gap = w * 0.07f
                val boxSize = (w * 0.76f - gap) / 2f
                val start = w * 0.12f
                val r = CornerRadius(w * 0.10f, w * 0.10f)
                drawRoundRect(tint, Offset(start, start), Size(boxSize, boxSize), r, style = stroke)
                drawRoundRect(tint, Offset(start + boxSize + gap, start), Size(boxSize, boxSize), r, style = stroke)
                drawRoundRect(tint, Offset(start, start + boxSize + gap), Size(boxSize, boxSize), r, style = stroke)
                drawRoundRect(tint, Offset(start + boxSize + gap, start + boxSize + gap), Size(boxSize, boxSize), r, style = stroke)
            }

            AeginIconType.SETTINGS -> {
                // Sleek sliders / studio control icon
                drawLine(tint, Offset(w * 0.14f, h * 0.28f), Offset(w * 0.86f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.36f, h * 0.28f))
                drawLine(tint, Offset(w * 0.14f, h * 0.52f), Offset(w * 0.86f, h * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.52f))
                drawLine(tint, Offset(w * 0.14f, h * 0.76f), Offset(w * 0.86f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.44f, h * 0.76f))
            }

            AeginIconType.SEND -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.82f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.44f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.44f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
            }

            AeginIconType.SPARKLES -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    quadraticTo(w * 0.54f, h * 0.44f, w * 0.88f, h * 0.50f)
                    quadraticTo(w * 0.54f, h * 0.56f, w * 0.50f, h * 0.90f)
                    quadraticTo(w * 0.46f, h * 0.56f, w * 0.12f, h * 0.50f)
                    quadraticTo(w * 0.46f, h * 0.44f, w * 0.50f, h * 0.10f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.VAULT -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.26f)
                    lineTo(w * 0.38f, h * 0.26f)
                    lineTo(w * 0.48f, h * 0.36f)
                    lineTo(w * 0.88f, h * 0.36f)
                    lineTo(w * 0.88f, h * 0.78f)
                    lineTo(w * 0.12f, h * 0.78f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.SHIELD -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.26f)
                    lineTo(w * 0.84f, h * 0.52f)
                    quadraticTo(w * 0.84f, h * 0.78f, w * 0.50f, h * 0.90f)
                    quadraticTo(w * 0.16f, h * 0.78f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.26f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.PLUS -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.THREAD -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.30f), Offset(w * 0.80f, h * 0.30f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.52f), Offset(w * 0.65f, h * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.74f), Offset(w * 0.50f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.76f, h * 0.72f))
            }

            AeginIconType.SUN -> {
                drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.10f), Offset(w * 0.5f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.90f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.10f, h * 0.5f), Offset(w * 0.18f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.82f, h * 0.5f), Offset(w * 0.90f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.MOON -> {
                val p = Path().apply {
                    moveTo(w * 0.64f, h * 0.18f)
                    quadraticTo(w * 0.26f, h * 0.24f, w * 0.26f, h * 0.54f)
                    quadraticTo(w * 0.26f, h * 0.82f, w * 0.68f, h * 0.82f)
                    quadraticTo(w * 0.46f, h * 0.68f, w * 0.46f, h * 0.46f)
                    quadraticTo(w * 0.46f, h * 0.28f, w * 0.64f, h * 0.18f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.BOLT -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.12f)
                    lineTo(w * 0.24f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.54f)
                    lineTo(w * 0.44f, h * 0.88f)
                    lineTo(w * 0.76f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.46f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.CHECK -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.52f), Offset(w * 0.42f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.80f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.CLOSE -> {
                drawLine(tint, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.76f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.24f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}
