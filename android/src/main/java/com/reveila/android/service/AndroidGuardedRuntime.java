package com.reveila.android.service;

import com.reveila.ai.InvocationResult;
import com.reveila.ai.SecurityPerimeter;
import com.reveila.system.Plugin;
import com.reveila.ai.AbstractGuardedRuntime;
import com.reveila.system.SystemProxy;
import com.reveila.system.Proxy;
import java.util.Map;
import android.content.Context;
import java.io.File;

import com.reveila.android.AndroidPlugins;
import com.reveila.android.AndroidPlatformAdapter;

/**
 * Android-specific implementation of GuardedRuntime.
 * Uses DexClassLoader for isolation instead of Docker.
 * 
 * @author CL
 */
public class AndroidGuardedRuntime extends AbstractGuardedRuntime {
    
    private Context androidContext;

    public AndroidGuardedRuntime() {
    }

    /**
     * Required for Proxy-based retrieval.
     */
    public com.reveila.ai.GuardedRuntime getInstance() {
        return this;
    }

    @Override
    protected InvocationResult onExecute(Plugin plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        String pluginId = plugin.getName();
        
        long startTime = System.currentTimeMillis();
        logger.info("Executing via AndroidGuardedRuntime for " + pluginId + " [Trace: " + plugin.getTraceId() + "] Started at: " + startTime);

        if (arguments == null || !arguments.containsKey("method")) {
            throw new IllegalArgumentException("Invocation arguments must specify the target 'method' name.");
        }
        
        Map<String, Object> argsMap = new java.util.LinkedHashMap<>(arguments);
        String methodName = (String) argsMap.remove("method");
        
        // The remaining arguments are passed to the plugin method
        Object[] argsArray = argsMap.values().toArray();
        
        try {
            Proxy proxy = this.context.getProxy(pluginId);
            if (proxy == null) {
                throw new RuntimeException("Failed to locate proxy for plugin: " + pluginId);
            }

            Object result = proxy.invoke(methodName, argsArray);
            return InvocationResult.success(result);
        } catch (Exception e) {
            return InvocationResult.error("Execution failed: " + e.getMessage());
        }
    }

    @Override
    protected void onStart() throws Exception {
        this.androidContext = ((AndroidPlatformAdapter) this.context.getPlatformAdapter()).getAndroidContext();
        logger.info(this.getClass().getSimpleName() + " started.");
    }

    @Override
    protected void onStop() throws Exception {
        logger.info(this.getClass().getSimpleName() + " stopped.");
    }
}
