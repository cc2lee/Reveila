package com.reveila.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;
import com.reveila.spring.service.PostgresFlightRecorder;

@ExtendWith(MockitoExtension.class)
class PostgresFlightRecorderTest {

    @Mock private JdbcAuditLogRepository auditRepository;
    private PostgresFlightRecorder flightRecorder;
    private AgentPrincipal principal;

    @BeforeEach
    void setUp() {
        flightRecorder = new PostgresFlightRecorder(auditRepository);
        principal = AgentPrincipal.create("audit-agent", "tenant-1");
    }

    @Test
    void testAuditIntegrity() {
        String reasoning = "I need to fetch the user's data to calculate the budget.";
        
        flightRecorder.recordReasoning(principal, reasoning);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).save(Objects.requireNonNull(captor.capture()));

        AuditLog savedLog = captor.getValue();
        assertEquals(principal.traceId(), savedLog.getTraceId());
        assertEquals("REASONING_TRACE", savedLog.getAction());
        assertEquals(reasoning, savedLog.getReasoningTrace());
    }

    @Test
    void testToolOutputAudit() {
        String toolName = "budget-plugin";
        Object output = Map.of("total", 5000);

        flightRecorder.recordToolOutput(principal, toolName, output);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).save(Objects.requireNonNull(captor.capture()));

        AuditLog savedLog = captor.getValue();
        assertEquals(principal.traceId(), savedLog.getTraceId());
        assertTrue(savedLog.getAction().contains(toolName));
        assertNotNull(savedLog.getMetadata());
    }
}
