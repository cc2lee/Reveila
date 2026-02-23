package com.reveila.spring.system;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.Reveila;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Reveila reveila;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        Object result = reveila.invoke(componentName, request.getMethodName(), args, callerIp);
        
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
}