package com.reveila.ai;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import com.reveila.system.Constants;
import com.reveila.system.PluginPrincipal;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;
import com.reveila.util.json.JsonUtil;

/**
 * Autonomous System Agent that performs recurring tasks defined in JSON.
 * These tasks are stored in the system-home/standard/tasks directory.
 * 
 * @author Charles Lee
 */
public class AutonomousAgent extends SystemComponent {

    private AgenticFabric agenticFabric;
    private OrchestrationService orchestrationService;

    @Override
    protected void onStart() throws Exception {
        // Wiring dependencies via the Proxy system
        this.agenticFabric = (AgenticFabric) ((SystemProxy) context.getProxy("AgenticFabric")).getInstance();
        this.orchestrationService = (OrchestrationService) ((SystemProxy) context.getProxy("OrchestrationService")).getInstance();
                
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
        String systemHome = context.getProperties().getProperty(Constants.SYSTEM_HOME);
        if (systemHome == null || systemHome.isBlank()) {
            logger.warning("SYSTEM_HOME is not set. Unable to process autonomous tasks.");
            return;
        }
        
        File tasksDir = new File(systemHome, "tasks");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
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
                logger.severe("Failed to process autonomous task: " + file.getName() + ". Error: " + e.getMessage());
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
        String finalResult = agenticFabric.processIntent(session, principal, prompt);
        
        logger.info("[AUTONOMOUS] Task " + taskId + " completed. Result summary: " + finalResult);
    }
}
