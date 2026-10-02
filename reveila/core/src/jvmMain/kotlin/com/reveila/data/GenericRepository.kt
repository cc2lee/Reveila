package com.reveila.data

import java.util.Optional

open class GenericRepository<T, ID : Any>(
    repo: JavaObjectRepository<T, ID>,
    mapper: EntityMapper<T>,
    entityClass: Class<T>,
    idClass: Class<ID>
) : Repository<Entity, @JvmSuppressWildcards Map<String, Map<String, Any>>> {

    private val repository: JavaObjectRepository<T, ID> = repo
    private val entityMapper: EntityMapper<T> = mapper
    private val entityType: String = repo.getType()
    private val typeClass: Class<T> = entityClass
    private val idClass: Class<ID> = idClass

    override fun fetchPage(
        filter: Filter?,
        sort: Sort?,
        fetches: List<String>?,
        page: Int,
        size: Int,
        includeCount: Boolean
    ): Page<Entity> {
        val typedPage = repository.fetchPage(filter, sort, fetches, page, size, includeCount)
        return typedPage.map { mapToGeneric(it) }
    }

    override fun store(entity: Entity): Entity {
        val keyMap = entity.key
        val idOpt = if (!keyMap.isNullOrEmpty()) reverseId(keyMap) else Optional.empty()
        val target: T

        if (idOpt.isPresent && repository.hasId(idOpt.get())) {
            // 1. Fetch existing entity
            target = repository.fetchById(idOpt.get()).orElseThrow()

            // 2. Merge incoming attributes
            try {
                EntityMapper.objectMapper
                    .readerForUpdating(target)
                    .readValue<T>(EntityMapper.objectMapper.writeValueAsBytes(entity.attributes))
            } catch (e: Exception) {
                throw RuntimeException("Merge failed", e)
            }
        } else {
            // 3. Create new if it doesn't exist
            target = entityMapper.fromGenericEntity(entity, typeClass)
        }

        val saved = repository.store(target)
        return mapToGeneric(saved)
    }

    override fun fetchById(id: Map<String, Map<String, Any>>): Optional<Entity> {
        if (id.isEmpty()) return Optional.empty()
        return reverseId(id)
            .flatMap { repository.fetchById(it) }
            .map { mapToGeneric(it) }
    }

    override fun disposeById(id: Map<String, Map<String, Any>>) {
        if (id.isNotEmpty()) {
            reverseId(id).ifPresent { repository.disposeById(it) }
        }
    }

    override fun storeAll(entities: Collection<Entity>): List<Entity> {
        val typedList = ArrayList<T>()
        for (e in entities) {
            requireNotNull(e) { "Entities collection cannot contain null." }
            typedList.add(entityMapper.fromGenericEntity(e, typeClass))
        }
        return repository.storeAll(typedList).map { mapToGeneric(it) }
    }

    override fun count(): Long = repository.count()

    override fun hasId(id: Map<String, Map<String, Any>>): Boolean {
        if (id.isEmpty()) return false
        return reverseId(id).map { repository.hasId(it) }.orElse(false)
    }

    override fun commit() {
        repository.commit()
    }

    private fun mapToGeneric(item: T): Entity {
        return entityMapper.toGenericEntity(item, entityType)
    }

    private fun reverseId(keyMap: Map<String, Map<String, Any>>?): Optional<ID> {
        if (keyMap.isNullOrEmpty()) {
            return Optional.empty()
        }

        val key = keyMap.keys.firstOrNull() ?: ""
        return try {
            val value: ID? = if (key.isNotEmpty()) {
                EntityMapper.objectMapper.convertValue(keyMap, idClass)
            } else {
                val mapValue = keyMap.values.firstOrNull()
                if (mapValue != null) {
                    EntityMapper.objectMapper.convertValue<ID>(mapValue, idClass)
                } else {
                    null
                }
            }
            Optional.ofNullable(value)
        } catch (e: IllegalArgumentException) {
            Optional.empty()
        }
    }

    override fun getType(): String = entityType

    override fun fetchAll(): List<Entity> {
        return repository.fetchAll().map { mapToGeneric(it) }
    }
}
