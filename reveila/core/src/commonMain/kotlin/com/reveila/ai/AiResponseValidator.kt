package com.reveila.ai

import com.reveila.crypto.PlatformCryptographer

open class AiResponseValidator {

    private val stateHistory: MutableSet<String> = mutableSetOf()
    private val platformCrypto = PlatformCryptographer()

    open fun getMessage(agentResponse: String?): String? {
        if (agentResponse == null) return null

        val stateHash = calculateHash(agentResponse)
        if (stateHistory.contains(stateHash)) {
            return null // AI is repeating itself
        }

        stateHistory.add(stateHash)
        return agentResponse
    }

    private fun calculateHash(input: String): String {
        val hash = platformCrypto.sha256(input.encodeToByteArray())
        return platformCrypto.base64Encode(hash)
    }
}
