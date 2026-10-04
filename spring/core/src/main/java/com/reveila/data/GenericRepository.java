package com.reveila.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.Filter;
import com.reveila.data.JavaObjectRepository;
import com.reveila.data.Optional;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.Sort;

/**
 * Bridges Spring-managed JavaObjectRepository instances to Reveila generic Entity repositories
 * using Jackson for POJO attribute conversion.
 */
public class GenericRepository<T, ID> implements Repository<Entity, Map<String, Map<String, Object>>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        Map<String, Map<String, Object>> keyMap = entity.getKey();
        Optional<ID> idOpt = keyMap != null && !keyMap.isEmpty() ? reverseId(keyMap) : Optional.empty();
        T target;

        if (idOpt.isPresent() && repository.hasId(idOpt.get())) {
            target = repository.fetchById(idOpt.get()).orElseThrow();
            try {
                MAPPER.readerForUpdating(target)
                        .readValue(MAPPER.writeValueAsBytes(entity.getAttributes()));
            } catch (Exception e) {
                throw new RuntimeException("Merge failed", e);
            }
        } else {
            target = MAPPER.convertValue(entity.getAttributes(), typeClass);
        }

        T saved = repository.store(target);
        return mapToGeneric(saved);
    }

    @Override
    public Optional<Entity> fetchById(Map<String, Map<String, Object>> idMap) {
        if (idMap == null || idMap.isEmpty()) return Optional.empty();
        Optional<ID> idOpt = reverseId(idMap);
        if (!idOpt.isPresent()) return Optional.empty();
        Optional<T> found = repository.fetchById(idOpt.get());
        if (!found.isPresent()) return Optional.empty();
        return Optional.of(mapToGeneric(found.get()));
    }

    @Override
    public void disposeById(Map<String, Map<String, Object>> idMap) {
        if (idMap != null && !idMap.isEmpty()) {
            Optional<ID> idOpt = reverseId(idMap);
            if (idOpt.isPresent()) {
                repository.disposeById(idOpt.get());
            }
        }
    }

    @Override
    public List<Entity> storeAll(Collection<Entity> entities) {
        List<T> typedList = new ArrayList<>();
        for (Entity e : entities) {
            if (e == null) {
                throw new IllegalArgumentException("Entities collection cannot contain null.");
            }
            typedList.add(MAPPER.convertValue(e.getAttributes(), typeClass));
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
        if (idMap == null || idMap.isEmpty()) return false;
        Optional<ID> idOpt = reverseId(idMap);
        return idOpt.isPresent() && repository.hasId(idOpt.get());
    }

    @Override
    public void commit() {
        repository.commit();
    }

    private Entity mapToGeneric(T item) {
        return entityMapper.toGenericEntity(item, entityType);
    }

    private Optional<ID> reverseId(Map<String, Map<String, Object>> keyMap) {
        if (keyMap == null || keyMap.isEmpty()) {
            return Optional.empty();
        }

        String key = keyMap.keySet().stream().findFirst().orElse("");
        try {
            if (key.length() > 0) {
                return Optional.ofNullable(MAPPER.convertValue(keyMap, idClass));
            } else {
                return Optional.ofNullable(
                        MAPPER.convertValue(keyMap.values().iterator().next(), idClass));
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
