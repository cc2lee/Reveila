package com.reveila.data;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {

    T save(T entity);
    Optional<T> findById(ID id);
    void deleteById(ID id);
    List<T> saveAll(Collection<T> entities);
    List<T> findAll();
    Page<T> findAll(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount);
    long count();
    boolean existsById(ID id);
    void flush();
    String getEntityType(); // Returns "user", "organization", etc.
}