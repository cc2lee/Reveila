package com.reveila.spring.repository.jpa;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.reveila.spring.model.jpa.User;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repo.count() == 0) {
            // 1. Create permissions
            List<GrantedAuthority> auths = AuthorityUtils.createAuthorityList("ROLE_ADMIN");

            // 2. Instantiate admin user
            User admin = new User("admin", passwordEncoder.encode("admin123"), auths);
            admin.setEnabled(true);

            // 3. Persist admin user
            repo.store(admin);
        }
    }
}