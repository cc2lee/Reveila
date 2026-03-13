package com.reveila.spring.system;

import com.reveila.spring.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing Sandbox simulations and Agent negotiations.
 */
@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    @Autowired
    private AgentService agentService;

    /**
     * Endpoint to simulate a negotiation step.
     * 
     * @param prompt The message content from the agent.
     * @return The response from the agent service, potentially intercepted by governance.
     */
    @PostMapping("/negotiate")
    public ResponseEntity<String> startNegotiation(@RequestBody String prompt) {
        // Simulate the Procurement Agent interacting with the system
        String response = agentService.processMessage(prompt, "Procurement_Agent_01");
        return ResponseEntity.ok(response);
    }
}
