package com.reveila.ai

import com.reveila.system.platform.PlatformSystem

/**
 * Scope hierarchy for persistent standing instructions.
 * Precedence order: TENANT > USER_GLOBAL > SPACE.
 */
enum class InstructionScope {
    /** Organization / tenant-wide standing rule enforced across all users in the tenant. */
    TENANT,

    /** Personal standing rule for a specific (tenantId, userId) across all Spaces and synced devices. */
    USER_GLOBAL,

    /** Space-scoped standing rule for a specific (tenantId, userId, spaceId). */
    SPACE;

    companion object {
        fun fromRawOrDefault(raw: String?, default: InstructionScope = USER_GLOBAL): InstructionScope {
            val norm = raw?.trim()?.uppercase()?.replace("-", "_") ?: return default
            return when (norm) {
                "TENANT", "ORG", "ORGANIZATION" -> TENANT
                "USER_GLOBAL", "GLOBAL", "USER", "PERSONAL" -> USER_GLOBAL
                "SPACE", "WORKSPACE" -> SPACE
                else -> default
            }
        }
    }
}

/**
 * Semantic category for standing instructions.
 * SAFETY rules receive highest prompt priority and deterministic output validation in [AiResponseValidator].
 */
enum class InstructionCategory {
    SAFETY,
    FORMAT,
    TONE,
    WORKFLOW,
    CUSTOM;

    companion object {
        fun fromRawOrDefault(raw: String?, default: InstructionCategory = CUSTOM): InstructionCategory {
            val norm = raw?.trim()?.uppercase() ?: return default
            return entries.firstOrNull { it.name == norm } ?: default
        }

        /**
         * Infers an appropriate category when the user does not specify one explicitly.
         */
        fun inferFromText(text: String): InstructionCategory {
            val lower = text.lowercase()
            val safetySignals = listOf(
                "never include", "never mention", "never discuss", "never generate",
                "do not include", "don't include", "do not discuss", "don't discuss",
                "avoid adult", "adult sexual", "sexual topic", "nsfw", "explicit",
                "prohibit", "forbid", "block ", "no profanity", "safe for work", "family friendly"
            )
            if (safetySignals.any { lower.contains(it) }) return SAFETY

            val formatSignals = listOf(
                "numbered list", "bullet", "markdown", "table", "json", "format your response",
                "format response", "always format", "heading", "concise list", "step-by-step"
            )
            if (formatSignals.any { lower.contains(it) }) return FORMAT

            val toneSignals = listOf(
                "tone", "polite", "formal", "casual", "brief", "concise", "friendly", "professional"
            )
            if (toneSignals.any { lower.contains(it) }) return TONE

            val workflowSignals = listOf(
                "always verify", "always cite", "always ask", "before executing", "summarize first"
            )
            if (workflowSignals.any { lower.contains(it) }) return WORKFLOW

            return CUSTOM
        }
    }
}

/**
 * A persistent standing instruction remembered by the Reveila AI Agent until changed or removed.
 *
 * Isolation & Multi-Device Sync:
 * - Strictly partitioned by [tenantId] and [userId] (with `userId == "*"` reserved for [InstructionScope.TENANT]).
 * - Supports Last-Write-Wins (LWW) multi-device synchronization via [version], [updatedAt], [deviceId], and [deleted] tombstones.
 */
data class AgentInstruction(
    val id: String,
    val tenantId: String = DEFAULT_TENANT_ID,
    val userId: String = DEFAULT_USER_ID,
    val scope: InstructionScope = InstructionScope.USER_GLOBAL,
    val spaceId: String? = null,
    val category: InstructionCategory = InstructionCategory.CUSTOM,
    val text: String,
    val blockedPatterns: List<String> = emptyList(),
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val priority: Int = 100,
    val version: Long = 1L,
    val deviceId: String = "",
    val deleted: Boolean = false,
    val createdAt: Long = PlatformSystem.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    companion object {
        const val DEFAULT_TENANT_ID: String = "default-tenant"
        const val DEFAULT_USER_ID: String = "local-user"
        const val TENANT_WILDCARD_USER_ID: String = "*"

        fun normalizeTenantId(raw: String?): String {
            val cleaned = raw?.trim()?.lowercase().orEmpty()
            if (cleaned.isEmpty()) return DEFAULT_TENANT_ID
            val slug = cleaned.replace(Regex("[^a-z0-9._-]"), "-").trim('-')
            return slug.ifEmpty { DEFAULT_TENANT_ID }
        }

        fun normalizeUserId(raw: String?, scope: InstructionScope = InstructionScope.USER_GLOBAL): String {
            if (scope == InstructionScope.TENANT) return TENANT_WILDCARD_USER_ID
            val cleaned = raw?.trim()?.lowercase().orEmpty()
            if (cleaned.isEmpty() || cleaned == "system") return DEFAULT_USER_ID
            return cleaned.replace(Regex("[^a-z0-9@._+-]"), "-").trim('-').ifEmpty { DEFAULT_USER_ID }
        }

        fun normalizeSpaceIdOrNull(raw: String?, scope: InstructionScope): String? {
            if (scope != InstructionScope.SPACE) return null
            val cleaned = raw?.trim().orEmpty()
            return if (cleaned.isEmpty()) AgentSession.DEFAULT_SPACE_ID else AgentSession.normalizeSpaceId(cleaned)
        }

        /**
         * Builds the composite partition key used in DataService so tenant/user records
         * can never collide or overwrite one another.
         */
        fun compositeKey(tenantId: String, userId: String, id: String): String {
            return "${normalizeTenantId(tenantId)}::${userId.trim().lowercase().ifEmpty { DEFAULT_USER_ID }}::${id.trim()}"
        }

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): AgentInstruction? {
            val id = map["id"]?.toString()?.trim().orEmpty()
            val text = map["text"]?.toString()?.trim().orEmpty()
            if (id.isEmpty() || text.isEmpty()) return null

            val scope = InstructionScope.fromRawOrDefault(map["scope"]?.toString())
            val tenantId = normalizeTenantId(map["tenantId"]?.toString())
            val userId = normalizeUserId(map["userId"]?.toString(), scope)
            val spaceId = normalizeSpaceIdOrNull(map["spaceId"]?.toString(), scope)
            val category = InstructionCategory.fromRawOrDefault(
                map["category"]?.toString(),
                default = InstructionCategory.inferFromText(text)
            )
            val blockedPatterns = when (val raw = map["blockedPatterns"]) {
                is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                is String -> raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }
            val enabled = (map["enabled"] as? Boolean)
                ?: map["enabled"]?.toString()?.equals("true", ignoreCase = true)
                ?: true
            val locked = (map["locked"] as? Boolean)
                ?: map["locked"]?.toString()?.equals("true", ignoreCase = true)
                ?: false
            val priority = (map["priority"] as? Number)?.toInt()
                ?: map["priority"]?.toString()?.toIntOrNull()
                ?: 100
            val version = (map["version"] as? Number)?.toLong()
                ?: map["version"]?.toString()?.toLongOrNull()
                ?: 1L
            val deviceId = map["deviceId"]?.toString()?.trim().orEmpty()
            val deleted = (map["deleted"] as? Boolean)
                ?: map["deleted"]?.toString()?.equals("true", ignoreCase = true)
                ?: false
            val now = PlatformSystem.currentTimeMillis()
            val createdAt = (map["createdAt"] as? Number)?.toLong()
                ?: map["createdAt"]?.toString()?.toLongOrNull()
                ?: now
            val updatedAt = (map["updatedAt"] as? Number)?.toLong()
                ?: map["updatedAt"]?.toString()?.toLongOrNull()
                ?: createdAt

            return AgentInstruction(
                id = id,
                tenantId = tenantId,
                userId = userId,
                scope = scope,
                spaceId = spaceId,
                category = category,
                text = text,
                blockedPatterns = blockedPatterns,
                enabled = enabled,
                locked = locked,
                priority = priority,
                version = version,
                deviceId = deviceId,
                deleted = deleted,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "tenantId" to tenantId,
        "userId" to userId,
        "scope" to scope.name,
        "spaceId" to (spaceId ?: ""),
        "category" to category.name,
        "text" to text,
        "blockedPatterns" to blockedPatterns,
        "enabled" to enabled,
        "locked" to locked,
        "priority" to priority,
        "version" to version,
        "deviceId" to deviceId,
        "deleted" to deleted,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
