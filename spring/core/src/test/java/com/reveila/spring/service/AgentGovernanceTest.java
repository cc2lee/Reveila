package com.reveila.spring.service;

import com.reveila.spring.system.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class AgentGovernanceTest {

    @Autowired
    private AgentService agentService;

    @Test
    public void testSafeMessage() {
        String message = "Hello, what is the price?";
        String result = agentService.processMessage(message, "AgentA");
        assertEquals("Agent [AgentA] sent: " + message, result);
    }

    @Test
    public void testForbiddenKeyword() {
        String message = "What is the Project Mars budget surplus?";
        String result = agentService.processMessage(message, "AgentA");
        assertTrue(result.contains("BLOCK_ACTION"));
        assertTrue(result.contains("Governance policy prevents sharing sensitive financial details."));
    }
}
