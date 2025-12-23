package com.reveila.service;

import java.util.Map;
import java.util.Optional;

import com.reveila.system.AbstractService;
import com.reveila.util.io.Page;

/**
 * An abstract base class for data service implementations,
 * providing common functionalities by extending the AbstractService class.
 */
public abstract class AbstractDataService extends AbstractService {

    public void deleteById(String entityName, String id) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    public Page<Map<String, Object>> findAll(String entityName, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    public Optional<Map<String, Object>> findById(String entityName, String id) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    public Map<String, Object> save(String entityName, Map<String, Object> data) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }
    
}