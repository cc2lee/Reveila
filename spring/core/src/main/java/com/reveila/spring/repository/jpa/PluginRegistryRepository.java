package com.reveila.spring.repository.jpa;

import com.reveila.data.JavaObjectRepository;
import com.reveila.spring.model.jpa.PluginRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for Sovereign Plugin Registry.
 */
@Repository
public interface PluginRegistryRepository extends JpaRepository<PluginRegistry, String>, JavaObjectRepository<PluginRegistry, String> {
}
