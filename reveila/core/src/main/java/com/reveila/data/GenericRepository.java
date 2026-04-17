package com.reveila.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GenericRepository<T, ID> implements Repository<Entity, Map<String, Map<String, Object>>> {

    private final JavaObjectRepository<T, ID> repository;
    private final EntityMapper<T> entityMapper;
    private final String entityType;
    private final Class<T> typeClass;
    private final Class<ID> idClass;

    public GenericRepository(JavaObjectRepository<T, ID> repo, EntityMapper<T> mapper, Class<T> entityClass, Class<ID> idClass) {
        this.repository = repo;
        this.entityMapper = mapper;
        this.entityType = repo.getType();
        this.typeClass = entityClass;
        this.idClass = idClass;
    }

    @Override
    public Page<Entity> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size,
            boolean includeCount) {
        Page<T> typedPage = repository.fetchPage(filter, sort, fetches, page, size, includeCount);
        return typedPage.map(this::mapToGeneric);
    }

    @Override
    public Entity store(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        Optional<ID> idOpt = reverseId(entity.getKey());
        T target;

        if (idOpt.isPresent() && repository.hasId(idOpt.get())) {
            // 1. Fetch existing entity
            target = repository.fetchById(idOpt.get()).orElseThrow();

            // 2. Merge incoming attributes
            try {
                EntityMapper.getObjectMapper()
                        .readerForUpdating(target)
                        .readValue(EntityMapper.getObjectMapper().writeValueAsBytes(entity.getAttributes()));
            } catch (Exception e) {
                throw new RuntimeException("Merge failed", e);
            }
        } else {
            // 3. Create new if it doesn't exist
            target = entityMapper.fromGenericEntity(entity, typeClass);
        }

        T saved = repository.store(target);
        return mapToGeneric(saved);
    }

    @Override
    public Optional<Entity> fetchById(Map<String, Map<String, Object>> idMap) {
        return reverseId(idMap)
                .flatMap(repository::fetchById)
                .map(this::mapToGeneric);
    }

    @Override
    public void disposeById(Map<String, Map<String, Object>> idMap) {
        reverseId(idMap).ifPresent(repository::disposeById);
    }

    @Override
    public List<Entity> storeAll(Collection<Entity> entities) {
        List<T> typedList = new ArrayList<>();
        for (Entity e : entities) {
            if (e == null) {
                throw new IllegalArgumentException("Entities collection cannot contain null.");
            }
            typedList.add(entityMapper.fromGenericEntity(e, typeClass));
        }
        return repository.storeAll(typedList).stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public boolean hasId(Map<String, Map<String, Object>> idMap) {
        return reverseId(idMap).map(repository::hasId).orElse(false);
    }

    @Override
    public void commit() {
        repository.commit();
    }

    // Helper to centralize mapping logic
    private Entity mapToGeneric(T item) {
        return entityMapper.toGenericEntity(item, entityType);
    }

    /**
     * Helper to extract the primary ID value (e.g., UUID or Composite Key Class)
     * from the key map using the localized mapper for type-safe conversion.
     */
    private Optional<ID> reverseId(Map<String, Map<String, Object>> keyMap) {
        if (keyMap == null || keyMap.isEmpty()) {
            return Optional.empty();
        }

        String key = keyMap.keySet().stream().findFirst().orElse("");
        try {
            if (key.length() > 0) {
                // If there is a specific key name (e.g., "id"), it's a composite key
                return Optional.ofNullable(EntityMapper.getObjectMapper().convertValue(keyMap, idClass));
            } else {
                // If no specific key name (flat), just merge all key parts
                return Optional.ofNullable(
                        EntityMapper.getObjectMapper().convertValue(keyMap.values().iterator().next(), idClass));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public String getType() {
        return entityType;
    }

    @Override
    public List<Entity> fetchAll() {
        return repository.fetchAll().stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }
}