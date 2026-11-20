package com.reveila.aegin

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.AeginApp
import com.reveila.aegin.ui.mascot.AeginMascotController
import com.reveila.aegin.ui.mascot.WindowsFloatingMascot
import com.reveila.aegin.ui.mascot.WindowsMascotTrayRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image as SkiaImage

fun main(args: Array<String>) {
    // Support headless verification mode for CI / build validation on Windows & macOS
    if (args.any { it == "--verify-headless" }) {
        runBlocking {
            println("===============================================================================")
            println("         AEGIN (KOTLIN MULTIPLATFORM) - HEADLESS ENGINE VERIFICATION           ")
            println("===============================================================================")
            val started = AeginBridge.startService()
            val status = AeginBridge.getEngineStatus()
            val stats = AeginBridge.getVaultStats()
            val (answer, sessionId) = AeginBridge.askAgent(
                "Summarize the Reveila Sovereign AI & Aegin Architecture from the KnowledgeVault.",
                null,
                null
            )
            println("[AeginVerify] Engine Started     : $started (${status.status} on ${status.platform})")
            println("[AeginVerify] Storage Mount Path : ${status.storageMountPath}")
            println("[AeginVerify] KnowledgeVault     : ${stats.documentCount} docs, ${stats.chunkCount} vector chunks")
            println("[AeginVerify] Session ID         : $sessionId")
            println("[AeginVerify] Agent Response     : $answer")
            AeginBridge.triggerEmergencyStop()
            println("===============================================================================")
            if (!started || stats.chunkCount <= 0) {
                kotlin.system.exitProcess(1)
            }
        }
        return
    }

    application {
        val mascotController = remember { AeginMascotController() }
        val mascotState by mascotController.state.collectAsState()
        var showFloatingCompanion by remember { mutableStateOf(false) }
        var trayFrameIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(mascotState.isBusy, mascotState.phase) {
            if (mascotState.isBusy) {
                while (true) {
                    delay(220L)
                    trayFrameIndex = (trayFrameIndex + 1) % 8
                }
            } else {
                trayFrameIndex = 0
            }
        }

        val windowState = rememberWindowState(
            size = DpSize(1024.dp, 760.dp),
            position = WindowPosition(Alignment.Center)
        )
        val windowIcon = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("aegin_icon.png")
            ?.use { it.readBytes() }
            ?.let { bytes -> BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()) }

        val trayIconPainter = remember(mascotState.phase, trayFrameIndex) {
            BitmapPainter(
                WindowsMascotTrayRenderer.renderTrayFrameBitmap(
                    state = mascotState,
                    frameIndex = trayFrameIndex
                )
            )
        }

        Tray(
            icon = trayIconPainter,
            tooltip = "Aegin — ${mascotState.statusLabel}",
            menu = {
                Item(
                    text = if (showFloatingCompanion) "Hide Floating Companion" else "Show Floating Companion",
                    onClick = { showFloatingCompanion = !showFloatingCompanion }
                )
                Item(
                    text = "Quit Aegin",
                    onClick = {
                        runBlocking { AeginBridge.triggerEmergencyStop() }
                        exitApplication()
                    }
                )
            }
        )

        Window(
            onCloseRequest = {
                runBlocking {
                    AeginBridge.triggerEmergencyStop()
                }
                exitApplication()
            },
            state = windowState,
            title = "Aegin — Reveila Sovereign AI",
            icon = windowIcon
        ) {
            AeginApp(mascotController = mascotController)
        }

        WindowsFloatingMascot(
            controller = mascotController,
            visible = showFloatingCompanion,
            onMascotClicked = { windowState.isMinimized = false },
            onCloseRequest = { showFloatingCompanion = false }
        )
    }
}
