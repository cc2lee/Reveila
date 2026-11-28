package com.reveila.ai

import com.reveila.service.DataService
import com.reveila.system.SystemComponent
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil

/**
 * Persistent, multi-tenant, per-user, and multi-device-syncable store for Reveila AI Agent
 * standing instructions.
 *
 * Architecture:
 * - Persisted via Reveila's neutral [DataService] repository abstraction (`AgentInstruction` entity),
 *   so instructions seamlessly persist to JSON edge repos, Android Room, or PostgreSQL.
 * - Strict tenant & user isolation: every record is partitioned by `(tenantId, userId)` and queries
 *   never leak across tenants or users.
 * - Optional admin/bootstrap seed file at `${system.home}/configs/agent-instructions.json`.
 * - Multi-device synchronization via [exportSyncDelta] and [mergeSyncPayload] using Last-Write-Wins
 *   (LWW) resolution with `deleted` tombstones so removals propagate cleanly across devices.
 */
open class AgentInstructionStore(
    private var configuredDataService: DataService? = null,
    private var configuredSystemHome: String? = null
) : SystemComponent() {

    companion object {
        const val COMPONENT_NAME: String = "AgentInstructionStore"
        const val ENTITY_TYPE: String = "AgentInstruction"
        const val CONFIG_FILE_NAME: String = "agent-instructions.json"
        const val DEFAULT_PROMPT_CHAR_BUDGET: Int = 2400
    }

    data class SyncMergeResult(
        val applied: Int,
        val skippedOlder: Int,
        val rejectedCrossBoundary: Int,
        val totalActiveForUser: Int
    )

    private val lock = PlatformLock()
    private val cache = linkedMapOf<String, AgentInstruction>()
    private var hydratedFromStorage = false

    /**
     * Optional observer notified whenever an instruction is added, updated, removed, or merged.
     * Used by [InstructionSyncCoordinator] on the Lead Node to emit a Cluster Pulse.
     */
    var onMutationListener: ((tenantId: String, userId: String, sourceDeviceId: String) -> Unit)? = null

    @Throws(Exception::class)
    override fun onStart() {
        resolveDataService()
        hydrateIfNeeded()
        loadBootstrapConfigFile()
        logger.info("AgentInstructionStore started with ${cache.values.count { !it.deleted }} active standing instruction(s).")
    }

    @Throws(Exception::class)
    override fun onStop() {
        lock.withLock {
            cache.clear()
            hydratedFromStorage = false
        }
    }

    private fun resolveDataService(): DataService? {
        if (configuredDataService == null) {
            try {
                val p = context?.getProxy("DataService")
                configuredDataService = (p?.getInstance() as? DataService) ?: p as? DataService
            } catch (_: Exception) {
            }
        }
        return configuredDataService
    }

    private fun resolveSystemHome(): String? {
        return configuredSystemHome?.takeIf { it.isNotBlank() }
            ?: context?.properties?.getProperty("system.home")?.takeIf { it.isNotBlank() }
    }

    private fun resolveDefaultDeviceId(): String {
        return context?.properties?.getProperty("reveila.device.id")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "local-device"
    }

    /**
     * Loads all persisted instructions from [DataService] into the in-memory cache once.
     */
    private fun hydrateIfNeeded() {
        lock.withLock {
            if (hydratedFromStorage) return@withLock
            hydratedFromStorage = true
            val ds = resolveDataService() ?: return@withLock
            try {
                var pageIdx = 0
                val pageSize = 200
                while (true) {
                    val page = ds.search(
                        mapOf(
                            "entityType" to ENTITY_TYPE,
                            "page" to pageIdx,
                            "size" to pageSize,
                            "isIncludeCount" to false
                        )
                    )
                    if (page.content.isEmpty()) break
                    for (entity in page.content) {
                        val parsed = parseEntity(entity.attributes) ?: continue
                        val key = AgentInstruction.compositeKey(parsed.tenantId, parsed.userId, parsed.id)
                        cache[key] = parsed
                    }
                    if (page.content.size < pageSize) break
                    pageIdx++
                }
            } catch (e: Exception) {
                logger.warning("Failed to hydrate AgentInstructionStore from DataService: ${e.message}")
            }
        }
    }

    /**
     * Optionally loads admin/bootstrap instructions from `${system.home}/configs/agent-instructions.json`.
     * Existing user-modified records in DataService are preserved unless the bootstrap record is locked
     * and newer.
     */
    @Suppress("UNCHECKED_CAST")
    open fun loadBootstrapConfigFile() {
        val home = resolveSystemHome() ?: return
        val fs = PlatformFileSystem()
        val cfgPath = fs.resolve(fs.resolve(home, "configs"), CONFIG_FILE_NAME)
        if (!fs.exists(cfgPath)) return

        try {
            val rawText = fs.readText(cfgPath).trim()
            if (rawText.isEmpty()) return
            val items: List<Map<String, Any?>> = if (rawText.startsWith("[")) {
                JsonUtil.parseJsonStringToList(rawText)
            } else {
                val root = JsonUtil.parseJsonStringToMap(rawText)
                (root["instructions"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
            }
            for (item in items) {
                val inst = AgentInstruction.fromMap(item) ?: continue
                val key = AgentInstruction.compositeKey(inst.tenantId, inst.userId, inst.id)
                val shouldSave = lock.withLock {
                    val existing = cache[key]
                    if (existing == null || (inst.locked && inst.updatedAt >= existing.updatedAt)) {
                        cache[key] = inst
                        true
                    } else {
                        false
                    }
                }
                if (shouldSave) {
                    persistInstruction(inst)
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to load bootstrap agent-instructions.json: ${e.message}")
        }
    }

    // =========================================================================
    // CRUD Operations (Strictly Tenant & User Isolated)
    // =========================================================================

    @kotlin.jvm.JvmOverloads
    open fun addInstruction(
        text: String,
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        scope: InstructionScope = InstructionScope.USER_GLOBAL,
        spaceId: String? = null,
        category: InstructionCategory? = null,
        blockedPatterns: List<String> = emptyList(),
        priority: Int = 100,
        locked: Boolean = false,
        deviceId: String? = null,
        id: String? = null
    ): AgentInstruction {
        hydrateIfNeeded()
        val cleanedText = text.trim()
        require(cleanedText.isNotEmpty()) { "Instruction text cannot be blank." }

        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, scope)
        val normSpace = AgentInstruction.normalizeSpaceIdOrNull(spaceId, scope)
        val resolvedCategory = category ?: InstructionCategory.inferFromText(cleanedText)
        val now = PlatformSystem.currentTimeMillis()
        val resolvedDevice = deviceId?.trim()?.takeIf { it.isNotEmpty() } ?: resolveDefaultDeviceId()
        val resolvedId = id?.trim()?.takeIf { it.isNotEmpty() } ?: "inst-${Uuid.randomUuid().take(10)}"

        // Deduplicate identical active instruction in the same tenant/user/scope/space
        val existingDuplicate = lock.withLock {
            cache.values.firstOrNull {
                !it.deleted &&
                    it.tenantId == normTenant &&
                    it.userId == normUser &&
                    it.scope == scope &&
                    it.spaceId == normSpace &&
                    it.text.equals(cleanedText, ignoreCase = true)
            }
        }
        if (existingDuplicate != null) {
            val updated = existingDuplicate.copy(
                enabled = true,
                category = resolvedCategory,
                blockedPatterns = if (blockedPatterns.isNotEmpty()) blockedPatterns else existingDuplicate.blockedPatterns,
                priority = priority,
                version = existingDuplicate.version + 1L,
                deviceId = resolvedDevice,
                updatedAt = now
            )
            saveInternal(updated)
            return updated
        }

        val created = AgentInstruction(
            id = resolvedId,
            tenantId = normTenant,
            userId = normUser,
            scope = scope,
            spaceId = normSpace,
            category = resolvedCategory,
            text = cleanedText,
            blockedPatterns = blockedPatterns.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            enabled = true,
            locked = locked,
            priority = priority,
            version = 1L,
            deviceId = resolvedDevice,
            deleted = false,
            createdAt = now,
            updatedAt = now
        )
        saveInternal(created)
        return created
    }

    @kotlin.jvm.JvmOverloads
    open fun updateInstruction(
        id: String,
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        newText: String? = null,
        enabled: Boolean? = null,
        category: InstructionCategory? = null,
        priority: Int? = null,
        blockedPatterns: List<String>? = null,
        isTenantAdmin: Boolean = false,
        deviceId: String? = null
    ): AgentInstruction? {
        hydrateIfNeeded()
        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)

        val existing = findOwnedOrTenantInstruction(id, normTenant, normUser) ?: return null
        if (existing.locked && !isTenantAdmin) {
            throw IllegalStateException("Instruction '${existing.id}' is locked by tenant policy and cannot be modified.")
        }
        if (existing.scope == InstructionScope.TENANT && !isTenantAdmin) {
            throw IllegalStateException("Tenant-scoped instruction '${existing.id}' requires tenant admin privileges.")
        }

        val now = PlatformSystem.currentTimeMillis()
        val updated = existing.copy(
            text = newText?.trim()?.takeIf { it.isNotEmpty() } ?: existing.text,
            enabled = enabled ?: existing.enabled,
            category = category ?: existing.category,
            priority = priority ?: existing.priority,
            blockedPatterns = blockedPatterns?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
                ?: existing.blockedPatterns,
            version = existing.version + 1L,
            deviceId = deviceId?.trim()?.takeIf { it.isNotEmpty() } ?: resolveDefaultDeviceId(),
            updatedAt = now
        )
        saveInternal(updated)
        return updated
    }

    /**
     * Soft-deletes (tombstones) an instruction so the removal propagates cleanly across synced devices.
     */
    @kotlin.jvm.JvmOverloads
    open fun removeInstruction(
        id: String,
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        isTenantAdmin: Boolean = false,
        deviceId: String? = null
    ): Boolean {
        hydrateIfNeeded()
        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)

        val existing = findOwnedOrTenantInstruction(id, normTenant, normUser) ?: return false
        if (existing.locked && !isTenantAdmin) {
            throw IllegalStateException("Instruction '${existing.id}' is locked by tenant policy and cannot be removed.")
        }
        if (existing.scope == InstructionScope.TENANT && !isTenantAdmin) {
            throw IllegalStateException("Tenant-scoped instruction '${existing.id}' requires tenant admin privileges.")
        }

        val now = PlatformSystem.currentTimeMillis()
        val tombstoned = existing.copy(
            enabled = false,
            deleted = true,
            version = existing.version + 1L,
            deviceId = deviceId?.trim()?.takeIf { it.isNotEmpty() } ?: resolveDefaultDeviceId(),
            updatedAt = now
        )
        saveInternal(tombstoned)
        return true
    }

    /**
     * Removes the first active instruction matching [queryText] (case-insensitive substring)
     * within the caller's `(tenantId, userId)` boundary. Useful for conversational "forget ..." requests.
     */
    @kotlin.jvm.JvmOverloads
    open fun removeInstructionByText(
        queryText: String,
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        spaceId: String? = null,
        isTenantAdmin: Boolean = false,
        deviceId: String? = null
    ): AgentInstruction? {
        hydrateIfNeeded()
        val needle = queryText.trim().lowercase()
        if (needle.isEmpty()) return null
        val candidates = listInstructions(
            tenantId = tenantId,
            userId = userId,
            spaceId = spaceId,
            includeDisabled = true
        )
        val match = candidates.firstOrNull { it.text.lowercase() == needle }
            ?: candidates.firstOrNull { it.text.lowercase().contains(needle) }
            ?: return null
        val removed = removeInstruction(
            id = match.id,
            tenantId = tenantId,
            userId = userId,
            isTenantAdmin = isTenantAdmin,
            deviceId = deviceId
        )
        return if (removed) match else null
    }

    /**
     * Lists instructions strictly isolated to `(tenantId, userId)` plus `TENANT`-scoped rules
     * for that same `tenantId`.
     *
     * When [spaceId] is provided, returns `TENANT` + `USER_GLOBAL` + matching `SPACE` rules.
     * When [spaceId] is null, returns all rules for that user (including all their Spaces) + `TENANT` rules.
     */
    @kotlin.jvm.JvmOverloads
    open fun listInstructions(
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        spaceId: String? = null,
        includeDisabled: Boolean = false
    ): List<AgentInstruction> {
        hydrateIfNeeded()
        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)
        val normSpace = spaceId?.trim()?.takeIf { it.isNotEmpty() }?.let { AgentSession.normalizeSpaceId(it) }

        return lock.withLock {
            cache.values.filter { inst ->
                if (inst.deleted) return@filter false
                if (!includeDisabled && !inst.enabled) return@filter false
                if (inst.tenantId != normTenant) return@filter false

                when (inst.scope) {
                    InstructionScope.TENANT -> true
                    InstructionScope.USER_GLOBAL -> inst.userId == normUser
                    InstructionScope.SPACE -> inst.userId == normUser && (normSpace == null || inst.spaceId == normSpace)
                }
            }
        }.sortedWith(instructionComparator())
    }

    /**
     * Returns all active SAFETY instructions for `(tenantId, userId, spaceId)` for deterministic
     * output validation in [AiResponseValidator].
     */
    @kotlin.jvm.JvmOverloads
    open fun getActiveSafetyInstructions(
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        spaceId: String? = null
    ): List<AgentInstruction> {
        return listInstructions(
            tenantId = tenantId,
            userId = userId,
            spaceId = spaceId,
            includeDisabled = false
        ).filter { it.category == InstructionCategory.SAFETY }
    }

    // =========================================================================
    // Prompt Rendering (Injected on Every Turn)
    // =========================================================================

    /**
     * Renders active standing instructions for `(tenantId, userId, spaceId)` into a concise,
     * prioritized system-prompt block that respects [maxChars] without dropping TENANT or SAFETY rules.
     */
    @kotlin.jvm.JvmOverloads
    open fun renderForPrompt(
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        spaceId: String? = null,
        maxChars: Int = DEFAULT_PROMPT_CHAR_BUDGET
    ): String? {
        val active = listInstructions(
            tenantId = tenantId,
            userId = userId,
            spaceId = spaceId,
            includeDisabled = false
        )
        if (active.isEmpty()) return null

        val mandatory = active.filter { it.scope == InstructionScope.TENANT || it.category == InstructionCategory.SAFETY }
        val userGlobal = active.filter { it.scope == InstructionScope.USER_GLOBAL && it.category != InstructionCategory.SAFETY }
        val spaceSpecific = active.filter { it.scope == InstructionScope.SPACE && it.category != InstructionCategory.SAFETY }

        val sb = StringBuilder()
        sb.appendLine("STANDING INSTRUCTIONS (persist across sessions; follow strictly until changed or removed by the user):")

        if (mandatory.isNotEmpty()) {
            sb.appendLine("[Mandatory Safety & Policy Rules]")
            for (inst in mandatory) {
                sb.appendLine("- ${inst.text}")
            }
        }

        var remaining = (maxChars - sb.length).coerceAtLeast(0)

        if (userGlobal.isNotEmpty() && remaining > 40) {
            sb.appendLine("[User Standing Preferences]")
            remaining = (maxChars - sb.length).coerceAtLeast(0)
            for (inst in userGlobal) {
                val line = "- ${inst.text}\n"
                if (line.length <= remaining) {
                    sb.append(line)
                    remaining -= line.length
                }
            }
        }

        if (spaceSpecific.isNotEmpty() && remaining > 40) {
            val spaceLabel = spaceId?.let { AgentSession.displayNameForSpace(it) } ?: "Current Space"
            sb.appendLine("[Space Rules ($spaceLabel)]")
            remaining = (maxChars - sb.length).coerceAtLeast(0)
            for (inst in spaceSpecific) {
                val line = "- ${inst.text}\n"
                if (line.length <= remaining) {
                    sb.append(line)
                    remaining -= line.length
                }
            }
        }

        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    // =========================================================================
    // Multi-Device Synchronization (LWW + Tombstones + Boundary Isolation)
    // =========================================================================

    /**
     * Exports all instructions (including `deleted` tombstones) modified after [sinceTimestampMs]
     * for `(tenantId, userId)` plus `TENANT` rules so another device can sync incrementally.
     */
    @kotlin.jvm.JvmOverloads
    open fun exportSyncDelta(
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        sinceTimestampMs: Long = 0L
    ): List<AgentInstruction> {
        hydrateIfNeeded()
        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)

        return lock.withLock {
            cache.values.filter { inst ->
                inst.tenantId == normTenant &&
                    (inst.scope == InstructionScope.TENANT || inst.userId == normUser) &&
                    inst.updatedAt >= sinceTimestampMs
            }.sortedBy { it.updatedAt }
        }
    }

    /**
     * Merges a remote device's sync delta into this store using deterministic Last-Write-Wins (LWW)
     * semantics while strictly enforcing `(tenantId, userId)` isolation boundaries.
     */
    @kotlin.jvm.JvmOverloads
    open fun mergeSyncPayload(
        tenantId: String,
        userId: String,
        incoming: List<AgentInstruction>,
        allowTenantRules: Boolean = true
    ): SyncMergeResult {
        hydrateIfNeeded()
        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)

        var applied = 0
        var skippedOlder = 0
        var rejectedCrossBoundary = 0

        for (raw in incoming) {
            val instTenant = AgentInstruction.normalizeTenantId(raw.tenantId)
            val instUser = AgentInstruction.normalizeUserId(raw.userId, raw.scope)

            // Strict tenant & user boundary enforcement
            val tenantMatches = instTenant == normTenant
            val userMatches = when (raw.scope) {
                InstructionScope.TENANT -> allowTenantRules
                InstructionScope.USER_GLOBAL, InstructionScope.SPACE -> instUser == normUser
            }
            if (!tenantMatches || !userMatches) {
                rejectedCrossBoundary++
                continue
            }

            val normalized = raw.copy(
                tenantId = instTenant,
                userId = instUser,
                spaceId = AgentInstruction.normalizeSpaceIdOrNull(raw.spaceId, raw.scope)
            )
            val key = AgentInstruction.compositeKey(normalized.tenantId, normalized.userId, normalized.id)

            val shouldApply = lock.withLock {
                val existing = cache[key]
                if (existing == null) {
                    true
                } else {
                    isIncomingNewer(existing, normalized)
                }
            }

            if (shouldApply) {
                saveInternal(normalized, notifyObserver = false)
                applied++
            } else {
                skippedOlder++
            }
        }

        val totalActive = listInstructions(normTenant, normUser, spaceId = null, includeDisabled = false).size
        return SyncMergeResult(
            applied = applied,
            skippedOlder = skippedOlder,
            rejectedCrossBoundary = rejectedCrossBoundary,
            totalActiveForUser = totalActive
        )
    }

    /**
     * Lead-Node RPC / REST handler for bidirectional cross-device instruction sync.
     *
     * Expects a payload map containing:
     * - `tenantId` (String)
     * - `userId` (String)
     * - `deviceId` (String, optional)
     * - `sinceTimestampMs` (Long, optional, default 0)
     * - `instructions` (List<Map<String, Any?>>, optional client delta to push)
     * - `isTenantAdmin` (Boolean, optional; only trusted when true to accept TENANT-scoped pushes)
     *
     * Merges the client's delta into the Lead's persistent store, emits a mutation event if anything
     * changed, and returns the Lead's delta since `sinceTimestampMs` for the client to merge locally.
     */
    @Suppress("UNCHECKED_CAST")
    open fun handleSyncRequest(payload: Map<String, Any?>): Map<String, Any?> {
        hydrateIfNeeded()
        val tenantId = AgentInstruction.normalizeTenantId(payload["tenantId"]?.toString())
        val userId = AgentInstruction.normalizeUserId(payload["userId"]?.toString(), InstructionScope.USER_GLOBAL)
        val sourceDeviceId = payload["deviceId"]?.toString()?.trim().orEmpty()
        val sinceTs = (payload["sinceTimestampMs"] as? Number)?.toLong()
            ?: payload["sinceTimestampMs"]?.toString()?.toLongOrNull()
            ?: 0L
        val allowTenantPush = (payload["isTenantAdmin"] as? Boolean) == true

        val rawList = (payload["instructions"] as? Iterable<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
            .orEmpty()
        val incoming = rawList.mapNotNull { AgentInstruction.fromMap(it) }

        val mergeResult = if (incoming.isNotEmpty()) {
            mergeSyncPayload(
                tenantId = tenantId,
                userId = userId,
                incoming = incoming,
                allowTenantRules = allowTenantPush
            )
        } else {
            SyncMergeResult(
                applied = 0,
                skippedOlder = 0,
                rejectedCrossBoundary = 0,
                totalActiveForUser = listInstructions(tenantId, userId, spaceId = null, includeDisabled = false).size
            )
        }

        if (mergeResult.applied > 0) {
            try {
                onMutationListener?.invoke(tenantId, userId, sourceDeviceId)
            } catch (_: Exception) {
            }
        }

        val serverNow = PlatformSystem.currentTimeMillis()
        val outgoing = exportSyncDelta(
            tenantId = tenantId,
            userId = userId,
            sinceTimestampMs = sinceTs
        )

        return linkedMapOf(
            "ok" to true,
            "tenantId" to tenantId,
            "userId" to userId,
            "serverTimestampMs" to serverNow,
            "applied" to mergeResult.applied,
            "skippedOlder" to mergeResult.skippedOlder,
            "rejectedCrossBoundary" to mergeResult.rejectedCrossBoundary,
            "totalActiveForUser" to mergeResult.totalActiveForUser,
            "instructions" to outgoing.map { it.toMap() }
        )
    }

    private fun isIncomingNewer(existing: AgentInstruction, incoming: AgentInstruction): Boolean {
        if (incoming.updatedAt != existing.updatedAt) {
            return incoming.updatedAt > existing.updatedAt
        }
        if (incoming.version != existing.version) {
            return incoming.version > existing.version
        }
        return incoming.deviceId > existing.deviceId
    }

    // =========================================================================
    // Internal Persistence & Helpers
    // =========================================================================

    private fun findOwnedOrTenantInstruction(
        id: String,
        normTenant: String,
        normUser: String
    ): AgentInstruction? {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) return null
        return lock.withLock {
            cache[AgentInstruction.compositeKey(normTenant, normUser, cleanId)]
                ?: cache[AgentInstruction.compositeKey(normTenant, AgentInstruction.TENANT_WILDCARD_USER_ID, cleanId)]
        }
    }

    private fun saveInternal(instruction: AgentInstruction, notifyObserver: Boolean = true) {
        val key = AgentInstruction.compositeKey(instruction.tenantId, instruction.userId, instruction.id)
        lock.withLock {
            cache[key] = instruction
        }
        persistInstruction(instruction)
        if (notifyObserver) {
            try {
                onMutationListener?.invoke(instruction.tenantId, instruction.userId, instruction.deviceId)
            } catch (_: Exception) {
            }
        }
    }

    private fun persistInstruction(instruction: AgentInstruction) {
        val ds = resolveDataService() ?: return
        try {
            val compositeKey = AgentInstruction.compositeKey(instruction.tenantId, instruction.userId, instruction.id)
            val keyMap = mapOf("instructionKey" to mapOf("value" to compositeKey))
            val attrs = mutableMapOf<String, Any>()
            attrs["instructionKey"] = compositeKey
            attrs["id"] = instruction.id
            attrs["tenantId"] = instruction.tenantId
            attrs["userId"] = instruction.userId
            attrs["scope"] = instruction.scope.name
            attrs["spaceId"] = instruction.spaceId ?: ""
            attrs["category"] = instruction.category.name
            attrs["text"] = instruction.text
            attrs["blockedPatternsJson"] = JsonUtil.toJsonString(instruction.blockedPatterns)
            attrs["enabled"] = instruction.enabled
            attrs["locked"] = instruction.locked
            attrs["priority"] = instruction.priority
            attrs["version"] = instruction.version
            attrs["deviceId"] = instruction.deviceId
            attrs["deleted"] = instruction.deleted
            attrs["createdAt"] = instruction.createdAt
            attrs["updatedAt"] = instruction.updatedAt

            ds.save(ENTITY_TYPE, mapOf("key" to keyMap, "attributes" to attrs))
        } catch (e: Exception) {
            logger.warning("Failed to persist AgentInstruction [${instruction.id}] to DataService: ${e.message}")
        }
    }

    private fun parseEntity(attrs: Map<String, Any>): AgentInstruction? {
        val mutable = LinkedHashMap<String, Any?>(attrs)
        val patternsJson = attrs["blockedPatternsJson"]?.toString()?.trim().orEmpty()
        if (patternsJson.startsWith("[")) {
            try {
                // Parse JSON array of strings safely
                val stripped = patternsJson.removePrefix("[").removeSuffix("]").trim()
                if (stripped.isNotEmpty()) {
                    val items = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(stripped)
                        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
                        .filter { it.isNotBlank() }
                        .toList()
                    mutable["blockedPatterns"] = items
                }
            } catch (_: Exception) {
            }
        }
        return AgentInstruction.fromMap(mutable)
    }

    private fun instructionComparator(): Comparator<AgentInstruction> = Comparator { a, b ->
        // 1. Scope precedence: TENANT (0) < USER_GLOBAL (1) < SPACE (2)
        val scopeCmp = a.scope.ordinal.compareTo(b.scope.ordinal)
        if (scopeCmp != 0) return@Comparator scopeCmp

        // 2. SAFETY category always first within a scope
        val aSafety = if (a.category == InstructionCategory.SAFETY) 0 else 1
        val bSafety = if (b.category == InstructionCategory.SAFETY) 0 else 1
        val safetyCmp = aSafety.compareTo(bSafety)
        if (safetyCmp != 0) return@Comparator safetyCmp

        // 3. Higher priority first
        val prioCmp = b.priority.compareTo(a.priority)
        if (prioCmp != 0) return@Comparator prioCmp

        // 4. Older creation time first for stable ordering
        a.createdAt.compareTo(b.createdAt)
    }
}
