package com.reveila.tools.browser

import com.reveila.crypto.DefaultCryptographer
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.Uuid
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Sovereign Password Manager for autonomous Virtual Browser web interaction.
 * Provides zero-knowledge cryptographic encryption at rest (AES-256-GCM),
 * domain hierarchy matching, safe credential retrieval, and LLM secret blinding.
 */
open class PasswordManager(
    val credentialStore: CredentialStore = InMemoryCredentialStore(),
    var masterKeyOrPassphrase: String = DEFAULT_MASTER_KEY
) : SystemComponent() {

    companion object {
        const val DEFAULT_MASTER_KEY = "reveila-sovereign-vault-key"
        const val VAULT_FILENAME = "web_credentials.vault"

        /**
         * Creates a PasswordManager backed by the default sovereign system vault file.
         */
        fun createDefault(systemHomePath: String? = null, masterPassphrase: String = DEFAULT_MASTER_KEY): PasswordManager {
            val fs = PlatformFileSystem()
            val base = systemHomePath ?: fs.getDefaultSystemHome()
            val vaultPath = fs.resolve(fs.resolve(base, "data"), "vault")
            val fullFilePath = fs.resolve(vaultPath, VAULT_FILENAME)
            val store = EncryptedJsonCredentialStore(fullFilePath, fs)
            return PasswordManager(store, masterPassphrase)
        }

        fun normalizeDomain(domainOrUrl: String): String {
            return CookieJar.extractDomain(domainOrUrl).trim().lowercase()
        }
    }

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("[PasswordManager] Started sovereign password manager.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        logger.info("[PasswordManager] Stopped sovereign password manager.")
    }

    /**
     * Stores or replaces credentials for a given website domain.
     * Passwords are immediately encrypted using AES-256-GCM before reaching storage.
     */
    fun saveCredential(
        domain: String,
        username: String,
        password: String,
        loginUrl: String? = null,
        notes: String? = null,
        selectors: CredentialFormSelectors? = null
    ): WebCredential {
        val normDomain = normalizeDomain(domain)
        if (normDomain.isBlank()) {
            throw IllegalArgumentException("Domain cannot be blank.")
        }
        if (username.isBlank()) {
            throw IllegalArgumentException("Username cannot be blank.")
        }

        val salt = Random.nextBytes(16)
        val saltHex = DefaultCryptographer.bytesToHex(salt)
        val crypto = DefaultCryptographer(masterKeyOrPassphrase, salt)
        val encryptedBytes = crypto.encrypt(password.encodeToByteArray())
        val encryptedHex = DefaultCryptographer.bytesToHex(encryptedBytes)

        val existing = credentialStore.get(normDomain)
        val now = Clock.System.now().toEpochMilliseconds()

        val credential = WebCredential(
            id = existing?.id ?: Uuid.randomUuid(),
            domain = normDomain,
            username = username,
            encryptedPasswordHex = encryptedHex,
            saltHex = saltHex,
            loginUrl = loginUrl ?: existing?.loginUrl,
            notes = notes ?: existing?.notes,
            selectors = selectors ?: existing?.selectors,
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
            lastUsedAtEpochMs = existing?.lastUsedAtEpochMs
        )

        credentialStore.save(credential)
        logger.info("[PasswordManager] Saved encrypted credential for domain: $normDomain (user: $username)")
        return credential
    }

    /**
     * Updates the password and optionally username for an existing website domain.
     */
    fun updatePassword(
        domain: String,
        newPassword: String,
        newUsername: String? = null
    ): WebCredential {
        val normDomain = normalizeDomain(domain)
        val existing = credentialStore.get(normDomain)
            ?: throw IllegalArgumentException("No existing credential found for domain '$normDomain'. Use saveCredential instead.")

        val targetUsername = newUsername ?: existing.username
        val salt = Random.nextBytes(16)
        val saltHex = DefaultCryptographer.bytesToHex(salt)
        val crypto = DefaultCryptographer(masterKeyOrPassphrase, salt)
        val encryptedBytes = crypto.encrypt(newPassword.encodeToByteArray())
        val encryptedHex = DefaultCryptographer.bytesToHex(encryptedBytes)

        val now = Clock.System.now().toEpochMilliseconds()

        val updated = WebCredential(
            id = existing.id,
            domain = normDomain,
            username = targetUsername,
            encryptedPasswordHex = encryptedHex,
            saltHex = saltHex,
            loginUrl = existing.loginUrl,
            notes = existing.notes,
            selectors = existing.selectors,
            createdAtEpochMs = existing.createdAtEpochMs,
            updatedAtEpochMs = now,
            lastUsedAtEpochMs = existing.lastUsedAtEpochMs
        )

        credentialStore.save(updated)
        logger.info("[PasswordManager] Updated encrypted credential for domain: $normDomain")
        return updated
    }

    /**
     * Retrieves and decrypts the credential for a specific domain.
     */
    fun getCredential(domain: String): DecryptedWebCredential? {
        val normDomain = normalizeDomain(domain)
        val entity = credentialStore.get(normDomain) ?: return null

        return decryptEntity(entity)
    }

    /**
     * Finds credentials matching a target URL with domain hierarchy fallback.
     * E.g. for "https://auth.example.com/signin", it checks:
     * 1. Exact host "auth.example.com"
     * 2. Apex domain "example.com"
     */
    fun findCredentialForUrl(url: String): DecryptedWebCredential? {
        val domain = normalizeDomain(url)
        val exactMatch = getCredential(domain)
        if (exactMatch != null) return exactMatch

        // Try parent domain hierarchy (e.g. sub.domain.com -> domain.com)
        val parts = domain.split(".")
        if (parts.size > 2) {
            val apexDomain = parts.takeLast(2).joinToString(".")
            val apexMatch = getCredential(apexDomain)
            if (apexMatch != null) return apexMatch
        }

        return null
    }

    /**
     * Lists all stored credentials as safe summaries WITHOUT exposing passwords.
     */
    fun listCredentials(): List<CredentialSummary> {
        return credentialStore.getAll().map {
            CredentialSummary(
                id = it.id,
                domain = it.domain,
                username = it.username,
                loginUrl = it.loginUrl,
                notes = it.notes,
                updatedAtEpochMs = it.updatedAtEpochMs,
                lastUsedAtEpochMs = it.lastUsedAtEpochMs
            )
        }
    }

    /**
     * Deletes a credential for a given domain.
     */
    fun deleteCredential(domain: String): Boolean {
        val normDomain = normalizeDomain(domain)
        val deleted = credentialStore.delete(normDomain)
        if (deleted) {
            logger.info("[PasswordManager] Deleted credential for domain: $normDomain")
        }
        return deleted
    }

    fun hasCredential(domain: String): Boolean {
        return credentialStore.exists(normalizeDomain(domain))
    }

    private fun decryptEntity(entity: WebCredential): DecryptedWebCredential {
        val salt = DefaultCryptographer.hexToBytes(entity.saltHex)
        val ciphertext = DefaultCryptographer.hexToBytes(entity.encryptedPasswordHex)

        val crypto = DefaultCryptographer(masterKeyOrPassphrase, salt)
        val decryptedBytes = crypto.decrypt(ciphertext)
        val plaintextPassword = decryptedBytes.decodeToString()

        entity.lastUsedAtEpochMs = Clock.System.now().toEpochMilliseconds()
        credentialStore.save(entity)

        return DecryptedWebCredential(
            id = entity.id,
            domain = entity.domain,
            username = entity.username,
            password = plaintextPassword,
            loginUrl = entity.loginUrl,
            notes = entity.notes,
            selectors = entity.selectors,
            updatedAtEpochMs = entity.updatedAtEpochMs,
            lastUsedAtEpochMs = entity.lastUsedAtEpochMs
        )
    }
}
