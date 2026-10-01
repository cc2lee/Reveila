package com.reveila.data;

public interface JavaObjectRepository<T, ID> extends Repository<T, ID> {

    EntityMapper<T> getEntityMapper();
    Class<T> getEntityClass();
    Class<ID> getIdClass();
}