package com.reveila.tools.secrets

import com.reveila.ai.ReveilaTool
import com.reveila.safety.SecretManager
import com.reveila.tools.browser.PasswordManager
import com.reveila.tools.browser.VirtualBrowser
import com.reveila.util.json.JsonUtil

/**
 * Encrypted Secret & Tool Authorization Manager Tool for Reveila agents.
 * Exposes safe metadata and blind secret/credential injection without ever leaking
 * plaintext secret values into LLM prompts or chat transcripts.
 */
class SecretManagerTool(
    private val secretManagerProvider: () -> SecretManager?,
    private val passwordManagerProvider: () -> PasswordManager? = { null },
    private val browserProvider: () -> VirtualBrowser? = { null }
) : ReveilaTool {

    override fun getName(): String = "secret_manager"

    override fun getDescription(): String = """
        Sovereign encrypted Secret Vault and Tool Permission Manager (never exposes plaintext secret values to AI).
        Actions:
        - list: { "action": "list" } (lists stored secret keys & credential metadata without secret values)
        - has: { "action": "has", "key": "GITHUB_TOKEN", "domain": "optional-domain.com" } (checks if secret exists)
        - store: { "action": "store", "key": "...", "value": "...", "category": "API_KEY|LOGIN|TOKEN", "domain": "...", "username": "..." }
        - delete: { "action": "delete", "key": "..." }
        - list_permissions: { "action": "list_permissions" } (lists pre-authorized tool permissions)
        - inject_login: { "action": "inject_login", "domain": "example.com", "submit": true } (autofills browser login without exposing password)
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase()?.trim() ?: "list"
        val sm = secretManagerProvider()
            ?: return "Error: SecretManager is not available in runtime context."
        val pm = passwordManagerProvider()

        return try {
            when (action) {
                "list", "all" -> {
                    val secrets = sm.listSecretMetadata()
                    val webCreds = pm?.listCredentials().orEmpty().map {
                        mapOf(
                            "key" to "web:${it.domain}",
                            "category" to "WEBSITE_LOGIN",
                            "domain" to it.domain,
                            "username" to it.username,
                            "loginUrl" to (it.loginUrl ?: ""),
                            "updatedAt" to it.updatedAtEpochMs
                        )
                    }
                    JsonUtil.toJsonString(
                        mapOf(
                            "secrets" to secrets,
                            "websiteCredentials" to webCreds,
                            "toolPermissions" to sm.listToolPermissions()
                        )
                    )
                }

                "has", "check", "exists" -> {
                    val key = (args["key"] as? String)?.trim().orEmpty()
                    val domain = (args["domain"] as? String)?.trim().orEmpty()
                    val hasKey = key.isNotEmpty() && sm.hasSecret(key)
                    val hasWebCred = domain.isNotEmpty() && (pm?.findCredentialForUrl(domain) != null || pm?.hasCredential(domain) == true)
                    val meta = if (hasKey) sm.getSecretMetadata(key) else null
                    JsonUtil.toJsonString(
                        mapOf(
                            "exists" to (hasKey || hasWebCred),
                            "hasSecretKey" to hasKey,
                            "hasWebsiteCredential" to hasWebCred,
                            "metadata" to meta,
                            "secretValue" to "[PROTECTED_IN_VAULT]"
                        )
                    )
                }

                "store", "save", "set" -> {
                    val key = (args["key"] as? String)?.trim()
                        ?: (args["domain"] as? String)?.trim()?.let { "web:$it" }
                        ?: return "Error: 'key' is required to store a secret."
                    val value = (args["value"] as? String) ?: (args["password"] as? String) ?: (args["secret"] as? String)
                        ?: return "Error: 'value' is required to store a secret."
                    val category = (args["category"] as? String)?.trim().orEmpty().ifBlank { "GENERAL" }
                    val domain = (args["domain"] as? String)?.trim().orEmpty()
                    val username = (args["username"] as? String)?.trim().orEmpty()
                    val description = (args["description"] as? String)?.trim().orEmpty()

                    sm.storeSecretWithMetadata(
                        key = key,
                        value = value,
                        category = category,
                        domain = domain,
                        username = username,
                        description = description
                    )

                    if (domain.isNotEmpty() && username.isNotEmpty() && pm != null) {
                        pm.saveCredential(
                            domain = domain,
                            username = username,
                            password = value,
                            loginUrl = args["loginUrl"] as? String
                        )
                    }

                    JsonUtil.toJsonString(
                        mapOf(
                            "stored" to true,
                            "key" to key,
                            "category" to category,
                            "domain" to domain,
                            "secretValue" to "[ENCRYPTED_IN_VAULT]"
                        )
                    )
                }

                "delete", "remove" -> {
                    val key = (args["key"] as? String)?.trim()
                        ?: return "Error: 'key' is required to delete a secret."
                    val deleted = sm.deleteSecret(key)
                    JsonUtil.toJsonString(mapOf("key" to key, "deleted" to deleted))
                }

                "list_permissions", "permissions" -> {
                    JsonUtil.toJsonString(
                        mapOf("permissions" to sm.listToolPermissions())
                    )
                }

                "inject_login", "autofill" -> {
                    val browser = browserProvider()
                        ?: return "Error: VirtualBrowser is not attached for inject_login."
                    val domain = (args["domain"] as? String)?.trim()
                        ?: (args["url"] as? String)?.trim()
                    val submit = (args["submit"] as? Boolean) ?: true
                    val formTarget = args["form"] as? String
                    browser.autofillLogin(domain, formTarget, submit)
                    "Injected stored credentials securely into browser for ${domain ?: "current page"}.\n\n" +
                        browser.getPageSnapshotForLlm()
                }

                else -> "Unknown secret_manager action '$action'. Available actions: list, has, store, delete, list_permissions, inject_login."
            }
        } catch (e: Exception) {
            "Secret Manager Error [$action]: ${e.message}"
        }
    }
}
