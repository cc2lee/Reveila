package com.reveila.ai;

import java.time.Duration;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.reveila.system.Plugin;

/**
 * Docker-based implementation of GuardedRuntime using gVisor (runsc).
 * Maps SecurityPerimeter resource records to Docker HostConfig settings.
 * 
 * Registered as "GuardedRuntime" in standard server environments.
 * 
 * @author CL
 */
public class DockerGuardedRuntime extends AbstractGuardedRuntime {
    
    private final DockerClient dockerClient;

    public DockerGuardedRuntime(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public DockerGuardedRuntime() {
        this(createDefaultClient());
    }

    /**
     * Required for Proxy-based retrieval.
     */
   private static DockerClient createDefaultClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    protected InvocationResult onExecute(Plugin plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        String pluginId = plugin.getName();
        long startTime = System.currentTimeMillis();
        logger.info("Executing via DockerGuardedRuntime for " + pluginId + " [Trace: " + plugin.getTraceId() + "] Started at: " + startTime);

        // Filesystem Isolation: Mount the plugin JAR as a read-only volume
        String pluginJarPath = "/opt/reveila/plugins/" + pluginId + ".jar";
        Volume pluginVolume = new Volume("/app/plugin.jar");

        // RESOURCE MAPPING: SecurityPerimeter -> Docker HostConfig (cgroups)
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withRuntime("runsc") // Force gVisor kernel isolation
                .withMemory(perimeter.maxMemoryMb() * 1024 * 1024) // RAM Limit
                .withCpuPeriod(100000L) // Default 100ms
                .withCpuQuota((long) (perimeter.maxCpuCores() * 100000))   // CPU Quota
                .withPidsLimit(100L) // Default limit
                .withBinds(new Bind(pluginJarPath, pluginVolume, AccessMode.ro)) // Read-Only filesystem
                .withAutoRemove(true);

        // Environment Variables & JIT Credentials
        String callbackUrl = context.getProperties().getProperty("system.callback.url", "http://host.docker.internal:8080");
        String jitToken = "JIT-" + java.util.UUID.randomUUID().toString(); // Temporary token generation
        
        java.util.List<String> envVars = new java.util.ArrayList<>(java.util.List.of(
            "PLUGIN_ID=" + pluginId,
            "TRACE_ID=" + plugin.getTraceId(),
            "TENANT_ID=" + plugin.getTenantId(),
            "REVEILA_CALLBACK_URL=" + callbackUrl,
            "REVEILA_JIT_TOKEN=" + jitToken
        ));

        // ADR 0006: Get the actual implementation class from the system registry if available
        String pluginClass = pluginId; // Fallback
        try {
            com.reveila.system.Proxy proxy = context.getProxy(pluginId);
            if (proxy instanceof com.reveila.system.SystemProxy sp) {
                pluginClass = sp.getInstance().getClass().getName();
            }
        } catch (Exception e) {
            logger.warning("Could not resolve implementation class for plugin: " + pluginId);
        }
        envVars.add("PLUGIN_CLASS=" + pluginClass);

        if (jitCredentials != null) {
            jitCredentials.forEach((k, v) -> envVars.add(k + "=" + v));
        }

        String methodName = "defaultMethod";
        Map<String, Object> argsMap = null;
        if (arguments != null) {
            argsMap = new java.util.LinkedHashMap<>(arguments);
            if (argsMap.containsKey("method")) {
                methodName = (String) argsMap.remove("method");
            }
        }
        envVars.add("METHOD_NAME=" + methodName);

        // Serialize Arguments to JSON
        if (argsMap != null) {
            try {
                String argsJson = com.reveila.util.json.JsonUtil.toJsonString(argsMap);
                envVars.add("PLUGIN_ARGS_JSON=" + argsJson);
            } catch (Exception e) {
                logger.warning("Failed to serialize plugin arguments for " + pluginId);
            }
        }

        // Network Policy from Perimeter
        if (perimeter != null) {
            envVars.add("NETWORK_RESTRICTED=" + (perimeter.internetAccessBlocked() || (perimeter.allowedDomains() != null && !perimeter.allowedDomains().isEmpty())));
        }

        CreateContainerResponse container = dockerClient.createContainerCmd("reveila-plugin-executor:latest")
                .withHostConfig(hostConfig)
                .withEnv(envVars)
                .withCmd("java", "-cp", "/app/plugin.jar:/app/reveila-core.jar", "com.reveila.system.PluginRunner")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return InvocationResult.success(null, "Execution started in Docker container " + container.getId() + " (gVisor)");
    }

    @Override
    protected void onStop() throws Exception {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                logger.info("DockerGuardedRuntime stopped: DockerClient connection closed.");
            } catch (Exception e) {
                logger.warning("Error closing DockerClient: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStart() throws Exception {
        // Ensure the Docker client is responsive during boot
        try {
            dockerClient.pingCmd().exec();
            logger.info("DockerGuardedRuntime started: Successfully pinged Docker daemon.");
        } catch (Exception e) {
            logger.severe("Failed to initialize DockerGuardedRuntime: " + e.getMessage());
            throw new com.reveila.error.SystemException("Docker daemon not reachable", e);
        }
    }
}
