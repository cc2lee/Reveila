package com.reveila.aegin.ui.mascot

enum class AeginMascotPhase {
    IDLE,               // Calm breathing, slow tail sway, periodic blink
    LISTENING_USER,     // Attentive head tilt toward user input
    ROUTING_LOCAL,      // On-device llama.cpp SLM classification active
    COORDINATING_CLOUD, // Busy coordinating between user & cloud LLM
    SPEAKING_RESPONSE,  // Streaming response back to user
    ERROR_ALERT         // Warning pulse on fault/timeout
}

data class AeginMascotState(
    val phase: AeginMascotPhase = AeginMascotPhase.IDLE,
    val tokensPerSecond: Float = 0f,
    val statusLabel: String = "Ready"
) {
    val isBusy: Boolean
        get() = phase == AeginMascotPhase.ROUTING_LOCAL ||
            phase == AeginMascotPhase.COORDINATING_CLOUD ||
            phase == AeginMascotPhase.SPEAKING_RESPONSE
}
