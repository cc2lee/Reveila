package com.reveila.spring.data;

import com.reveila.data.EntityMapper;
import java.util.Map;

public class OrganizationEntityMapper extends EntityMapper<Organization> {

    @Override
    public Map<String, Map<String, Object>> extractKey(Organization entity) {
        if (entity != null && entity.getId() != null) {
            // Using "" key strategy for flat/simple IDs
            return Map.of("", Map.of("id", entity.getId()));
        }
        return null;
    }
}