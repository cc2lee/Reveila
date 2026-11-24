package com.reveila.aegin.ui.mascot

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.imageResource
import reveila_suite.apps.aegin.generated.resources.Res
import reveila_suite.apps.aegin.generated.resources.app_icon

enum class AeginMascotMode(val defaultSize: Dp) {
    SMALL_BADGE(36.dp),
    COMPANION(96.dp)
}

@Composable
fun AeginRobotDogMascot(
    state: AeginMascotState,
    modifier: Modifier = Modifier,
    mode: AeginMascotMode = AeginMascotMode.COMPANION,
    size: Dp = mode.defaultSize,
    showTelemetryEffects: Boolean = true
) {
    val phase = state.phase
    val isCompactBadge = size <= 44.dp
    val transition = updateTransition(targetState = phase, label = "MascotPhaseTransition")

    // 1. State-driven telemetry color (Cyan -> Emerald -> Electric Blue -> Amber/Red)
    val earGlowColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300) },
        label = "EarGlowColor"
    ) { target ->
        when (target) {
            AeginMascotPhase.IDLE -> Color(0xFF38BDF8)               // Calm cyan
            AeginMascotPhase.LISTENING_USER -> Color(0xFF22D3EE)     // Bright cyan
            AeginMascotPhase.ROUTING_LOCAL -> Color(0xFF10B981)      // Emerald (on-device)
            AeginMascotPhase.COORDINATING_CLOUD -> Color(0xFF00E5FF) // High-energy cyan-blue
            AeginMascotPhase.SPEAKING_RESPONSE -> Color(0xFF60A5FA)  // Warm sky cyan
            AeginMascotPhase.ERROR_ALERT -> Color(0xFFF43F5E)        // Amber-red alert
        }
    }

    val pawGlowAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "PawGlowAlpha"
    ) { target ->
        when (target) {
            AeginMascotPhase.IDLE -> 0.25f
            AeginMascotPhase.LISTENING_USER -> 0.48f
            AeginMascotPhase.ERROR_ALERT -> 0.20f
            else -> 0.85f
        }
    }

    // 2. Continuous ambient & activity loops
    val infiniteTransition = rememberInfiniteTransition(label = "MascotLiveLoops")

    // Vertical breathing / energetic bounce / speaking nod synced with token speed
    val bounceDuration = when (phase) {
        AeginMascotPhase.COORDINATING_CLOUD -> 420
        AeginMascotPhase.ROUTING_LOCAL -> 500
        AeginMascotPhase.SPEAKING_RESPONSE -> {
            val tpsBoost = state.tokensPerSecond.coerceIn(0f, 40f)
            (620 - (tpsBoost * 6f).toInt()).coerceIn(340, 620)
        }
        AeginMascotPhase.LISTENING_USER -> 1100
        else -> 1800
    }
    val verticalBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bounceDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VerticalBob"
    )

    // Coordination ping-pong sway (simulates looking back & forth between user and AI model)
    val coordinationSwing by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    AeginMascotPhase.COORDINATING_CLOUD -> 650
                    AeginMascotPhase.SPEAKING_RESPONSE -> 1100
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoordinationSwing"
    )

    // Brass inner-ear clockwork gear rotation
    val gearAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    AeginMascotPhase.ROUTING_LOCAL -> 1100
                    AeginMascotPhase.COORDINATING_CLOUD -> 1450
                    AeginMascotPhase.SPEAKING_RESPONSE -> 2200
                    else -> 8000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "GearRotation"
    )

    // 3. Natural periodic eye blink (~3.5s when idle so the mascot feels alive)
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        while (true) {
            val waitMs = if (state.isBusy) 4200L else 3500L
            delay(waitMs)
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }
    val blinkProgress by animateFloatAsState(
        targetValue = if (isBlinking) 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "BlinkProgress"
    )

    // Scale motion cleanly for compact badge vs companion size
    val motionScale = if (isCompactBadge) 0.45f else 1f
    val translationYPx = when (phase) {
        AeginMascotPhase.COORDINATING_CLOUD -> -verticalBob * 5f * motionScale
        AeginMascotPhase.ROUTING_LOCAL -> -verticalBob * 3.5f * motionScale
        AeginMascotPhase.SPEAKING_RESPONSE -> -verticalBob * 3.2f * motionScale
        else -> -verticalBob * 2f * motionScale
    }

    val translationXPx = when (phase) {
        AeginMascotPhase.COORDINATING_CLOUD -> coordinationSwing * 4.5f * motionScale
        AeginMascotPhase.LISTENING_USER -> 3.0f * motionScale
        else -> coordinationSwing * 0.8f * motionScale
    }

    val tiltDegrees = when (phase) {
        AeginMascotPhase.COORDINATING_CLOUD -> coordinationSwing * 5.5f
        AeginMascotPhase.LISTENING_USER -> 4.5f
        AeginMascotPhase.ERROR_ALERT -> -7.0f
        AeginMascotPhase.SPEAKING_RESPONSE -> coordinationSwing * 2.2f
        else -> coordinationSwing * 1.2f
    }

    val theme = com.reveila.aegin.ui.theme.LocalAeginColors.current
    val isCorporateLogo = com.reveila.aegin.ui.theme.LocalAeginTheme.current.isCorporateLogoMascot
    val isLowDpiDisplay = LocalDensity.current.density < 2.0f
    // Avoid sub-pixel bitmap shimmer on low-DPI desktop displays and keep corporate logos steady.
    val disableBitmapMotion = isCorporateLogo || isLowDpiDisplay
    val innerIconPadding = (size.value * 0.11f).dp.coerceAtLeast(3.dp)

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(theme.card)
            .border(
                width = 1.5.dp,
                color = if (state.isBusy) earGlowColor.copy(alpha = 0.65f) else theme.glassBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Underlay: Soft drop shadow beneath the icon + ambient coordination signal ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Soft radial drop shadow beneath the PNG so the icon floats above the circular badge
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = if (theme.isDark) 0.45f else 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.80f),
                    radius = w * 0.36f
                ),
                topLeft = Offset(w * 0.14f, h * 0.64f),
                size = Size(w * 0.72f, h * 0.28f)
            )

            if (showTelemetryEffects) {
                // Magenta paw-pad ground glow (robot_dog high-DPI only)
                if (!disableBitmapMotion) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEC4899).copy(alpha = pawGlowAlpha * (0.7f + 0.3f * verticalBob)),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.48f, h * 0.89f),
                            radius = w * 0.34f
                        ),
                        topLeft = Offset(w * 0.16f, h * 0.78f),
                        size = Size(w * 0.64f, h * 0.20f)
                    )
                }

                // Clean centered vector coordination ring when brokering user <-> AI
                if (phase == AeginMascotPhase.COORDINATING_CLOUD ||
                    phase == AeginMascotPhase.ROUTING_LOCAL ||
                    phase == AeginMascotPhase.ERROR_ALERT
                ) {
                    val pivot = Offset(w * 0.5f, h * 0.5f)
                    val inset = w * 0.06f
                    val ringSize = w - (inset * 2f)
                    rotate(degrees = gearAngle, pivot = pivot) {
                        drawArc(
                            color = earGlowColor.copy(alpha = 0.65f),
                            startAngle = 0f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(ringSize, ringSize),
                            style = Stroke(width = (w * 0.032f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = earGlowColor.copy(alpha = 0.38f),
                            startAngle = 180f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(ringSize, ringSize),
                            style = Stroke(width = (w * 0.032f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Core Mascot / Brand Logo Artwork + Optional Robot-Dog Articulated Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerIconPadding)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    translationX = if (disableBitmapMotion) 0f else translationXPx
                    translationY = if (disableBitmapMotion) 0f else translationYPx
                    rotationZ = if (disableBitmapMotion) 0f else tiltDegrees
                    val scalePulse = when {
                        disableBitmapMotion -> 1f
                        state.isBusy -> 1f + (verticalBob * 0.035f)
                        else -> 1f + (verticalBob * 0.015f)
                    }
                    scaleX = scalePulse
                    scaleY = scalePulse
                }
        ) {
            val rawDogBitmap = imageResource(Res.drawable.app_icon)
            val targetPx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(32) }
            val prefilteredDogBitmap = remember(rawDogBitmap, targetPx) {
                downscaleProgressivelyForDisplay(rawDogBitmap, targetPx)
            }
            Image(
                bitmap = prefilteredDogBitmap,
                contentDescription = if (isCorporateLogo) "Brand Assistant (${state.statusLabel})" else "Aegin Robot Dog (${state.statusLabel})",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )

            // Precision overlays aligned to robot dog ears/eyes (skipped for corporate logos and low-DPI desktop displays)
            if (showTelemetryEffects && !disableBitmapMotion) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height

                    // 1. Left & Right Inner-Ear Cyan Glow + Rotating Brass Clockwork Gears
                    val leftEarCenter = Offset(w * 0.34f, h * 0.19f)
                    val rightEarCenter = Offset(w * 0.71f, h * 0.145f)
                    val pulseAlpha = when (phase) {
                        AeginMascotPhase.LISTENING_USER -> 0.42f + (0.20f * verticalBob)
                        AeginMascotPhase.ERROR_ALERT -> 0.45f + (0.40f * verticalBob)
                        else -> if (state.isBusy) 0.35f + (0.35f * verticalBob) else 0.18f
                    }

                    drawEarTelemetry(
                        center = leftEarCenter,
                        radius = w * 0.068f,
                        glowColor = earGlowColor,
                        glowAlpha = pulseAlpha,
                        rotationDeg = gearAngle
                    )
                    drawEarTelemetry(
                        center = rightEarCenter,
                        radius = w * 0.056f,
                        glowColor = earGlowColor,
                        glowAlpha = pulseAlpha,
                        rotationDeg = -gearAngle * 1.25f
                    )

                    // 2. Side Ear-Hub Cyan/Alert Ring Pulse
                    val earHubCenter = Offset(w * 0.355f, h * 0.395f)
                    drawCircle(
                        color = earGlowColor.copy(alpha = 0.4f + 0.45f * verticalBob),
                        radius = w * 0.044f,
                        center = earHubCenter,
                        style = Stroke(width = (w * 0.016f).coerceAtLeast(1.2f))
                    )

                    // 3. Metallic Eyelid Blink Overlay over glossy eyes
                    if (blinkProgress > 0.02f) {
                        drawEyelidBlink(
                            eyeCenter = Offset(w * 0.465f, h * 0.37f),
                            eyeRadiusX = w * 0.068f,
                            eyeRadiusY = h * 0.068f,
                            progress = blinkProgress
                        )
                        drawEyelidBlink(
                            eyeCenter = Offset(w * 0.705f, h * 0.335f),
                            eyeRadiusX = w * 0.052f,
                            eyeRadiusY = h * 0.062f,
                            progress = blinkProgress
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawEarTelemetry(
    center: Offset,
    radius: Float,
    glowColor: Color,
    glowAlpha: Float,
    rotationDeg: Float
) {
    // Soft cyan-emerald aura inside the translucent ear
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
            center = center,
            radius = radius * 1.5f
        ),
        radius = radius * 1.5f,
        center = center
    )

    // Rotating bronze gear ring
    val gearColor = Color(0xFFD99B52).copy(alpha = 0.78f)
    rotate(degrees = rotationDeg, pivot = center) {
        drawCircle(
            color = gearColor,
            radius = radius * 0.65f,
            center = center,
            style = Stroke(width = (radius * 0.18f).coerceAtLeast(1f))
        )
        val teethCount = 8
        for (i in 0 until teethCount) {
            val angleRad = (i * (360.0 / teethCount)) * (PI / 180.0)
            val inner = Offset(
                x = center.x + (cos(angleRad) * radius * 0.65f).toFloat(),
                y = center.y + (sin(angleRad) * radius * 0.65f).toFloat()
            )
            val outer = Offset(
                x = center.x + (cos(angleRad) * radius * 0.95f).toFloat(),
                y = center.y + (sin(angleRad) * radius * 0.95f).toFloat()
            )
            drawLine(
                color = gearColor,
                start = inner,
                end = outer,
                strokeWidth = (radius * 0.20f).coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawEyelidBlink(
    eyeCenter: Offset,
    eyeRadiusX: Float,
    eyeRadiusY: Float,
    progress: Float
) {
    val lidHeight = eyeRadiusY * 2f * progress
    val eyelidPath = Path().apply {
        addOval(
            Rect(
                left = eyeCenter.x - eyeRadiusX,
                top = eyeCenter.y - eyeRadiusY,
                right = eyeCenter.x + eyeRadiusX,
                bottom = eyeCenter.y - eyeRadiusY + lidHeight
            )
        )
    }
    drawPath(
        path = eyelidPath,
        color = Color(0xFF94A3B8) // Brushed metallic silver eyelid
    )
}

/**
 * Progressively downscales a high-resolution [ImageBitmap] in <= 2x steps down to ~2x the
 * target display pixel size (supersampled mipmap level). This eliminates Skia/Desktop
 * aliasing and pixel-shimmering noise when downscaling a 512x512 PNG into a 54-96px slot
 * under continuous rotation/translation animations.
 */
private fun downscaleProgressivelyForDisplay(
    source: ImageBitmap,
    targetDisplayPx: Int
): ImageBitmap {
    val desiredPx = (targetDisplayPx * 2).coerceIn(64, maxOf(source.width, source.height))
    if (source.width <= desiredPx && source.height <= desiredPx) {
        return source
    }
    return try {
        var current = source
        val paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.High
        }
        while (current.width > desiredPx * 1.5f && current.height > desiredPx * 1.5f) {
            val nextW = (current.width / 2).coerceAtLeast(desiredPx)
            val nextH = (current.height / 2).coerceAtLeast(desiredPx)
            val next = ImageBitmap(nextW, nextH)
            val canvas = Canvas(next)
            canvas.drawImageRect(
                image = current,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(current.width, current.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(nextW, nextH),
                paint = paint
            )
            current = next
        }
        current
    } catch (_: Exception) {
        source
    }
}
