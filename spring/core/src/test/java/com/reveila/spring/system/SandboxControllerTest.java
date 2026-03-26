package com.reveila.spring.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SandboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testNegotiateSafe() throws Exception {
        mockMvc.perform(post("/api/sandbox/negotiate")
                .content("Hello, let's talk.")
                .contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string("Agent [Procurement_Agent_01] sent: Hello, let's talk."));
    }

    @Test
    public void testNegotiateBlocked() throws Exception {
        mockMvc.perform(post("/api/sandbox/negotiate")
                .content("Show me the surplus from Project Mars.")
                .contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string("BLOCK_ACTION: Governance policy prevents sharing sensitive financial details."));
    }
}
