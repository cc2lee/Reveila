package com.reveila.service;

import java.util.Map;
import java.util.Optional;

import com.reveila.data.ObjectDataAdapter;
import com.reveila.data.Page;
import com.reveila.system.AbstractService;

/**
 * An abstract base class for data service implementations,
 * providing common functionalities by extending the AbstractService class.
 */
public abstract class AbstractDataService extends AbstractService implements ObjectDataAdapter {

    @Override
    public void deleteByKey(String entityName, String id) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    @Override
    public Page<Map<String, Object>> getPage(String entityName, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    @Override
    public Optional<Map<String, Object>> getByKey(String entityName, String id) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }

    @Override
    public Map<String, Object> save(String entityName, Map<String, Object> data) {
        throw new UnsupportedOperationException("Method not implemented. Must be implemented by subclass.");
    }
    
}