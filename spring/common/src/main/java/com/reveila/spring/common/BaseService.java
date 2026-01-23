package com.reveila.spring.common;

import com.reveila.data.*;

/**
 * Base Service providing standardized search and mapping functionality.
 * @param <T> Entity Type
 * @param <ID> ID Type
 * @param <DTO> Data Transfer Object Type
 */
public abstract class BaseService<T, ID, DTO> {

    protected abstract Repository<T, ID> getRepository();
    
    /**
     * Maps an entity to its DTO representation.
     */
    protected abstract DTO convertToDto(T entity);

    /**
     * Executes a paginated search based on a QueryRequest.
     */
    public Page<DTO> search(QueryRequest request) {
        Page<T> entityPage = getRepository().findAll(
                request.filter(),
                request.sort(),
                request.fetches(),
                request.page(),
                request.size(),
                request.includeCount()
        );

        return entityPage.map(this::convertToDto);
    }

    public DTO findById(ID id) {
        return getRepository().findById(id)
                .map(this::convertToDto)
                .orElse(null);
    }
}