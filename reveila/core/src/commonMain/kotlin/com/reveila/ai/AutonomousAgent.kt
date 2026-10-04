package com.reveila.ai

import com.reveila.system.Constants
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil
import kotlinx.datetime.Clock

/**
 * Autonomous System Agent that performs recurring tasks defined in JSON.
 * These tasks are stored in the system-home/standard/tasks directory.
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

        val timestamp = Clock.System.now().toString().substringBefore('T')

        val fabricProxy = context?.getProxy("AgenticFabric")
        this.agenticFabric = (fabricProxy?.getInstance() as? AgenticFabric) ?: fabricProxy as? AgenticFabric

        val orchProxy = context?.getProxy("OrchestrationService")
        this.orchestrationService = (orchProxy?.getInstance() as? OrchestrationService) ?: orchProxy as? OrchestrationService

        this.session = orchestrationService?.createSession("autonomous-agent-session $timestamp")
        logger.info("AutonomousAgent started. Ready to process recurring tasks.")
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
            logger.warning("SYSTEM_HOME is not set. Unable to process autonomous tasks.")
            return
        }

        val fs = PlatformFileSystem()
        val tasksDir = fs.resolve(systemHome, "tasks")
        if (!fs.exists(tasksDir)) {
            fs.createDirectories(tasksDir)
            return
        }

        val taskFiles = fs.listRelativePaths(tasksDir, "json").map { fs.resolve(tasksDir, it) }
        if (taskFiles.isEmpty()) {
            return
        }

        for (file in taskFiles) {
            try {
                processTaskFile(file)
            } catch (e: Exception) {
                logger.severe("Failed to process autonomous task: $file. Error: ${e.message}")
            }
        }
    }

    @Throws(Exception::class)
    private fun processTaskFile(filePath: String) {
        val fs = PlatformFileSystem()
        val content = fs.readText(filePath)
        val taskDef = JsonUtil.parseJsonStringToMap(content)

        val taskId = taskDef["taskId"] as? String
        val prompt = taskDef["prompt"] as? String

        if (taskId == null || prompt == null) {
            logger.warning("Skipping invalid task definition in $filePath")
            return
        }

        logger.info("[AUTONOMOUS] Starting task loop: $taskId")
        val fabric = agenticFabric
        val sess = session
        val subj = subject
        if (fabric != null && sess != null && subj != null) {
            val finalResult = fabric.processIntent(sess, subj, prompt)
            logger.info("[AUTONOMOUS] Task $taskId completed. Result summary: $finalResult")
        } else {
            logger.warning("Cannot process task $taskId: fabric, session, or subject is null.")
        }
    }
}
