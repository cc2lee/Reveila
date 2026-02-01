package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService extends BaseService<User, UUID> implements UserDetailsService {

    @Autowired
    public UserService(UserRepository repository) {
        super(repository, repository.getEntityMapper(), repository.getEntityClass());
    }

    // Custom business logic:

    public void deactivateUser(UUID id) {
        User user = repository.findById(id).orElseThrow();
        user.setEnabled(false);
        repository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return repository.getEntityManager().createQuery(
                    "SELECT u FROM User u JOIN FETCH u.org WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }
}