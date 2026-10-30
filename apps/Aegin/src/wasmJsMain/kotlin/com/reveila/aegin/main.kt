package com.reveila.aegin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.reveila.aegin.ui.AeginApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Aegin — Reveila Sovereign AI") {
        AeginApp()
    }
}
