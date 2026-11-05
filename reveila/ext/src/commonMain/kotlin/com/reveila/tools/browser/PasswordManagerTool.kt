package com.reveila.tools.browser

import com.reveila.ai.ReveilaTool

/**
 * ReveilaTool integration allowing LLM agents (and external function calls)
 * to store, update, list, and trigger browser autofill of website credentials
 * while enforcing strict secret blinding to prevent leaking passwords into LLM prompts.
 */
class PasswordManagerTool(
    val passwordManager: PasswordManager = PasswordManager(),
    val browser: VirtualBrowser? = null
) : ReveilaTool {

    override fun getName(): String = "password_manager"

    override fun getDescription(): String {
        return """
            Sovereign Password Manager for website credentials and sign-in automation.
            Commands:
            - save: { "action": "save", "domain": "example.com", "username": "user@example.com", "password": "...", "login_url": "https://example.com/login" }
            - update: { "action": "update", "domain": "example.com", "password": "newPassword", "username": "optionalNewUser" }
            - list: { "action": "list" } (returns managed domains and usernames without exposing passwords)
            - get: { "action": "get", "domain": "example.com" } (returns metadata and masked secret status)
            - delete: { "action": "delete", "domain": "example.com" }
            - autofill: { "action": "autofill", "domain": "example.com", "submit": true } (directly auto-fills and logs into browser without exposing password to LLM)
        """.trimIndent()
    }

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase()
            ?: (args["command"] as? String)?.lowercase()
            ?: "list"

        return try {
            when (action) {
                "save", "store", "create" -> {
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                        ?: return "Error: 'domain' is required to save credentials."
                    val username = (args["username"] as? String) ?: (args["user"] as? String) ?: (args["email"] as? String)
                        ?: return "Error: 'username' is required to save credentials."
                    val password = (args["password"] as? String) ?: (args["secret"] as? String)
                        ?: return "Error: 'password' is required to save credentials."
                    val loginUrl = (args["login_url"] as? String) ?: (args["loginUrl"] as? String)
                    val notes = args["notes"] as? String

                    passwordManager.saveCredential(
                        domain = domain,
                        username = username,
                        password = password,
                        loginUrl = loginUrl,
                        notes = notes
                    )

                    "Credential for '$domain' successfully saved in sovereign encrypted vault (username: '$username'). Secret is encrypted and protected."
                }

                "update", "change" -> {
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                        ?: return "Error: 'domain' is required to update credentials."
                    val password = (args["password"] as? String) ?: (args["new_password"] as? String)
                        ?: return "Error: 'password' is required to update credentials."
                    val username = (args["username"] as? String) ?: (args["user"] as? String)

                    passwordManager.updatePassword(
                        domain = domain,
                        newPassword = password,
                        newUsername = username
                    )

                    "Credential for '$domain' successfully updated and re-encrypted in sovereign vault."
                }

                "list", "all" -> {
                    val list = passwordManager.listCredentials()
                    if (list.isEmpty()) {
                        "No credentials currently stored in the password manager."
                    } else {
                        val sb = StringBuilder("=== STORED CREDENTIALS ===\n")
                        for (item in list) {
                            sb.appendLine("- Domain: ${item.domain} | Username: ${item.username} | Last Updated: ${item.updatedAtEpochMs}")
                            if (!item.loginUrl.isNullOrBlank()) {
                                sb.appendLine("  Login URL: ${item.loginUrl}")
                            }
                        }
                        sb.toString().trimEnd()
                    }
                }

                "get", "find" -> {
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                        ?: return "Error: 'domain' is required to get credential info."
                    val cred = passwordManager.getCredential(domain)
                        ?: return "No credential found for domain '$domain'."

                    "Credential found:\nDomain: ${cred.domain}\nUsername: ${cred.username}\nPassword: [PROTECTED_IN_VAULT]\nLogin URL: ${cred.loginUrl ?: "None"}\nUpdated: ${cred.updatedAtEpochMs}"
                }

                "delete", "remove" -> {
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                        ?: return "Error: 'domain' is required to delete credential."
                    val success = passwordManager.deleteCredential(domain)
                    if (success) {
                        "Credential for domain '$domain' successfully deleted from vault."
                    } else {
                        "No credential found for domain '$domain' to delete."
                    }
                }

                "autofill", "login", "signin" -> {
                    val targetBrowser = browser ?: return "Error: No VirtualBrowser attached to PasswordManagerTool for autofill action."
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                    val submit = (args["submit"] as? Boolean) ?: (args["auto_submit"] as? Boolean) ?: true
                    val formTarget = (args["form"] as? String) ?: (args["target"] as? String)

                    targetBrowser.autofillLogin(domain, formTarget, submit)
                    "Login credentials auto-filled and submitted securely for current page.\n\n" + targetBrowser.getPageSnapshotForLlm()
                }

                else -> "Unknown password manager action: '$action'. Available actions: save, update, list, get, delete, autofill."
            }
        } catch (e: Exception) {
            "Password Manager Error [$action]: ${e.message}"
        }
    }
}
