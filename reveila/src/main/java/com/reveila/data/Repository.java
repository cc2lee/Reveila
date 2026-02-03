package com.reveila.data;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {

    T store(T entity);
    Optional<T> fetchById(ID id);
    void disposeById(ID id);
    List<T> storeAll(Collection<T> entities);
    List<T> fetchAll();
    Page<T> fetchPage(Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount);
    long count();
    boolean hasId(ID id);
    void commit();
    String getType();
}