package com.reveila.spring.model.jpa;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;

@Component
public class UserEntityMapper extends EntityMapper<User> {

    public UserEntityMapper() {
        super(User.class);
    }

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