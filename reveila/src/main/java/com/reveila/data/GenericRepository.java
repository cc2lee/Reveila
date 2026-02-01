package com.reveila.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GenericRepository<T, ID> implements Repository<Entity, Map<String, Map<String, Object>>> {

    private final Repository<T, ID> repository;
    private final EntityMapper<T> entityMapper;
    private final String entityType;
    private final Class<T> typeClass;
    private final Class<ID> idClass;

    public GenericRepository(Repository<T, ID> repo, EntityMapper<T> mapper, Class<T> entityClass, Class<ID> idClass) {
        this.repository = repo;
        this.entityMapper = mapper;
        this.entityType = repo.getEntityType();
        this.typeClass = entityClass;
        this.idClass = idClass;
    }

    @Override
    public Page<Entity> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size,
            boolean includeCount) {
        Page<T> typedPage = repository.findAll(filter, sort, fetches, page, size, includeCount);
        return typedPage.map(this::mapToGeneric);
    }

    // Inside GenericRepository.java
    @Override
    public Entity save(Entity obj) {
        if (!(obj instanceof Entity)) {
            throw new IllegalArgumentException("Expected Entity type");
        }
        Entity entity = (Entity) obj;
        ID id = reverseId(entity.getKey());
        T target;

        if (id != null && repository.existsById(id)) {
            // 1. Fetch existing managed entity
            target = repository.findById(id).orElseThrow();

            // 2. Merge incoming attributes into the managed bean
            try {
                entityMapper.getMapper()
                        .readerForUpdating(target)
                        .readValue(entityMapper.getMapper().writeValueAsBytes(entity.getAttributes()));
            } catch (Exception e) {
                throw new RuntimeException("Merge failed", e);
            }
        } else {
            // 3. Create new if it doesn't exist
            target = entityMapper.fromGenericEntity(entity, typeClass);
        }

        T saved = repository.save(target);
        return mapToGeneric(saved);
    }

    @Override
    public Optional<Entity> findById(Map<String, Map<String, Object>> idMap) {
        ID id = reverseId(idMap);
        return repository.findById(id).map(this::mapToGeneric);
    }

    @Override
    public void deleteById(Map<String, Map<String, Object>> idMap) {
        repository.deleteById(reverseId(idMap));
    }

    @Override
    public List<Entity> saveAll(Collection<Entity> entities) {
        List<T> typedList = new ArrayList<>();
        Iterator<Entity> i = entities.iterator();
        while (i.hasNext()) {
            Entity e = i.next();
            if (!(e instanceof Entity)) {
                throw new IllegalArgumentException("This repository only supports " + Entity.class.getName() + ".");
            } else {
                typedList.add(entityMapper.fromGenericEntity(e, typeClass));
            }
        }
        return repository.saveAll(typedList).stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }

    @Override
    public List<Entity> findAll() {
        return repository.findAll().stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public boolean existsById(Map<String, Map<String, Object>> idMap) {
        return repository.existsById(reverseId(idMap));
    }

    @Override
    public void flush() {
        repository.flush();
    }

    // Helper to centralize mapping logic
    private Entity mapToGeneric(T item) {
        return entityMapper.toGenericEntity(item, entityType);
    }

    /**
     * Helper to extract the primary ID value (e.g., UUID or Composite Key Class)
     * from the key map using the localized mapper for type-safe conversion.
     */
    private ID reverseId(Map<String, Map<String, Object>> keyMap) {
        if (keyMap == null) {
            return null;
        }
        
        String key = keyMap.keySet().stream().findFirst().orElse("");
        if (key.length() > 0) {
            // If there is a specific key name (e.g., "id"), it's a composite key
            return entityMapper.getMapper().convertValue(keyMap, idClass);
        } else {
            // If no specific key name (flat), just merge all key parts
            return entityMapper.getMapper().convertValue(keyMap.values().iterator().next(), idClass);
        }
    }

    @Override
    public String getEntityType() {
        return entityType;
    }
}