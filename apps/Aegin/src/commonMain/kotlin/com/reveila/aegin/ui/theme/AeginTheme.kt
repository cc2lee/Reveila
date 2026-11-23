package com.reveila.aegin.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import reveila_suite.apps.aegin.generated.resources.Res
import reveila_suite.apps.aegin.generated.resources.aegin_robot_dog

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
    val onPrimary: Color = Color.White,
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
                primary,
                violet,
                accent,
                cyan
            )
        )

    val subtleSurfaceGradient: Brush
        get() = Brush.verticalGradient(listOf(card, tabBar))
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
 * Aegin robotic pup brand avatar across chat, Spaces, and Studio headers.
 * - When [isThinking] is true: lively trot bounce, head wiggle, and pulsing cyan/indigo aura.
 * - When idle with [animateIdle] enabled: calm slow breathing punctuated by periodic playful head tilts & hops.
 */
@Composable
fun AeginOrbAvatar(
    size: Dp = 36.dp,
    isLocalMode: Boolean = true,
    isThinking: Boolean = false,
    showStatusDot: Boolean = false,
    animateIdle: Boolean = true
) {
    val theme = LocalAeginColors.current

    // Only run continuous 60fps infinite transitions while actively thinking;
    // idle frames stay stationary between calm periodic gestures to avoid burning GPU/CPU.
    var activePhase = 0f
    var haloAlpha = 0f
    var haloSweepAngle = 0f
    if (isThinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "aegin_pup_transition")
        activePhase = infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "active_phase"
        ).value
        haloAlpha = infiniteTransition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "halo_alpha"
        ).value
        haloSweepAngle = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "halo_sweep"
        ).value
    }

    // Periodic playful movement while relaxing in idle state (zero redraws during wait)
    val idleTilt = remember { Animatable(0f) }
    val idleHop = remember { Animatable(0f) }

    LaunchedEffect(isThinking, animateIdle) {
        if (isThinking || !animateIdle) {
            idleTilt.snapTo(0f)
            idleHop.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            delay(5000L)
            launch {
                idleTilt.animateTo(-6f, tween(320, easing = FastOutSlowInEasing))
                delay(220L)
                idleTilt.animateTo(4f, tween(300, easing = FastOutSlowInEasing))
                idleTilt.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            }
            idleHop.animateTo(-2f, tween(170, easing = FastOutSlowInEasing))
            idleHop.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
        }
    }

    val translationYDp: Float
    val rotationDeg: Float
    val scaleFactor: Float

    when {
        isThinking -> {
            // Brisk excited trot & ear/head wiggle
            val bounceRatio = 1f - kotlin.math.abs(activePhase)
            translationYDp = -3.2f * bounceRatio
            rotationDeg = activePhase * 5.5f
            scaleFactor = 0.90f + 0.05f * bounceRatio
        }
        animateIdle -> {
            // Calm periodic playful gesture without continuous redraws
            translationYDp = idleHop.value
            rotationDeg = idleTilt.value
            scaleFactor = 1.0f
        }
        else -> {
            translationYDp = 0f
            rotationDeg = 0f
            scaleFactor = 1.0f
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isThinking) {
            Canvas(modifier = Modifier.size(size)) {
                val radius = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF07DBEC).copy(alpha = haloAlpha),
                            Color(0xFF6366F1).copy(alpha = haloAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                drawArc(
                    color = Color(0xFF07DBEC).copy(alpha = 0.85f),
                    startAngle = haloSweepAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Image(
            painter = painterResource(Res.drawable.aegin_robot_dog),
            contentDescription = "Aegin",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = translationYDp.dp.toPx()
                    rotationZ = rotationDeg
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }
        )

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
    CLOSE,
    MENU,
    BACK,
    DEVICE,
    CLOUD,
    FOLDERS_STACK
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
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(tint, radius = w * 0.19f, center = Offset(cx, cy), style = stroke)
                val innerR = w * 0.31f
                val outerR = w * 0.44f
                for (i in 0 until 8) {
                    val angle = i * (PI / 4.0)
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(cx + innerR * dx, cy + innerR * dy),
                        end = Offset(cx + outerR * dx, cy + outerR * dy),
                        strokeWidth = sw * 0.9f,
                        cap = StrokeCap.Round
                    )
                }
            }

            AeginIconType.MOON -> {
                val outerBounds = Rect(w * 0.16f, h * 0.16f, w * 0.84f, h * 0.84f)
                val p = Path().apply {
                    arcTo(
                        rect = outerBounds,
                        startAngleDegrees = -78f,
                        sweepAngleDegrees = -204f,
                        forceMoveTo = true
                    )
                    cubicTo(
                        w * 0.36f, h * 0.73f,
                        w * 0.36f, h * 0.27f,
                        w * 0.57f, h * 0.17f
                    )
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                // Crisp companion star twinkle in upper-right
                val sx = w * 0.76f
                val sy = h * 0.25f
                val sr = w * 0.08f
                drawLine(tint, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
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

            AeginIconType.MENU -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.72f), Offset(w * 0.82f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.BACK -> {
                drawLine(tint, Offset(w * 0.62f, h * 0.20f), Offset(w * 0.30f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.62f, h * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
            }

            AeginIconType.DEVICE -> {
                // Crisp smartphone line-art silhouette with top earpiece and bottom home indicator
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.26f, h * 0.11f),
                    size = Size(w * 0.48f, h * 0.78f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(w * 0.42f, h * 0.22f),
                    Offset(w * 0.58f, h * 0.22f),
                    strokeWidth = sw * 0.9f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(w * 0.40f, h * 0.77f),
                    Offset(w * 0.60f, h * 0.77f),
                    strokeWidth = sw * 0.95f,
                    cap = StrokeCap.Round
                )
            }

            AeginIconType.CLOUD -> {
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.74f)
                    lineTo(w * 0.74f, h * 0.74f)
                    cubicTo(w * 0.86f, h * 0.74f, w * 0.90f, h * 0.64f, w * 0.88f, h * 0.55f)
                    cubicTo(w * 0.87f, h * 0.47f, w * 0.80f, h * 0.42f, w * 0.72f, h * 0.43f)
                    cubicTo(w * 0.69f, h * 0.28f, w * 0.54f, h * 0.22f, w * 0.42f, h * 0.28f)
                    cubicTo(w * 0.34f, h * 0.32f, w * 0.30f, h * 0.39f, w * 0.30f, h * 0.44f)
                    cubicTo(w * 0.19f, h * 0.45f, w * 0.12f, h * 0.53f, w * 0.12f, h * 0.62f)
                    cubicTo(w * 0.12f, h * 0.70f, w * 0.19f, h * 0.74f, w * 0.28f, h * 0.74f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            AeginIconType.FOLDERS_STACK -> {
                // Back stacked folder (top-right offset)
                val backFolder = Path().apply {
                    moveTo(w * 0.24f, h * 0.24f)
                    lineTo(w * 0.44f, h * 0.24f)
                    lineTo(w * 0.52f, h * 0.32f)
                    lineTo(w * 0.88f, h * 0.32f)
                    lineTo(w * 0.88f, h * 0.68f)
                }
                drawPath(backFolder, color = tint.copy(alpha = 0.65f), style = stroke)

                // Front primary folder (bottom-left)
                val frontFolder = Path().apply {
                    moveTo(w * 0.12f, h * 0.38f)
                    lineTo(w * 0.36f, h * 0.38f)
                    lineTo(w * 0.44f, h * 0.47f)
                    lineTo(w * 0.78f, h * 0.47f)
                    lineTo(w * 0.78f, h * 0.82f)
                    lineTo(w * 0.12f, h * 0.82f)
                    close()
                }
                drawPath(frontFolder, color = tint, style = stroke)
            }
        }
    }
}
