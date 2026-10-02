package com.reveila.data

interface JavaObjectRepository<T, ID> : Repository<T, ID> {

    fun getEntityMapper(): EntityMapper<T>
    fun getEntityClass(): Class<T>
    fun getIdClass(): Class<ID>
}
