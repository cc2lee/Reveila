package com.reveila.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.HashMap;

public class EntityMapper {
    private final ObjectMapper mapper;

    public EntityMapper() {
        // Using the modern Builder pattern as discussed
        this.mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /**
     * JPA POJO -> Generic Entity DTO
     */
    public Entity toGenericEntity(Object pojo, String type, Map<String, Object> key) {
        Map<String, Object> attributes = mapper.convertValue(pojo, new TypeReference<Map<String, Object>>() {});
        
        // Ensure the key values are not duplicated in the attributes map
        if (key != null) {
            key.keySet().forEach(attributes::remove);
        }

        return new Entity(type, key, attributes);
    }

    /**
     * Generic Entity DTO -> JPA POJO
     */
    public <T> T fromGenericEntity(Entity entity, Class<T> targetClass) {
        // Combine Key and Attributes into a single source map for Jackson
        Map<String, Object> sourceMap = new HashMap<>();
        if (entity.getAttributes() != null) {
            sourceMap.putAll(entity.getAttributes());
        }
        if (entity.getKey() != null) {
            sourceMap.putAll(entity.getKey());
        }

        // Jackson handles the conversion from Map to the Typed POJO
        return mapper.convertValue(sourceMap, targetClass);
    }
}