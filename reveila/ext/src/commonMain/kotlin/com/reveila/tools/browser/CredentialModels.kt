package com.reveila.tools.browser

import com.reveila.util.Uuid
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Custom form element selectors for non-standard web login forms.
 */
@Serializable
data class CredentialFormSelectors(
    val usernameSelector: String? = null,
    val passwordSelector: String? = null,
    val submitSelector: String? = null
)

/**
 * Encrypted web credential entity persisted at rest.
 * Passwords and sensitive tokens are encrypted using AES-256-GCM authenticated cipher.
 */
@Serializable
data class WebCredential(
    val id: String = Uuid.randomUuid(),
    val domain: String,
    val username: String,
    val encryptedPasswordHex: String,
    val saltHex: String,
    val loginUrl: String? = null,
    val notes: String? = null,
    val selectors: CredentialFormSelectors? = null,
    val createdAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    var updatedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    var lastUsedAtEpochMs: Long? = null
)

/**
 * Transient in-memory decrypted credential for direct browser injection.
 * MUST NOT be leaked into LLM prompts, context windows, or persistent unencrypted logs.
 */
data class DecryptedWebCredential(
    val id: String,
    val domain: String,
    val username: String,
    val password: String,
    val loginUrl: String? = null,
    val notes: String? = null,
    val selectors: CredentialFormSelectors? = null,
    val updatedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long?
)

/**
 * Safe metadata summary of a managed credential.
 * Safe to be viewed by the user or surfaced to the LLM agent without leaking secrets.
 */
@Serializable
data class CredentialSummary(
    val id: String,
    val domain: String,
    val username: String,
    val loginUrl: String? = null,
    val notes: String? = null,
    val updatedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long?
)

/**
 * Container model for serializing the credential vault to encrypted/structured storage.
 */
@Serializable
data class CredentialVault(
    val version: Int = 1,
    val credentials: MutableList<WebCredential> = mutableListOf()
)
