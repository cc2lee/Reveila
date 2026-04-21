package com.reveila.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;
import com.reveila.spring.service.PostgresFlightRecorder;
import com.reveila.system.InvocationTarget;
import com.reveila.system.SystemContext;
import com.reveila.system.SystemProxy;

@ExtendWith(MockitoExtension.class)
class PostgresFlightRecorderTest {

    @Mock private JdbcAuditLogRepository auditRepository;
    @Mock private SystemContext systemContext;
    @Mock private SystemProxy dataServiceProxy;
    private PostgresFlightRecorder flightRecorder;
    private InvocationTarget plugin;

    @BeforeEach
    void setUp() throws Exception {
        when(systemContext.getProxy("DataService")).thenReturn(dataServiceProxy);
        doReturn(auditRepository).when(dataServiceProxy).invoke(ArgumentMatchers.eq("getRepository"), ArgumentMatchers.any(Object[].class));

        flightRecorder = new PostgresFlightRecorder();
        flightRecorder.setContext(systemContext);
        flightRecorder.start();
        
        plugin = InvocationTarget.create("audit-agent", "tenant-1");
    }

    @Test
    void testAuditIntegrity() {
        String reasoning = "I need to fetch the user's data to calculate the budget.";
        
        flightRecorder.recordReasoning(plugin, reasoning);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).store(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertEquals(plugin.getTraceId(), savedLog.getTraceId());
        assertEquals("REASONING_TRACE", savedLog.getAction());
        assertEquals(reasoning, savedLog.getReasoningTrace());
    }

    @Test
    void testToolOutputAudit() {
        String toolName = "budget-plugin";
        Object output = Map.of("total", 5000);

        flightRecorder.recordToolOutput(plugin, toolName, output);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).store(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertEquals(plugin.getTraceId(), savedLog.getTraceId());
        assertTrue(savedLog.getAction().contains(toolName));
        assertNotNull(savedLog.getMetadata());
    }
}
