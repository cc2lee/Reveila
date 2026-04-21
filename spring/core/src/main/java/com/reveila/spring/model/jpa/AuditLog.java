package com.reveila.spring.model.jpa;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "governance_audit")
public class AuditLog {
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "timestamp")
    private Instant timestamp = Instant.now();

    @Column(name = "target_id", nullable = false)
    private String targetName;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "inner_monologue", columnDefinition = "TEXT")
    private String innerMonologue;

    @Column(name = "proposed_action", columnDefinition = "TEXT")
    private String action; // Renamed back to 'action' for JPA property consistency

    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    @Column(name = "status")
    private String status;

    @Column(name = "policy_triggered")
    private String policyTriggered;

    @Column(name = "risk_score")
    private BigDecimal riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    // --- Backward Compatibility & Helper Methods ---
    
    public void setTraceId(String traceId) { 
        this.targetName = traceId; 
        try {
            this.sessionId = UUID.fromString(traceId);
        } catch (Exception e) {
            if (this.sessionId == null) this.sessionId = UUID.randomUUID();
        }
    }
    public String getTraceId() { return this.targetName; }
    
    public void setReasoningTrace(String trace) { this.innerMonologue = trace; }
    public String getReasoningTrace() { return this.innerMonologue; }

    // For frontend/JSON consistency
    public String getProposedAction() { return this.action; }
    public void setProposedAction(String action) { this.action = action; }

    // --- Standard Getters and Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public String getInnerMonologue() { return innerMonologue; }
    public void setInnerMonologue(String innerMonologue) { this.innerMonologue = innerMonologue; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPolicyTriggered() { return policyTriggered; }
    public void setPolicyTriggered(String policyTriggered) { this.policyTriggered = policyTriggered; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return String.format(
            "[AuditLog | %s] Status: %s, Target: %s, Action: %s, Risk: %s, Policy: %s",
            timestamp,
            status,
            targetName,
            action,
            riskScore != null ? riskScore.toPlainString() : "N/A",
            policyTriggered != null ? policyTriggered : "NONE"
        );
    }
}
