package com.reveila.spring.system;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Interceptor for Agent communications to enforce governance policies.
 */
@Aspect
@Component
public class FabricGovernanceInterceptor {

    private static final List<String> FORBIDDEN_KEYWORDS = List.of("Project Mars", "surplus", "budget limit");

    @Around("execution(* com.reveila.spring.service.AgentService.processMessage(..))")
    public Object inspectAgentTraffic(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        
        if (args.length < 2 || !(args[0] instanceof String) || !(args[1] instanceof String)) {
            return joinPoint.proceed();
        }

        String agentMessage = (String) args[0];
        String agentName = (String) args[1];

        // 1. Audit Phase: Log the attempt to the Fabric Ledger
        System.out.println("[FABRIC AUDIT] Intercepting message from: " + agentName);

        // 2. Security Phase: Check for data leaks (Sovereign Control)
        for (String leak : FORBIDDEN_KEYWORDS) {
            if (agentMessage != null && agentMessage.toLowerCase().contains(leak.toLowerCase())) {
                System.out.println("[FABRIC BLOCK] Security Violation! Agent attempted to leak: " + leak);
                return "BLOCK_ACTION: Governance policy prevents sharing sensitive financial details.";
            }
        }

        // 3. Execution Phase: If safe, let the agent continue
        return joinPoint.proceed();
    }
}
