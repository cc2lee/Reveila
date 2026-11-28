package com.reveila.tools

import com.reveila.ai.AgentInstruction
import com.reveila.ai.AgentInstructionStore
import com.reveila.ai.InstructionCategory
import com.reveila.ai.InstructionScope
import com.reveila.ai.ReveilaTool
import com.reveila.util.json.JsonUtil

/**
 * Built-in Reveila tool (`manage_instructions`) enabling the AI agent to add, list, update,
 * toggle, or remove persistent standing instructions on behalf of the authenticated user.
 *
 * Tenant & user isolation:
 * `_authenticatedTenantId`, `_authenticatedUserId`, and `_authenticatedSpaceId` are injected
 * by [com.reveila.ai.ReveilaToolCoordinator] from the active session/trace context and always
 * override any model-supplied tenant/user parameters.
 */
class AgentInstructionTool(
    private val storeProvider: () -> AgentInstructionStore?
) : ReveilaTool {

    override fun getName(): String = "manage_instructions"

    override fun getDescription(): String =
        "Manage persistent standing instructions that the AI agent must remember across turns and sessions " +
            "until changed or removed by the user. Supports actions: add, list, update, remove."

    override fun execute(args: Map<String, Any?>): String {
        val arguments = args
        val store = storeProvider()
            ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "AgentInstructionStore is not available."))

        val action = (arguments["action"] as? String)?.trim()?.lowercase()
            ?: if (!arguments["text"]?.toString().isNullOrBlank()) "add" else "list"

        // Always prefer coordinator-injected authenticated identity over model-supplied fields
        val tenantId = (arguments["_authenticatedTenantId"] as? String)?.takeIf { it.isNotBlank() }
            ?: (arguments["tenantId"] as? String)?.takeIf { it.isNotBlank() }
            ?: AgentInstruction.DEFAULT_TENANT_ID
        val userId = (arguments["_authenticatedUserId"] as? String)?.takeIf { it.isNotBlank() }
            ?: (arguments["userId"] as? String)?.takeIf { it.isNotBlank() }
            ?: AgentInstruction.DEFAULT_USER_ID
        val currentSpaceId = (arguments["_authenticatedSpaceId"] as? String)?.takeIf { it.isNotBlank() }
            ?: (arguments["spaceId"] as? String)?.takeIf { it.isNotBlank() }

        return try {
            when (action) {
                "add", "remember", "save", "create", "set" -> {
                    val text = (arguments["text"] ?: arguments["instruction"] ?: arguments["rule"])
                        ?.toString()?.trim().orEmpty()
                    if (text.isEmpty()) {
                        return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Instruction 'text' is required."))
                    }
                    val requestedScope = InstructionScope.fromRawOrDefault(
                        arguments["scope"]?.toString(),
                        default = InstructionScope.USER_GLOBAL
                    )
                    // Prevent non-admin conversational tool calls from escalating to TENANT scope
                    val safeScope = if (requestedScope == InstructionScope.TENANT) {
                        InstructionScope.USER_GLOBAL
                    } else {
                        requestedScope
                    }
                    val targetSpace = if (safeScope == InstructionScope.SPACE) {
                        (arguments["spaceId"] as? String)?.takeIf { it.isNotBlank() } ?: currentSpaceId
                    } else {
                        null
                    }
                    val category = (arguments["category"] as? String)?.takeIf { it.isNotBlank() }
                        ?.let { InstructionCategory.fromRawOrDefault(it) }
                    val blockedPatterns = when (val raw = arguments["blockedPatterns"]) {
                        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                        is String -> raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                        else -> emptyList()
                    }
                    val priority = (arguments["priority"] as? Number)?.toInt() ?: 100

                    val saved = store.addInstruction(
                        text = text,
                        tenantId = tenantId,
                        userId = userId,
                        scope = safeScope,
                        spaceId = targetSpace,
                        category = category,
                        blockedPatterns = blockedPatterns,
                        priority = priority
                    )
                    JsonUtil.toJsonString(
                        mapOf(
                            "ok" to true,
                            "action" to "added",
                            "instruction" to saved.toMap(),
                            "message" to "Saved standing instruction [${saved.id}] (${saved.category.name}, ${saved.scope.name}): ${saved.text}"
                        )
                    )
                }

                "list", "get", "show", "all" -> {
                    val includeDisabled = (arguments["includeDisabled"] as? Boolean) == true
                    val scopeFilter = (arguments["spaceId"] as? String)?.takeIf { it.isNotBlank() } ?: currentSpaceId
                    val list = store.listInstructions(
                        tenantId = tenantId,
                        userId = userId,
                        spaceId = scopeFilter,
                        includeDisabled = includeDisabled
                    )
                    JsonUtil.toJsonString(
                        mapOf(
                            "ok" to true,
                            "count" to list.size,
                            "instructions" to list.map { it.toMap() }
                        )
                    )
                }

                "update", "edit", "toggle" -> {
                    val id = (arguments["id"] as? String)?.trim().orEmpty()
                    if (id.isEmpty()) {
                        return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Instruction 'id' is required for update."))
                    }
                    val newText = (arguments["text"] ?: arguments["instruction"] ?: arguments["rule"])
                        ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val enabled = arguments["enabled"] as? Boolean
                    val category = (arguments["category"] as? String)?.takeIf { it.isNotBlank() }
                        ?.let { InstructionCategory.fromRawOrDefault(it) }
                    val priority = (arguments["priority"] as? Number)?.toInt()

                    val updated = store.updateInstruction(
                        id = id,
                        tenantId = tenantId,
                        userId = userId,
                        newText = newText,
                        enabled = enabled,
                        category = category,
                        priority = priority
                    )
                    if (updated == null) {
                        JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Instruction '$id' not found."))
                    } else {
                        JsonUtil.toJsonString(
                            mapOf(
                                "ok" to true,
                                "action" to "updated",
                                "instruction" to updated.toMap()
                            )
                        )
                    }
                }

                "remove", "delete", "forget" -> {
                    val id = (arguments["id"] as? String)?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        val removed = store.removeInstruction(id = id, tenantId = tenantId, userId = userId)
                        JsonUtil.toJsonString(
                            mapOf(
                                "ok" to removed,
                                "action" to "removed",
                                "id" to id,
                                "message" to if (removed) "Removed standing instruction '$id'." else "Instruction '$id' not found."
                            )
                        )
                    } else {
                        val query = (arguments["text"] ?: arguments["instruction"] ?: arguments["query"])
                            ?.toString()?.trim().orEmpty()
                        if (query.isEmpty()) {
                            return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Provide 'id' or 'text' to remove an instruction."))
                        }
                        val removed = store.removeInstructionByText(
                            queryText = query,
                            tenantId = tenantId,
                            userId = userId,
                            spaceId = currentSpaceId
                        )
                        if (removed != null) {
                            JsonUtil.toJsonString(
                                mapOf(
                                    "ok" to true,
                                    "action" to "removed",
                                    "instruction" to removed.toMap(),
                                    "message" to "Removed standing instruction [${removed.id}]: ${removed.text}"
                                )
                            )
                        } else {
                            JsonUtil.toJsonString(mapOf("ok" to false, "error" to "No matching standing instruction found for '$query'."))
                        }
                    }
                }

                else -> JsonUtil.toJsonString(
                    mapOf("ok" to false, "error" to "Unsupported action '$action'. Use add, list, update, or remove.")
                )
            }
        } catch (e: Exception) {
            JsonUtil.toJsonString(mapOf("ok" to false, "error" to (e.message ?: "Instruction operation failed.")))
        }
    }
}
