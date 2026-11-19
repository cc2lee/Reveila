package com.reveila.safety

import com.reveila.crypto.DefaultCryptographer
import com.reveila.crypto.PlatformCryptographer
import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.random.Random

/**
 * Phase 3: JIT Token Management & Sovereign Encrypted Secret Vault.
 * Handles encrypted storage of sovereign secrets, blind secret references,
 * and persistent scoped tool authorization grants for autonomous agents.
 */
open class SecretManager : SystemComponent() {

    private val jitTokens: MutableMap<String, String> = mutableMapOf()
    private val inMemoryPermissionGrants: MutableMap<String, Long> = mutableMapOf()
    private val platformCrypto = PlatformCryptographer()
    private val vaultLock = PlatformLock()

    companion object {
        private const val ENC_PREFIX = "ENC:"
        private const val ENC2_PREFIX = "ENC2:"
        private const val TOOL_AUTH_PREFIX = "tool_auth::"
        private const val FALLBACK_VAULT_PASSPHRASE = "reveila-sovereign-secret-manager"
    }

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("SecretManager (Sovereign Vault) starting...")
        ensureSecretStoreExists()
    }

    private fun resolveSecretsRepo(): Repository<Entity, Map<String, Map<String, Any>>>? {
        @Suppress("UNCHECKED_CAST")
        return try {
            context?.getProxy("DataService")
                ?.invoke("getRepository", arrayOf("reveila_secrets")) as? Repository<Entity, Map<String, Map<String, Any>>>
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureSecretStoreExists() {
        try {
            resolveSecretsRepo()
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
            val repo = resolveSecretsRepo()
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
        storeSecretWithMetadata(key = key, value = value)
    }

    @Suppress("UNCHECKED_CAST")
    open fun storeSecretWithMetadata(
        key: String,
        value: String?,
        category: String = "GENERAL",
        domain: String = "",
        username: String = "",
        description: String = ""
    ) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        try {
            val encryptedValue = encrypt(value)
            val idMap = mapOf("id" to mapOf<String, Any>("value" to cleanKey))
            val nowMs = PlatformSystem.currentTimeMillis()
            val attributes = mutableMapOf<String, Any>(
                "id" to cleanKey,
                "value" to (encryptedValue ?: ""),
                "category" to category.trim().ifBlank { "GENERAL" },
                "domain" to domain.trim().lowercase(),
                "username" to username.trim(),
                "description" to description.trim(),
                "updatedAt" to nowMs
            )
            val entity = Entity("reveila_secrets", idMap, attributes)
            vaultLock.withLock {
                resolveSecretsRepo()?.store(entity)
            }
            logger.info("Successfully stored encrypted secret: $cleanKey (category=${attributes["category"]})")
        } catch (e: Exception) {
            logger.severe("Failed to store secret '$cleanKey': ${e.message}")
        }
    }

    open fun hasSecret(key: String): Boolean {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return false
        if (jitTokens.containsKey(cleanKey)) return true
        if (PlatformSystem.getEnv(cleanKey) != null) return true
        return try {
            val id = mapOf("id" to mapOf<String, Any>("value" to cleanKey))
            val opt = resolveSecretsRepo()?.fetchById(id)
            opt != null && opt.isPresent
        } catch (_: Exception) {
            false
        }
    }

    open fun deleteSecret(key: String): Boolean {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return false
        return try {
            val id = mapOf("id" to mapOf<String, Any>("value" to cleanKey))
            vaultLock.withLock {
                val repo = resolveSecretsRepo() ?: return@withLock false
                val exists = repo.fetchById(id).isPresent
                if (exists) {
                    repo.disposeById(id)
                }
                exists
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete secret '$cleanKey': ${e.message}")
            false
        }
    }

    open fun getSecretMetadata(key: String): Map<String, Any?>? {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return null
        return try {
            val id = mapOf("id" to mapOf<String, Any>("value" to cleanKey))
            val opt = resolveSecretsRepo()?.fetchById(id)
            if (opt == null || !opt.isPresent) return null
            val attrs = opt.get().attributes
            linkedMapOf(
                "key" to cleanKey,
                "category" to (attrs["category"]?.toString() ?: "GENERAL"),
                "domain" to (attrs["domain"]?.toString() ?: ""),
                "username" to (attrs["username"]?.toString() ?: ""),
                "description" to (attrs["description"]?.toString() ?: ""),
                "updatedAt" to ((attrs["updatedAt"] as? Number)?.toLong() ?: 0L),
                "hasValue" to !(attrs["value"] as? String).isNullOrBlank()
            )
        } catch (_: Exception) {
            null
        }
    }

    open fun listSecretMetadata(): List<Map<String, Any?>> {
        return try {
            resolveSecretsRepo()?.fetchAll().orEmpty()
                .mapNotNull { entity ->
                    val attrs = entity.attributes
                    val id = attrs["id"]?.toString().orEmpty()
                    if (id.isBlank() || id.startsWith(TOOL_AUTH_PREFIX)) return@mapNotNull null
                    linkedMapOf<String, Any?>(
                        "key" to id,
                        "category" to (attrs["category"]?.toString() ?: "GENERAL"),
                        "domain" to (attrs["domain"]?.toString() ?: ""),
                        "username" to (attrs["username"]?.toString() ?: ""),
                        "description" to (attrs["description"]?.toString() ?: ""),
                        "updatedAt" to ((attrs["updatedAt"] as? Number)?.toLong() ?: 0L),
                        "hasValue" to !(attrs["value"] as? String).isNullOrBlank()
                    )
                }
                .sortedBy { it["key"]?.toString().orEmpty().lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves secret values internally for tool execution (blind injection).
     * Accepts `"SEC:<key>"`, `"secret:<key>"`, or bare `<key>`.
     */
    open fun resolveSecretReference(reference: String): String? {
        val trimmed = reference.trim()
        val key = when {
            trimmed.startsWith("SEC:", ignoreCase = true) -> trimmed.substring(4).trim()
            trimmed.startsWith("secret:", ignoreCase = true) -> trimmed.substring(7).trim()
            else -> trimmed
        }
        if (key.isEmpty()) return null
        return getSecret(key)
    }

    open fun grantToolPermission(
        toolName: String,
        scope: String = "*",
        persistent: Boolean = true,
        grantedBy: String = "local-user",
        expiresAtMs: Long = 0L
    ) {
        val normTool = toolName.trim().lowercase()
        val normScope = scope.trim().lowercase().ifBlank { "*" }
        if (normTool.isEmpty()) return
        val grantKey = "$TOOL_AUTH_PREFIX$normTool::$normScope"

        vaultLock.withLock {
            inMemoryPermissionGrants["$normTool::$normScope"] = expiresAtMs
        }

        if (persistent) {
            val payload = JsonUtil.toJsonString(
                mapOf(
                    "tool" to normTool,
                    "scope" to normScope,
                    "grantedBy" to grantedBy.ifBlank { "local-user" },
                    "grantedAt" to PlatformSystem.currentTimeMillis(),
                    "expiresAt" to expiresAtMs
                )
            )
            storeSecretWithMetadata(
                key = grantKey,
                value = payload,
                category = "TOOL_PERMISSION",
                domain = normScope,
                username = grantedBy,
                description = "Tool authorization grant for $normTool ($normScope)"
            )
        }
    }

    open fun hasToolPermission(toolName: String, scope: String = "*"): Boolean {
        val normTool = toolName.trim().lowercase()
        val normScope = scope.trim().lowercase().ifBlank { "*" }
        if (normTool.isEmpty()) return false

        val nowMs = PlatformSystem.currentTimeMillis()
        val candidates = listOf("$normTool::$normScope", "$normTool::*")

        val memoryHit = vaultLock.withLock {
            candidates.any { k ->
                val exp = inMemoryPermissionGrants[k]
                exp != null && (exp == 0L || exp > nowMs)
            }
        }
        if (memoryHit) return true

        for (candidate in candidates) {
            val raw = getSecret("$TOOL_AUTH_PREFIX$candidate") ?: continue
            val valid = try {
                val map = JsonUtil.parseJsonStringToMap(raw)
                val exp = (map["expiresAt"] as? Number)?.toLong() ?: 0L
                exp == 0L || exp > nowMs
            } catch (_: Exception) {
                true
            }
            if (valid) {
                vaultLock.withLock {
                    inMemoryPermissionGrants[candidate] = 0L
                }
                return true
            }
        }
        return false
    }

    open fun revokeToolPermission(toolName: String, scope: String = "*"): Boolean {
        val normTool = toolName.trim().lowercase()
        val normScope = scope.trim().lowercase().ifBlank { "*" }
        if (normTool.isEmpty()) return false
        vaultLock.withLock {
            inMemoryPermissionGrants.remove("$normTool::$normScope")
        }
        return deleteSecret("$TOOL_AUTH_PREFIX$normTool::$normScope")
    }

    open fun listToolPermissions(): List<Map<String, Any?>> {
        val nowMs = PlatformSystem.currentTimeMillis()
        val out = mutableMapOf<String, Map<String, Any?>>()

        try {
            resolveSecretsRepo()?.fetchAll().orEmpty().forEach { entity ->
                val id = entity.attributes["id"]?.toString().orEmpty()
                if (!id.startsWith(TOOL_AUTH_PREFIX)) return@forEach
                val raw = decryptIfNeeded(entity.attributes["value"] as? String) ?: return@forEach
                try {
                    val parsed = JsonUtil.parseJsonStringToMap(raw)
                    val exp = (parsed["expiresAt"] as? Number)?.toLong() ?: 0L
                    if (exp == 0L || exp > nowMs) {
                        val tool = parsed["tool"]?.toString().orEmpty()
                        val scope = parsed["scope"]?.toString().orEmpty()
                        out["$tool::$scope"] = parsed
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        vaultLock.withLock {
            inMemoryPermissionGrants.forEach { (k, exp) ->
                if ((exp == 0L || exp > nowMs) && !out.containsKey(k)) {
                    val parts = k.split("::", limit = 2)
                    out[k] = mapOf(
                        "tool" to parts.getOrElse(0) { "" },
                        "scope" to parts.getOrElse(1) { "*" },
                        "grantedBy" to "session",
                        "expiresAt" to exp
                    )
                }
            }
        }
        return out.values.toList()
    }

    private fun encrypt(value: String?): String? {
        if (value == null) return null
        val crypto = context?.cryptographer ?: context?.platformAdapter?.getCryptographer()
        if (crypto != null) {
            val encrypted = crypto.encrypt(value.encodeToByteArray())
            return ENC_PREFIX + platformCrypto.base64Encode(encrypted)
        }
        val salt = Random.nextBytes(16)
        val fallbackCrypto = DefaultCryptographer(FALLBACK_VAULT_PASSPHRASE, salt)
        val encrypted = fallbackCrypto.encrypt(value.encodeToByteArray())
        return ENC2_PREFIX + DefaultCryptographer.bytesToHex(salt) + ":" + DefaultCryptographer.bytesToHex(encrypted)
    }

    private fun decryptIfNeeded(value: String?): String? {
        if (value == null) return null
        if (value.startsWith(ENC2_PREFIX)) {
            return try {
                val payload = value.substring(ENC2_PREFIX.length)
                val sep = payload.indexOf(':')
                if (sep <= 0) return value
                val salt = DefaultCryptographer.hexToBytes(payload.substring(0, sep))
                val cipher = DefaultCryptographer.hexToBytes(payload.substring(sep + 1))
                DefaultCryptographer(FALLBACK_VAULT_PASSPHRASE, salt).decrypt(cipher).decodeToString()
            } catch (e: Exception) {
                throw RuntimeException("Failed to decrypt secret value: ${e.message}", e)
            }
        }
        if (!value.startsWith(ENC_PREFIX)) {
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
        return mapOf(com.reveila.system.Constants.ENV_JIT_TOKEN to token)
    }

    open fun registerJitToken(token: String, metadata: String = "sandbox:callback") {
        val clean = normalizeToken(token)
        if (clean.isNotEmpty()) {
            jitTokens[clean] = metadata
        }
    }

    open fun validateToken(token: String?): Boolean {
        val clean = normalizeToken(token)
        return clean.isNotEmpty() && jitTokens.containsKey(clean)
    }

    open fun revokeToken(token: String?) {
        val clean = normalizeToken(token)
        if (clean.isNotEmpty()) {
            jitTokens.remove(clean)
        }
    }

    private fun normalizeToken(token: String?): String {
        if (token.isNullOrBlank()) return ""
        return token.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
    }
}
