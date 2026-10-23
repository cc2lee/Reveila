package com.reveila.service

import com.reveila.data.Entity
import com.reveila.data.JsonFileRepository
import com.reveila.data.Page
import com.reveila.data.Repository
import com.reveila.system.PlatformAdapter
import com.reveila.system.SystemComponent

/**
 * The DataService bridges the Reveila API and the Repositories.
 * It abstracts and separates Reveila data access interfaces from the underlying
 * platform specific data storage technologies (PostgreSQL, Room, SQLite, JSON files),
 * allowing different storage products to be swapped in and out freely.
 */
open class DataService : SystemComponent() {

    private var platform: PlatformAdapter? = null
    private val dynamicRepositories: MutableMap<String, Repository<Entity, Map<String, Map<String, Any>>>> = mutableMapOf()

    @Throws(Exception::class)
    override fun onStart() {
        this.platform = context?.platformAdapter
        logger.info("DataService initialized and bridged to PlatformAdapter.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        dynamicRepositories.clear()
        logger.info("DataService stopping.")
    }

    /**
     * Primary dispatch method invoked by ApiController or internal services.
     */
    @Suppress("UNCHECKED_CAST")
    open fun search(requestMap: Map<String, @kotlin.jvm.JvmSuppressWildcards Any>): Page<Entity> {
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
    open fun save(entityType: String, entityMap: Map<String, Any?>): Entity {
        val key = entityMap["key"] as? Map<String, Map<String, Any>>
        val attrs = ((entityMap["attributes"] as? Map<String, Any?>) ?: entityMap)
            .filterValues { it != null }
            .mapValues { it.value as Any }
        val finalAttrs = if (attrs.isEmpty()) mapOf("payload" to "empty") else attrs
        val entity = Entity(entityType, key, finalAttrs)
        return getRepository(entityType).store(entity)
    }

    /**
     * Stores a pre-constructed Entity directly.
     */
    open fun store(entity: Entity): Entity {
        return getRepository(entity.type).store(entity)
    }

    open fun delete(entityType: String, key: Map<String, Map<String, Any>>) {
        getRepository(entityType).disposeById(key)
    }

    /**
     * Resolves the storage repository for the requested entity type.
     * If the host platform does not provide a custom repository (e.g. Postgres or Room),
     * auto-spawns a self-contained sovereign edge repository in the configured data directory (default ./data).
     */
    @Suppress("UNCHECKED_CAST")
    open fun getRepository(entityType: String): Repository<Entity, Map<String, Map<String, Any>>> {
        val normalizedType = entityType.lowercase()

        // 1. Check PlatformAdapter provided repository (e.g., PostgreSQL in Spring or Room in Android)
        val adapter = platform ?: context?.platformAdapter
        if (adapter != null) {
            val repo = adapter.getRepository(entityType)
            if (repo != null) {
                return repo
            }
        }

        // 2. Check local sovereign repository cache
        dynamicRepositories[normalizedType]?.let { return it }

        // 3. Auto-spawn a self-contained Sovereign Edge repository (Hexagonal Architecture)
        val dataDir = context?.properties?.getProperty("data.dir", "./data") ?: "./data"
        val sovereignRepo = JsonFileRepository(dataDir, entityType)
        dynamicRepositories[normalizedType] = sovereignRepo
        logger.info("Auto-spawned sovereign edge repository for entity [$entityType] at: $dataDir")
        return sovereignRepo
    }

    open fun hasRepository(entityType: String): Boolean {
        val adapter = platform ?: context?.platformAdapter
        return (adapter?.getRepository(entityType) != null) || dynamicRepositories.containsKey(entityType.lowercase())
    }
}
