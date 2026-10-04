package com.reveila.data

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil
import kotlin.math.min

open class JsonFileRepository(
    dataDir: String,
    private val entityType: String
) : Repository<Entity, Map<String, Map<String, Any>>> {

    private val fs: PlatformFileSystem = PlatformFileSystem()
    private val filePath: String = "$dataDir/${entityType.lowercase()}s.json"
    private var data: MutableList<Entity> = mutableListOf()

    init {
        load()
    }

    private fun load() {
        try {
            if (fs.exists(filePath)) {
                val list = JsonUtil.parseJsonFileToList(filePath)
                data = list.map { map ->
                    @Suppress("UNCHECKED_CAST")
                    val key = map["key"] as? Map<String, Map<String, Any>>
                    @Suppress("UNCHECKED_CAST")
                    val attrs = (map["attributes"] as? Map<String, Any>) ?: map
                    Entity(entityType, key, attrs)
                }.toMutableList()
            } else {
                data = mutableListOf()
            }
        } catch (e: Exception) {
            data = mutableListOf()
        }
    }

    private fun save() {
        try {
            val list = data.map { e ->
                mapOf(
                    "type" to e.type,
                    "key" to (e.key ?: emptyMap()),
                    "attributes" to e.attributes
                )
            }
            JsonUtil.toJsonFile(list, filePath)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun getType(): String = entityType

    override fun fetchAll(): List<Entity> = data.toList()

    override fun fetchById(id: Map<String, Map<String, Any>>): Optional<Entity> {
        val target = data.firstOrNull { it.key == id || matchesKey(it, id) }
        return Optional.ofNullable(target)
    }

    private fun matchesKey(entity: Entity, id: Map<String, Map<String, Any>>): Boolean {
        for ((k, v) in id) {
            val valueObj = v["value"]
            if (entity.attributes[k] == valueObj) return true
        }
        return false
    }

    override fun store(entity: Entity): Entity {
        val key = entity.key
        if (key != null) {
            data.removeAll { it.key == key || matchesKey(it, key) }
        }
        data.add(entity)
        save()
        return entity
    }

    override fun storeAll(entities: Collection<Entity>): List<Entity> {
        for (e in entities) {
            val key = e.key
            if (key != null) {
                data.removeAll { it.key == key || matchesKey(it, key) }
            }
            data.add(e)
        }
        save()
        return entities.toList()
    }

    override fun disposeById(id: Map<String, Map<String, Any>>) {
        data.removeAll { it.key == id || matchesKey(it, id) }
        save()
    }

    override fun count(): Long = data.size.toLong()

    override fun hasId(id: Map<String, Map<String, Any>>): Boolean {
        return data.any { it.key == id || matchesKey(it, id) }
    }

    override fun commit() {
        save()
    }

    override fun fetchPage(
        filter: Filter?,
        sort: Sort?,
        fetches: List<String>?,
        page: Int,
        size: Int,
        includeCount: Boolean
    ): Page<Entity> {
        val start = page * size
        if (start >= data.size) {
            return Page(emptyList(), page, size, false, data.size.toLong())
        }
        val end = min(start + size, data.size)
        val content = data.subList(start, end)
        val hasNext = end < data.size
        return Page(content, page, size, hasNext, data.size.toLong())
    }
}
