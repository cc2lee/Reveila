package com.reveila.spring.model.jpa;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reveila.spring.repository.jpa.UserRepository;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository repo;

    @Autowired
    public UserService(UserRepository repository) {
        this.repo = repository;
    }

    // Custom business logic:

    public void deactivateUser(UUID id) {
        User user = repo.fetchById(id).orElseThrow();
        user.setEnabled(false);
        repo.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Construct the filter using your fluent .add() method
        com.reveila.data.Filter filter = new com.reveila.data.Filter()
                .add("username", username, com.reveila.data.Filter.SearchOp.EQUAL);

        // 2. Use fetchPage to get the user and eager-load the 'org' association
        // We pass List.of("org") to ensure the organization is fetched in the same
        // query
        com.reveila.data.Page<User> page = repo.fetchPage(
                filter,
                null,
                java.util.List.of("org"),
                0, 1,
                false);

        // 3. Extract the first result or throw the standard security exception
        return page.getContent().stream()
                .findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

}