package com.reveila.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public abstract class EntityMapper<T> {

    protected Class<T> entityClass;

    protected EntityMapper(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("Entity class cannot be null");
        }
        this.entityClass = entityClass;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            // Your current settings
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            // 1. Critical for Hibernate Lazy Loading
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

            // 2. Handle Java 8+ features like Optional (used in your Repositories)
            .addModule(new Jdk8Module())

            // 3. Prevent floating point precision loss on IDs/Longs
            .enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.mappedFeature())

            // 4. Ensure consistent date formats across the platform
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // 5. For Hibernate entities that use private fields without public
            // getters/setters
            .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

            // 6. Force a specific TimeZone globally
            .defaultTimeZone(TimeZone.getDefault())

            .build();

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Entity toGenericEntity(T pojo, String type) {
        if (pojo == null) {
            throw new IllegalArgumentException("pojo cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) getObjectMapper().convertValue(pojo, Map.class);
        Map<String, Map<String, Object>> keyMap = extractKey(pojo);
        return new Entity(type, keyMap, attributes);
    }

    public T fromGenericEntity(Entity entity, Class<T> targetClass) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass cannot be null");
        }
        // Combine attributes and key back into a single map for Jackson conversion
        Map<String, Object> sourceMap = new HashMap<>();
        if (entity.getAttributes() != null)
            sourceMap.putAll(entity.getAttributes());
        Map<String, Map<String, Object>> keyMap = entity.getKey();
        if (keyMap != null && !keyMap.isEmpty()) {
            String keyName = keyMap.keySet().stream().findFirst().orElse(null);
            if (keyName != null && !keyName.isEmpty()) {
                // If there is a specific key name (e.g., "id"), it's a composite key
                sourceMap.put(keyName, keyMap.get(keyName));
            } else {
                // If no specific key name (flat), just merge all key parts
                Iterator<Map<String, Object>> it = keyMap.values().iterator();
                if (it.hasNext()) {
                    sourceMap.putAll(it.next());
                }
            }
        }

        T result = getObjectMapper().convertValue(sourceMap, targetClass);
        if (result == null) {
            throw new IllegalStateException("Failed to convert GenericEntity to " + targetClass.getName());
        }
        return result;
    }

    /*
     * Override this method to extract the key, e.g., {"id": {"value": 123}}.
     * For composite keys, return a map with the key name mapping to its parts.
     * E.g., {"compositeKey": {"part1": "A", "part2": "B"}}
     * For simple keys, use empty key name and return {"": {"id": 123}}.
     */
    public abstract Map<String, Map<String, Object>> extractKey(T typedEntity);

}