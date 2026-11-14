package com.reveila.spring.system;

import com.reveila.safety.SecretManager;
import com.reveila.spring.security.OversightInterceptor;
import com.reveila.system.Principal;
import com.reveila.system.Proxy;
import com.reveila.system.Reveila;
import com.reveila.system.Subject;
import com.reveila.system.SystemContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiControllerTest {

    private Reveila reveila;
    private ApiController apiController;

    @BeforeEach
    void setUp() {
        reveila = mock(Reveila.class);
        when(reveila.getVersion()).thenReturn("1.0.0");
        apiController = new ApiController(reveila);
    }

    @Test
    void invokeComponent_withMatchingVersion_returnsOkAndInvokesEngine() throws Exception {
        when(reveila.invoke(eq("DataService"), eq("search"), any(), anyString(), any(Subject.class)))
                .thenReturn(Map.of("count", 3));

        MethodDTO request = new MethodDTO("1.0.0", "search", new Object[]{"query-1"});
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("10.0.0.5");

        ResponseEntity<Object> response = apiController.invokeComponent("DataService", request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("count", 3), response.getBody());
        verify(reveila, times(1)).invoke(
                eq("DataService"),
                eq("search"),
                argThat(args -> args != null && args.length == 1 && "query-1".equals(args[0])),
                eq("10.0.0.5"),
                any(Subject.class)
        );
    }

    @Test
    void invokeComponent_withMissingVersion_returnsConflict409() throws Exception {
        MethodDTO request = new MethodDTO(null, "search", new Object[0]);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        ResponseEntity<Object> response = apiController.invokeComponent("DataService", request, httpRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("API Version Conflict", body.get("error"));
        assertEquals("missing", body.get("clientVersion"));
        assertEquals("1.0.0", body.get("backendVersion"));
        verify(reveila, never()).invoke(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void invokeComponent_withMismatchedVersion_returnsConflict409() throws Exception {
        MethodDTO request = new MethodDTO("2.1.0", "search", new Object[0]);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        ResponseEntity<Object> response = apiController.invokeComponent("DataService", request, httpRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("API Version Conflict", body.get("error"));
        assertEquals("2.1.0", body.get("clientVersion"));
        assertEquals("1.0.0", body.get("backendVersion"));
        verify(reveila, never()).invoke(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void invokeComponent_withValidJitToken_elevatesSubjectPrincipalsForSandboxCallback() throws Exception {
        SecretManager secretManager = new SecretManager();
        String jitToken = "jit_testtoken123";
        secretManager.registerJitToken(jitToken, "sandbox:callback");

        SystemContext systemContext = mock(SystemContext.class);
        Proxy smProxy = mock(Proxy.class);
        when(smProxy.getInstance()).thenReturn(secretManager);
        when(systemContext.getProxy("SecretManager")).thenReturn(smProxy);
        when(reveila.getSystemContext()).thenReturn(systemContext);

        when(reveila.invoke(eq("ManagedInvocation"), eq("handleCallback"), any(), anyString(), any(Subject.class)))
                .thenReturn(Map.of("status", "SUCCESS"));

        MethodDTO request = new MethodDTO("1.0.0", "handleCallback", new Object[]{
                jitToken, "KnowledgeVault", "search", Map.of("q", "test")
        });
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("Authorization", "Bearer " + jitToken);

        ResponseEntity<Object> response = apiController.invokeComponent("ManagedInvocation", request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Subject> subjectCaptor = ArgumentCaptor.forClass(Subject.class);
        verify(reveila).invoke(eq("ManagedInvocation"), eq("handleCallback"), any(), anyString(), subjectCaptor.capture());

        Subject capturedSubject = subjectCaptor.getValue();
        Set<String> principalNames = capturedSubject.getPrincipals().stream()
                .map(Principal::getName)
                .collect(Collectors.toSet());
        assertTrue(principalNames.contains("system"), "JIT token should grant 'system' principal");
        assertTrue(principalNames.contains("plugin"), "JIT token should grant 'plugin' principal");
    }

    @Test
    void invokeComponent_withOversightTokenAttribute_elevatesSubjectWithOverwatchOperator() throws Exception {
        when(reveila.invoke(eq("FlightRecorder"), eq("getTrace"), any(), anyString(), any(Subject.class)))
                .thenReturn(Map.of("traceId", "t-1"));

        MethodDTO request = new MethodDTO("1.0.0", "getTrace", new Object[]{"t-1"});
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(OversightInterceptor.OVERSIGHT_TOKEN_ID, "OVW-TOKEN-001");

        ResponseEntity<Object> response = apiController.invokeComponent("FlightRecorder", request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Subject> subjectCaptor = ArgumentCaptor.forClass(Subject.class);
        verify(reveila).invoke(eq("FlightRecorder"), eq("getTrace"), any(), anyString(), subjectCaptor.capture());

        Subject capturedSubject = subjectCaptor.getValue();
        Set<String> principalNames = capturedSubject.getPrincipals().stream()
                .map(Principal::getName)
                .collect(Collectors.toSet());
        assertTrue(principalNames.contains("overwatch-operator"), "Oversight attribute should grant 'overwatch-operator' principal");
    }
}
