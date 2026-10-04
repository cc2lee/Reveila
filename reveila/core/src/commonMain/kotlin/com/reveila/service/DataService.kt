package com.reveila.service

import com.reveila.data.Entity
import com.reveila.data.Page
import com.reveila.data.Repository
import com.reveila.system.PlatformAdapter
import com.reveila.system.SystemComponent

/**
 * The DataService bridges the Reveila API and the Repositories.
 * It is registered as a component in the Reveila engine.
 */
open class DataService : SystemComponent() {

    private var platform: PlatformAdapter? = null

    @Throws(Exception::class)
    override fun onStart() {
        this.platform = context?.platformAdapter
        logger.info("DataService initialized and bridged to PlatformAdapter.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        logger.info("DataService stopping.")
    }

    /**
     * Primary dispatch method invoked by ApiController.
     */
    @Suppress("UNCHECKED_CAST")
    open fun search(requestMap: Map<String, Any>): Page<Entity> {
        val entityType = requestMap["entityType"] as? String
            ?: throw IllegalArgumentException("Missing entityType in search request")
        val page = (requestMap["page"] as? Number)?.toInt() ?: 0
        val size = (requestMap["size"] as? Number)?.toInt() ?: 10
        val includeCount = requestMap["isIncludeCount"] as? Boolean ?: false
        val repo = getRepository(entityType)
        return repo.fetchPage(
            filter = null,
            sort = null,
            fetches = null,
            page = page,
            size = size,
            includeCount = includeCount
        )
    }

    /**
     * Finds a single entity by its hierarchical key.
     */
    open fun findById(entityType: String, key: Map<String, Map<String, Any>>): Entity? {
        val opt = getRepository(entityType).fetchById(key)
        return if (opt.isPresent) opt.get() else null
    }

    /**
     * Persists a generic Entity.
     */
    @Suppress("UNCHECKED_CAST")
    open fun save(entityType: String, entityMap: Map<String, Any>): Entity {
        val key = entityMap["key"] as? Map<String, Map<String, Any>>
        val attrs = (entityMap["attributes"] as? Map<String, Any>) ?: entityMap
        val entity = Entity(entityType, key, attrs)
        return getRepository(entityType).store(entity)
    }

    open fun delete(entityType: String, key: Map<String, Map<String, Any>>) {
        getRepository(entityType).disposeById(key)
    }

    @Suppress("UNCHECKED_CAST")
    open fun getRepository(entityType: String): Repository<Entity, Map<String, Map<String, Any>>> {
        val adapter = platform ?: context?.platformAdapter
            ?: throw IllegalStateException("PlatformAdapter is not available")
        val repo = adapter.getRepository(entityType)
            ?: throw IllegalArgumentException("Unsupported entity type: $entityType")
        return repo
    }
}
