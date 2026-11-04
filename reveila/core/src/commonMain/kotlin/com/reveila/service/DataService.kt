package com.reveila.service

import com.reveila.data.Entity
import com.reveila.data.JsonFileRepository
import com.reveila.data.Page
import com.reveila.data.Repository
import com.reveila.system.PlatformAdapter
import com.reveila.system.SystemComponent

/**
 * Types of mutations emitted during entity lifecycle events.
 */
enum class MutationType {
    SAVE,
    DELETE
}

/**
 * Carries metadata and payloads for entity mutations dispatched by DataService.
 */
data class EntityMutation(
    val type: MutationType,
    val entityType: String,
    val entity: Entity? = null,
    val key: Map<String, Map<String, Any>>? = null
)

/**
 * Listener interface for observing entity persistence mutations.
 * Enables reactive index synchronization (e.g. VectorStore/KnowledgeVault updates).
 */
fun interface EntityMutationListener {
    fun onMutation(mutation: EntityMutation)
}

/**
 * The DataService bridges the Reveila API and the Repositories.
 * It abstracts and separates Reveila data access interfaces from the underlying
 * platform specific data storage technologies (PostgreSQL, Room, SQLite, JSON files),
 * allowing different storage products to be swapped in and out freely.
 */
open class DataService : SystemComponent() {

    private var platform: PlatformAdapter? = null
    private val dynamicRepositories: MutableMap<String, Repository<Entity, Map<String, Map<String, Any>>>> = mutableMapOf()
    private val mutationListeners = mutableListOf<EntityMutationListener>()
    private val listenerLock = Any()

    @Throws(Exception::class)
    override fun onStart() {
        this.platform = context?.platformAdapter
        logger.info("DataService initialized and bridged to PlatformAdapter.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        dynamicRepositories.clear()
        synchronized(listenerLock) {
            mutationListeners.clear()
        }
        logger.info("DataService stopping.")
    }

    /**
     * Registers a listener to receive notifications when entities are saved or deleted.
     */
    open fun addMutationListener(listener: EntityMutationListener) {
        synchronized(listenerLock) {
            mutationListeners.add(listener)
        }
    }

    /**
     * Unregisters an entity mutation listener.
     */
    open fun removeMutationListener(listener: EntityMutationListener) {
        synchronized(listenerLock) {
            mutationListeners.remove(listener)
        }
    }

    /**
     * Dispatches mutation events to all registered listeners safely.
     */
    protected open fun notifyMutation(mutation: EntityMutation) {
        val snapshot: List<EntityMutationListener>
        synchronized(listenerLock) {
            snapshot = ArrayList(mutationListeners)
        }
        for (listener in snapshot) {
            try {
                listener.onMutation(mutation)
            } catch (e: Exception) {
                logger.warning("Error notifying EntityMutationListener: ${e.message}")
            }
        }
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
        val saved = getRepository(entityType).store(entity)
        notifyMutation(EntityMutation(MutationType.SAVE, entityType, saved, saved.key))
        return saved
    }

    /**
     * Stores a pre-constructed Entity directly.
     */
    open fun store(entity: Entity): Entity {
        val saved = getRepository(entity.type).store(entity)
        notifyMutation(EntityMutation(MutationType.SAVE, entity.type, saved, saved.key))
        return saved
    }

    open fun delete(entityType: String, key: Map<String, Map<String, Any>>) {
        getRepository(entityType).disposeById(key)
        notifyMutation(EntityMutation(MutationType.DELETE, entityType, null, key))
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
