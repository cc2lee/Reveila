package com.reveila.ai

import com.reveila.safety.SecurityPerimeter
import com.reveila.system.platform.PlatformSystem
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

/**
 * Isolated execution sandbox for an individual autonomous task run.
 * Encapsulates an isolated [AgentSession], a scoped [SecurityPerimeter],
 * cooperative suspension gates, and preemptive cancellation handles.
 */
class TaskExecutionSandbox(
    val sandboxId: String,
    val taskId: String,
    val title: String,
    val session: AgentSession,
    val perimeter: SecurityPerimeter = SecurityPerimeter(
        accessScopes = setOf("vault.read", "ai.infer", "task.execute"),
        allowedDomains = emptySet(),
        internetAccessBlocked = false,
        maxMemoryMb = 256L,
        maxCpuCores = 1,
        maxExecutionSec = 120,
        delegationAllowed = false
    ),
    val startedAt: Long = PlatformSystem.currentTimeMillis()
) {
    @Volatile
    var state: String = STATE_RUNNING
        private set

    @Volatile
    var currentStep: String = "Initializing sandbox [$sandboxId]..."

    @Volatile
    var workerJob: Job? = null

    fun isActive(): Boolean = state == STATE_RUNNING || state == STATE_SUSPENDED

    fun isSuspended(): Boolean = state == STATE_SUSPENDED

    fun isKilled(): Boolean = state == STATE_KILLED

    fun suspendExecution(): Boolean {
        if (state == STATE_RUNNING) {
            state = STATE_SUSPENDED
            currentStep = "Suspended in sandbox [$sandboxId] — awaiting resume or termination"
            return true
        }
        return false
    }

    fun resumeExecution(): Boolean {
        if (state == STATE_SUSPENDED) {
            state = STATE_RUNNING
            currentStep = "Resumed execution in sandbox [$sandboxId]..."
            return true
        }
        return false
    }

    fun killExecution(): Boolean {
        state = STATE_KILLED
        currentStep = "Terminated in sandbox [$sandboxId]"
        workerJob?.cancel(CancellationException("Task [$taskId] killed by user in sandbox [$sandboxId]"))
        return true
    }

    fun markCompleted(summaryStep: String = "Completed in sandbox [$sandboxId]") {
        state = STATE_COMPLETED
        currentStep = summaryStep
    }

    fun markFailed(errorStep: String) {
        state = STATE_FAILED
        currentStep = errorStep
    }

    /**
     * Cooperative suspension and preemptive kill gate checked before and between
     * task execution stages, vault lookups, and LLM invocations.
     */
    suspend fun checkExecutionGate(
        stepLabel: String,
        isGlobalSuspended: () -> Boolean = { false }
    ) {
        val jobActive = currentCoroutineContext()[Job]?.isActive ?: true
        if (state == STATE_KILLED || !jobActive) {
            throw CancellationException("Task [$taskId] killed in sandbox [$sandboxId].")
        }

        while (state == STATE_SUSPENDED || isGlobalSuspended()) {
            val stillActive = currentCoroutineContext()[Job]?.isActive ?: true
            if (state == STATE_KILLED || !stillActive) {
                throw CancellationException("Task [$taskId] killed while suspended in sandbox [$sandboxId].")
            }
            delay(250)
        }

        currentStep = stepLabel
    }

    fun toMetadataMap(): Map<String, Any?> = linkedMapOf(
        "sandboxId" to sandboxId,
        "taskId" to taskId,
        "title" to title,
        "sessionId" to session.sessionId,
        "state" to state,
        "currentStep" to currentStep,
        "startedAt" to startedAt,
        "maxMemoryMb" to perimeter.maxMemoryMb,
        "maxExecutionSec" to perimeter.maxExecutionSec
    )

    companion object {
        const val STATE_RUNNING = "RUNNING"
        const val STATE_SUSPENDED = "SUSPENDED"
        const val STATE_COMPLETED = "COMPLETED"
        const val STATE_KILLED = "KILLED"
        const val STATE_FAILED = "FAILED"
    }
}
