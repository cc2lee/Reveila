package com.reveila.spring.data;

import java.util.Map;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reveila.data.Filter;

@Service
public class ReveilaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ReveilaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Uses the correctly typed static factory method
        Filter filter = new Filter(Map.of("username", Filter.Criterion.equal(username)));

        return userRepository.findAll(filter, null, null, 0, 1, false)
                .getContent()
                .stream()
                .findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}