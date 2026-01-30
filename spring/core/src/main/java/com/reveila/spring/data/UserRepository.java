package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
public class UserRepository extends BaseRepository<User, UUID> {
    public UserRepository(EntityManager entityManager) {
        super(entityManager, User.class);
    }
    // No other code needed! All Repository methods are inherited.
}