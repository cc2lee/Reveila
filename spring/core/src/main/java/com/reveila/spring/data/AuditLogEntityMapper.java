package com.reveila.spring.data;

import java.util.Map;

import com.reveila.data.EntityMapper;

public class AuditLogEntityMapper extends EntityMapper<AuditLog> {
    @Override
    public Map<String, Map<String, Object>> extractKey(Object typedEntity) {
        if (typedEntity instanceof AuditLog auditLog && auditLog.getId() != null) {
            return Map.of("", Map.of("id", auditLog.getId()));
        }
        return null;
    }
}
