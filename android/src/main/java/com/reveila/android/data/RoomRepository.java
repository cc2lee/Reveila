package com.reveila.android.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.android.db.GenericDao;
import com.reveila.android.db.GenericEntity;
import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.Filter;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.Sort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Java implementation of the Room-backed repository.
 * Handles the mapping between generic Reveila Entities and Room GenericEntities.
 */
public class RoomRepository implements VaultRepository, Repository<Entity, Map<String, Map<String, Object>>> {

    private final String entityType;
    private final GenericDao dao;
    private final ObjectMapper objectMapper;
    
    // Equivalent to the Kotlin object : TypeReference...
    private final TypeReference<Map<String, Object>> mapTypeRef = new TypeReference<>() {};

    public RoomRepository(String entityType, GenericDao dao) {
        this.entityType = entityType;
        this.dao = dao;
        this.objectMapper = EntityMapper.getObjectMapper();
    }

    @Override
    public String getType() {
        return entityType;
    }

    @Override
    public Entity store(Entity entity) {
        try {
            String id = extractId(entity.getKey());
            String attributesJson = objectMapper.writeValueAsString(entity.getAttributes());
            GenericEntity genericEntity = new GenericEntity(id, entityType, attributesJson);
            dao.insert(genericEntity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store entity: " + entityType, e);
        }
    }

    @Override
    public Optional<Entity> fetchById(Map<String, Map<String, Object>> idMap) {
        String id = extractId(idMap);
        GenericEntity genericEntity = dao.findById(id, entityType);
        if (genericEntity != null) {
            return Optional.of(mapToEntity(genericEntity));
        }
        return Optional.empty();
    }

    @Override
    public void disposeById(Map<String, Map<String, Object>> idMap) {
        String id = extractId(idMap);
        dao.deleteById(id, entityType);
    }

    @Override
    public List<Entity> storeAll(Collection<Entity> entities) {
        List<GenericEntity> genericEntities = entities.stream().map(entity -> {
            try {
                String id = extractId(entity.getKey());
                String attributesJson = objectMapper.writeValueAsString(entity.getAttributes());
                return new GenericEntity(id, entityType, attributesJson);
            } catch (Exception e) {
                throw new RuntimeException("Serialization failed during batch store", e);
            }
        }).collect(Collectors.toList());

        dao.insertAll(genericEntities);
        return new ArrayList<>(entities);
    }

    @Override
    public List<Entity> fetchAll() {
        return dao.findByType(entityType).stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Entity> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
        int offset = page * size;
        List<Entity> items = dao.fetchPage(entityType, size, offset).stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());

        Long count = includeCount ? dao.countByType(entityType) : null;
        boolean hasNext = items.size() == size;

        return new Page<>(items, page, size, hasNext, count);
    }

    @Override
    public long count() {
        return dao.countByType(entityType);
    }

    @Override
    public boolean hasId(Map<String, Map<String, Object>> idMap) {
        String id = extractId(idMap);
        return dao.exists(id, entityType) > 0;
    }

    @Override
    public void commit() {
        // Room auto-commits transactions on DAO methods.
        // For manual transaction control, you would use database.runInTransaction()
    }

    private String extractId(Map<String, Map<String, Object>> idMap) {
        if (idMap == null || idMap.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        
        if (idMap.containsKey("id")) {
            Map<String, Object> idPart = idMap.get("id");
            if (idPart != null && idPart.containsKey("value")) {
                return String.valueOf(idPart.get("value"));
            }
        }
        
        // Fallback to first available value
        Map<String, Object> firstEntry = idMap.values().iterator().next();
        return String.valueOf(firstEntry.get("value"));
    }

    private Entity mapToEntity(GenericEntity genericEntity) {
        Map<String, Object> attributes;
        try {
            attributes = objectMapper.readValue(genericEntity.getAttributesJson(), mapTypeRef);
        } catch (Exception e) {
            attributes = new HashMap<>();
        }

        Map<String, Map<String, Object>> key = new HashMap<>();
        Map<String, Object> idPart = new HashMap<>();
        idPart.put("value", genericEntity.getId());
        key.put("id", idPart);

        return new Entity(entityType, key, attributes);
    }

    @Override
    public Map<String, Long> getIndexedFiles() {
        // Logic to return a map of URI -> LastModified from your DAO
        return new java.util.HashMap<>(); 
    }

    @Override
    public void insertFact(String subject, String type, String predicate, String object, String objectType, String metadata) {
        // Map this to your 'store' or a specific fact-insertion DAO method
    }

    @Override
    public void markFileAsIndexed(String uri, long timestamp) {
        // Update the 'last indexed' timestamp in your DB
    }

    @Override
    public void saveSecret(String data) {
        // Implementation for the Kill Switch or sensitive data storage
    }
}