package com.reveila.ai

import com.reveila.system.Constants
import com.reveila.system.RolePrincipal
import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy
import com.reveila.util.json.JsonUtil
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.security.auth.Subject

/**
 * Autonomous System Agent that performs recurring tasks defined in JSON.
 * These tasks are stored in the system-home/standard/tasks directory.
 *
 * @author Charles Lee
 */
open class AutonomousAgent : SystemComponent() {

    private var agenticFabric: AgenticFabric? = null
    private var orchestrationService: OrchestrationService? = null
    private var session: AgentSession? = null
    private var subject: Subject? = null

    @Throws(Exception::class)
    override fun onStart() {
        val s = Subject()
        s.principals.add(RolePrincipal("system"))
        s.principals.add(RolePrincipal("autonomous-agent"))
        this.subject = s

        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formattedTimestamp = now.format(formatter)

        this.agenticFabric = (context?.getProxy("AgenticFabric") as? SystemProxy)?.getInstance() as? AgenticFabric
            ?: context?.getProxy("AgenticFabric") as? AgenticFabric
        this.orchestrationService = (context?.getProxy("OrchestrationService") as? SystemProxy)?.getInstance() as? OrchestrationService
            ?: context?.getProxy("OrchestrationService") as? OrchestrationService

        this.session = orchestrationService?.createSession("autonomous-agent-session $formattedTimestamp")
        logger?.info("AutonomousAgent started. Ready to process recurring tasks.")
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    /**
     * Entry point for recurring task execution, triggered by the 'runnable' configuration.
     * Iterates through the tasks directory and executes each defined AI workflow.
     */
    open fun doTask() {
        val systemHome = context?.properties?.getProperty(Constants.SYSTEM_HOME)
        if (systemHome.isNullOrBlank()) {
            logger?.warning("SYSTEM_HOME is not set. Unable to process autonomous tasks.")
            return
        }

        val tasksDir = File(systemHome, "tasks")
        if (!tasksDir.exists()) {
            tasksDir.mkdirs()
            return
        }

        val taskFiles = tasksDir.listFiles { _, name -> name.lowercase().endsWith(".json") }
        if (taskFiles == null || taskFiles.isEmpty()) {
            return
        }

        for (file in taskFiles) {
            try {
                processTaskFile(file)
            } catch (e: Exception) {
                logger?.severe("Failed to process autonomous task: ${file.name}. Error: ${e.message}")
            }
        }
    }

    @Throws(Exception::class)
    private fun processTaskFile(file: File) {
        val content = Files.readString(file.toPath())
        val taskDef = JsonUtil.parseJsonStringToMap(content)

        val taskId = taskDef["taskId"] as? String
        val prompt = taskDef["prompt"] as? String

        if (taskId == null || prompt == null) {
            logger?.warning("Skipping invalid task definition in ${file.name}")
            return
        }

        logger?.info("[AUTONOMOUS] Starting task loop: $taskId")
        val fabric = agenticFabric
        val sess = session
        val subj = subject
        if (fabric != null && sess != null && subj != null) {
            val finalResult = fabric.processIntent(sess, subj, prompt)
            logger?.info("[AUTONOMOUS] Task $taskId completed. Result summary: $finalResult")
        } else {
            logger?.warning("Cannot process task $taskId: fabric, session, or subject is null.")
        }
    }
}
