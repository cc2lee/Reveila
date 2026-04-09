package com.reveila.spring.system;

import java.util.Collection;
import javax.security.auth.Subject;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.reveila.system.Reveila;
import com.reveila.system.RolePrincipal;
import com.reveila.system.SystemProxy;
import com.reveila.ai.ManagedInvocation;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Reveila reveila;

    public ApiController(Reveila reveila) {
        this.reveila = reveila;
    }

    @PostMapping("/components/{componentName}/invoke")
    public ResponseEntity<?> invokeComponent(
            @PathVariable("componentName") String componentName,
            @RequestBody MethodDTO request,
            HttpServletRequest httpRequest) throws Exception {
        
        Object[] args = request.getArgs();
        String callerIp = httpRequest.getRemoteAddr();
        Subject subject = new Subject();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities != null) {
                for (GrantedAuthority authority : authorities) {
                    // Convert Spring authorities (e.g. ROLE_SYSTEM) into RolePrincipals
                    String role = authority.getAuthority();
                    if (role != null) {
                        if (role.startsWith("ROLE_")) {
                            role = role.substring(5);
                        }
                        subject.getPrincipals().add(new RolePrincipal(role));
                    }
                }
            }
        }

        Object result = reveila.invoke(componentName, request.getMethodName(), args, callerIp, subject);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Dual-Key Overwatch Endpoint.
     * Protected by OversightInterceptor via /api/v1/overwatch prefix.
     */
    @PostMapping("/v1/overwatch/components/{componentName}/invoke")
    public ResponseEntity<?> invokeOversightComponent(
            @PathVariable("componentName") String componentName,
            @RequestBody MethodDTO request,
            HttpServletRequest httpRequest) throws Exception {
        return invokeComponent(componentName, request, httpRequest);
    }

    /**
     * Secure callback endpoint for containerized plugins.
     * ADR 0006: Routes calls from isolated workers back to the host system.
     */
    @PostMapping("/system/callback")
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, Object> payload) throws Exception {
        String jitToken = (String) payload.get("jit_token");
        String component = (String) payload.get("component");
        String method = (String) payload.get("method");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) payload.get("arguments");

        ManagedInvocation bridge;
        try {
            bridge = (ManagedInvocation) ((SystemProxy) reveila.getSystemContext()
                    .getProxy("ManagedInvocation"))
                    .getInstance();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ManagedInvocation not found", e);
        }

        Object result = bridge.handleCallback(jitToken, component, method, arguments);
        return ResponseEntity.ok(Map.of("data", result != null ? result : "SUCCESS"));
    }
}