package com.reveila.data

import java.util.Optional

interface Repository<T, ID> {

    fun store(entity: T): T
    fun fetchById(id: ID): Optional<T>
    fun disposeById(id: ID)
    fun storeAll(entities: @JvmSuppressWildcards Collection<T>): List<T>
    fun fetchAll(): List<T>
    fun fetchPage(filter: Filter?, sort: Sort?, fetches: List<String>?, page: Int, size: Int, includeCount: Boolean): Page<T>
    fun count(): Long
    fun hasId(id: ID): Boolean
    fun commit()
    fun getType(): String
}
