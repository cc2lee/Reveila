package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
public class AuditLogRepository extends BaseRepository<AuditLog, UUID> {
    public AuditLogRepository(EntityManager em) {
        // Arguments: EntityManager entityManager, Class<T> entityClass, Class<ID> idClass, EntityMapper entityMapper
        super(em, AuditLog.class, UUID.class, new AuditLogEntityMapper());
    }
}