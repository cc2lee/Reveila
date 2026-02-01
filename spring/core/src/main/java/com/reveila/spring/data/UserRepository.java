package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
public class UserRepository extends JpaRepository<User, UUID> {
    public UserRepository(EntityManager entityManager) {
        // Arguments: EntityManager entityManager, Class<T> entityClass, Class<ID> idClass, EntityMapper entityMapper
        super(entityManager, User.class, UUID.class, new UserEntityMapper());
    }
}