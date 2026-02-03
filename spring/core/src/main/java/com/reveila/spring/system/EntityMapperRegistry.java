package com.reveila.spring.system;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.reveila.data.EntityMapper;

@Component
public class EntityMapperRegistry {
    private final Map<Class<?>, EntityMapper<?>> mappers = new HashMap<>();

    // Spring automatically injects all beans implementing EntityMapper
    @Autowired
    public EntityMapperRegistry(List<EntityMapper<?>> allMappers) {
        for (EntityMapper<?> mapper : allMappers) {
            // We assume your EntityMapper has a getEntityClass() method
            mappers.put(mapper.getEntityClass(), mapper);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> EntityMapper<T> getMapper(Class<T> entityClass) {
        return (EntityMapper<T>) mappers.get(entityClass);
    }
}