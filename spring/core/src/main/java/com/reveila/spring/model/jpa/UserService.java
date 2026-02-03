package com.reveila.spring.model.jpa;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.reveila.spring.repository.jpa.UserRepositoryJPA;

@Service
public class UserService {

    private final UserRepositoryJPA repo;
    
    @Autowired
    public UserService(UserRepositoryJPA repository) {
        this.repo = repository;
    }

    // Custom business logic:

    public void deactivateUser(UUID id) {
        User user = repo.fetchById(id).orElseThrow();
        user.setEnabled(false);
        repo.save(user);
    }

}