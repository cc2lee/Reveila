package com.reveila.android;

import com.reveila.ai.AgencyPerimeter;
import com.reveila.system.PluginPrincipal;
import com.reveila.ai.AbstractGuardedRuntime;
import com.reveila.system.SystemProxy;
import java.util.Map;
import android.content.Context;
import java.io.File;

/**
 * Android-specific implementation of GuardedRuntime.
 * Uses DexClassLoader for isolation instead of Docker.
 * 
 * @author CL
 */
public class AndroidGuardedRuntime extends AbstractGuardedRuntime {
    
    private final Context context;

    public AndroidGuardedRuntime(Context context) {
        this.context = context;
    }

    /**
     * Required for Proxy-based retrieval.
     */
    public com.reveila.ai.GuardedRuntime getInstance() {
        return this;
    }

    @Override
    public Object execute(PluginPrincipal principal, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        validateRequest(principal, perimeter);
        
        long startTime = System.currentTimeMillis();
        logger.info("Executing via AndroidGuardedRuntime for " + pluginId + " [Trace: " + principal.getTraceId() + "] Started at: " + startTime);

        // Filesystem Isolation: Plugins are stored in app-private storage
        String pluginFileName = pluginId + ".jar"; // Or .dex
        
        // Use SafePluginLoader to create a proxy and execute
        // Note: In a real implementation, we'd map perimeter constraints to Android specific restrictions
        try {
            SystemProxy proxy = SafePluginLoader.loadPlugin(context, pluginFileName, "com.reveila.plugin.EntryPoint");
            if (proxy == null) {
                throw new RuntimeException("Failed to load plugin: " + pluginId);
            }
            
            String methodName = arguments != null ? String.valueOf(arguments.get("method")) : "execute";
            return proxy.invoke(methodName, new Object[]{arguments});
        } catch (Exception e) {
            logger.severe("Execution failed in AndroidGuardedRuntime: " + e.getMessage());
            throw new RuntimeException("Plugin execution failed", e);
        }
    }

    @Override
    protected void onStart() throws Exception {
        logger.info("AndroidGuardedRuntime started: Native Android isolation active.");
    }

    @Override
    protected void onStop() throws Exception {
        logger.info("AndroidGuardedRuntime stopped.");
    }
}
