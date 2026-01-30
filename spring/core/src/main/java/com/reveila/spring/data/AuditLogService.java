package com.reveila.spring.data;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.reveila.data.BaseService;
import com.reveila.data.EntityMapper;
import com.reveila.data.Repository;

@Service
public class AuditLogService extends BaseService<AuditLog, UUID> {
    @Autowired
    private AuditLogRepository repository;

    @Override
    protected Repository<AuditLog, UUID> getRepository() {
        return repository;
    }

    @Override
    protected EntityMapper getEntityMapper() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityMapper'");
    }

    @Override
    protected String getEntityType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityType'");
    }

    @Override
    protected Class<AuditLog> getEntityClass() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityClass'");
    }

    @Override
    protected Function<AuditLog, Map<String, Object>> getKeyExtractor() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getKeyExtractor'");
    }
}