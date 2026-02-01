package com.reveila.spring.data;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.Page;
import com.reveila.data.QueryRequest;

/**
 * Modernized Base Service that abstracts JPA entities into generic Reveila
 * Entities.
 */
public abstract class BaseService<T, ID> {

    protected final JpaRepository<T, ID> repository;
    protected final EntityMapper<T> entityMapper;
    protected final Class<T> entityClass;

    public BaseService(JpaRepository<T, ID> repository, EntityMapper<T> entityMapper, Class<T> entityClass) {
        this.repository = repository;
        this.entityMapper = entityMapper;
        this.entityClass = entityClass;
    }

    public Page<Entity> search(QueryRequest request) {
        Page<T> entityPage = repository.findAll(
                request.filter(),
                request.sort(),
                request.fetches(),
                request.page(),
                request.size(),
                request.includeCount());

        return entityPage.map(this::mapToGeneric);
    }

    public Entity findById(ID id) {
        return repository.findById(id)
                .map(this::mapToGeneric)
                .orElse(null);
    }

    /**
     * Internal mapping logic using the centralized EntityMapper.
     */
    private Entity mapToGeneric(T jpaEntity) {
        return entityMapper.toGenericEntity(jpaEntity, repository.getEntityType());
    }

    public Entity save(Entity genericEntity) {
        // 1. Convert the generic Entity DTO back to a typed JPA POJO
        // We assume getEntityClass() is added as an abstract method
        T typedEntity = entityMapper.fromGenericEntity(genericEntity, entityClass);

        // 2. Persist the typed entity
        T saved = repository.save(typedEntity);

        // 3. Return as a generic Entity
        return mapToGeneric(saved);
    }
}