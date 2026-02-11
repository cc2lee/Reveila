package com.reveila.spring.model.jpa;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class AuditLog {
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String action;

    private String traceId;

    private String reasoningTrace;

    private String metadata;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getReasoningTrace() {
        return reasoningTrace;
    }

    public void setReasoningTrace(String reasoningTrace) {
        this.reasoningTrace = reasoningTrace;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}