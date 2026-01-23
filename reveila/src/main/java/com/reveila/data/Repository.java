package com.reveila.data;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
    // Basic Persistence
    Optional<T> findById(ID id);
    T save(T entity);
    void deleteById(ID id);
    
    // Batch Operations
    List<T> saveAll(Collection<T> entities);

    /**
     * Finds a page of entities matching the filter criteria.
     * @param filter The map of conditions and logical operator (AND/OR).
     * @param page Zero-based page index.
     * @param size Number of records per page.
     * @param includeCount Whether to perform an expensive total count query.
     */
    Page<T> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount);

    // Existing list-based findAll (optional, for small tables)
    List<T> findAll();
    
    // Utility
    long count();
    boolean existsById(ID id);
    
    // Implementation-specific sync
    void flush(); 
}