package com.reveila.spring.service;

import org.springframework.stereotype.Service;

/**
 * Service to simulate Agent communication processing.
 */
@Service
public class AgentService {

    /**
     * Processes a message from an agent.
     * 
     * @param message The message content.
     * @param agentName The name of the agent sending the message.
     * @return The processed message or a block response.
     */
    public String processMessage(String message, String agentName) {
        // This is where the actual LLM call would happen.
        // For the skeleton, we simulate a successful transmission.
        return "Agent [" + agentName + "] sent: " + message;
    }
}
