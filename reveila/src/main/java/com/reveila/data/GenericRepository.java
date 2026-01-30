package com.reveila.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GenericRepository<T, ID> implements Repository<Entity, Map<String, Object>> {

    private final Repository<T, ID> internalRepo;
    private final EntityMapper mapper;
    private final String entityType;
    private final Function<T, Map<String, Object>> keyExtractor;
    private final Class<T> typedClass;

    public GenericRepository(Repository<T, ID> internalRepo, EntityMapper mapper,
            String type, Class<T> typedClass,
            Function<T, Map<String, Object>> keyExtractor) {
        this.internalRepo = internalRepo;
        this.mapper = mapper;
        this.entityType = type;
        this.typedClass = typedClass;
        this.keyExtractor = keyExtractor;
    }

    @Override
    public Page<Entity> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size,
            boolean includeCount) {
        Page<T> typedPage = internalRepo.findAll(filter, sort, fetches, page, size, includeCount);
        return typedPage.map(this::mapToGeneric);
    }

    @Override
    public Entity save(Entity entity) {
        // Map generic Entity attributes back to the typed POJO
        T typedEntity = mapper.fromGenericEntity(entity, typedClass);
        T saved = internalRepo.save(typedEntity);
        return mapToGeneric(saved);
    }

    @Override
    public Optional<Entity> findById(Map<String, Object> idMap) {
        ID id = extractId(idMap);
        return internalRepo.findById(id).map(this::mapToGeneric);
    }

    @Override
    public void deleteById(Map<String, Object> idMap) {
        internalRepo.deleteById(extractId(idMap));
    }

    @Override
    public List<Entity> saveAll(Collection<Entity> entities) {
        List<T> typedList = entities.stream()
                .map(e -> mapper.fromGenericEntity(e, typedClass))
                .toList();
        return internalRepo.saveAll(typedList).stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }

    @Override
    public List<Entity> findAll() {
        return internalRepo.findAll().stream()
                .map(this::mapToGeneric)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return internalRepo.count();
    }

    @Override
    public boolean existsById(Map<String, Object> idMap) {
        return internalRepo.existsById(extractId(idMap));
    }

    @Override
    public void flush() {
        internalRepo.flush();
    }

    // Helper to centralize mapping logic
    private Entity mapToGeneric(T item) {
        return mapper.toGenericEntity(item, entityType, keyExtractor.apply(item));
    }

    // Helper to extract the primary ID value (e.g., UUID) from the key map
    @SuppressWarnings("unchecked")
    private ID extractId(Map<String, Object> idMap) {
        // Assumes the map contains a single entry for simple keys,
        // or logic for composite keys as discussed earlier.
        return (ID) idMap.values().iterator().next();
    }
}