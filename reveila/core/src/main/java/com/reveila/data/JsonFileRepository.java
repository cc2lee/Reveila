package com.reveila.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonFileRepository<T, ID> implements JavaObjectRepository<T, ID> {

    private final Path filePath;
    private final Class<T> entityClass;
    private final Class<ID> idClass;
    private final String entityType;
    private final ObjectMapper mapper = EntityMapper.getObjectmapper();
    private List<T> data = new ArrayList<>();

    public JsonFileRepository(Path dataDir, String entityType, Class<T> entityClass, Class<ID> idClass) {
        this.filePath = dataDir.resolve(entityType.toLowerCase() + "s.json");
        this.entityType = entityType;
        this.entityClass = entityClass;
        this.idClass = idClass;
        load();
    }

    private void load() {
        if (Files.exists(filePath)) {
            try {
                data = mapper.readValue(filePath.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, entityClass));
            } catch (IOException e) {
                System.err.println("Failed to load JSON data from " + filePath + ": " + e.getMessage());
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(filePath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
        } catch (IOException e) {
            System.err.println("Failed to save JSON data to " + filePath + ": " + e.getMessage());
        }
    }

    @Override
    public EntityMapper<T> getEntityMapper() {
        return new EntityMapper<T>(entityClass) {
            @Override
            public Map<String, Map<String, Object>> extractKey(T typedEntity) {
                Map<String, Map<String, Object>> keys = new HashMap<>();
                Map<String, Object> idPart = new HashMap<>();
                idPart.put("value", getId(typedEntity));
                keys.put("id", idPart);
                return keys;
            }
        };
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public Class<ID> getIdClass() {
        return idClass;
    }

    @Override
    public String getType() {
        return entityType;
    }

    @Override
    public List<T> fetchAll() {
        return new ArrayList<>(data);
    }

    @Override
    public Optional<T> fetchById(ID id) {
        return data.stream().filter(item -> Objects.equals(getId(item), id)).findFirst();
    }

    @Override
    public T store(T entity) {
        ID id = getId(entity);
        data.removeIf(item -> Objects.equals(getId(item), id));
        data.add(entity);
        save();
        return entity;
    }

    @Override
    public List<T> storeAll(Collection<T> entities) {
        for (T entity : entities) {
            ID id = getId(entity);
            data.removeIf(item -> Objects.equals(getId(item), id));
        }
        data.addAll(entities);
        save();
        return new ArrayList<>(entities);
    }

    @Override
    public void disposeById(ID id) {
        data.removeIf(item -> Objects.equals(getId(item), id));
        save();
    }

    @Override
    public long count() {
        return data.size();
    }

    @Override
    public boolean hasId(ID id) {
        return data.stream().anyMatch(item -> Objects.equals(getId(item), id));
    }

    @Override
    public void commit() {
        save();
    }

    @Override
    public Page<T> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
        int start = page * size;
        if (start >= data.size()) return new Page<T>(new ArrayList<T>(), page, size, false, (long)data.size());
        
        int end = Math.min(start + size, data.size());
        List<T> content = data.subList(start, end);
        boolean hasNext = end < data.size();
        return new Page<T>(new ArrayList<T>(content), page, size, hasNext, (long)data.size());
    }

    private ID getId(T entity) {
        try {
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            return idClass.cast(getIdMethod.invoke(entity));
        } catch (Exception e) {
            // Fallback for AuditLog which has getId returning UUID
            return null;
        }
    }
}
