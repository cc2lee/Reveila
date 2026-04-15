package com.reveila.android.data

import com.fasterxml.jackson.core.type.TypeReference
import com.reveila.android.db.GenericDao
import com.reveila.android.db.GenericEntity
import com.reveila.data.Entity
import com.reveila.data.EntityMapper
import com.reveila.data.Filter
import com.reveila.data.Page
import com.reveila.data.Repository
import com.reveila.data.Sort
import java.util.Optional
import java.util.UUID

class RoomRepository(
    private val entityType: String,
    private val dao: GenericDao
) : Repository<Entity, MutableMap<String, MutableMap<String, Any>>> {

    private val objectMapper = EntityMapper.getObjectmapper()
    private val mapTypeRef = object : TypeReference<MutableMap<String, Any>>() {}

    override fun getType(): String {
        return entityType
    }

    override fun store(entity: Entity): Entity {
        val id = extractId(entity.key)
        val attributesJson = objectMapper.writeValueAsString(entity.attributes)
        val genericEntity = GenericEntity(id, entityType, attributesJson)
        dao.insert(genericEntity)
        return entity
    }

    override fun fetchById(idMap: MutableMap<String, MutableMap<String, Any>>): Optional<Entity> {
        val id = extractId(idMap)
        val genericEntity = dao.findById(id, entityType)
        return if (genericEntity != null) {
            Optional.of(mapToEntity(genericEntity))
        } else {
            Optional.empty()
        }
    }

    override fun disposeById(idMap: MutableMap<String, MutableMap<String, Any>>) {
        val id = extractId(idMap)
        dao.deleteById(id, entityType)
    }

    override fun storeAll(entities: MutableCollection<Entity>): MutableList<Entity> {
        val genericEntities = entities.map { entity ->
            val id = extractId(entity.key)
            val attributesJson = objectMapper.writeValueAsString(entity.attributes)
            GenericEntity(id, entityType, attributesJson)
        }
        dao.insertAll(genericEntities)
        return entities.toMutableList()
    }

    override fun fetchAll(): MutableList<Entity> {
        return dao.findByType(entityType).map { mapToEntity(it) }.toMutableList()
    }

    override fun fetchPage(
        filter: Filter?,
        sort: Sort?,
        fetches: MutableList<String>?,
        page: Int,
        size: Int,
        includeCount: Boolean
    ): Page<Entity> {
        val offset = page * size
        val items = dao.fetchPage(entityType, size, offset).map { mapToEntity(it) }
        val count = if (includeCount) dao.countByType(entityType) else null
        val hasNext = items.size == size
        
        return Page(items, page, size, hasNext, count)
    }

    override fun count(): Long {
        return dao.countByType(entityType)
    }

    override fun hasId(idMap: MutableMap<String, MutableMap<String, Any>>): Boolean {
        val id = extractId(idMap)
        return dao.exists(id, entityType) > 0
    }

    override fun commit() {
        // Room auto-commits transactions on DAO methods
    }

    private fun extractId(idMap: MutableMap<String, MutableMap<String, Any>>?): String {
        if (idMap == null || idMap.isEmpty()) return UUID.randomUUID().toString()
        if (idMap.containsKey("id")) {
            return idMap["id"]?.get("value").toString()
        }
        return idMap.values.iterator().next()["value"].toString()
    }

    private fun mapToEntity(genericEntity: GenericEntity): Entity {
        val attributes: MutableMap<String, Any> = try {
            objectMapper.readValue(genericEntity.attributesJson, mapTypeRef)
        } catch (e: Exception) {
            mutableMapOf()
        }
        
        // Ensure id is part of attributes or key
        val key = mutableMapOf<String, MutableMap<String, Any>>()
        val idPart = mutableMapOf<String, Any>("value" to genericEntity.id)
        key["id"] = idPart
        
        return Entity(entityType, key, attributes)
    }
}
