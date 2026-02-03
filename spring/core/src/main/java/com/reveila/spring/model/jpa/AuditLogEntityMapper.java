package com.reveila.spring.model.jpa;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.reveila.data.EntityMapper;

@Component
public class AuditLogEntityMapper extends EntityMapper<AuditLog> {

    public AuditLogEntityMapper() {
        super(AuditLog.class);
    }

    @Override
    public Map<String, Map<String, Object>> extractKey(AuditLog typedEntity) {
        if (typedEntity instanceof AuditLog auditLog && auditLog.getId() != null) {
            return Map.of("", Map.of("id", auditLog.getId()));
        }
        return null;
    }
}
