package com.reveila.spring.data;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.reveila.data.BaseService;
import com.reveila.data.EntityMapper;
import com.reveila.data.Repository;

@Service
public class UserService extends BaseService<User, UUID> {

    // Standard operations are handled by BaseService

    private final UserRepository userRepository;
    private final EntityMapper entityMapper;

    public UserService(UserRepository userRepository, EntityMapper entityMapper) {
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
    }

    @Override
    protected Repository<User, UUID> getRepository() {
        return userRepository;
    }

    @Override
    protected EntityMapper getEntityMapper() {
        return entityMapper;
    }

    @Override
    protected String getEntityType() {
        return "user";
    }

    @Override
    protected Function<User, Map<String, Object>> getKeyExtractor() {
        return user -> Map.of("id", user.getId());
    }

    // Custom business logic

    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }
}