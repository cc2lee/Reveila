package com.reveila.ai;

import java.util.Map;

import com.reveila.system.PluginPrincipal;

/**
 * A local or dummy GuardedRuntime implementation primarily for Android and non-Docker environments.
 * It executes plugins directly without physical isolation but can enforce Java-based
 * security constraints or simply delegate execution.
 * 
 * @author CL
 */
public class LocalGuardedRuntime implements GuardedRuntime {

    @Override
    public Object execute(PluginPrincipal principal, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        // Since we cannot spawn isolated Docker containers on Android,
        // we could potentially use a custom classloader or simply log the execution here.
        // For a true local execution without overhead, this could act as a pass-through
        // or a lightweight sandbox.
        System.out.println("LocalGuardedRuntime executing plugin " + pluginId + " with principal " + principal);
        
        // The actual invocation of the plugin methods would happen via the systemContext or
        // a direct proxy call. If Proxy.invoke() delegates here, we'd need a reference back
        // to the proxy or system context to actually perform the reflective call.
        
        // This is a dummy implementation, so we return null or a generic success message.
        return null;
    }

}
