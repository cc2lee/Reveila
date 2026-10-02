package com.reveila.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.reveila.system.PlatformAdapter
import java.io.IOException
import java.lang.reflect.Method
import java.util.Optional
import kotlin.math.min

open class JsonFileRepository<T : Any, ID : Any>(
    dataDir: String,
    entityType: String,
    private val entityClass: Class<T>,
    private val idClass: Class<ID>,
    private val platformAdapter: PlatformAdapter
) : JavaObjectRepository<T, ID> {

    private val filePath: String = "$dataDir/${entityType.lowercase()}s.json"
    private val entityType: String = entityType
    private val mapper: ObjectMapper = EntityMapper.objectMapper
    private var data: MutableList<T> = mutableListOf()
    private var getIdMethod: Method? = null

    init {
        try {
            this.getIdMethod = entityClass.getMethod("getId")
        } catch (e: NoSuchMethodException) {
            this.getIdMethod = null
        }
        load()
    }

    private fun load() {
        try {
            platformAdapter.getFileInputStream(filePath)?.use { `is` ->
                val loaded: List<T>? = mapper.readValue(`is`, mapper.typeFactory.constructCollectionType(ArrayList::class.java, entityClass))
                data = if (loaded != null) loaded.toMutableList() else mutableListOf()
            } ?: run {
                data = mutableListOf()
            }
        } catch (e: IOException) {
            System.err.println("Failed to load JSON data from $filePath: ${e.message}")
            data = mutableListOf()
        }
    }

    @Synchronized
    private fun save() {
        try {
            platformAdapter.getFileOutputStream(filePath, false)?.use { os ->
                mapper.writerWithDefaultPrettyPrinter().writeValue(os, data)
            }
        } catch (e: IOException) {
            System.err.println("Failed to save JSON data to $filePath: ${e.message}")
        }
    }

    override fun getEntityMapper(): EntityMapper<T> {
        return object : EntityMapper<T>(entityClass) {
            override fun extractKey(typedEntity: T): Map<String, Map<String, Any>> {
                val keys = mutableMapOf<String, Map<String, Any>>()
                val idPart = mutableMapOf<String, Any>()
                idPart["value"] = getId(typedEntity).orElseThrow()
                keys["id"] = idPart
                return keys
            }
        }
    }

    override fun getEntityClass(): Class<T> = entityClass
    override fun getIdClass(): Class<ID> = idClass
    override fun getType(): String = entityType

    @Synchronized
    override fun fetchAll(): List<T> = ArrayList(data)

    @Synchronized
    override fun fetchById(id: ID): Optional<T> {
        val targetId = Optional.ofNullable(id)
        return Optional.ofNullable(data.firstOrNull { getId(it) == targetId })
    }

    @Synchronized
    override fun store(entity: T): T {
        val id = getId(entity)
        data.removeAll { getId(it) == id }
        data.add(entity)
        save()
        return entity
    }

    @Synchronized
    override fun storeAll(entities: Collection<T>): List<T> {
        for (entity in entities) {
            val id = getId(entity)
            data.removeAll { getId(it) == id }
        }
        data.addAll(entities)
        save()
        return ArrayList(entities)
    }

    @Synchronized
    override fun disposeById(id: ID) {
        val targetId = Optional.ofNullable(id)
        data.removeAll { getId(it) == targetId }
        save()
    }

    @Synchronized
    override fun count(): Long = data.size.toLong()

    @Synchronized
    override fun hasId(id: ID): Boolean {
        val targetId = Optional.ofNullable(id)
        return data.any { getId(it) == targetId }
    }

    @Synchronized
    override fun commit() {
        save()
    }

    @Synchronized
    override fun fetchPage(
        filter: Filter?,
        sort: Sort?,
        fetches: List<String>?,
        page: Int,
        size: Int,
        includeCount: Boolean
    ): Page<T> {
        val start = page * size
        if (start >= data.size) {
            return Page(emptyList(), page, size, false, data.size.toLong())
        }

        val end = min(start + size, data.size)
        val content = data.subList(start, end)
        val hasNext = end < data.size
        return Page(content, page, size, hasNext, data.size.toLong())
    }

    private fun getId(entity: T?): Optional<ID> {
        if (entity == null || getIdMethod == null) {
            return Optional.empty()
        }
        return try {
            val id = getIdMethod?.invoke(entity)
            Optional.ofNullable(idClass.cast(id))
        } catch (e: Exception) {
            Optional.empty()
        }
    }
}
