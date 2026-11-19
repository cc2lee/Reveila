package com.reveila.tools

import com.reveila.ai.AgentSession
import com.reveila.ai.ReveilaToolCoordinator
import com.reveila.crypto.Cryptographer
import com.reveila.error.SecurityException
import com.reveila.safety.SecretManager
import com.reveila.service.DataService
import com.reveila.system.BasePlatformAdapter
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.system.platform.PlatformSystem
import com.reveila.tools.browser.WebPage
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolCoordinatorAndSecretTest {

    private fun createCoordinatorFixture(): Triple<ReveilaToolCoordinator, SecretManager, String> {
        ToolActivityHub.resetForTest()
        val fs = PlatformFileSystem()
        val homeDir = fs.resolve("build", "test-tools-${Uuid.randomUuid().take(8)}")
        fs.createDirectories(homeDir)
        val configsDir = fs.resolve(homeDir, "configs")
        fs.createDirectories(configsDir)
        fs.writeText(fs.resolve(configsDir, "reveila.properties"), "system.name=ToolTest\n", append = false)

        val props = Properties(
            mapOf(
                "system.home" to homeDir,
                "data.dir" to fs.resolve(homeDir, "data")
            )
        )
        val adapter = BasePlatformAdapter(props)
        val dataService = DataService()
        val secretManager = SecretManager()

        val dsProxy = object : Proxy {
            override fun getName(): String = "DataService"
            override fun getRequiredRoles(): List<String> = emptyList()
            override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
                return when (methodName) {
                    "getRepository" -> dataService.getRepository(args?.firstOrNull()?.toString().orEmpty())
                    else -> null
                }
            }
            override fun getInstance(): Any = dataService
        }

        val ctx = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy = when (name) {
                "DataService" -> dsProxy
                else -> throw IllegalArgumentException("Unknown proxy: $name")
            }
            override val properties: Properties = props
            override val platformAdapter: PlatformAdapter = adapter
            override val cryptographer: Cryptographer? = adapter.getCryptographer()
        }

        dataService.setContext(ctx)
        dataService.start()
        secretManager.setContext(ctx)
        secretManager.start()

        val coordinator = ReveilaToolCoordinator(
            systemHome = homeDir,
            secretManagerProvider = { secretManager },
            downloadManagerProvider = { null }
        )
        return Triple(coordinator, secretManager, homeDir)
    }

    @Test
    fun testSecretManagerBlindedCrudAndPermissions() {
        val (coordinator, sm, homeDir) = createCoordinatorFixture()
        try {
            val storeOut = coordinator.secretManagerTool.execute(
                mapOf(
                    "action" to "store",
                    "key" to "DEPLOY_TOKEN",
                    "value" to "super-secret-token-xyz",
                    "category" to "API_KEY",
                    "description" to "CI Token"
                )
            )
            assertFalse(storeOut.contains("super-secret-token-xyz"), "LLM response must never leak raw secret")
            assertEquals("super-secret-token-xyz", sm.getSecret("DEPLOY_TOKEN"))
            assertEquals("super-secret-token-xyz", sm.resolveSecretReference("SEC:DEPLOY_TOKEN"))

            val listOut = coordinator.secretManagerTool.execute(mapOf("action" to "list"))
            assertTrue(listOut.contains("DEPLOY_TOKEN"))
            assertFalse(listOut.contains("super-secret-token-xyz"))

            val hasOut = coordinator.secretManagerTool.execute(mapOf("action" to "has", "key" to "DEPLOY_TOKEN"))
            assertTrue(hasOut.contains("\"exists\":true"))
            assertFalse(hasOut.contains("super-secret-token-xyz"))

            sm.grantToolPermission("terminal", "execute_script", persistent = true, grantedBy = "alice")
            assertTrue(sm.hasToolPermission("terminal", "execute_script"))
            assertTrue(sm.revokeToolPermission("terminal", "execute_script"))
            assertFalse(sm.hasToolPermission("terminal", "execute_script"))
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }

    @Test
    fun testTerminalToolBlindSecretInjectionAndAuthorizationFlow() {
        val (coordinator, sm, homeDir) = createCoordinatorFixture()
        try {
            sm.storeSecret("MY_APP_SECRET", "alpha-bravo-99")
            val session = AgentSession("s1", "trace-term-1", 10)
            val isWin = PlatformSystem.getOsInfo().name.lowercase().contains("win")
            val script = if (isWin) {
                "Write-Output \"ENV=\$env:MY_APP_SECRET\""
            } else {
                "echo \"ENV=\$MY_APP_SECRET\""
            }
            val call = mapOf(
                "name" to "terminal",
                "arguments" to mapOf(
                    "action" to "execute_script",
                    "script" to script,
                    "secretEnv" to mapOf("MY_APP_SECRET" to "SEC:MY_APP_SECRET")
                )
            )

            // 1. First execution without grant must pause & prompt user via ToolActivityHub
            val ex = assertFailsWith<SecurityException> {
                coordinator.authorizeAndExecute(call, session, "alice", null)
            }
            assertTrue(ex.message!!.startsWith("APPROVAL_REQUIRED|"))
            val pending = ToolActivityHub.getSnapshot().pendingAuthorization
            assertNotNull(pending)
            assertTrue(ToolActivityHub.getSnapshot().waitingForUserInput)
            assertEquals("terminal", pending.toolName)

            // 2. Approve with APPROVE_ALWAYS (and test script execution)
            val resolution = coordinator.resolvePendingAuthorization(
                requestId = pending.requestId,
                decision = "APPROVE_ALWAYS",
                approverId = "alice"
            )
            assertTrue(resolution.found && resolution.approved)
            assertTrue(resolution.toolOutput.contains("ENV=alpha-bravo-99"))
            assertTrue(sm.hasToolPermission("terminal", "execute_script"))
            assertFalse(ToolActivityHub.getSnapshot().waitingForUserInput)
            assertEquals("COMPLETED", ToolActivityHub.getSnapshot().terminal.status)

            // 3. Subsequent execution uses saved permission autonomously
            val directOut = coordinator.authorizeAndExecute(call, session, "alice", null)
            assertTrue(directOut.contains("COMPLETED"))
            assertTrue(directOut.contains("ENV=alpha-bravo-99"))
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }

    @Test
    fun testBrowserTakeoverYieldAndReturnControlWithCookies() {
        val (coordinator, sm, homeDir) = createCoordinatorFixture()
        try {
            sm.grantToolPermission("virtual_browser", "*", persistent = true, grantedBy = "alice")
            coordinator.virtualBrowser.loadPageForTest(
                WebPage(
                    url = "https://portal.example.com/login",
                    statusCode = 200,
                    title = "Portal Sign In",
                    contentText = "Please enter credentials",
                    elements = emptyList(),
                    forms = emptyList(),
                    rawHtml = "<html><body><h1>Sign In</h1></body></html>"
                )
            )

            // Missing credentials triggers credential-input authorization card
            val session = AgentSession("s2", "trace-web-1", 10)
            assertFailsWith<SecurityException> {
                coordinator.authorizeAndExecute(
                    toolCallObj = mapOf(
                        "name" to "virtual_browser",
                        "arguments" to mapOf("action" to "login", "domain" to "portal.example.com", "submit" to false)
                    ),
                    session = session,
                    actorId = "alice",
                    preferredProviderName = null
                )
            }
            val pendingCred = ToolActivityHub.getSnapshot().pendingAuthorization
            assertNotNull(pendingCred)
            assertTrue(pendingCred.requiresCredentials)
            assertEquals("WEBSITE_LOGIN", pendingCred.credentialType)
            assertEquals("portal.example.com", pendingCred.credentialDomain)

            // User takes over browser window manually
            coordinator.handleBrowserUserAction("takeover")
            assertEquals("USER", ToolActivityHub.getSnapshot().browser.controlOwner)

            // While user is controlling browser, autonomous navigation yields
            val yieldMsg = coordinator.authorizeAndExecute(
                toolCallObj = mapOf(
                    "name" to "virtual_browser",
                    "arguments" to mapOf("action" to "navigate", "url" to "https://portal.example.com/dashboard")
                ),
                session = session,
                actorId = "alice",
                preferredProviderName = null
            )
            assertTrue(yieldMsg.contains("user is currently in manual control", ignoreCase = true))

            // User clicks Return Control with updated WebView URL, cookies, and DOM HTML
            val snapJson = coordinator.handleBrowserUserAction(
                action = "return_control",
                url = "https://portal.example.com/dashboard",
                cookieHeader = "sid=user-verified-cookie-123",
                rawHtml = "<html><head><title>Dashboard</title></head><body><p>Welcome Alice</p></body></html>"
            )
            val snapMap = JsonUtil.parseJsonStringToMap(snapJson)
            @Suppress("UNCHECKED_CAST")
            val browserMap = snapMap["browser"] as Map<String, Any?>
            assertEquals("AGENT", browserMap["controlOwner"])
            assertEquals("https://portal.example.com/dashboard", browserMap["url"])
            assertTrue(coordinator.virtualBrowser.cookieJar.getCookieHeader("https://portal.example.com/dashboard")?.contains("sid=user-verified-cookie-123") == true)
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }
}
