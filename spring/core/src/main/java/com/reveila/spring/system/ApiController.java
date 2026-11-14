package com.reveila.spring.system;

import java.util.Collection;
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
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "API Version Conflict",
                    "message", String.format(
                            "Client version '%s' is incompatible with deployed backend version '%s'.",
                            clientVersion != null ? clientVersion : "missing",
                            backendVersion),
                    "clientVersion", clientVersion != null ? clientVersion : "missing",
                    "backendVersion", backendVersion
            ));
        }

        Object[] args = request.getArgs();
        String callerIp = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        Subject subject = new Subject();

        // 2. Populate Subject Principals from Spring Security Authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities != null) {
                for (GrantedAuthority authority : authorities) {
                    String role = authority.getAuthority();
                    if (role != null) {
                        if (role.startsWith("ROLE_")) {
                            role = role.substring(5);
                        }
                        subject.getPrincipals().add(new RolePrincipal(role.toLowerCase()));
                    }
                }
            }
        }

        // 3. Enrich Subject if authenticated via Sandbox JIT Bearer Token
        if (hasValidJitToken(httpRequest, args)) {
            subject.getPrincipals().add(new RolePrincipal(Constants.SYSTEM));
            subject.getPrincipals().add(new RolePrincipal(Constants.PLUGIN));
        }

        // 4. Enrich Subject if Dual-Key Overwatch Token was verified by OversightInterceptor
        if (httpRequest != null && httpRequest.getAttribute("OVERSIGHT_TOKEN_ID") != null) {
            subject.getPrincipals().add(new RolePrincipal("overwatch-operator"));
        }

        Object result = reveila.invoke(componentName, request.getMethodName(), args, callerIp, subject);
        return ResponseEntity.ok(result);
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
        } catch (Exception ignored) {
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
        String bearerToken = null;
        if (httpRequest != null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                bearerToken = authHeader.substring(7).trim();
            }
        }
        if ((bearerToken == null || bearerToken.isEmpty()) && args != null && args.length > 0 && args[0] instanceof String s) {
            if (s.startsWith("jit_") || s.startsWith("JIT-")) {
                bearerToken = s;
            }
        }
        if (bearerToken == null || bearerToken.isEmpty()) {
            return false;
        }

        try {
            if (reveila.getSystemContext() != null) {
                Object smObj = reveila.getSystemContext().getProxy("SecretManager").getInstance();
                if (smObj instanceof SecretManager sm) {
                    return sm.validateToken(bearerToken);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
