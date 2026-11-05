package com.reveila.tools.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordManagerTest {

    @Test
    fun testSaveRetrieveAndEncryptionAtRest() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, masterKeyOrPassphrase = "test-secret-passphrase")

        val cred = pm.saveCredential(
            domain = "github.com",
            username = "alice@example.com",
            password = "SuperSecretPassword123!",
            loginUrl = "https://github.com/login"
        )

        assertNotNull(cred)
        assertEquals("github.com", cred.domain)
        assertEquals("alice@example.com", cred.username)
        // Verify ciphertext does NOT leak plaintext password
        assertFalse(cred.encryptedPasswordHex.contains("SuperSecretPassword123!"))
        assertTrue(cred.encryptedPasswordHex.isNotBlank())
        assertTrue(cred.saltHex.isNotBlank())

        // Retrieve and decrypt
        val decrypted = pm.getCredential("github.com")
        assertNotNull(decrypted)
        assertEquals("SuperSecretPassword123!", decrypted.password)
        assertEquals("alice@example.com", decrypted.username)
        assertEquals("https://github.com/login", decrypted.loginUrl)
    }

    @Test
    fun testUpdatePasswordViaFunctionCall() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, masterKeyOrPassphrase = "master-key-xyz")

        pm.saveCredential(
            domain = "portal.enterprise.com",
            username = "admin",
            password = "InitialPassword_1"
        )

        val initial = pm.getCredential("portal.enterprise.com")
        assertNotNull(initial)
        assertEquals("InitialPassword_1", initial.password)

        // Update password
        val updated = pm.updatePassword(
            domain = "portal.enterprise.com",
            newPassword = "UpdatedPassword_999!"
        )
        assertNotNull(updated)

        val retrievedUpdated = pm.getCredential("portal.enterprise.com")
        assertNotNull(retrievedUpdated)
        assertEquals("UpdatedPassword_999!", retrievedUpdated.password)
        assertEquals("admin", retrievedUpdated.username)
    }

    @Test
    fun testDomainHierarchyMatching() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, masterKeyOrPassphrase = "master-key-xyz")

        pm.saveCredential(
            domain = "reveila.com",
            username = "developer@reveila.com",
            password = "DevPass_2026!"
        )

        // Exact match
        val exact = pm.findCredentialForUrl("https://reveila.com/account")
        assertNotNull(exact)
        assertEquals("developer@reveila.com", exact.username)

        // Subdomain fallback (auth.reveila.com -> reveila.com)
        val subdomainMatch = pm.findCredentialForUrl("https://auth.reveila.com/oauth/authorize?client_id=123")
        assertNotNull(subdomainMatch)
        assertEquals("developer@reveila.com", subdomainMatch.username)
        assertEquals("DevPass_2026!", subdomainMatch.password)

        // Unknown domain
        val unknown = pm.findCredentialForUrl("https://other-site.org/login")
        assertNull(unknown)
    }

    @Test
    fun testSafeListingWithoutPasswordLeakage() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, masterKeyOrPassphrase = "key")

        pm.saveCredential("site1.com", "user1", "pass1")
        pm.saveCredential("site2.com", "user2", "pass2")

        val summaries = pm.listCredentials()
        assertEquals(2, summaries.size)

        for (s in summaries) {
            assertTrue(s.domain.isNotBlank())
            assertTrue(s.username.isNotBlank())
        }

        // Summary class does not even have a password field
    }

    @Test
    fun testDeleteCredential() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, masterKeyOrPassphrase = "key")

        pm.saveCredential("temp.com", "tempuser", "temppass")
        assertTrue(pm.hasCredential("temp.com"))

        val deleted = pm.deleteCredential("temp.com")
        assertTrue(deleted)
        assertFalse(pm.hasCredential("temp.com"))
        assertNull(pm.getCredential("temp.com"))
    }

    @Test
    fun testVirtualBrowserAutofillAndSecretBlinding() {
        val pm = PasswordManager(InMemoryCredentialStore(), "browser-key")
        pm.saveCredential(
            domain = "portal.reveila.com",
            username = "agent_operator@reveila.com",
            password = "TopSecretOperatorPassword!#$"
        )

        val browser = VirtualBrowser()
        browser.passwordManager = pm

        // Simulate navigating to login page
        val loginHtml = """
            <html>
            <head><title>Reveila Enterprise Sign-In</title></head>
            <body>
                <form id="loginForm" action="/login/submit" method="POST">
                    <input type="text" name="username" placeholder="Username" />
                    <input type="password" name="password" placeholder="Password" />
                    <button type="submit">Sign In</button>
                </form>
            </body>
            </html>
        """.trimIndent()

        val mockPage = HtmlParser.parse("https://portal.reveila.com/login", 200, loginHtml, emptyMap())
        browser.loadPageForTest(mockPage)

        // Perform auto-fill without auto-submitting
        val filledPage = browser.autofillLogin(autoSubmit = false)
        assertNotNull(filledPage)

        val userElem = filledPage.elements.find { it.name == "username" }
        assertNotNull(userElem)
        assertEquals("agent_operator@reveila.com", userElem.value)

        val passElem = filledPage.elements.find { it.name == "password" }
        assertNotNull(passElem)
        assertEquals("TopSecretOperatorPassword!#$", passElem.value)
        assertTrue(passElem.isSecret)

        // Verify Secret Blinding: getPageSnapshotForLlm() must NOT leak password!
        val snapshot = browser.getPageSnapshotForLlm()
        assertFalse(snapshot.contains("TopSecretOperatorPassword!#$"))
        assertTrue(snapshot.contains("[SECRET]"))
        assertTrue(snapshot.contains("agent_operator@reveila.com"))
    }

    @Test
    fun testPasswordManagerToolExecution() {
        val store = InMemoryCredentialStore()
        val pm = PasswordManager(store, "tool-key")
        val tool = PasswordManagerTool(pm)

        // 1. Save
        val saveResult = tool.execute(mapOf(
            "action" to "save",
            "domain" to "service.internal.net",
            "username" to "bot_worker",
            "password" to "BotWorkerSecret_2026",
            "login_url" to "https://service.internal.net/login"
        ))
        assertTrue(saveResult.contains("successfully saved"))

        // 2. List
        val listResult = tool.execute(mapOf("action" to "list"))
        assertTrue(listResult.contains("service.internal.net"))
        assertTrue(listResult.contains("bot_worker"))
        assertFalse(listResult.contains("BotWorkerSecret_2026")) // Zero password leak!

        // 3. Get
        val getResult = tool.execute(mapOf(
            "action" to "get",
            "domain" to "service.internal.net"
        ))
        assertTrue(getResult.contains("bot_worker"))
        assertTrue(getResult.contains("[PROTECTED_IN_VAULT]"))
        assertFalse(getResult.contains("BotWorkerSecret_2026")) // Masked!

        // 4. Update
        val updateResult = tool.execute(mapOf(
            "action" to "update",
            "domain" to "service.internal.net",
            "password" to "NewBotSecret_777"
        ))
        assertTrue(updateResult.contains("successfully updated"))

        // Verify update in manager
        val cred = pm.getCredential("service.internal.net")
        assertNotNull(cred)
        assertEquals("NewBotSecret_777", cred.password)

        // 5. Delete
        val delResult = tool.execute(mapOf(
            "action" to "delete",
            "domain" to "service.internal.net"
        ))
        assertTrue(delResult.contains("successfully deleted"))
        assertNull(pm.getCredential("service.internal.net"))
    }
}
