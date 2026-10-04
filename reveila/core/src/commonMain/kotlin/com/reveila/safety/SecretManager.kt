package com.reveila.safety

import com.reveila.crypto.PlatformCryptographer
import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import com.reveila.system.platform.PlatformSystem

import com.reveila.util.Uuid

/**
 * Phase 3: JIT Token Management.
 * Generates short-lived credentials for agentic execution.
 * Handles encrypted storage of sovereign secrets.
 */
open class SecretManager : SystemComponent() {

    private val jitTokens: MutableMap<String, String> = mutableMapOf()
    private val platformCrypto = PlatformCryptographer()

    companion object {
        private const val ENC_PREFIX = "ENC:"
    }

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("SecretManager (Sovereign Vault) starting...")
        ensureSecretStoreExists()
    }

    private fun ensureSecretStoreExists() {
        try {
            context?.getProxy("DataService")?.invoke("getRepository", arrayOf("reveila_secrets"))
            logger.info("Verified Sovereign Secret Store: reveila_secrets")
        } catch (e: Exception) {
            logger.severe("Failed to verify Sovereign Secret Store: ${e.message}")
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    @Suppress("UNCHECKED_CAST")
    open fun getSecret(secretKey: String): String? {
        val token = jitTokens[secretKey]
        if (token != null) return token

        val envValue = PlatformSystem.getEnv(secretKey)
        if (envValue != null) return envValue

        return try {
            val id = mapOf("id" to mapOf<String, Any>("value" to secretKey))
            val repo = context?.getProxy("DataService")
                ?.invoke("getRepository", arrayOf("reveila_secrets")) as? Repository<Entity, Map<String, Map<String, Any>>>

            val opt = repo?.fetchById(id)
            val entity = if (opt != null && opt.isPresent) opt.get() else null
            val value = entity?.attributes?.get("value") as? String
            decryptIfNeeded(value)
        } catch (e: Exception) {
            logger.warning("Failed to retrieve secret from Sovereign Store: $secretKey. Error: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun storeSecret(key: String, value: String?) {
        try {
            val encryptedValue = encrypt(value)
            val idMap = mapOf("id" to mapOf<String, Any>("value" to key))
            val attributes = mapOf<String, Any>("id" to key, "value" to (encryptedValue ?: ""))
            val entity = Entity("reveila_secrets", idMap, attributes)

            val repo = context?.getProxy("DataService")
                ?.invoke("getRepository", arrayOf("reveila_secrets")) as? Repository<Entity, Map<String, Map<String, Any>>>

            repo?.store(entity)
            logger.info("Successfully stored encrypted secret: $key")
        } catch (e: Exception) {
            logger.severe("Failed to store secret '$key': ${e.message}")
        }
    }

    private fun encrypt(value: String?): String? {
        if (value == null) return null
        val crypto = context?.cryptographer ?: context?.platformAdapter?.getCryptographer() ?: return null
        val encrypted = crypto.encrypt(value.encodeToByteArray())
        return ENC_PREFIX + platformCrypto.base64Encode(encrypted)
    }

    private fun decryptIfNeeded(value: String?): String? {
        if (value == null || !value.startsWith(ENC_PREFIX)) {
            return value
        }

        return try {
            val base64Data = value.substring(ENC_PREFIX.length)
            val encryptedData = platformCrypto.base64Decode(base64Data)
            val crypto = context?.cryptographer ?: context?.platformAdapter?.getCryptographer()
                ?: throw IllegalStateException("Cryptographer not available")
            crypto.decrypt(encryptedData).decodeToString()
        } catch (e: Exception) {
            val errorMsg = "Failed to decrypt secret value: ${e.message}"
            logger.severe(errorMsg)
            throw RuntimeException(errorMsg, e)
        }
    }

    open fun generateJitToken(plugin: Plugin, scope: String): Map<String, String> {
        val token = "jit_" + Uuid.randomUuid().substring(0, 8)
        jitTokens[token] = "${plugin.traceId}:$scope"
        return mapOf("REVEILA_JIT_TOKEN" to token)
    }

    open fun validateToken(token: String): Boolean {
        return jitTokens.containsKey(token)
    }
}
