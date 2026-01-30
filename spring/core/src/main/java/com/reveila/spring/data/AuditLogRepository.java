package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
public class AuditLogRepository extends BaseRepository<AuditLog, UUID> {
    public AuditLogRepository(EntityManager em) {
        super(em, AuditLog.class);
    }
}