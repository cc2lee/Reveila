package com.reveila.spring.system;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reveila.safety.SecretManager;
import com.reveila.system.Constants;
import com.reveila.system.Reveila;
import com.reveila.system.ReveilaEngine;
import com.reveila.system.RolePrincipal;
import com.reveila.system.Subject;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Reveila reveila;

    public ApiController(Reveila reveila) {
        this.reveila = reveila;
    }

    /**
     * Unified REST invocation endpoint for all Reveila components, distributed peer nodes,
     * Dual-Key Overwatch actions, and isolated Docker sandbox callbacks.
     */
    @PostMapping("/components/{componentName}/invoke")
    public ResponseEntity<Object> invokeComponent(
            @PathVariable("componentName") String componentName,
            @RequestBody MethodDTO request,
            HttpServletRequest httpRequest) throws Exception {

        if (request == null || request.getMethodName() == null || request.getMethodName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid invocation request: 'methodName' is required."));
        }

        // 1. Enforce API Version Compatibility against the currently deployed backend version
        String clientVersion = request.getVersion();
        String backendVersion = resolveBackendVersion();
        if (!isVersionCompatible(clientVersion, backendVersion)) {
            return buildVersionConflictResponse(clientVersion, backendVersion);
        }

        Object[] args = request.getArgs();
        String callerIp = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        Subject subject = buildSubject(httpRequest, args);

        Object result = reveila.invoke(componentName, request.getMethodName(), args, callerIp, subject);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Object> buildVersionConflictResponse(String clientVersion, String backendVersion) {
        String resolvedClientVersion = clientVersion != null ? clientVersion : "missing";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "API Version Conflict",
                "message", String.format(
                        "Client version '%s' is incompatible with deployed backend version '%s'.",
                        resolvedClientVersion,
                        backendVersion),
                "clientVersion", resolvedClientVersion,
                "backendVersion", backendVersion
        ));
    }

    private Subject buildSubject(HttpServletRequest httpRequest, Object[] args) {
        Subject subject = new Subject();

        // Populate Subject Principals from Spring Security Authentication
        populateSpringSecurityPrincipals(subject);

        // Enrich Subject if authenticated via Sandbox JIT Bearer Token
        if (hasValidJitToken(httpRequest, args)) {
            subject.getPrincipals().add(new RolePrincipal(Constants.SYSTEM));
            subject.getPrincipals().add(new RolePrincipal(Constants.PLUGIN));
        }

        // Enrich Subject if Dual-Key Overwatch Token was verified by OversightInterceptor
        if (httpRequest != null && httpRequest.getAttribute("OVERSIGHT_TOKEN_ID") != null) {
            subject.getPrincipals().add(new RolePrincipal("overwatch-operator"));
        }

        return subject;
    }

    private void populateSpringSecurityPrincipals(Subject subject) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if (role != null) {
                String normalizedRole = role.startsWith("ROLE_") ? role.substring(5) : role;
                subject.getPrincipals().add(new RolePrincipal(normalizedRole.toLowerCase()));
            }
        }
    }

    private String resolveBackendVersion() {
        try {
            String version = reveila.getVersion();
            if (version != null && !version.isBlank()) {
                return version.trim();
            }
            if (reveila.getSystemContext() != null && reveila.getSystemContext().getProperties() != null) {
                String propVer = reveila.getSystemContext().getProperties()
                        .getProperty(Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION);
                if (propVer != null && !propVer.isBlank()) {
                    return propVer.trim();
                }
            }
        } catch (Exception e) {
            if (reveila.getSystemContext() != null && reveila.getSystemContext().getLogger() != null) {
                reveila.getSystemContext().getLogger().warning("Failed to resolve backend version, caused by: " + e.getMessage() + " | Constants.DEFAULT_SYSTEM_VERSION is used.");
            }
        }
        return Constants.DEFAULT_SYSTEM_VERSION;
    }

    private boolean isVersionCompatible(String clientVersion, String backendVersion) {
        if (clientVersion == null || clientVersion.isBlank()) {
            return false;
        }
        return ReveilaEngine.normalizeVersion(backendVersion)
                .equals(ReveilaEngine.normalizeVersion(clientVersion));
    }

    private boolean hasValidJitToken(HttpServletRequest httpRequest, Object[] args) {
        String bearerToken = extractJitToken(httpRequest, args);
        if (bearerToken == null || bearerToken.isEmpty()) {
            return false;
        }

        try {
            if (reveila.getSystemContext() != null) {
                var proxy = reveila.getSystemContext().getProxy("SecretManager");
                if (proxy != null && proxy.getInstance() instanceof SecretManager sm) {
                    return sm.validateToken(bearerToken);
                }
            }
        } catch (Exception e) {
            if (reveila.getSystemContext() != null && reveila.getSystemContext().getLogger() != null) {
                reveila.getSystemContext().getLogger().warning("Failed to validate JIT token via SecretManager: " + e.getMessage());
            }
        }
        return false;
    }

    private String extractJitToken(HttpServletRequest httpRequest, Object[] args) {
        if (httpRequest != null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = authHeader.substring(7).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
        }
        if (args != null && args.length > 0 && args[0] instanceof String s
                && (s.startsWith("jit_") || s.startsWith("JIT-"))) {
            return s;
        }
        return null;
    }
}
