package com.reveila.data

import com.reveila.util.json.JsonUtil

abstract class EntityMapper<T>(
    open var entityClass: Any? = null
) {
    constructor() : this(null)

    open fun toGenericEntity(pojo: T, type: String): Entity {
        requireNotNull(pojo) { "pojo cannot be null" }
        requireNotNull(type) { "type cannot be null" }
        val json = JsonUtil.toJsonString(pojo)
        val attributes = JsonUtil.parseJsonStringToMap(json)
        val keyMap = extractKey(pojo)
        return Entity(type, keyMap, attributes)
    }

    abstract fun extractKey(typedEntity: T): Map<String, Map<String, Any>>
}
