package com.reveila.data;

/**
 * Modernized Base Service that abstracts JPA entities into generic Reveila
 * Entities.
 */
public abstract class BaseService<T, ID> {

    protected abstract Repository<T, ID> getRepository();

    protected abstract EntityMapper<T> getEntityMapper();

    protected abstract String getEntityType();

    protected abstract Class<T> getEntityClass();

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
        return getEntityMapper().toGenericEntity(jpaEntity, getEntityType());
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