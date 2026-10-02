package com.reveila.ai

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import java.util.HashSet

open class AiResponseValidator {

    private val stateHistory: MutableSet<String> = HashSet()
    private val digest: MessageDigest

    init {
        try {
            this.digest = MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Critical Error: SHA-256 not found for state hashing.")
        }
    }

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
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }
}
