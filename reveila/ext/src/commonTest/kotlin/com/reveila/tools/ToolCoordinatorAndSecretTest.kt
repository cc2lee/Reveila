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

    @Test
    fun testOpenAiNativeToolCallsAndFlexibleExtraction() {
        val (coordinator, _, homeDir) = createCoordinatorFixture()
        try {
            // 1. OpenAI/Gemini tool schema serialization excludes internal reveila_metadata
            val provider = object : com.reveila.ai.OpenAiLlmProvider() {
                init {
                    model = "gpt-4o"
                    allowNoApiKey = true
                }
                fun encode(r: com.reveila.ai.LlmRequest) = buildRequestBody(r)
                fun decode(j: String) = parseResponse(j)
            }
            val request = com.reveila.ai.LlmRequest.builder()
                .tools(coordinator.getAllLlmTools())
                .addMessage(com.reveila.ai.ReveilaMessage.user("Give me a news brief"))
                .addMessage(com.reveila.ai.ReveilaMessage.tool("Search completed: 5 headlines"))
                .build()
            val body = provider.encode(request)
            assertTrue(body.contains("\"tools\""))
            assertFalse(body.contains("reveila_metadata"))
            assertFalse(body.contains("\"role\":\"tool\""))
            assertTrue(body.contains("[Tool Execution Result]"))

            // 2. Native OpenAI tool_calls with null content converts cleanly into TOOL CALL envelope
            val nativeApiJson = """
                {
                  "choices": [
                    {
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "virtual_browser",
                              "arguments": "{\"action\":\"search\",\"query\":\"latest news\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent()
            val parsed = provider.decode(nativeApiJson)
            assertEquals("tool_calls", parsed.finishReason)
            assertEquals(1, parsed.toolCalls?.size)
            assertEquals("virtual_browser", parsed.toolCalls?.firstOrNull()?.functionName)
            val envelope = JsonUtil.parseJsonStringToMap(parsed.content.orEmpty())
            assertEquals("TOOL CALL", envelope["status"])
            @Suppress("UNCHECKED_CAST")
            val calls = envelope["tool-call"] as List<Map<String, Any?>>
            assertEquals("virtual_browser", coordinator.extractToolName(calls[0]))
            val extractedArgs = coordinator.extractArguments(calls[0])
            assertEquals("search", extractedArgs["action"])
            assertEquals("latest news", extractedArgs["query"])

            // 3. Flat tool-call payload (without nested arguments wrapper) still extracts properly
            val flatCall = mapOf("tool" to "browser", "query" to "AI headlines")
            assertEquals("browser", coordinator.extractToolName(flatCall))
            assertEquals("virtual_browser", coordinator.normalizeToolName(coordinator.extractToolName(flatCall)))
            assertEquals("AI headlines", coordinator.extractArguments(flatCall)["query"])

            // 4. Open-ended web/news intents trigger agentic routing while general chat stays lightweight
            assertTrue(coordinator.shouldRouteToAgenticLoop("Give me a news brief"))
            assertTrue(coordinator.shouldRouteToAgenticLoop("Search the web for Kotlin Multiplatform 2.1"))
            assertFalse(coordinator.shouldRouteToAgenticLoop("Explain what recursion is in computer science"))
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }

    @Test
    fun testSelectiveModelExposureAndDynamicToolRegistration() {
        val (coordinator, _, homeDir) = createCoordinatorFixture()
        try {
            val registry = com.reveila.safety.MetadataRegistry()
            coordinator.registerManifests(registry)

            val customTool = object : com.reveila.ai.ReveilaTool {
                override fun getName(): String = "stock_ticker"
                override fun getDescription(): String = "Fetches real-time stock quotes."
                override fun execute(args: Map<String, Any?>): String = "NVDA: 140.00"
            }
            coordinator.registerTool(customTool, registry)
            assertTrue(coordinator.isRegisteredTool("stock_ticker"))

            val perimeter = com.reveila.safety.SecurityPerimeter(emptySet(), emptySet(), false, 256L, 1, 10, false)
            val cloudOnlyManifest = com.reveila.safety.MetadataRegistry.PluginManifest(
                plugin_id = "deep_research",
                manifestName = "Deep Research",
                manifestVersion = "1.0.0",
                tool_definitions = mapOf("type" to "object"),
                tier = "Tier 2",
                agency_perimeter = perimeter,
                secret_parameters = emptySet(),
                masked_parameters = emptySet(),
                exposeAsTool = true,
                cloudOnly = true,
                allowedModels = setOf("gpt-4o", "gemini"),
                routingKeywords = setOf("market ticker")
            )
            val sizedManifest = cloudOnlyManifest.copy(
                plugin_id = "medium_tool",
                cloudOnly = false,
                allowedModels = emptySet(),
                minParameterSizeB = 7.0
            )
            registry.register(cloudOnlyManifest)
            registry.register(sizedManifest)

            // Cloud model allowed vs On-Device blocked for cloudOnly
            assertTrue(coordinator.isManifestAllowedForTarget(cloudOnlyManifest, isOnDevice = false, modelName = "gpt-4o", systemHome = homeDir))
            assertFalse(coordinator.isManifestAllowedForTarget(cloudOnlyManifest, isOnDevice = false, modelName = "claude-3-opus", systemHome = homeDir))
            assertFalse(coordinator.isManifestAllowedForTarget(cloudOnlyManifest, isOnDevice = true, modelName = "qwen2.5-7b-instruct.gguf", systemHome = homeDir))

            // minParameterSizeB enforces local model capacity threshold
            assertFalse(coordinator.isManifestAllowedForTarget(sizedManifest, isOnDevice = true, modelName = "gemma-2-2b-it.gguf", systemHome = homeDir))
            assertTrue(coordinator.isManifestAllowedForTarget(sizedManifest, isOnDevice = true, modelName = "qwen2.5-7b-instruct.gguf", systemHome = homeDir))

            // Config-driven routing keywords wake agent loop without hardcoded Kotlin changes
            assertTrue(coordinator.shouldRouteToAgenticLoop("Check the market ticker for NVDA", registry.getLocalRoutingKeywords()))
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }

    @Test
    fun testWebLookupIntentScopesToolsToVirtualBrowserAndBlocksCurlSpin() {
        val (coordinator, _, homeDir) = createCoordinatorFixture()
        try {
            val allTools = coordinator.getAllLlmTools()
            assertTrue(allTools.any { it.name == "terminal" })
            assertTrue(allTools.any { it.name == "virtual_browser" })

            // Pure news prompt scopes builtin tools to virtual_browser only (no terminal spin)
            val newsTools = coordinator.filterToolsForIntent("What's in the news today?", allTools)
            assertEquals(listOf("virtual_browser"), newsTools.map { it.name })
            assertTrue(coordinator.shouldRouteToAgenticLoop("What's in the news today?"))

            // Explicit shell/terminal prompt retains terminal tool
            val shellTools = coordinator.filterToolsForIntent("Run a bash script in terminal to check disk space", allTools)
            assertTrue(shellTools.any { it.name == "terminal" })

            // Bare curl web fetch in TerminalTool is redirected without publishing a phantom terminal card
            val redirectMsg = coordinator.terminalTool.execute(
                mapOf("action" to "run_command", "command" to "curl -s https://www.bbc.com/news")
            )
            assertTrue(redirectMsg.contains("virtual_browser", ignoreCase = true))
            assertFalse(ToolActivityHub.getSnapshot().terminal.isVisible)
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }

    @Test
    fun testFolderScopedRelationshipAndExpenseAnalysisAcrossAllFiles() {
        val vault = com.reveila.ai.KnowledgeVault()
        vault.indexDocument("expenses/rent.pdf", "Monthly Apartment Rent: $2,100.00 paid on Nov 1", mapOf("relativePath" to "expenses/rent.pdf"))
        vault.indexDocument("expenses/cloud-aws.pdf", "AWS Cloud Hosting Invoice: $640.50 due to GPU cluster scaling", mapOf("relativePath" to "expenses/cloud-aws.pdf"))
        vault.indexDocument("expenses/electric.txt", "City Power Electric Bill: $185.20", mapOf("relativePath" to "expenses/electric.txt"))
        vault.indexDocument("expenses/groceries.csv", "FreshMart Groceries Total: $520.00", mapOf("relativePath" to "expenses/groceries.csv"))
        vault.indexDocument("expenses/internet.txt", "Fiber Internet Bill: $89.99", mapOf("relativePath" to "expenses/internet.txt"))
        vault.indexDocument("expenses/travel.md", "Flight & Hotel Conference Expense: $910.00", mapOf("relativePath" to "expenses/travel.md"))
        vault.indexDocument("notes/journal.md", "Unrelated personal journal note", mapOf("relativePath" to "notes/journal.md"))

        // Auto-infers expenses/ folder from a general prompt
        val inferred = vault.inferFolderScopesFromPrompt("Where did my top 5 expenses go this month?")
        assertEquals(listOf("expenses/"), inferred)

        // Analytical query returns exhaustive dossier containing ALL 6 expense files and excludes notes/
        val dossier = vault.search("Where did my top 5 expenses go this month?", limit = 3)
        assertTrue(dossier.contains("Total Files: 6"))
        assertTrue(dossier.contains("expenses/rent.pdf"))
        assertTrue(dossier.contains("expenses/cloud-aws.pdf"))
        assertTrue(dossier.contains("expenses/travel.md"))
        assertTrue(dossier.contains("expenses/internet.txt"))
        assertFalse(dossier.contains("notes/journal.md"))

        // Coordinator scopes tools to knowledge_vault for folder relationship/expense analysis
        val homeDir = "build/tmp/kv-tool-test-${com.reveila.util.Uuid.randomUuid().take(8)}"
        PlatformFileSystem().createDirectories(homeDir)
        try {
            val coordinator = ReveilaToolCoordinator(
                systemHome = homeDir,
                secretManagerProvider = { null },
                downloadManagerProvider = { null },
                knowledgeVaultProvider = { vault }
            )
            val scopedTools = coordinator.filterToolsForIntent(
                "Analyze cause-effect relationships in my expenses folder",
                coordinator.getAllLlmTools()
            )
            assertEquals(listOf("knowledge_vault"), scopedTools.map { it.name })

            val toolDossier = coordinator.knowledgeVaultTool.execute(
                mapOf("action" to "analyze_folder", "folder" to "expenses")
            )
            assertTrue(toolDossier.contains("Total Files: 6"))
            assertTrue(toolDossier.contains("GPU cluster scaling"))
        } finally {
            ToolActivityHub.resetForTest()
            PlatformFileSystem().delete(homeDir, recursive = true)
        }
    }
}
