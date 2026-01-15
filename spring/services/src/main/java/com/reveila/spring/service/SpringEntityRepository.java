package com.reveila.spring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringEntityRepository extends JpaRepository<SpringEntity, String> {
    Page<SpringEntity> findByEntityType(String entityType, Pageable pageable);
}