package com.reveila.spring.repository.jpa;

import com.reveila.data.JavaObjectRepository;
import com.reveila.spring.model.jpa.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA Repository for AuditLog.
 */
@Repository
public interface JdbcAuditLogRepository extends JpaRepository<AuditLog, UUID>, JavaObjectRepository<AuditLog, UUID> {
}
