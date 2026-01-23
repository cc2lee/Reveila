package com.reveila.data;

public abstract class BaseService<T, ID, DTO> {

    protected abstract Repository<T, ID> getRepository();
    protected abstract DTO convertToDto(T entity);

    public Page<DTO> search(QueryRequest request) {
        // 1. Execute the optimized repository call
        Page<T> entityPage = getRepository().findAll(
            request.filter(),
            request.sort(),
            request.fetches(),
            request.page(),
            request.size(),
            request.includeCount()
        );

        // 2. Map the results to DTOs using your record's map function
        return entityPage.map(this::convertToDto);
    }
}