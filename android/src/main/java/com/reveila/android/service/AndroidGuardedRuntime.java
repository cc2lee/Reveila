package com.reveila.android.service;

import com.reveila.ai.SecurityPerimeter;
import com.reveila.system.InvocationTarget;
import com.reveila.ai.AbstractGuardedRuntime;
import com.reveila.system.SystemProxy;
import java.util.Map;
import android.content.Context;
import java.io.File;

import com.reveila.android.AndroidPlugins;

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
    public Object execute(InvocationTarget plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        validateRequest(plugin, perimeter);
        String pluginId = plugin.getTargetName();
        
        long startTime = System.currentTimeMillis();
        logger.info("Executing via AndroidGuardedRuntime for " + pluginId + " [Trace: " + plugin.getTraceId() + "] Started at: " + startTime);

        if (arguments == null || !arguments.containsKey("method")) {
            throw new IllegalArgumentException("Invocation arguments must specify the target 'method' name.");
        }
        
        String methodName = String.valueOf(arguments.get("method"));
        Object[] argsArray = arguments.containsKey("args") ? (Object[]) arguments.get("args") : new Object[0];
        
        try {
            com.reveila.system.Proxy proxy = this.context.getProxy(pluginId);
            if (proxy == null) {
                throw new RuntimeException("Failed to locate proxy for plugin: " + pluginId);
            }

            // Load InvocationTarget library if exists
            String pluginFileName = pluginId + ".jar";
            File pluginDir = new File(androidContext.getFilesDir(), "plugins/" + pluginId);
            File pluginFile = new File(pluginDir, pluginFileName);

            if (pluginFile.exists() && proxy instanceof SystemProxy) {
                // Add the child first android dex class loader here
                ClassLoader currentLoader = ((SystemProxy) proxy).getClass().getClassLoader();
                ClassLoader pluginLoader = new com.reveila.android.ChildFirstDexClassLoader(
                    pluginFile.getAbsolutePath(),
                    pluginDir.getAbsolutePath(),
                    null,
                    currentLoader != null ? currentLoader : this.getClass().getClassLoader()
                );
                
                java.lang.reflect.Method setClassLoaderMethod = SystemProxy.class.getDeclaredMethod("setClassLoader", ClassLoader.class);
                setClassLoaderMethod.setAccessible(true);
                setClassLoaderMethod.invoke(proxy, pluginLoader);
            }
            
            return proxy.invoke(methodName, argsArray);
        } catch (Exception e) {
            logger.severe("Execution failed in AndroidGuardedRuntime: " + e.getMessage());
            throw new RuntimeException("InvocationTarget execution failed", e);
        }
    }

    @Override
    protected void onStart() throws Exception {
        this.androidContext = ((com.reveila.android.AndroidPlatformAdapter) this.context.getPlatformAdapter()).getAndroidContext();
        logger.info("AndroidGuardedRuntime started: Native Android isolation active.");
    }

    @Override
    protected void onStop() throws Exception {
        logger.info("AndroidGuardedRuntime stopped.");
    }
}
