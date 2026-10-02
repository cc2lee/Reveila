package com.reveila.service

import com.reveila.data.Entity
import com.reveila.data.EntityMapper
import com.reveila.data.Page
import com.reveila.data.Repository
import com.reveila.data.SearchRequest
import com.reveila.system.PlatformAdapter
import com.reveila.system.SystemComponent

/**
 * The DataService bridges the Reveila API and the JPA Repositories.
 * It is registered as a component in the Reveila engine.
 */
open class DataService : SystemComponent() {

    private var platform: PlatformAdapter? = null

    @Throws(Exception::class)
    override fun onStart() {
        this.platform = context?.platformAdapter
        logger?.info("DataService initialized and bridged to PlatformAdapter.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        logger?.info("DataService stopping.")
    }

    /**
     * Primary dispatch method invoked by ApiController.
     */
    @Suppress("UNCHECKED_CAST")
    open fun search(requestMap: Map<String, @JvmSuppressWildcards Any>): Page<Entity> {
        val request = EntityMapper.getObjectMapper().convertValue(requestMap, SearchRequest::class.java)
        val entityType = request.entityType() ?: throw IllegalArgumentException("Missing entityType in search request")
        val repo = getRepository(entityType)
        return repo.fetchPage(
            request.filter(),
            request.sort(),
            request.fetches(),
            request.page(),
            request.size(),
            request.includeCount()
        )
    }

    /**
     * Finds a single entity by its hierarchical key.
     */
    open fun findById(entityType: String, key: Map<String, Map<String, Any>>): Entity? {
        return getRepository(entityType).fetchById(key).orElse(null)
    }

    /**
     * Persists a generic Entity.
     */
    @Suppress("UNCHECKED_CAST")
    open fun save(entityType: String, entityMap: Map<String, @JvmSuppressWildcards Any>): Entity {
        val entity = EntityMapper.getObjectMapper().convertValue(entityMap, Entity::class.java)
        return getRepository(entityType).store(entity)
    }

    open fun delete(entityType: String, key: Map<String, Map<String, Any>>) {
        getRepository(entityType).disposeById(key)
    }

    @Suppress("UNCHECKED_CAST")
    open fun getRepository(entityType: String): Repository<Entity, @JvmSuppressWildcards Map<String, Map<String, Any>>> {
        val adapter = platform ?: context?.platformAdapter
            ?: throw IllegalStateException("PlatformAdapter is not available")
        val repo = adapter.getRepository(entityType)
            ?: throw IllegalArgumentException("Unsupported entity type: $entityType")
        return repo as Repository<Entity, Map<String, Map<String, Any>>>
    }
}
