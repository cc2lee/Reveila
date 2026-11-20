package com.reveila.aegin.ui.mascot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

@Composable
fun WindowsFloatingMascot(
    controller: AeginMascotController,
    visible: Boolean,
    onMascotClicked: () -> Unit,
    onCloseRequest: () -> Unit
) {
    if (!visible) return

    val windowState = rememberWindowState(
        size = DpSize(104.dp, 104.dp),
        position = WindowPosition(Alignment.BottomEnd)
    )

    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "Aegin Companion",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true
    ) {
        val state by controller.state.collectAsState()
        WindowDraggableArea(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMascotClicked() },
                contentAlignment = Alignment.Center
            ) {
                AeginRobotDogMascot(
                    state = state,
                    mode = AeginMascotMode.COMPANION,
                    size = 96.dp
                )
            }
        }
    }
}

/**
 * Offscreen frame renderer for the Windows System Tray icon when Aegin is busy,
 * compositing the robot dog artwork with a live state-colored telemetry arc ring.
 */
object WindowsMascotTrayRenderer {
    private val baseDogImage: BufferedImage? by lazy {
        val loader = Thread.currentThread().contextClassLoader
        val candidates = listOf(
            "composeResources/reveila_suite.apps.aegin.generated.resources/drawable/aegin_robot_dog.png",
            "aegin_icon.png"
        )
        candidates.firstNotNullOfOrNull { path ->
            try {
                loader?.getResourceAsStream(path)?.use { ImageIO.read(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun renderTrayFrameBitmap(
        state: AeginMascotState,
        frameIndex: Int,
        sizePx: Int = 64
    ): ImageBitmap {
        val img = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            val pad = (sizePx * 0.10f).toInt()
            val innerSize = sizePx - (pad * 2)
            val bobOffsetY = if (state.isBusy && frameIndex % 2 == 1) -2 else 0

            val accent = when (state.phase) {
                AeginMascotPhase.IDLE -> AwtColor(0x38, 0xBD, 0xF8, 170)
                AeginMascotPhase.LISTENING_USER -> AwtColor(0x22, 0xD3, 0xEE, 220)
                AeginMascotPhase.ROUTING_LOCAL -> AwtColor(0x10, 0xB9, 0x81, 240)
                AeginMascotPhase.COORDINATING_CLOUD -> AwtColor(0x00, 0xE5, 0xFF, 245)
                AeginMascotPhase.SPEAKING_RESPONSE -> AwtColor(0x60, 0xA5, 0xFA, 230)
                AeginMascotPhase.ERROR_ALERT -> AwtColor(0xF4, 0x3F, 0x5E, 245)
            }

            // Telemetry ring around tray icon when busy or alerting
            if (state.isBusy || state.phase == AeginMascotPhase.ERROR_ALERT) {
                g.color = accent
                g.stroke = BasicStroke((sizePx * 0.07f).coerceAtLeast(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val startDeg = (frameIndex * 45) % 360
                g.drawArc(2, 2, sizePx - 5, sizePx - 5, startDeg, 220)
            }

            baseDogImage?.let { dog ->
                g.drawImage(dog, pad, pad + bobOffsetY, innerSize, innerSize, null)
            } ?: run {
                g.color = accent
                g.fillOval(pad, pad, innerSize, innerSize)
            }
        } finally {
            g.dispose()
        }
        return img.toComposeImageBitmap()
    }
}
