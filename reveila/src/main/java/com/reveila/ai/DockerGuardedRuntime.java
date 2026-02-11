package com.reveila.ai;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * Docker-based implementation of GuardedRuntime using gVisor (runsc).
 * Maps AgencyPerimeter resource records to Docker HostConfig settings.
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
    public Object execute(AgentPrincipal principal, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials) {
        validateRequest(principal, perimeter);
        long startTime = System.currentTimeMillis();
        System.out.println("Executing via DockerGuardedRuntime for " + pluginId + " [Trace: " + principal.traceId() + "]");

        // Filesystem Isolation: Mount the plugin JAR as a read-only volume
        String pluginJarPath = "/opt/reveila/plugins/" + pluginId + ".jar";
        Volume pluginVolume = new Volume("/app/plugin.jar");

        // RESOURCE MAPPING: AgencyPerimeter -> Docker HostConfig (cgroups)
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withRuntime("runsc") // Force gVisor kernel isolation
                .withMemory(perimeter.maxMemoryBytes()) // RAM Limit
                .withCpuPeriod(perimeter.cpuPeriodUs()) // CPU Period
                .withCpuQuota(perimeter.cpuQuotaUs())   // CPU Quota
                .withPidsLimit((long) perimeter.pidsLimit()) // PID/Fork Limit
                .withBinds(new Bind(pluginJarPath, pluginVolume, AccessMode.ro)) // Read-Only filesystem
                .withAutoRemove(true);

        // Environment Variables & JIT Credentials
        java.util.List<String> envVars = new java.util.ArrayList<>(java.util.List.of(
            "PLUGIN_ID=" + pluginId,
            "TRACE_ID=" + principal.traceId(),
            "TENANT_ID=" + principal.tenantId()
        ));

        if (jitCredentials != null) {
            jitCredentials.forEach((k, v) -> envVars.add(k + "=" + v));
        }

        CreateContainerResponse container = dockerClient.createContainerCmd("reveila-plugin-executor:latest")
                .withHostConfig(hostConfig)
                .withEnv(envVars)
                .withCmd("java", "-cp", "/app/plugin.jar", "com.reveila.system.PluginRunner")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return "Execution started in Docker container " + container.getId() + " (gVisor)";
    }
}
