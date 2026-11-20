package com.reveila.ai

import com.reveila.error.SecurityException
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecretManager
import com.reveila.safety.SecurityPerimeter
import com.reveila.safety.TraceContextHolder
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.tools.KnowledgeVaultTool
import com.reveila.tools.PendingToolAuthorizationView
import com.reveila.tools.Terminal
import com.reveila.tools.TerminalTool
import com.reveila.tools.ToolActivityHub
import com.reveila.tools.browser.CookieJar
import com.reveila.tools.browser.PasswordManager
import com.reveila.tools.browser.PasswordManagerTool
import com.reveila.tools.browser.VirtualBrowser
import com.reveila.tools.browser.VirtualBrowserTool
import com.reveila.tools.download.ResumableDownloadManager
import com.reveila.tools.download.ResumableDownloadTool
import com.reveila.tools.secrets.SecretManagerTool
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil

/**
 * Coordinates autonomous execution, security permissions, saved credentials, and interactive
 * user takeover across the core Reveila tools (`virtual_browser`, `resumable_download`,
 * `terminal`, `secret_manager`, and `password_manager`).
 */
class ReveilaToolCoordinator(
    private val systemHome: String,
    private val secretManagerProvider: () -> SecretManager?,
    private val downloadManagerProvider: () -> ResumableDownloadManager?,
    private val knowledgeVaultProvider: () -> KnowledgeVault? = { null },
    private val auditLogger: (action: String, details: String) -> Unit = { _, _ -> }
) {
    data class PendingToolExecution(
        val view: PendingToolAuthorizationView,
        val sessionId: String,
        val spaceId: String,
        val actorId: String,
        val preferredProviderName: String?,
        val toolCallObj: Map<String, Any?>,
        val remainingCalls: List<Map<String, Any?>> = emptyList(),
        val accumulatedResults: String = "",
        val originalReasoning: String = ""
    )

    data class AuthorizationResolution(
        val found: Boolean,
        val approved: Boolean,
        val pendingExecution: PendingToolExecution? = null,
        val toolOutput: String = "",
        val message: String = ""
    )

    private val fs = PlatformFileSystem()
    private val lock = PlatformLock()
    private val pendingExecutions = mutableMapOf<String, PendingToolExecution>()

    val passwordManager: PasswordManager = PasswordManager.createDefault(systemHome)
    val virtualBrowser: VirtualBrowser = VirtualBrowser().apply {
        passwordManager = this@ReveilaToolCoordinator.passwordManager
        defaultSearchUrlTemplate = loadPersistedSearchUrlTemplate()
        onPageChangedCallback = { page, actionDesc ->
            ToolActivityHub.updateBrowserPage(
                page = page,
                actionDescription = actionDesc,
                waitingForUser = takeoverState != null,
                takeoverReason = takeoverState?.reason.orEmpty(),
                takeoverMessage = takeoverState?.message.orEmpty()
            )
        }
        onTakeoverCallback = { takeover ->
            ToolActivityHub.setBrowserControl(
                owner = "AGENT",
                waitingForUser = true,
                reason = takeover.reason,
                message = takeover.message
            )
        }
    }

    private fun resolveBrowserConfigPath(): String {
        val configsDir = fs.resolve(systemHome, "configs")
        try {
            if (!fs.exists(configsDir)) fs.createDirectories(configsDir)
        } catch (_: Exception) {
        }
        return fs.resolve(configsDir, "browser.json")
    }

    private fun loadPersistedSearchUrlTemplate(): String {
        return try {
            val cfgPath = resolveBrowserConfigPath()
            if (!fs.exists(cfgPath)) return ""
            val map = JsonUtil.parseJsonStringToMap(fs.readText(cfgPath))
            (map["defaultSearchUrl"] as? String)?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun getBrowserConfigJson(): String {
        val current = virtualBrowser.defaultSearchUrlTemplate.trim()
        return JsonUtil.toJsonString(
            mapOf(
                "defaultSearchUrl" to current,
                "presets" to listOf(
                    mapOf("id" to "auto", "label" to "Smart Auto (News RSS + DuckDuckGo)", "url" to ""),
                    mapOf("id" to "duckduckgo", "label" to "DuckDuckGo HTML", "url" to VirtualBrowser.DEFAULT_DUCKDUCKGO_TEMPLATE),
                    mapOf("id" to "google_news", "label" to "Google News RSS", "url" to VirtualBrowser.DEFAULT_GOOGLE_NEWS_RSS_TEMPLATE),
                    mapOf("id" to "bing_news", "label" to "Bing News RSS", "url" to VirtualBrowser.DEFAULT_BING_NEWS_RSS_TEMPLATE),
                    mapOf("id" to "bing", "label" to "Bing Web Search", "url" to VirtualBrowser.DEFAULT_BING_SEARCH_TEMPLATE)
                )
            )
        )
    }

    fun saveBrowserConfigJson(defaultSearchUrl: String): String {
        val cleaned = defaultSearchUrl.trim()
        virtualBrowser.defaultSearchUrlTemplate = cleaned
        try {
            val cfgPath = resolveBrowserConfigPath()
            fs.writeText(
                cfgPath,
                JsonUtil.toPrettyJsonString(mapOf("defaultSearchUrl" to cleaned)),
                append = false
            )
            auditLogger("BROWSER_CONFIG_UPDATED", "Updated default search URL: '${cleaned.ifBlank { "Smart Auto" }}'")
        } catch (_: Exception) {
        }
        return getBrowserConfigJson()
    }

    private var lazyDownloadManager: ResumableDownloadManager? = null

    fun resolveDownloadManager(): ResumableDownloadManager {
        val fromCtx = downloadManagerProvider()
        val mgr = fromCtx ?: lazyDownloadManager ?: ResumableDownloadManager(configuredSystemHome = systemHome).also {
            lazyDownloadManager = it
        }
        if (mgr.onProgressObserver == null) {
            mgr.onProgressObserver = { progress ->
                val avail = try {
                    mgr.checkAvailableStorage(progress.destinationPath.ifBlank { mgr.resolveTempDirectory() })
                } catch (_: Exception) {
                    -1L
                }
                ToolActivityHub.updateDownloadProgress(progress, avail)
            }
        }
        return mgr
    }

    val terminal: Terminal = Terminal.getInstance(fs.resolve(systemHome, "temp"))

    val virtualBrowserTool = VirtualBrowserTool(virtualBrowser)
    val passwordManagerTool = PasswordManagerTool(passwordManager, virtualBrowser)
    val downloadTool: ResumableDownloadTool by lazy { ResumableDownloadTool(resolveDownloadManager()) }
    val terminalTool = TerminalTool(
        terminal = terminal,
        secretManagerProvider = secretManagerProvider,
        defaultWorkingDirProvider = { systemHome }
    )
    val secretManagerTool = SecretManagerTool(
        secretManagerProvider = secretManagerProvider,
        passwordManagerProvider = { passwordManager },
        browserProvider = { virtualBrowser }
    )
    val knowledgeVaultTool = KnowledgeVaultTool(knowledgeVaultProvider)

    private val toolsByName: MutableMap<String, ReveilaTool> by lazy {
        linkedMapOf(
            virtualBrowserTool.getName() to virtualBrowserTool,
            knowledgeVaultTool.getName() to knowledgeVaultTool,
            downloadTool.getName() to downloadTool,
            terminalTool.getName() to terminalTool,
            secretManagerTool.getName() to secretManagerTool,
            passwordManagerTool.getName() to passwordManagerTool
        )
    }

    /**
     * Dynamically registers an additional [ReveilaTool] instance at runtime and optionally
     * publishes its capability manifest into [MetadataRegistry].
     */
    @kotlin.jvm.JvmOverloads
    fun registerTool(tool: ReveilaTool, registry: MetadataRegistry? = null) {
        toolsByName[tool.getName()] = tool
        if (registry != null) {
            registerManifestForTool(tool, registry)
        }
    }

    fun getToolNames(): Set<String> = toolsByName.keys

    fun isRegisteredTool(pluginId: String): Boolean {
        val norm = normalizeToolName(pluginId)
        return toolsByName.containsKey(norm)
    }

    fun normalizeToolName(rawName: String): String {
        val clean = rawName.trim().lowercase()
        return when (clean) {
            "browser", "web", "virtualbrowser", "web_browser" -> "virtual_browser"
            "knowledge", "knowledgevault", "vault_files", "folder_analysis", "documents" -> "knowledge_vault"
            "download", "downloader", "resumabledownload" -> "resumable_download"
            "shell", "bash", "powershell", "script" -> "terminal"
            "secret", "secrets", "secretmanager", "vault" -> "secret_manager"
            "password", "passwords", "passwordmanager" -> "password_manager"
            else -> clean
        }
    }

    /**
     * Extracts the target tool/plugin name across LLM wrapper variations
     * (`plugin`, `name`, `tool`, `tool_name`, or nested `function.name`).
     */
    fun extractToolName(toolCallObj: Map<String, Any?>): String {
        val fnObj = toolCallObj["function"] as? Map<*, *>
        val candidates = listOf(
            toolCallObj["plugin"],
            toolCallObj["name"],
            toolCallObj["tool"],
            toolCallObj["tool_name"],
            fnObj?.get("name"),
            if (fnObj == null) toolCallObj["function"] else null
        )
        return candidates.firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
    }

    /**
     * Extracts tool arguments across nested (`arguments`/`parameters`/`args`/`input`/JSON string)
     * and flat (`{"name": "...", "action": "...", ...}`) LLM payloads.
     */
    @Suppress("UNCHECKED_CAST")
    fun extractArguments(toolCallObj: Map<String, Any?>): MutableMap<String, Any?> {
        val fnObj = toolCallObj["function"] as? Map<*, *>
        val rawArgs = toolCallObj["arguments"]
            ?: toolCallObj["parameters"]
            ?: toolCallObj["args"]
            ?: toolCallObj["input"]
            ?: fnObj?.get("arguments")

        val extracted: MutableMap<String, Any?> = when (rawArgs) {
            is Map<*, *> -> rawArgs.entries.associateTo(linkedMapOf()) { (k, v) -> k.toString() to v }
            is String -> rawArgs.trim().takeIf { it.startsWith("{") }?.let {
                try {
                    JsonUtil.parseJsonStringToMap(it).toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }
            } ?: mutableMapOf()
            else -> mutableMapOf()
        }

        if (!extracted.containsKey("action") && toolCallObj.containsKey("method")) {
            extracted["action"] = toolCallObj["method"]
        }

        if (extracted.isEmpty()) {
            val envelopeKeys = setOf(
                "plugin", "name", "tool", "tool_name", "function",
                "arguments", "parameters", "args", "input", "method", "id", "type"
            )
            for ((k, v) in toolCallObj) {
                if (k !in envelopeKeys) {
                    extracted[k] = v
                }
            }
        }
        return extracted
    }

    fun getAllLlmTools(): List<LlmTool> {
        return toolsByName.values.map { t ->
            LlmTool().apply {
                name = t.getName()
                description = t.getDescription()
                parameterSchema = buildSchemaForTool(t.getName())
                setReveilaMetadata("Tier 2", emptyList(), emptyList())
            }
        }
    }

    fun registerManifests(registry: MetadataRegistry?) {
        if (registry == null) return
        for (tool in toolsByName.values) {
            registerManifestForTool(tool, registry)
        }
    }

    private fun registerManifestForTool(tool: ReveilaTool, registry: MetadataRegistry) {
        val id = tool.getName()
        val perimeter = if (id == "virtual_browser" || id == "resumable_download") {
            SecurityPerimeter(
                setOf("tool:execute", "network:https"),
                emptySet(),
                false,
                1024L,
                2,
                120,
                false
            )
        } else {
            SecurityPerimeter(
                setOf("tool:execute", "device:shell"),
                emptySet(),
                true,
                1024L,
                2,
                120,
                false
            )
        }
        val manifest = MetadataRegistry.PluginManifest(
            plugin_id = id,
            manifestName = id,
            manifestVersion = "1.0.0",
            tool_definitions = mapOf("inputSchema" to buildSchemaForTool(id)),
            tier = "Tier 2",
            agency_perimeter = perimeter,
            secret_parameters = setOf("password", "value", "secret", "new_password"),
            masked_parameters = setOf("password", "value", "secret"),
            description = tool.getDescription(),
            exposeAsTool = true
        )
        registry.register(manifest)
    }

    /**
     * Evaluates whether [manifest] should be exposed to the selected target model for this turn.
     */
    fun isManifestAllowedForTarget(
        manifest: MetadataRegistry.PluginManifest,
        isOnDevice: Boolean,
        modelName: String?,
        systemHome: String?
    ): Boolean {
        if (!manifest.isToolExposed()) return false
        if (isOnDevice && manifest.cloudOnly) return false

        val minSize = manifest.minParameterSizeB
        if (isOnDevice && minSize != null) {
            val detectedSize = PromptTemplateRegistry.resolveParameterSizeBillions(modelName, systemHome)
            if (detectedSize == null || detectedSize < minSize) return false
        }

        if (manifest.allowedModels.isNotEmpty()) {
            val normModel = modelName?.trim()?.lowercase().orEmpty()
            if (normModel.isEmpty()) return false
            if (manifest.allowedModels.none { normModel.contains(it) }) return false
        }
        return true
    }

    @kotlin.jvm.JvmOverloads
    fun shouldRouteToAgenticLoop(userPrompt: String, extraKeywords: Collection<String> = emptySet()): Boolean {
        val lower = userPrompt.lowercase()
        val toolKeywords = listOf(
            "http://", "https://", "browse ", "open website", "open url", "web page", "webpage",
            "search the web", "search online", "web search", "look up online", "look up on the web",
            "find online", "news brief", "latest news", "breaking news", "today's news",
            "todays news", "daily news", "news headlines", "headlines", "in the news", "news today",
            "news update", "weather forecast", "current weather", "stock price", "live price",
            "log in to", "login to", "sign in to", "scrape ", "click ", "submit form",
            "download ", "pause download", "resume download", "cancel download", "gguf",
            "terminal", "run script", "execute script", "run command", "shell ", "powershell", "bash ",
            "secret manager", "store secret", "save secret", "save credential", "password manager",
            "stored password", "api token", "list secrets", "list permissions",
            "analyze folder", "cause-effect", "cause and effect", "relationship", "top 5 expenses",
            "expenses", "invoices", "bills"
        )
        if (toolKeywords.any { lower.contains(it) }) return true
        val vault = knowledgeVaultProvider()
        if (vault != null && (vault.getSearchScope().isNotEmpty() || vault.inferFolderScopesFromPrompt(userPrompt).isNotEmpty())) {
            if (vault.isAnalyticalOrFolderWideQuery(userPrompt)) return true
        }
        return extraKeywords.any { kw -> kw.isNotBlank() && lower.contains(kw.lowercase()) }
    }

    /**
     * Detects folder-scoped document/relationship/expense analysis prompts that belong to `knowledge_vault`.
     */
    fun isKnowledgeFolderAnalysisIntent(userPrompt: String): Boolean {
        val vault = knowledgeVaultProvider() ?: return false
        val hasScopeOrFolderMatch = vault.getSearchScope().isNotEmpty() ||
            vault.inferFolderScopesFromPrompt(userPrompt).isNotEmpty()
        if (!hasScopeOrFolderMatch) return false
        val lower = userPrompt.lowercase()
        val explicitOtherTools = listOf(
            "http://", "https://", "browse ", "search the web", "search online", "latest news",
            "terminal", "bash", "powershell", "run script", "download "
        )
        return vault.isAnalyticalOrFolderWideQuery(userPrompt) && explicitOtherTools.none { lower.contains(it) }
    }

    /**
     * Detects pure web/news/search lookup prompts that do not ask for shell, download, or secret tools.
     */
    fun isPureWebLookupIntent(userPrompt: String): Boolean {
        val lower = userPrompt.lowercase()
        val webLookupSignals = listOf(
            "news", "headline", "briefing", "weather", "stock price", "live price",
            "search the web", "search online", "web search", "look up online", "find online",
            "browse ", "open website", "open url", "web page", "webpage", "what's happening",
            "whats happening", "current events"
        )
        val nonBrowserSignals = listOf(
            "terminal", "shell", "bash", "powershell", "script", "command", "curl", "wget",
            "download", "file", "folder", "directory", "disk", "git ", "gradle", "python",
            "secret", "password", "credential", "login", "log in", "sign in", "token"
        )
        return webLookupSignals.any { lower.contains(it) } &&
            nonBrowserSignals.none { lower.contains(it) }
    }

    /**
     * Scopes builtin tools to the user's intent so the LLM does not waste round trips
     * calling `terminal` (e.g. curl/wget) for web or news lookups that belong to `virtual_browser`.
     */
    fun filterToolsForIntent(userPrompt: String, tools: List<LlmTool>): List<LlmTool> {
        if (tools.isEmpty()) return tools
        val builtinNames = getToolNames()
        if (isKnowledgeFolderAnalysisIntent(userPrompt)) {
            val hasKv = tools.any { normalizeToolName(it.name.orEmpty()) == "knowledge_vault" }
            if (hasKv) {
                return tools.filter { tool ->
                    val norm = normalizeToolName(tool.name.orEmpty())
                    norm == "knowledge_vault" || norm !in builtinNames
                }
            }
        }
        if (!isPureWebLookupIntent(userPrompt)) return tools
        val hasBrowser = tools.any { normalizeToolName(it.name.orEmpty()) == "virtual_browser" }
        if (!hasBrowser) return tools
        return tools.filter { tool ->
            val norm = normalizeToolName(tool.name.orEmpty())
            norm == "virtual_browser" || norm !in builtinNames
        }
    }

    /**
     * Evaluates authorization & saved-credential availability, then executes the requested local tool
     * or throws [SecurityException] with `APPROVAL_REQUIRED|<requestId>|<reason>` if user input is needed.
     */
    fun authorizeAndExecute(
        toolCallObj: Map<String, Any?>,
        session: AgentSession,
        actorId: String,
        preferredProviderName: String?,
        requireInteractiveAuth: Boolean = true,
        remainingCalls: List<Map<String, Any?>> = emptyList(),
        accumulatedResults: String = "",
        originalReasoning: String = ""
    ): String {
        val rawName = extractToolName(toolCallObj)
        val toolName = normalizeToolName(rawName)
        val tool = toolsByName[toolName]
            ?: throw IllegalArgumentException("Unknown tool '$rawName'")

        val args = extractArguments(toolCallObj)
        val action = (args["action"] as? String)?.lowercase()?.trim() ?: inferDefaultAction(toolName, args)

        // Yield gracefully if the user is actively controlling this tool window
        when (toolName) {
            "virtual_browser" -> if (ToolActivityHub.isBrowserUserControlled() && action != "snapshot") {
                return "Paused: user is currently in manual control of the Browser window. Ask the user to click 'Return Control to AI Agent' when finished."
            }
            "terminal" -> if (ToolActivityHub.isTerminalUserControlled() && action != "status") {
                return "Paused: user is currently in manual control of the Terminal window. Ask the user to click 'Return Control to AI Agent' when finished."
            }
            "resumable_download" -> if (ToolActivityHub.isDownloadUserControlled() && action != "status" && action != "list_incomplete" && action != "check_storage") {
                return "Paused: user is currently in manual control of the Download window. Ask the user to click 'Return Control to AI Agent' when finished."
            }
        }

        val scope = computeScope(toolName, action, args)
        val readOnly = isReadOnlyAction(toolName, action)
        val sm = secretManagerProvider()
        val traceId = session.parentTraceId ?: TraceContextHolder.getTraceId() ?: Uuid.randomUuid()

        val credInfo = inspectMissingCredentials(toolName, action, args, sm)
        val hasGrant = readOnly || (sm?.hasToolPermission(toolName, scope) == true)

        if (requireInteractiveAuth && (!hasGrant || credInfo.missing)) {
            val requestId = "auth-${Uuid.randomUuid().take(10)}"
            val title = buildFriendlyAuthTitle(toolName, action, args, credInfo)
            val preview = buildCommandPreview(toolName, action, args)
            val reason = if (credInfo.missing) {
                "Credentials required for ${credInfo.domain.ifBlank { credInfo.secretKey }} before [$toolName:$action] can proceed."
            } else {
                "Authorization required for AI agent to run [$toolName:$action] ($scope)."
            }
            val view = PendingToolAuthorizationView(
                requestId = requestId,
                traceId = traceId,
                sessionId = session.sessionId,
                spaceId = session.spaceId,
                toolName = toolName,
                action = action,
                scope = scope,
                title = title,
                reason = reason,
                commandPreview = preview,
                riskLevel = if (toolName == "terminal" || action == "delete") "HIGH" else "MEDIUM",
                requiresCredentials = credInfo.missing,
                credentialType = credInfo.type,
                credentialDomain = credInfo.domain,
                credentialKey = credInfo.secretKey,
                createdAtMs = PlatformSystem.currentTimeMillis()
            )
            val pending = PendingToolExecution(
                view = view,
                sessionId = session.sessionId,
                spaceId = session.spaceId,
                actorId = actorId,
                preferredProviderName = preferredProviderName,
                toolCallObj = LinkedHashMap(toolCallObj).apply {
                    this["name"] = toolName
                    this["arguments"] = args
                },
                remainingCalls = remainingCalls,
                accumulatedResults = accumulatedResults,
                originalReasoning = originalReasoning
            )
            lock.withLock {
                pendingExecutions[requestId] = pending
            }
            ToolActivityHub.setPendingAuthorization(view)
            auditLogger("TOOL_AUTHORIZATION_REQUESTED", "requestId=$requestId tool=$toolName action=$action scope=$scope actor=$actorId")
            throw SecurityException("APPROVAL_REQUIRED|$requestId|$reason")
        }

        return executeToolInternal(tool, toolName, action, args, traceId)
    }

    private fun executeToolInternal(
        tool: ReveilaTool,
        toolName: String,
        action: String,
        args: Map<String, Any?>,
        traceId: String
    ): String {
        val output = tool.execute(args)
        if (toolName == "resumable_download") {
            refreshDownloadHubSnapshot()
        }
        auditLogger("TOOL_EXECUTED", "traceId=$traceId tool=$toolName action=$action")
        return output
    }

    fun resolvePendingAuthorization(
        requestId: String,
        decision: String,
        approverId: String = "local-user",
        username: String = "",
        password: String = "",
        secretKey: String = "",
        secretValue: String = "",
        editedScript: String = ""
    ): AuthorizationResolution {
        val pending = lock.withLock {
            pendingExecutions.remove(requestId)
        } ?: return AuthorizationResolution(
            found = false,
            approved = false,
            message = "No pending authorization request found for ID '$requestId'."
        )

        ToolActivityHub.setPendingAuthorization(null)
        val normDecision = decision.trim().uppercase()
        if (normDecision == "DENY" || normDecision == "REJECT") {
            auditLogger(
                "TOOL_AUTHORIZATION_DENIED",
                "requestId=$requestId tool=${pending.view.toolName} scope=${pending.view.scope} rejectedBy=$approverId"
            )
            return AuthorizationResolution(
                found = true,
                approved = false,
                pendingExecution = pending,
                message = "User denied authorization for ${pending.view.toolName} (${pending.view.action})."
            )
        }

        val sm = secretManagerProvider()
        if (normDecision == "APPROVE_ALWAYS" || normDecision == "ALWAYS") {
            sm?.grantToolPermission(
                toolName = pending.view.toolName,
                scope = pending.view.scope,
                persistent = true,
                grantedBy = approverId
            )
        }

        // Persist user-supplied credentials/secrets securely without echoing into LLM prompt
        if (pending.view.credentialType == "WEBSITE_LOGIN" && pending.view.credentialDomain.isNotBlank() &&
            username.isNotBlank() && password.isNotBlank()
        ) {
            passwordManager.saveCredential(
                domain = pending.view.credentialDomain,
                username = username.trim(),
                password = password,
                loginUrl = pending.toolCallObj["arguments"]?.let { (it as? Map<*, *>)?.get("url")?.toString() }
            )
            sm?.storeSecretWithMetadata(
                key = "web:${pending.view.credentialDomain}",
                value = password,
                category = "WEBSITE_LOGIN",
                domain = pending.view.credentialDomain,
                username = username.trim(),
                description = "Website login for ${pending.view.credentialDomain}"
            )
        } else if (secretKey.isNotBlank() && secretValue.isNotBlank()) {
            sm?.storeSecretWithMetadata(
                key = secretKey.trim(),
                value = secretValue,
                category = "TOOL_SECRET",
                description = "Secret provided during tool authorization for ${pending.view.toolName}"
            )
        }

        @Suppress("UNCHECKED_CAST")
        val mutableArgs = ((pending.toolCallObj["arguments"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf())
        if (pending.view.toolName == "terminal" && editedScript.isNotBlank()) {
            if (mutableArgs.containsKey("command") && !mutableArgs.containsKey("script")) {
                mutableArgs["command"] = editedScript
            } else {
                mutableArgs["script"] = editedScript
            }
        }

        auditLogger(
            "TOOL_AUTHORIZATION_GRANTED",
            "requestId=$requestId tool=${pending.view.toolName} scope=${pending.view.scope} decision=$normDecision approvedBy=$approverId"
        )

        val tool = toolsByName[pending.view.toolName]
            ?: return AuthorizationResolution(
                found = true,
                approved = true,
                pendingExecution = pending,
                toolOutput = "Error: Tool '${pending.view.toolName}' unavailable."
            )

        val resultText = executeToolInternal(
            tool = tool,
            toolName = pending.view.toolName,
            action = pending.view.action,
            args = mutableArgs,
            traceId = pending.view.traceId
        )

        return AuthorizationResolution(
            found = true,
            approved = true,
            pendingExecution = pending,
            toolOutput = resultText
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interactive User Takeover & Live Window Actions
    // ─────────────────────────────────────────────────────────────────────────

    fun handleBrowserUserAction(
        action: String,
        url: String = "",
        target: String = "",
        text: String = "",
        form: String = "",
        cookieHeader: String = "",
        rawHtml: String = ""
    ): String {
        val act = action.lowercase().trim()
        return try {
            when (act) {
                "takeover", "take_over" -> {
                    ToolActivityHub.setBrowserControl(owner = "USER", waitingForUser = false)
                    ToolActivityHub.getSnapshotJson()
                }
                "return_control", "resume_agent" -> {
                    if (url.isNotBlank() || cookieHeader.isNotBlank() || rawHtml.isNotBlank()) {
                        val effectiveUrl = url.ifBlank { virtualBrowser.getCurrentPage()?.url ?: "" }
                        if (effectiveUrl.isNotBlank()) {
                            virtualBrowser.updateFromLiveWebView(
                                url = effectiveUrl,
                                cookieHeader = cookieHeader.ifBlank { null },
                                rawHtml = rawHtml.ifBlank { null },
                                resumeControl = true
                            )
                        }
                    } else if (virtualBrowser.getCurrentPage() != null) {
                        virtualBrowser.updateFromLiveWebView(
                            url = virtualBrowser.getCurrentPage()!!.url,
                            resumeControl = true
                        )
                    }
                    ToolActivityHub.setBrowserControl(owner = "AGENT", waitingForUser = false)
                    ToolActivityHub.getSnapshotJson()
                }
                "sync_webview" -> {
                    if (url.isNotBlank()) {
                        virtualBrowser.updateFromLiveWebView(
                            url = url,
                            cookieHeader = cookieHeader.ifBlank { null },
                            rawHtml = rawHtml.ifBlank { null },
                            resumeControl = false
                        )
                    }
                    ToolActivityHub.getSnapshotJson()
                }
                "navigate", "open" -> {
                    if (url.isNotBlank()) virtualBrowser.navigate(url)
                    ToolActivityHub.getSnapshotJson()
                }
                "click" -> {
                    if (target.isNotBlank()) virtualBrowser.click(target)
                    ToolActivityHub.getSnapshotJson()
                }
                "type" -> {
                    if (target.isNotBlank()) virtualBrowser.type(target, text)
                    ToolActivityHub.getSnapshotJson()
                }
                "submit" -> {
                    virtualBrowser.submit(form.ifBlank { null })
                    ToolActivityHub.getSnapshotJson()
                }
                "back" -> {
                    virtualBrowser.back()
                    ToolActivityHub.getSnapshotJson()
                }
                "reload" -> {
                    virtualBrowser.reload()
                    ToolActivityHub.getSnapshotJson()
                }
                else -> ToolActivityHub.getSnapshotJson()
            }
        } catch (e: Exception) {
            JsonUtil.toJsonString(mapOf("error" to (e.message ?: "Browser action failed")))
        }
    }

    fun handleTerminalUserAction(
        action: String,
        script: String = "",
        workingDir: String = ""
    ): String {
        return when (action.lowercase().trim()) {
            "takeover", "take_over" -> {
                ToolActivityHub.setTerminalControl(owner = "USER", waitingForUser = false)
                ToolActivityHub.getSnapshotJson()
            }
            "return_control", "resume_agent" -> {
                ToolActivityHub.setTerminalControl(owner = "AGENT", waitingForUser = false)
                ToolActivityHub.getSnapshotJson()
            }
            "execute", "run", "execute_script" -> {
                ToolActivityHub.setTerminalControl(owner = "USER", waitingForUser = false)
                val args = mutableMapOf<String, Any?>(
                    "action" to "execute_script",
                    "script" to script
                )
                if (workingDir.isNotBlank()) args["workingDir"] = workingDir
                terminalTool.execute(args)
                ToolActivityHub.getSnapshotJson()
            }
            "stop", "cancel" -> {
                terminalTool.execute(mapOf("action" to "stop"))
                ToolActivityHub.getSnapshotJson()
            }
            "clear" -> {
                ToolActivityHub.updateTerminal(outputText = "", exitCode = null, status = "IDLE")
                ToolActivityHub.getSnapshotJson()
            }
            else -> ToolActivityHub.getSnapshotJson()
        }
    }

    fun handleDownloadUserAction(action: String, downloadId: String = ""): String {
        val mgr = resolveDownloadManager()
        when (action.lowercase().trim()) {
            "takeover", "take_over" -> {
                ToolActivityHub.setDownloadControl(owner = "USER")
            }
            "return_control", "resume_agent" -> {
                ToolActivityHub.setDownloadControl(owner = "AGENT")
            }
            "pause" -> if (downloadId.isNotBlank()) {
                ToolActivityHub.setDownloadControl(owner = "USER")
                mgr.pause(downloadId)
            }
            "resume" -> if (downloadId.isNotBlank()) {
                mgr.resume(downloadId)
            }
            "cancel" -> if (downloadId.isNotBlank()) {
                mgr.cancel(downloadId)
            }
            "cleanup_temp" -> {
                mgr.runHousekeeping()
            }
        }
        refreshDownloadHubSnapshot()
        return ToolActivityHub.getSnapshotJson()
    }

    fun handleSecretVaultUserAction(
        action: String,
        key: String = "",
        value: String = "",
        category: String = "GENERAL",
        domain: String = "",
        username: String = ""
    ): String {
        val sm = secretManagerProvider()
        return when (action.lowercase().trim()) {
            "grant_permission" -> {
                sm?.grantToolPermission(key, domain.ifBlank { "*" }, persistent = true, grantedBy = "user")
                JsonUtil.toJsonString(mapOf("granted" to true, "permissions" to sm?.listToolPermissions().orEmpty()))
            }
            "revoke_permission" -> {
                val ok = sm?.revokeToolPermission(key, domain.ifBlank { "*" }) ?: false
                JsonUtil.toJsonString(mapOf("revoked" to ok, "permissions" to sm?.listToolPermissions().orEmpty()))
            }
            "store" -> {
                secretManagerTool.execute(
                    mapOf(
                        "action" to "store",
                        "key" to key,
                        "value" to value,
                        "category" to category,
                        "domain" to domain,
                        "username" to username
                    )
                )
            }
            "delete" -> {
                secretManagerTool.execute(mapOf("action" to "delete", "key" to key))
            }
            else -> secretManagerTool.execute(mapOf("action" to "list"))
        }
    }

    private fun refreshDownloadHubSnapshot() {
        val mgr = resolveDownloadManager()
        val avail = try {
            mgr.checkAvailableStorage(mgr.resolveTempDirectory())
        } catch (_: Exception) {
            -1L
        }
        mgr.listAllProgress().forEach { p ->
            ToolActivityHub.updateDownloadProgress(p, avail, isVisible = true)
        }
        if (ToolActivityHub.getSnapshot().downloads.items.isEmpty()) {
            ToolActivityHub.setDownloadControl(
                owner = ToolActivityHub.getSnapshot().downloads.controlOwner,
                availableStorageBytes = avail,
                makeVisible = false
            )
        }
    }

    private data class CredentialCheckResult(
        val missing: Boolean,
        val type: String = "NONE",
        val domain: String = "",
        val secretKey: String = ""
    )

    private fun inspectMissingCredentials(
        toolName: String,
        action: String,
        args: Map<String, Any?>,
        sm: SecretManager?
    ): CredentialCheckResult {
        if ((toolName == "virtual_browser" || toolName == "password_manager" || toolName == "secret_manager") &&
            (action in listOf("login", "autofill", "signin", "inject_login"))
        ) {
            val domainOrUrl = (args["domain"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                ?: (args["url"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                ?: virtualBrowser.getCurrentPage()?.url
                ?: ""
            val normDomain = if (domainOrUrl.isNotBlank()) CookieJar.extractDomain(domainOrUrl) else ""
            if (normDomain.isBlank()) {
                return CredentialCheckResult(missing = true, type = "WEBSITE_LOGIN", domain = domainOrUrl)
            }
            val foundInPm = passwordManager.findCredentialForUrl(normDomain) != null || passwordManager.hasCredential(normDomain)
            val foundInSm = sm?.hasSecret("web:$normDomain") == true
            if (!foundInPm && !foundInSm) {
                return CredentialCheckResult(missing = true, type = "WEBSITE_LOGIN", domain = normDomain)
            }
        }

        if (toolName == "terminal") {
            val rawSecretEnv = args["secretEnv"] as? Map<*, *>
            if (!rawSecretEnv.isNullOrEmpty()) {
                for ((_, vObj) in rawSecretEnv) {
                    val ref = vObj?.toString()?.trim().orEmpty()
                    if (ref.isNotEmpty() && sm?.resolveSecretReference(ref) == null) {
                        val keyName = ref.removePrefix("SEC:").removePrefix("secret:").trim()
                        return CredentialCheckResult(missing = true, type = "SECRET_KEY", secretKey = keyName)
                    }
                }
            }
        }

        return CredentialCheckResult(missing = false)
    }

    private fun inferDefaultAction(toolName: String, args: Map<String, Any?>): String = when (toolName) {
        "virtual_browser" -> when {
            args.containsKey("query") || args.containsKey("q") -> "search"
            args.containsKey("url") -> "navigate"
            else -> "snapshot"
        }
        "resumable_download" -> if (args.containsKey("url")) "download" else "status"
        "terminal" -> when {
            args.containsKey("script") -> "execute_script"
            args.containsKey("command") -> "run_command"
            else -> "status"
        }
        "secret_manager" -> if (args.containsKey("value")) "store" else "list"
        "password_manager" -> "list"
        else -> "status"
    }

    private fun isReadOnlyAction(toolName: String, action: String): Boolean = when (toolName) {
        "knowledge_vault" -> true
        "virtual_browser" -> action in setOf("snapshot", "status", "read")
        "resumable_download" -> action in setOf("status", "check_storage", "list_incomplete")
        "terminal" -> action == "status"
        "secret_manager" -> action in setOf("list", "all", "has", "check", "exists", "list_permissions", "permissions")
        "password_manager" -> action in setOf("list", "all", "get", "find")
        else -> false
    }

    private fun computeScope(toolName: String, action: String, args: Map<String, Any?>): String {
        return when (toolName) {
            "virtual_browser", "password_manager" -> {
                val target = (args["domain"] as? String)
                    ?: (args["url"] as? String)
                    ?: if (toolName == "virtual_browser" && action == "search") "web-search" else null
                    ?: virtualBrowser.getCurrentPage()?.url
                    ?: "*"
                val domain = if (target == "*") "*" else CookieJar.extractDomain(target).ifBlank { target }
                "$action:$domain"
            }
            "resumable_download" -> {
                val url = (args["url"] as? String).orEmpty()
                val domain = if (url.isNotBlank()) CookieJar.extractDomain(url).ifBlank { "*" } else (args["downloadId"] as? String ?: "*")
                "$action:$domain"
            }
            "terminal" -> action
            "secret_manager" -> {
                val key = (args["key"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: (args["domain"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "*"
                "$action:$key"
            }
            else -> action
        }
    }

    private fun buildFriendlyAuthTitle(
        toolName: String,
        action: String,
        args: Map<String, Any?>,
        credInfo: CredentialCheckResult
    ): String {
        if (credInfo.missing) {
            return if (credInfo.type == "WEBSITE_LOGIN") {
                "Sign-In Credentials Needed (${credInfo.domain})"
            } else {
                "Secret Required (${credInfo.secretKey})"
            }
        }
        return when (toolName) {
            "terminal" -> "Run Script on Device"
            "virtual_browser" -> when (action) {
                "login", "autofill", "signin" -> "Browser Login on ${args["domain"] ?: args["url"] ?: "website"}"
                "search", "web_search", "find" -> "Search Web: ${args["query"] ?: args["q"] ?: "web search"}"
                else -> "Browse Web: ${args["url"] ?: action}"
            }
            "resumable_download" -> "Download File (${action})"
            "secret_manager" -> "Secret Vault Action ($action)"
            "password_manager" -> "Password Vault Action ($action)"
            else -> "Authorize $toolName ($action)"
        }
    }

    private fun buildCommandPreview(toolName: String, action: String, args: Map<String, Any?>): String {
        return when (toolName) {
            "terminal" -> (args["script"] as? String)
                ?: (args["command"] as? String)
                ?: "Action: $action"
            "virtual_browser" -> buildString {
                append("Action: $action")
                val query = (args["query"] ?: args["q"])?.toString()
                if (!query.isNullOrBlank()) append(" • Query: $query")
                val url = (args["url"] ?: args["domain"])?.toString()
                if (!url.isNullOrBlank()) append(" • Target: $url")
                val target = args["target"]?.toString()
                if (!target.isNullOrBlank()) append(" • Element: $target")
            }
            "resumable_download" -> buildString {
                append("Action: $action")
                val url = args["url"]?.toString()
                if (!url.isNullOrBlank()) append("\nURL: $url")
                val dst = args["destinationPath"]?.toString()
                if (!dst.isNullOrBlank()) append("\nSave to: $dst")
            }
            "secret_manager", "password_manager" -> "Action: $action • Key/Domain: ${args["key"] ?: args["domain"] ?: "-"}"
            else -> "$toolName.$action"
        }.take(600)
    }

    private fun buildSchemaForTool(toolName: String): Map<String, Any?> {
        val props: Map<String, Any?> = when (toolName) {
            "virtual_browser" -> mapOf(
                "action" to mapOf("type" to "string"),
                "query" to mapOf("type" to "string"),
                "search_url" to mapOf("type" to "string"),
                "url" to mapOf("type" to "string"),
                "target" to mapOf("type" to "string"),
                "text" to mapOf("type" to "string"),
                "domain" to mapOf("type" to "string"),
                "submit" to mapOf("type" to "boolean"),
                "reason" to mapOf("type" to "string"),
                "message" to mapOf("type" to "string")
            )
            "knowledge_vault" -> mapOf(
                "action" to mapOf("type" to "string"),
                "folder" to mapOf("type" to "string"),
                "path" to mapOf("type" to "string"),
                "query" to mapOf("type" to "string"),
                "maxChars" to mapOf("type" to "integer")
            )
            "resumable_download" -> mapOf(
                "action" to mapOf("type" to "string"),
                "url" to mapOf("type" to "string"),
                "destinationPath" to mapOf("type" to "string"),
                "downloadId" to mapOf("type" to "string"),
                "expectedSha256" to mapOf("type" to "string"),
                "expectedSizeBytes" to mapOf("type" to "integer")
            )
            "terminal" -> mapOf(
                "action" to mapOf("type" to "string"),
                "script" to mapOf("type" to "string"),
                "command" to mapOf("type" to "string"),
                "workingDir" to mapOf("type" to "string"),
                "timeoutSeconds" to mapOf("type" to "integer")
            )
            else -> mapOf(
                "action" to mapOf("type" to "string"),
                "key" to mapOf("type" to "string"),
                "domain" to mapOf("type" to "string"),
                "username" to mapOf("type" to "string"),
                "value" to mapOf("type" to "string"),
                "category" to mapOf("type" to "string")
            )
        }
        return mapOf(
            "type" to "object",
            "properties" to props,
            "required" to listOf("action")
        )
    }
}
