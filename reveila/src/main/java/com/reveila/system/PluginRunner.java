package com.reveila.system;

/**
 * The Mini-Reveila Runtime (Reveila Worker Agent).
 * ADR 0006: Executes plugin code in total isolation within a container.
 * 
 * @author CL
 */
public class PluginRunner {
    public static void main(String[] args) {
        String pluginId = System.getenv("PLUGIN_ID");
        String methodName = System.getenv("METHOD_NAME");
        String traceId = System.getenv("TRACE_ID");

        System.out.println("--- Reveila Worker Agent Starting ---");
        System.out.println("Plugin: " + pluginId);
        System.out.println("Method: " + methodName);
        System.out.println("Trace: " + traceId);

        try {
            // Standardize entry point for containerized plugins.
            // In a real implementation, this would use reflection to load the plugin class
            // from the mounted /app/plugin.jar and execute the requested method.
            
            System.out.println("Executing " + methodName + " on " + pluginId + "...");
            
            // Simulating execution
            Thread.sleep(100); 
            
            System.out.println("--- Execution Completed Successfully ---");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
