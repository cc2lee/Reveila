package com.reveila.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.reveila.data.Entity;
import com.reveila.data.Page;
import com.reveila.service.DataService;

/**
 * A DataService implementation for the Android environment.
 */
public class AndroidDataService extends DataService {

    @Override
    public Entity save(String entityType, Map<String, Object> entityMap) {
        // Simple mock implementation for Android
        Map<String, Object> data = new HashMap<>(entityMap);
        data.put("id", "android-" + System.currentTimeMillis());
        data.put("savedWith", "Reveila Android Data Service");

        Map<String, Map<String, Object>> key = new HashMap<>();
        Map<String, Object> keyData = new HashMap<>();
        keyData.put("id", data.get("id"));
        key.put("primary", keyData);
        
        return new Entity(entityType, key, data);
    }

    @Override
    public Page<Entity> search(Map<String, Object> requestMap) {
        // Return an empty page for now
        return new Page<>(new ArrayList<>(), 0, 10, false);
    }

    @Override
    public void delete(String entityType, Map<String, Map<String, Object>> key) {
        // No-op for now
    }
}
