package com.reveila.spring.data;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService extends BaseService<Organization, UUID> {

    @Autowired
    public OrganizationService(OrganizationRepository repository) {
        super(repository, repository.getEntityMapper(), repository.getEntityClass());
    }
}