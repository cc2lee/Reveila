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

        HostConfig hostConfig = buildHostConfig(pluginId, perimeter);
        List<String> envVars = buildEnvironmentVariables(getContext(), plugin, perimeter, arguments, jitCredentials);

        CreateContainerResponse container = dockerClient.createContainerCmd("reveila-plugin-executor:latest")
                .withHostConfig(hostConfig)
                .withEnv(envVars)
                .withCmd("java", "-cp", "/app/plugin.jar:/app/reveila-core.jar", "com.reveila.system.PluginRunner")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return InvocationResult.Companion.success(null, "Execution started in Docker container " + container.getId() + " (gVisor)");
    }

    private HostConfig buildHostConfig(String pluginId, SecurityPerimeter perimeter) {
        String pluginJarPath = "/opt/reveila/plugins/" + pluginId + ".jar";
        Volume pluginVolume = new Volume("/app/plugin.jar");

        long maxMemoryMb = (perimeter != null && perimeter.maxMemoryMb() > 0) ? perimeter.maxMemoryMb() : 512L;
        int maxCpuCores = (perimeter != null && perimeter.maxCpuCores() > 0) ? perimeter.maxCpuCores() : 1;

        return HostConfig.newHostConfig()
                .withRuntime("runsc") // Force gVisor kernel isolation
                .withMemory(maxMemoryMb * 1024L * 1024L) // RAM Limit
                .withCpuPeriod(100000L) // Default 100ms
                .withCpuQuota(maxCpuCores * 100000L) // CPU Quota
                .withPidsLimit(100L) // Default limit
                .withBinds(new Bind(pluginJarPath, pluginVolume, AccessMode.ro)) // Read-Only filesystem
                .withAutoRemove(true);
    }

    private List<String> buildEnvironmentVariables(
            Context ctx,
            Plugin plugin,
            SecurityPerimeter perimeter,
            Map<String, Object> arguments,
            Map<String, String> jitCredentials) {
        String pluginId = plugin.getName();
        String callbackUrl = resolveContextProperty(ctx, "system.callback.url", "http://host.docker.internal:8080");
        String systemVersion = resolveContextProperty(ctx, Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION);
        String jitToken = resolveJitToken(ctx, plugin, jitCredentials);

        List<String> envVars = new ArrayList<>(List.of(
                "PLUGIN_ID=" + pluginId,
                "TRACE_ID=" + plugin.getTraceId(),
                "TENANT_ID=" + plugin.getTenantId(),
                Constants.ENV_CALLBACK_URL + "=" + callbackUrl,
                Constants.ENV_JIT_TOKEN + "=" + jitToken,
                Constants.ENV_REVEILA_VERSION + "=" + systemVersion,
                "PLUGIN_CLASS=" + resolvePluginClass(ctx, pluginId)
        ));

        appendExtraCredentials(envVars, jitCredentials);
        appendMethodAndArguments(envVars, pluginId, arguments);

        if (perimeter != null) {
            boolean networkRestricted = perimeter.internetAccessBlocked()
                    || (perimeter.allowedDomains() != null && !perimeter.allowedDomains().isEmpty());
            envVars.add("NETWORK_RESTRICTED=" + networkRestricted);
        }

        return envVars;
    }

    private String resolveContextProperty(Context ctx, String key, String defaultValue) {
        if (ctx == null || ctx.getProperties() == null) {
            return defaultValue;
        }
        String value = ctx.getProperties().getProperty(key, defaultValue);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String resolvePluginClass(Context ctx, String pluginId) {
        if (ctx == null) {
            return pluginId;
        }
        try {
            Proxy proxy = ctx.getProxy(pluginId);
            if (proxy instanceof SystemProxy sp) {
                return sp.getInstance().getClass().getName();
            }
        } catch (Exception e) {
            getLogger().warning("Could not resolve implementation class for plugin: " + pluginId);
        }
        return pluginId;
    }

    private void appendExtraCredentials(List<String> envVars, Map<String, String> jitCredentials) {
        if (jitCredentials == null) {
            return;
        }
        jitCredentials.forEach((k, v) -> {
            if (!Constants.ENV_JIT_TOKEN.equals(k)
                    && !Constants.ENV_CALLBACK_URL.equals(k)
                    && !Constants.ENV_REVEILA_VERSION.equals(k)) {
                envVars.add(k + "=" + v);
            }
        });
    }

    private void appendMethodAndArguments(List<String> envVars, String pluginId, Map<String, Object> arguments) {
        String methodName = "defaultMethod";
        Map<String, Object> argsMap = null;
        if (arguments != null) {
            argsMap = new LinkedHashMap<>(arguments);
            if (argsMap.containsKey("method")) {
                methodName = String.valueOf(argsMap.remove("method"));
            }
        }
        envVars.add("METHOD_NAME=" + methodName);

        if (argsMap != null) {
            try {
                String argsJson = JsonUtil.Companion.toJsonString(argsMap);
                envVars.add("PLUGIN_ARGS_JSON=" + argsJson);
            } catch (Exception e) {
                getLogger().warning("Failed to serialize plugin arguments for " + pluginId);
            }
        }
    }

    private String resolveJitToken(Context ctx, Plugin plugin, Map<String, String> jitCredentials) {
        SecretManager sm = resolveSecretManager(ctx, plugin);

        String providedToken = (jitCredentials != null) ? jitCredentials.get(Constants.ENV_JIT_TOKEN) : null;
        if (providedToken != null && !providedToken.isBlank()) {
            String cleanToken = providedToken.trim();
            ensureTokenRegistered(sm, plugin, cleanToken);
            return cleanToken;
        }

        String generatedToken = generateTokenFromSecretManager(sm, plugin);
        if (generatedToken != null) {
            return generatedToken;
        }

        return "jit_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void ensureTokenRegistered(SecretManager sm, Plugin plugin, String token) {
        if (sm == null) {
            return;
        }
        try {
            if (!sm.validateToken(token)) {
                getLogger().warning("Provided JIT token for " + plugin.getName()
                        + " was not registered in SecretManager; registering fallback callback scope.");
                sm.registerJitToken(token, plugin.getTraceId() + ":sandbox:callback");
            }
        } catch (Exception e) {
            getLogger().warning("Error processing JIT token for " + plugin.getName() + ": " + e.getMessage());
        }
    }

    private String generateTokenFromSecretManager(SecretManager sm, Plugin plugin) {
        if (sm == null) {
            return null;
        }
        try {
            Map<String, String> generated = sm.generateJitToken(plugin, "sandbox:callback");
            String token = (generated != null) ? generated.get(Constants.ENV_JIT_TOKEN) : null;
            if (token != null && !token.isBlank()) {
                return token.trim();
            }
        } catch (Exception e) {
            getLogger().warning("Failed to generate JIT token via SecretManager for "
                    + plugin.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private SecretManager resolveSecretManager(Context ctx, Plugin plugin) {
        if (ctx == null) {
            return null;
        }
        try {
            Proxy proxy = ctx.getProxy("SecretManager");
            if (proxy != null && proxy.getInstance() instanceof SecretManager sm) {
                return sm;
            }
        } catch (Exception e) {
            getLogger().warning("SecretManager not available for " + plugin.getName() + ": " + e.getMessage());
        }
        return null;
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
