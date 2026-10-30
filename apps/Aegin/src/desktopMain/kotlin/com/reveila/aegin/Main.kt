package com.reveila.aegin

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.ui.AeginApp
import kotlinx.coroutines.runBlocking

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
        val windowState = rememberWindowState(
            size = DpSize(1024.dp, 760.dp),
            position = WindowPosition(Alignment.Center)
        )
        Window(
            onCloseRequest = {
                runBlocking {
                    AeginBridge.triggerEmergencyStop()
                }
                exitApplication()
            },
            state = windowState,
            title = "Aegin — Reveila Sovereign AI"
        ) {
            AeginApp()
        }
    }
}
