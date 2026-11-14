package com.reveila.rt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.reveila.safety.AbstractGuardedRuntime;
import com.reveila.safety.InvocationResult;
import com.reveila.safety.SecretManager;
import com.reveila.safety.SecurityPerimeter;
import com.reveila.system.Constants;
import com.reveila.system.Context;
import com.reveila.system.Plugin;
import com.reveila.system.Proxy;
import com.reveila.system.SystemProxy;
import com.reveila.util.json.JsonUtil;

/**
 * Docker-based implementation of GuardedRuntime using gVisor (runsc).
 * Maps SecurityPerimeter resource records to Docker HostConfig settings and
 * provisions the container with standardized callback & version credentials
 * for the unified `/api/components/{componentName}/invoke` architecture.
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
        getLogger().info("Executing via DockerGuardedRuntime for " + pluginId + " [Trace: " + plugin.getTraceId() + "] Started at: " + startTime);

        // Filesystem Isolation: Mount the plugin JAR as a read-only volume
        String pluginJarPath = "/opt/reveila/plugins/" + pluginId + ".jar";
        Volume pluginVolume = new Volume("/app/plugin.jar");

        long maxMemoryMb = (perimeter != null && perimeter.maxMemoryMb() > 0) ? perimeter.maxMemoryMb() : 512L;
        int maxCpuCores = (perimeter != null && perimeter.maxCpuCores() > 0) ? perimeter.maxCpuCores() : 1;

        // RESOURCE MAPPING: SecurityPerimeter -> Docker HostConfig (cgroups)
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withRuntime("runsc") // Force gVisor kernel isolation
                .withMemory(maxMemoryMb * 1024L * 1024L) // RAM Limit
                .withCpuPeriod(100000L) // Default 100ms
                .withCpuQuota((long) (maxCpuCores * 100000L)) // CPU Quota
                .withPidsLimit(100L) // Default limit
                .withBinds(new Bind(pluginJarPath, pluginVolume, AccessMode.ro)) // Read-Only filesystem
                .withAutoRemove(true);

        // Environment Variables, System Version & JIT Credentials
        Context ctx = getContext();
        String callbackUrl = (ctx != null && ctx.getProperties() != null)
                ? ctx.getProperties().getProperty("system.callback.url", "http://host.docker.internal:8080")
                : "http://host.docker.internal:8080";
        String systemVersion = (ctx != null && ctx.getProperties() != null)
                ? ctx.getProperties().getProperty(Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION)
                : Constants.DEFAULT_SYSTEM_VERSION;
        if (systemVersion == null || systemVersion.isBlank()) {
            systemVersion = Constants.DEFAULT_SYSTEM_VERSION;
        }

        String jitToken = resolveJitToken(ctx, plugin, jitCredentials);

        List<String> envVars = new ArrayList<>(List.of(
            "PLUGIN_ID=" + pluginId,
            "TRACE_ID=" + plugin.getTraceId(),
            "TENANT_ID=" + plugin.getTenantId(),
            Constants.ENV_CALLBACK_URL + "=" + callbackUrl,
            Constants.ENV_JIT_TOKEN + "=" + jitToken,
            Constants.ENV_REVEILA_VERSION + "=" + systemVersion
        ));

        // ADR 0006: Get the actual implementation class from the system registry if available
        String pluginClass = pluginId; // Fallback
        try {
            if (ctx != null) {
                Proxy proxy = ctx.getProxy(pluginId);
                if (proxy instanceof SystemProxy sp) {
                    pluginClass = sp.getInstance().getClass().getName();
                }
            }
        } catch (Exception e) {
            getLogger().warning("Could not resolve implementation class for plugin: " + pluginId);
        }
        envVars.add("PLUGIN_CLASS=" + pluginClass);

        if (jitCredentials != null) {
            jitCredentials.forEach((k, v) -> {
                if (!Constants.ENV_JIT_TOKEN.equals(k)
                        && !Constants.ENV_CALLBACK_URL.equals(k)
                        && !Constants.ENV_REVEILA_VERSION.equals(k)) {
                    envVars.add(k + "=" + v);
                }
            });
        }

        String methodName = "defaultMethod";
        Map<String, Object> argsMap = null;
        if (arguments != null) {
            argsMap = new LinkedHashMap<>(arguments);
            if (argsMap.containsKey("method")) {
                methodName = String.valueOf(argsMap.remove("method"));
            }
        }
        envVars.add("METHOD_NAME=" + methodName);

        // Serialize Arguments to JSON
        if (argsMap != null) {
            try {
                String argsJson = JsonUtil.Companion.toJsonString(argsMap);
                envVars.add("PLUGIN_ARGS_JSON=" + argsJson);
            } catch (Exception e) {
                getLogger().warning("Failed to serialize plugin arguments for " + pluginId);
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

        return InvocationResult.Companion.success(null, "Execution started in Docker container " + container.getId() + " (gVisor)");
    }

    private String resolveJitToken(Context ctx, Plugin plugin, Map<String, String> jitCredentials) {
        String providedToken = (jitCredentials != null) ? jitCredentials.get(Constants.ENV_JIT_TOKEN) : null;
        if (providedToken != null && !providedToken.isBlank()) {
            // Ensure SecretManager knows about this token if not already registered
            try {
                if (ctx != null) {
                    Object smObj = ctx.getProxy("SecretManager").getInstance();
                    if (smObj instanceof SecretManager sm && !sm.validateToken(providedToken)) {
                        sm.registerJitToken(providedToken, plugin.getTraceId() + ":sandbox:callback");
                    }
                }
            } catch (Exception ignored) {
            }
            return providedToken;
        }

        try {
            if (ctx != null) {
                Object smObj = ctx.getProxy("SecretManager").getInstance();
                if (smObj instanceof SecretManager sm) {
                    Map<String, String> generated = sm.generateJitToken(plugin, "sandbox:callback");
                    String token = generated.get(Constants.ENV_JIT_TOKEN);
                    if (token != null && !token.isBlank()) {
                        return token;
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("SecretManager not available when generating JIT token for " + plugin.getName() + "; falling back to ephemeral token.");
        }

        return "jit_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    protected void onStop() throws Exception {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                getLogger().info("DockerGuardedRuntime stopped: DockerClient connection closed.");
            } catch (Exception e) {
                getLogger().warning("Error closing DockerClient: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStart() throws Exception {
        // Ensure the Docker client is responsive during boot
        try {
            dockerClient.pingCmd().exec();
            getLogger().info("DockerGuardedRuntime started: Successfully pinged Docker daemon.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize DockerGuardedRuntime: " + e.getMessage());
            throw new com.reveila.error.SystemException("Docker daemon not reachable", e);
        }
    }
}
