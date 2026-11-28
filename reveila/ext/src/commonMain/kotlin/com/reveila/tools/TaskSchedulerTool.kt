package com.reveila.tools

import com.reveila.ai.AutonomousAgent
import com.reveila.ai.ReveilaTool
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.TimeFormat
import com.reveila.util.json.JsonUtil
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Built-in Reveila tool (`manage_tasks`) enabling the AI agent to create recurring tasks,
 * schedule exact date/time reminders, list, pause, resume, run, or cancel tasks via natural language
 * across the Assistant chat UI and connected channels (Teams, WhatsApp, Slack, Email).
 */
class TaskSchedulerTool(
    private val autonomousAgentProvider: () -> AutonomousAgent?
) : ReveilaTool {

    override fun getName(): String = "manage_tasks"

    override fun getDescription(): String =
        "Create, schedule, list, pause, resume, run, or cancel recurring autonomous tasks and exact date/time reminders. " +
            "Actions: create_reminder, create_task, list, pause, resume, cancel, run_now. " +
            "For reminders or exact dates, pass scheduledAt (ISO-8601 e.g. '2026-12-03T14:30:00') or delayMinutes."

    override fun execute(args: Map<String, Any?>): String {
        val agent = autonomousAgentProvider()
            ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "AutonomousAgent is not available."))

        val action = (args["action"] as? String)?.trim()?.lowercase()
            ?: when {
                args.containsKey("scheduledAt") || args.containsKey("scheduledAtMs") || args.containsKey("delayMinutes") -> "create_reminder"
                args.containsKey("prompt") || args.containsKey("message") -> "create_task"
                else -> "list"
            }

        val tenantId = (args["_authenticatedTenantId"] as? String)?.trim().orEmpty()
        val userId = (args["_authenticatedUserId"] as? String)?.trim().orEmpty()
        val replyChannel = (args["replyChannel"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (args["_replyChannel"] as? String)?.trim().orEmpty()
        val replyTarget = (args["replyTarget"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (args["_replyTarget"] as? String)?.trim().orEmpty()

        return try {
            when (action) {
                "create_reminder", "remind", "reminder", "add_reminder", "schedule_reminder" -> {
                    val message = (args["message"] ?: args["prompt"] ?: args["text"] ?: args["title"])
                        ?.toString()?.trim().orEmpty()
                    if (message.isEmpty()) {
                        return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Reminder 'message' (or 'prompt') is required."))
                    }
                    val title = (args["title"] as? String)?.trim().orEmpty()
                    val scheduledAtMs = resolveScheduledAtMs(args)
                    if (scheduledAtMs <= 0L) {
                        return JsonUtil.toJsonString(
                            mapOf(
                                "ok" to false,
                                "error" to "Could not resolve reminder date/time. Provide 'scheduledAt' (ISO-8601 like '2026-12-03T14:30:00'), 'scheduledAtMs', or 'delayMinutes'."
                            )
                        )
                    }
                    val frequency = (args["frequency"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "once"
                    val createdJson = agent.createReminder(
                        title = title,
                        message = message,
                        scheduledAtMs = scheduledAtMs,
                        frequency = frequency,
                        replyChannel = replyChannel,
                        replyTarget = replyTarget,
                        actorId = userId,
                        tenantId = tenantId,
                        userId = userId
                    )
                    val created = JsonUtil.parseJsonStringToMap(createdJson)
                    JsonUtil.toJsonString(
                        mapOf(
                            "ok" to true,
                            "action" to "reminder_created",
                            "task" to created,
                            "scheduledFor" to TimeFormat.timestamp(scheduledAtMs).substringBefore("."),
                            "message" to "Reminder scheduled for ${TimeFormat.timestamp(scheduledAtMs).substringBefore(".")}: $message"
                        )
                    )
                }

                "create_task", "create", "schedule", "schedule_task", "add" -> {
                    val prompt = (args["prompt"] ?: args["message"] ?: args["text"])
                        ?.toString()?.trim().orEmpty()
                    if (prompt.isEmpty()) {
                        return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Task 'prompt' is required."))
                    }
                    val title = (args["title"] as? String)?.trim().orEmpty()
                    val frequency = (args["frequency"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "daily"
                    val scheduleTime = (args["scheduleTime"] as? String)?.trim().orEmpty()
                    val scheduledAtMs = resolveScheduledAtMs(args)
                    val requiresApproval = (args["requiresApproval"] as? Boolean) == true
                    val executionTarget = (args["executionTarget"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "auto"

                    val createdJson = agent.createTaskWithIntent(
                        title = title,
                        intent = (args["intent"] as? String)?.trim().orEmpty().ifBlank { "auto" },
                        prompt = prompt,
                        frequency = frequency,
                        scheduleTime = scheduleTime,
                        requiresApproval = requiresApproval,
                        executionTarget = executionTarget,
                        actorId = userId,
                        taskType = "AGENT_TASK",
                        scheduledAtMs = scheduledAtMs,
                        replyChannel = replyChannel,
                        replyTarget = replyTarget,
                        tenantId = tenantId,
                        userId = userId
                    )
                    val created = JsonUtil.parseJsonStringToMap(createdJson)
                    JsonUtil.toJsonString(
                        mapOf(
                            "ok" to true,
                            "action" to "task_created",
                            "task" to created,
                            "message" to "Scheduled task '${created["title"]}' (${created["frequency"]})."
                        )
                    )
                }

                "list", "get", "show", "all" -> {
                    val tasks = agent.listTasks()
                    val filtered = if (tenantId.isNotEmpty() && userId.isNotEmpty()) {
                        tasks.filter { t ->
                            val tTenant = t["tenantId"]?.toString()?.trim().orEmpty()
                            val tUser = t["userId"]?.toString()?.trim().orEmpty()
                            (tTenant.isEmpty() || tTenant == tenantId) && (tUser.isEmpty() || tUser == userId)
                        }
                    } else {
                        tasks
                    }
                    JsonUtil.toJsonString(
                        mapOf(
                            "ok" to true,
                            "count" to filtered.size,
                            "tasks" to filtered.map { t ->
                                linkedMapOf(
                                    "taskId" to t["taskId"],
                                    "title" to t["title"],
                                    "taskType" to (t["taskType"] ?: "AGENT_TASK"),
                                    "frequency" to t["frequency"],
                                    "scheduleTime" to t["scheduleTime"],
                                    "nextRunAtMs" to (t["nextRunAtMs"] ?: 0L),
                                    "status" to t["status"],
                                    "enabled" to t["enabled"],
                                    "prompt" to t["prompt"]
                                )
                            }
                        )
                    )
                }

                "pause", "stop" -> {
                    val taskId = resolveTaskId(agent, args)
                        ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Specify 'taskId' or matching 'title' to pause."))
                    val res = agent.pauseTask(taskId)
                    JsonUtil.toJsonString(mapOf("ok" to !res.contains("\"error\""), "action" to "paused", "taskId" to taskId, "result" to JsonUtil.parseJsonStringToMap(res)))
                }

                "resume", "enable", "start" -> {
                    val taskId = resolveTaskId(agent, args)
                        ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Specify 'taskId' or matching 'title' to resume."))
                    val res = agent.resumeTask(taskId)
                    JsonUtil.toJsonString(mapOf("ok" to !res.contains("\"error\""), "action" to "resumed", "taskId" to taskId, "result" to JsonUtil.parseJsonStringToMap(res)))
                }

                "cancel", "delete", "remove" -> {
                    val taskId = resolveTaskId(agent, args)
                        ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Specify 'taskId' or matching 'title' to cancel/delete."))
                    val res = agent.deleteTask(taskId)
                    JsonUtil.toJsonString(mapOf("ok" to !res.contains("\"error\""), "action" to "deleted", "taskId" to taskId, "result" to JsonUtil.parseJsonStringToMap(res)))
                }

                "run_now", "run", "trigger" -> {
                    val taskId = resolveTaskId(agent, args)
                        ?: return JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Specify 'taskId' or matching 'title' to run."))
                    val res = agent.runTaskNow(taskId)
                    JsonUtil.toJsonString(mapOf("ok" to !res.contains("\"error\""), "action" to "triggered", "taskId" to taskId, "result" to JsonUtil.parseJsonStringToMap(res)))
                }

                else -> JsonUtil.toJsonString(
                    mapOf("ok" to false, "error" to "Unsupported action '$action'. Use create_reminder, create_task, list, pause, resume, cancel, or run_now.")
                )
            }
        } catch (e: Exception) {
            JsonUtil.toJsonString(mapOf("ok" to false, "error" to (e.message ?: "Task scheduling failed.")))
        }
    }

    private fun resolveTaskId(agent: AutonomousAgent, args: Map<String, Any?>): String? {
        val explicit = (args["taskId"] ?: args["id"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit
        val query = (args["title"] ?: args["query"] ?: args["prompt"] ?: args["message"])?.toString()?.trim()?.lowercase().orEmpty()
        if (query.isEmpty()) return null
        val tasks = agent.listTasks()
        return tasks.firstOrNull { t ->
            val title = t["title"]?.toString()?.lowercase().orEmpty()
            val prompt = t["prompt"]?.toString()?.lowercase().orEmpty()
            title.contains(query) || prompt.contains(query)
        }?.get("taskId")?.toString()
    }

    companion object {
        /**
         * Resolves exact epoch milliseconds from `scheduledAtMs`, `delayMinutes`, or ISO-8601 `scheduledAt`
         * (e.g., `2026-12-03T14:30:00`, `2026-12-03 14:30`, `2026-12-03T14:30:00Z`, or `2026-12-03`).
         */
        fun resolveScheduledAtMs(args: Map<String, Any?>, nowMs: Long = PlatformSystem.currentTimeMillis()): Long {
            val directMs = (args["scheduledAtMs"] as? Number)?.toLong()
                ?: args["scheduledAtMs"]?.toString()?.toLongOrNull()
            if (directMs != null && directMs > 0L) return directMs

            val delayMins = (args["delayMinutes"] as? Number)?.toLong()
                ?: args["delayMinutes"]?.toString()?.toLongOrNull()
            if (delayMins != null && delayMins > 0L) {
                return nowMs + delayMins * 60_000L
            }

            val rawIso = (args["scheduledAt"] ?: args["dateTime"] ?: args["datetime"] ?: args["remindAt"])
                ?.toString()?.trim().orEmpty()
            if (rawIso.isEmpty()) return 0L
            return parseDateTimeToEpochMs(rawIso)
        }

        fun parseDateTimeToEpochMs(raw: String): Long {
            val cleaned = raw.trim()
            if (cleaned.isEmpty()) return 0L
            // 1. Instant with explicit offset or Z (e.g. 2026-12-03T14:30:00Z or 2026-12-03T14:30:00-06:00)
            try {
                return Instant.parse(cleaned).toEpochMilliseconds()
            } catch (_: Exception) {
            }
            // 2. LocalDateTime without timezone (e.g. 2026-12-03T14:30:00 or 2026-12-03 14:30)
            try {
                val normalized = cleaned.replace(' ', 'T')
                val withSeconds = if (normalized.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$"))) {
                    "$normalized:00"
                } else {
                    normalized
                }
                val ldt = LocalDateTime.parse(withSeconds)
                return ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } catch (_: Exception) {
            }
            // 3. Date-only (e.g. 2026-12-03) -> default 09:00 local time
            try {
                val date = LocalDate.parse(cleaned)
                val ldt = LocalDateTime(date, LocalTime(9, 0, 0))
                return ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } catch (_: Exception) {
            }
            return 0L
        }
    }
}
