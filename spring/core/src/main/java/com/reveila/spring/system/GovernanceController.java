package com.reveila.spring.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;
import com.reveila.spring.service.NotificationService;
import com.reveila.system.Reveila;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Governance and Auditing operations.
 */
@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final JdbcAuditLogRepository auditRepository;
    private final NotificationService notificationService;
    private final Reveila reveila;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${notification.alert.threshold:0.80}")
    private BigDecimal alertThreshold;

    public GovernanceController(JdbcAuditLogRepository auditRepository,
            NotificationService notificationService,
            Reveila reveila) {
        this.auditRepository = auditRepository;
        this.notificationService = notificationService;
        this.reveila = reveila;
    }

    /**
     * Records an audit log entry with automatic sovereignty enforcement.
     * 
     * @param log The audit log entry to save.
     * @return The saved entry.
     */
    @PostMapping("/audit")
    public ResponseEntity<AuditLog> recordAudit(@RequestBody AuditLog log) {
        // Ensure sessionId is present for the new schema
        if (log.getSessionId() == null) {
            log.setSessionId(java.util.UUID.randomUUID());
        }

        // Apply Sovereignty Enforcement Logic based on external policy
        enforceSovereignty(log);

        // Human-In-The-Loop: Trigger notification if risk threshold is exceeded
        if (log.getRiskScore() != null && log.getRiskScore().compareTo(alertThreshold) >= 0) {
            notificationService.sendSovereigntyAlert(log);
        }

        System.err.println("[DEBUG] Saving AuditLog with Risk Score: " + log.getRiskScore());
        AuditLog saved = auditRepository.save(log);
        return ResponseEntity.ok(saved);
    }

    /**
     * Enforces sovereignty policies on the audit record using externalized risk
     * patterns.
     * 
     * @param record The audit record to evaluate and update.
     */
    public void enforceSovereignty(AuditLog record) {
        try {
            // Load Policy from system home
            String systemHome = reveila.getSystemContext().getProperties().getProperty("system.home");
            if (systemHome == null)
                systemHome = System.getenv("REVEILA_HOME");
            if (systemHome == null)
                systemHome = "../../system-home/standard";

            File policyFile = new File(systemHome, "configs/governance-policy.json");

            if (!policyFile.exists()) {
                System.err.println("Governance Policy File missing: " + policyFile.getAbsolutePath());
                if (record.getRiskScore() == null) {
                    record.setStatus("APPROVED");
                    record.setRiskScore(BigDecimal.valueOf(0.10));
                }
                return;
            }

            JsonNode policy = mapper.readTree(policyFile);
            List<String> restrictedKeywords = new ArrayList<>();
            policy.get("restrictedKeywords").forEach(node -> restrictedKeywords.add(node.asText()));

            String action = record.getProposedAction();
            if (action == null) {
                if (record.getRiskScore() == null) {
                    record.setStatus("APPROVED");
                    record.setRiskScore(BigDecimal.valueOf(0.10));
                }
                return;
            }

            // Score the intent
            long matchCount = restrictedKeywords.stream()
                    .filter(keyword -> action.toUpperCase().contains(keyword.toUpperCase()))
                    .count();

            if (matchCount > 0) {
                record.setStatus("INTERCEPTED");
                record.setPolicyTriggered(policy.get("policyName").asText());

                double baseRisk = policy.get("baseRiskScore").asDouble();
                double penalty = policy.get("keywordPenalty").asDouble();

                BigDecimal calculatedScore = BigDecimal.valueOf(baseRisk + (matchCount * penalty));
                // Only override if calculated is higher or current is null
                if (record.getRiskScore() == null || calculatedScore.compareTo(record.getRiskScore()) > 0) {
                    record.setRiskScore(calculatedScore.min(BigDecimal.ONE));
                }
            } else if (record.getRiskScore() == null) {
                record.setStatus("APPROVED");
                record.setRiskScore(BigDecimal.valueOf(0.10));
            }

        } catch (Exception e) {
            System.err.println("FAILED to enforce sovereignty policy: " + e.getMessage());
            e.printStackTrace();
            record.setStatus("ERROR");
        }
    }
}
