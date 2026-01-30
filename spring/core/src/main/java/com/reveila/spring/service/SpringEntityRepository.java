package com.reveila.spring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringEntityRepository extends JpaRepository<Json, String>, com.reveila.data.Repository<Json, String> {
    Page<Json> findByEntityType(String entityType, Pageable pageable);
}