package com.reveila.spring.data;

import java.util.UUID;

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
    protected EntityMapper<AuditLog> getEntityMapper() {
        return new AuditLogEntityMapper();
    }

    @Override
    protected String getEntityType() {
        return AuditLog.class.getSimpleName();
    }

    @Override
    protected Class<AuditLog> getEntityClass() {
        return AuditLog.class;
    }
}