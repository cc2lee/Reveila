package com.reveila.aegin.ui.mascot

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

val LocalAeginMascotController = staticCompositionLocalOf { AeginMascotController() }

class AeginMascotController {
    private val _state = MutableStateFlow(AeginMascotState())
    val state: StateFlow<AeginMascotState> = _state.asStateFlow()

    fun onIdle() {
        _state.update {
            AeginMascotState(
                phase = AeginMascotPhase.IDLE,
                tokensPerSecond = 0f,
                statusLabel = "Ready"
            )
        }
    }

    fun onUserInputActive(isTypingOrSpeaking: Boolean) {
        if (!isTypingOrSpeaking && _state.value.phase == AeginMascotPhase.LISTENING_USER) {
            onIdle()
            return
        }
        if (isTypingOrSpeaking && !_state.value.isBusy) {
            _state.update {
                it.copy(
                    phase = AeginMascotPhase.LISTENING_USER,
                    statusLabel = "Listening..."
                )
            }
        }
    }

    fun onLocalSlmRouting() {
        _state.update {
            it.copy(
                phase = AeginMascotPhase.ROUTING_LOCAL,
                statusLabel = "Local triage..."
            )
        }
    }

    fun onCoordinatingWithModel(modelName: String = "AI Model") {
        _state.update {
            it.copy(
                phase = AeginMascotPhase.COORDINATING_CLOUD,
                statusLabel = "Coordinating with $modelName..."
            )
        }
    }

    fun onStreamingResponse(tokensPerSecond: Float = 0f) {
        _state.update {
            it.copy(
                phase = AeginMascotPhase.SPEAKING_RESPONSE,
                tokensPerSecond = tokensPerSecond,
                statusLabel = "Responding..."
            )
        }
    }

    fun onError(message: String = "Attention needed") {
        _state.update {
            AeginMascotState(
                phase = AeginMascotPhase.ERROR_ALERT,
                tokensPerSecond = 0f,
                statusLabel = message
            )
        }
    }
}
