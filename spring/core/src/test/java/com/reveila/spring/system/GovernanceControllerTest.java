package com.reveila.spring.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.reveila.spring.service.NotificationService;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    public void testRecordAuditSafe() throws Exception {
        String json = """
            {
                "agentId": "gtc-demo-agent",
                "sessionId": "497f6eca-6276-4993-bfeb-53cbbbba6f08",
                "proposedAction": "HELLO_WORLD",
                "innerMonologue": "Thinking..."
            }
            """;

        mockMvc.perform(post("/api/governance/audit")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.riskScore").value(0.10));
                
        // Should NOT trigger notification
        verify(notificationService, times(0)).sendSovereigntyAlert(any());
    }

    @Test
    public void testRecordAuditIntercepted() throws Exception {
        String json = """
            {
                "agentId": "gtc-demo-agent",
                "sessionId": "497f6eca-6276-4993-bfeb-53cbbbba6f08",
                "proposedAction": "QUERY_DATABASE_AND_METADATA",
                "innerMonologue": "Thinking..."
            }
            """;

        mockMvc.perform(post("/api/governance/audit")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERCEPTED"))
                .andExpect(jsonPath("$.policyTriggered").value("SOVEREIGN_ACCESS_CONTROL_v1"))
                .andExpect(jsonPath("$.riskScore").value(0.90));
                
        // SHOULD trigger notification
        verify(notificationService, times(1)).sendSovereigntyAlert(any());
    }
}
