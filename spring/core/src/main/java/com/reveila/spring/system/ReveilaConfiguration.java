package com.reveila.spring.system;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import com.reveila.ai.AgentSessionManager;
import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.model.jpa.OrganizationEntityMapper;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;
import com.reveila.system.Constants;
import com.reveila.system.Reveila;
import com.reveila.system.RuntimeUtil;

@Configuration
public class ReveilaConfiguration {

    /**
     * Creates a singleton instance of the Reveila engine.
     */
    @Bean
    public Reveila reveila() {
        return new Reveila();
    }

    @Bean
    public AgentSessionManager agentSessionManager() {
        return new AgentSessionManager();
    }

    /**
     * Starts the Reveila engine and seeds demo data if necessary.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner reveilaRunner(Reveila reveila, 
                                          ApplicationContext context, 
                                          JdbcAuditLogRepository auditRepository,
                                          AgentSessionManager sessionManager,
                                          OrganizationEntityMapper organizationEntityMapper,
                                          Environment springEnv) {
        return args -> {
            try {
                Properties props = RuntimeUtil.getArgsAsProperties(args.getSourceArgs());
                
                if (props.getProperty(Constants.SYSTEM_HOME) == null) {
                    props.setProperty(Constants.SYSTEM_HOME, "reveila/runtime-directory");
                }
                
                reveila.start(new SpringPlatformAdapter(context, props));

                // DIRECT SEEDING LOGIC
                String mode = props.getProperty(Constants.SYSTEM_MODE);
                boolean isDemoProfile = Arrays.asList(springEnv.getActiveProfiles()).contains("demo");
                long count = auditRepository.count();

                if ("demo".equalsIgnoreCase(mode) || isDemoProfile || count == 0) {
                    if (!"production".equalsIgnoreCase(mode)) {
                        System.out.println("SEEDER: Initializing demo data in H2...");
                        seedDemoData(auditRepository, sessionManager);
                        auditRepository.flush();
                        System.out.println("SEEDER: Done. Count: " + auditRepository.count());
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start Reveila", e);
            }
        };
    }

    private void seedDemoData(JdbcAuditLogRepository auditRepository, AgentSessionManager sessionManager) {
        // M&A Project Falcon
        String trace1 = "PROJECT-FALCON-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        
        Map<String, Object> ctx1 = new HashMap<>();
        ctx1.put("title", "M&A Due Diligence - Project Falcon");
        sessionManager.saveContext(trace1, ctx1);

        createLog(auditRepository, trace1, "Orchestrator requesting financial risk summary for 10,000 sensitive PDFs.", now.minus(115, ChronoUnit.MINUTES));
        createLog(auditRepository, trace1, "Docker Sandbox ID sandbox-7721 spawned. lateral_movement=BLOCKED.", now.minus(110, ChronoUnit.MINUTES));
        createLog(auditRepository, trace1, "Gemini Auditor: Validated intent. No exfiltration detected. Reasoning monologue verified.", now.minus(105, ChronoUnit.MINUTES));

        // Healthcare Audit
        String trace2 = "HIPAA-CLAIMS-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> ctx2 = new HashMap<>();
        ctx2.put("title", "HIPAA Compliance - Claims Lineage Audit");
        sessionManager.saveContext(trace2, ctx2);

        createLog(auditRepository, trace2, "Worker (ChatGPT): Calculating patient liability for Claim #9982.", now.minus(45, ChronoUnit.MINUTES));
        createLog(auditRepository, trace2, "Auditor (Gemini): REJECTED: PII exposure risk detected in reasoning trace. Redacting and rerouting.", now.minus(40, ChronoUnit.MINUTES));
    }

    private void createLog(JdbcAuditLogRepository repo, String traceId, String action, Instant ts) {
        AuditLog log = new AuditLog();
        log.setTraceId(traceId);
        log.setAction(action);
        log.setTimestamp(ts);
        log.setMetadata("{\"demo\": true}");
        repo.save(log);
    }

}