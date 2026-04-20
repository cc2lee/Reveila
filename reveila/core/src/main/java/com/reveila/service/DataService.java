package com.reveila.service;

import java.util.Map;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.data.SearchRequest;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.SystemComponent;

/**
 * The DataService bridges the Reveila API and the JPA Repositories.
 * It is registered as a component in the Reveila engine.
 * Example JSON payload to search for users:
 * {
 * "methodName": "search",
 * "args": [
 * "user",
 * { "conditions": { "active": { "value": true, "operator": "EQUAL" } } },
 * {
 * "sort": { "field": "username", "ascending": true }
 * },
 * ["org"], 0, 10, true
 * ]
 * }
 */
public class DataService extends SystemComponent {

    private PlatformAdapter platform;

    @Override
    protected void onStart() throws Exception {
        this.platform = context.getPlatformAdapter();
        logger.info("DataService initialized and bridged to PlatformAdapter.");
    }

    @Override
    protected void onStop() throws Exception {
        logger.info("DataService stopping.");
    }

    /**
     * Primary dispatch method invoked by ApiController.
     * Args index: 0 = entityType (String), 1 = payload (Map/Filter/Entity)
     * Example payload for search:
     * 
    {
        "methodName": "search",
        "args": [
            {
                "entityType": "user",
                "filter": { "conditions": { "username": { "value": "charles", "operator": "LIKE" } } },
                "sort": { "field": "username", "ascending": true },
                "fetches": ["org"],
                "page": 0,
                "size": 10,
                "includeCount": true
            }
        ]
    }
     */
    @SuppressWarnings("null")
    public Page<Entity> search(Map<String, Object> requestMap) {
        // This will now look for a "sort" key inside the requestMap automatically
        SearchRequest request = EntityMapper.getObjectMapper().convertValue(requestMap, SearchRequest.class);

        Repository<Entity, Map<String, Map<String, Object>>> repo = getRepository(request.entityType());
        return repo.fetchPage(request.filter(), request.sort(), request.fetches(),
                request.page(), request.size(), request.includeCount());
    }

    /**
     * Finds a single entity by its hierarchical key.
     */
    public Entity findById(String entityType, Map<String, Map<String, Object>> key) {
        return getRepository(entityType).fetchById(key).orElse(null);
    }

    /**
     * Persists a generic Entity.
     */
    @SuppressWarnings("null")
    public Entity save(String entityType, Map<String, Object> entityMap) {
        // Convert Map to Generic Entity DTO
        Entity entity = EntityMapper.getObjectMapper().convertValue(entityMap, Entity.class);
        return getRepository(entityType).store(entity);
    }

    public void delete(String entityType, Map<String, Map<String, Object>> key) {
        getRepository(entityType).disposeById(key);
    }

    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        Repository<Entity, Map<String, Map<String, Object>>> repo = platform.getRepository(entityType);
        if (repo == null) {
            throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        }
        return repo;
    }

}