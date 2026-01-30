package com.reveila.data;

import java.util.Map;
import java.util.function.Function;

/**
 * Modernized Base Service that abstracts JPA entities into generic Reveila
 * Entities.
 */
public abstract class BaseService<T, ID> {

    protected abstract Repository<T, ID> getRepository();

    protected abstract EntityMapper getEntityMapper();

    protected abstract String getEntityType();

    protected abstract Class<T> getEntityClass();

    /**
     * Defines how to extract the key from the JPA entity.
     */
    protected abstract Function<T, Map<String, Object>> getKeyExtractor();

    /**
     * Standardized search that returns the generic "Property Bag" DTO.
     */
    public Page<Entity> search(QueryRequest request) {
        Page<T> entityPage = getRepository().findAll(
                request.filter(),
                request.sort(),
                request.fetches(),
                request.page(),
                request.size(),
                request.includeCount());

        return entityPage.map(this::mapToGeneric);
    }

    public Entity findById(ID id) {
        return getRepository().findById(id)
                .map(this::mapToGeneric)
                .orElse(null);
    }

    /**
     * Internal mapping logic using the centralized EntityMapper.
     */
    private Entity mapToGeneric(T jpaEntity) {
        return getEntityMapper().toGenericEntity(
                jpaEntity,
                getEntityType(),
                getKeyExtractor().apply(jpaEntity));
    }

    public Entity save(Entity genericEntity) {
        // 1. Convert the generic Entity DTO back to a typed JPA POJO
        // We assume getEntityClass() is added as an abstract method
        T typedEntity = getEntityMapper().fromGenericEntity(genericEntity, getEntityClass());

        // 2. Persist the typed entity
        T saved = getRepository().save(typedEntity);

        // 3. Return as a generic Entity
        return mapToGeneric(saved);
    }
}