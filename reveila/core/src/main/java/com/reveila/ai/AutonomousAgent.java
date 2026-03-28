package com.reveila.ai;

import com.reveila.system.AbstractService;
import com.reveila.system.PluginPrincipal;
import com.reveila.system.SystemProxy;
import com.reveila.util.json.JsonUtil;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * Autonomous System Agent that performs recurring tasks defined in JSON.
 * These tasks are stored in the system-home/standard/tasks directory.
 * 
 * @author Charles Lee
 */
public class AutonomousAgent extends AbstractService {

    private AgenticFabric agenticFabric;
    private OrchestrationService orchestrationService;

    @Override
    protected void onStart() throws Exception {
        // Wiring dependencies via the Proxy system
        this.agenticFabric = (AgenticFabric) ((SystemProxy) context.getProxy("AgenticFabric")
                .orElseThrow(() -> new IllegalStateException("AgenticFabric not found")))
                .getInstance();
                
        this.orchestrationService = (OrchestrationService) ((SystemProxy) context.getProxy("OrchestrationService")
                .orElseThrow(() -> new IllegalStateException("OrchestrationService not found")))
                .getInstance();
                
        logger.info("AutonomousAgent started. Ready to process recurring tasks.");
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Entry point for recurring task execution, triggered by the 'runnable' configuration.
     * Iterates through the tasks directory and executes each defined AI workflow.
     */
    public void doTask() {
        String tasksDirPath = context.getProperties().getProperty("system.home", "../../system-home/standard") + "/tasks";
        File tasksDir = new File(tasksDirPath);
        
        if (!tasksDir.exists() || !tasksDir.isDirectory()) {
            logger.warning("Autonomous tasks directory not found: " + tasksDirPath);
            return;
        }

        File[] taskFiles = tasksDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (taskFiles == null || taskFiles.length == 0) {
            return;
        }

        for (File file : taskFiles) {
            try {
                processTaskFile(file);
            } catch (Exception e) {
                logger.severe("Failed to process autonomous task file: " + file.getName() + ". Error: " + e.getMessage());
            }
        }
    }

    private void processTaskFile(File file) throws Exception {
        String content = Files.readString(file.toPath());
        Map<String, Object> taskDef = JsonUtil.parseJsonStringToMap(content);
        
        String taskId = (String) taskDef.get("taskId");
        String prompt = (String) taskDef.get("prompt");
        
        if (taskId == null || prompt == null) {
            logger.warning("Skipping invalid task definition in " + file.getName());
            return;
        }

        // Create a dedicated security principal for this autonomous task
        PluginPrincipal principal = PluginPrincipal.create("autonomous-agent-" + taskId, "system-internal");
        
        // Create a new session (topic) for the task
        AgentSession session = orchestrationService.createSession(principal.getTraceId());
        
        logger.info("[AUTONOMOUS] Starting task loop: " + taskId);
        
        // Execute the AI Session Loop
        String finalResult = agenticFabric.processLoop(session, principal, prompt);
        
        logger.info("[AUTONOMOUS] Task " + taskId + " completed. Result summary: " + finalResult);
    }
}
