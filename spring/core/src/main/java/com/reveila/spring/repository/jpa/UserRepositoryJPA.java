package com.reveila.spring.repository.jpa;

import com.reveila.data.Repository;
import com.reveila.spring.model.jpa.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

@org.springframework.stereotype.Repository
public interface UserRepositoryJPA extends JpaRepository<User, UUID>, Repository<User, UUID>, UserDetailsService {
    
    // Spring auto-implements this using the BaseRepository class,
    // which is annotated in the Spring Application class.
    User findByUsername(String username);
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    
    // The Reveila DataService sees store(), fetchById(), etc. defined in Repository<T, ID>.

}