package com.reveila.spring.data;

import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import java.util.UUID;

@Repository
public class OrganizationRepository extends JpaRepository<Organization, UUID> {
    public OrganizationRepository(EntityManager entityManager) {
        super(entityManager, Organization.class, UUID.class, new OrganizationEntityMapper());
    }
}