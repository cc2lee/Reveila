package com.reveila.spring.data;

import java.util.Map;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;

public class UserEntityMapper extends EntityMapper<User> {

    @Override
    public Map<String, Map<String, Object>> extractKey(User typedEntity) {
        if (typedEntity instanceof User user && user.getId() != null) {
            return Map.of("", Map.of("id", user.getId()));
        }
        return null;
    }

    @Override
    public Entity toGenericEntity(User pojo, String type) {
        Entity entity = super.toGenericEntity(pojo, type);
        Map<String, Object> attributes = entity.getAttributes();

        // Security: Remove sensitive fields from the attributes map
        attributes.remove("password");
        attributes.remove("secretKey");

        return entity;
    }
}