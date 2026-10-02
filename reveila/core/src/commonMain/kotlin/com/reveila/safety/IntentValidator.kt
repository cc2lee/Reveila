package com.reveila.safety

import com.reveila.error.SecurityException

/**
 * Validates that an agent's intent maps to a registered, metadata-defined plugin.
 */
interface IntentValidator {

    /**
     * Maps an intent to a plugin ID.
     * @throws SecurityException If the intent is unauthorized or unknown.
     */
    @Throws(SecurityException::class)
    fun validateIntent(intent: String)

    /**
     * Performs a safety audit on the tool arguments using a secondary guardrail model.
     * @return true if approved, false otherwise.
     */
    fun performSafetyAudit(pluginId: String, maskedArgs: String, systemContext: String): Boolean
}
