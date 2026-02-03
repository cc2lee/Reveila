package com.reveila.spring.model.jpa;

import com.reveila.data.EntityMapper;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OrganizationEntityMapper extends EntityMapper<Organization> {

    public OrganizationEntityMapper() {
        super(Organization.class);
    }

    @Override
    public Map<String, Map<String, Object>> extractKey(Organization entity) {
        if (entity != null && entity.getId() != null) {
            // Using "" key strategy for flat/simple IDs
            return Map.of("", Map.of("id", entity.getId()));
        }
        return null;
    }
}