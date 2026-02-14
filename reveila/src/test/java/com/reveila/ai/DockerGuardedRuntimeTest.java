package com.reveila.ai;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerGuardedRuntimeTest {

    @Mock private DockerClient dockerClient;
    @InjectMocks private DockerGuardedRuntime runtime;

    @Test
    void testContainerSpawning() {
        AgentPrincipal principal = AgentPrincipal.create("a1", "t1");
        AgencyPerimeter perimeter = new AgencyPerimeter(Set.of(), Set.of(), true, 512, 1, 10, 100, 50, false);
        
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("test-id");
        
        StartContainerCmd startCmd = mock(StartContainerCmd.class);

        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withEnv(anyList())).thenReturn(createCmd);
        when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(dockerClient.startContainerCmd("test-id")).thenReturn(startCmd);

        Object result = runtime.execute(principal, perimeter, "p1", Map.of(), Map.of());

        assertNotNull(result);
        assertTrue(result.toString().contains("test-id"));
        verify(dockerClient).startContainerCmd("test-id");
    }

    @Test
    void testInvalidRequest() {
        assertThrows(IllegalArgumentException.class, () -> 
            runtime.execute(null, null, "p1", Map.of(), null)
        );
    }
}
