package com.reveila.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Abstracts standard CRUD operations to ensure the application 
 * remains independent of the underlying database technology.
 */
public interface DatabaseEngine {
    
    /**
     * Saves an entity.
     */
    <T> T save(T entity);

    /**
     * Finds an entity by its ID.
     */
    <T, ID> Optional<T> findById(ID id, Class<T> entityClass);

    /**
     * Retrieves all entities of a given type.
     */
    <T> List<T> findAll(Class<T> entityClass);

    /**
     * Deletes a given entity.
     */
    <T> void delete(T entity);

    /**
     * Deletes an entity by its ID.
     */
    <T, ID> void deleteById(ID id, Class<T> entityClass);
}
