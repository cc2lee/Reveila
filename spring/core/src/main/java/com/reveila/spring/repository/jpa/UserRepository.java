package com.reveila.spring.repository.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reveila.data.JavaObjectRepository;
import com.reveila.spring.model.jpa.User;

@org.springframework.stereotype.Repository
public interface UserRepository extends JpaRepository<User, UUID>, JavaObjectRepository<User, UUID> {
}