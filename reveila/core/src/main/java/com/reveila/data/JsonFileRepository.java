package com.reveila.data;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.PlatformAdapter;

public class JsonFileRepository<T, ID> implements JavaObjectRepository<T, ID> {

    private final String filePath;
    private final Class<T> entityClass;
    private final Class<ID> idClass;
    private final String entityType;
    private final ObjectMapper mapper = EntityMapper.getObjectMapper();
    private List<T> data = new ArrayList<>();
    private final PlatformAdapter platformAdapter;
    private Method getIdMethod;

    public JsonFileRepository(String dataDir, String entityType, Class<T> entityClass, Class<ID> idClass, PlatformAdapter platformAdapter) {
        this.filePath = dataDir + "/" + entityType.toLowerCase() + "s.json";
        this.entityType = entityType;
        this.entityClass = entityClass;
        this.idClass = idClass;
        this.platformAdapter = platformAdapter;
        try {
            this.getIdMethod = entityClass.getMethod("getId");
        } catch (NoSuchMethodException e) {
            this.getIdMethod = null;
        }
        load();
    }

    private void load() {
        try (java.io.InputStream is = platformAdapter.getFileInputStream(filePath)) {
            if (is != null) {
                data = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, entityClass));
            } else {
                data = new ArrayList<>();
            }
        } catch (IOException e) {
            System.err.println("Failed to load JSON data from " + filePath + ": " + e.getMessage());
            data = new ArrayList<>();
        }
    }

    private synchronized void save() {
        try (java.io.OutputStream os = platformAdapter.getFileOutputStream(filePath, false)) {
            if (os != null) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(os, data);
            }
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
                idPart.put("value", getId(typedEntity).orElseThrow());
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
    public synchronized List<T> fetchAll() {
        return new ArrayList<>(data);
    }

    @Override
    public synchronized Optional<T> fetchById(ID id) {
        return data.stream().filter(item -> getId(item).equals(Optional.ofNullable(id))).findFirst();
    }

    @Override
    public synchronized T store(T entity) {
        Optional<ID> id = getId(entity);
        data.removeIf(item -> getId(item).equals(id));
        data.add(entity);
        save();
        return entity;
    }

    @Override
    public synchronized List<T> storeAll(Collection<T> entities) {
        for (T entity : entities) {
            Optional<ID> id = getId(entity);
            data.removeIf(item -> getId(item).equals(id));
        }
        data.addAll(entities);
        save();
        return new ArrayList<>(entities);
    }

    @Override
    public synchronized void disposeById(ID id) {
        data.removeIf(item -> getId(item).equals(Optional.ofNullable(id)));
        save();
    }

    @Override
    public synchronized long count() {
        return data.size();
    }

    @Override
    public synchronized boolean hasId(ID id) {
        return data.stream().anyMatch(item -> getId(item).equals(Optional.ofNullable(id)));
    }

    @Override
    public synchronized void commit() {
        save();
    }

    @Override
    public synchronized Page<T> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
        int start = page * size;
        if (start >= data.size()) return new Page<T>(List.of(), page, size, false, (long)data.size());
        
        int end = Math.min(start + size, data.size());
        List<T> content = data.subList(start, end);
        boolean hasNext = end < data.size();
        return new Page<T>(content, page, size, hasNext, (long)data.size());
    }

    private Optional<ID> getId(T entity) {
        if (entity == null || getIdMethod == null) {
            return Optional.empty();
        }
        try {
            Object id = getIdMethod.invoke(entity);
            return Optional.ofNullable(idClass.cast(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
