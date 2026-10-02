package com.reveila.data

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.util.TimeZone

abstract class EntityMapper<T>(
    @JvmField
    protected var entityClass: Class<T>
) {
    init {
        requireNotNull(entityClass) { "Entity class cannot be null" }
    }

    open fun getEntityClass(): Class<T> = entityClass

    companion object {
        @JvmField
        val objectMapper: ObjectMapper = JsonMapper.builder()
            .addModule(JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .addModule(Jdk8Module())
            .enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.mappedFeature())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .defaultTimeZone(TimeZone.getDefault())
            .build()

        @JvmStatic
        fun getObjectMapper(): ObjectMapper = objectMapper
    }

    open fun toGenericEntity(pojo: T, type: String): Entity {
        requireNotNull(pojo) { "pojo cannot be null" }
        requireNotNull(type) { "type cannot be null" }

        @Suppress("UNCHECKED_CAST")
        val attributes = objectMapper.convertValue(pojo, MutableMap::class.java) as MutableMap<String, Any>
        val keyMap = extractKey(pojo)
        return Entity(type, keyMap, attributes)
    }

    open fun fromGenericEntity(entity: Entity?, targetClass: Class<T>?): T {
        requireNotNull(entity) { "entity cannot be null" }
        requireNotNull(targetClass) { "targetClass cannot be null" }

        val sourceMap = HashMap<String, Any>()
        sourceMap.putAll(entity.attributes)
        val keyMap = entity.key
        if (!keyMap.isNullOrEmpty()) {
            val keyName = keyMap.keys.firstOrNull()
            if (!keyName.isNullOrEmpty()) {
                sourceMap[keyName] = keyMap[keyName] as Any
            } else {
                val it = keyMap.values.iterator()
                if (it.hasNext()) {
                    sourceMap.putAll(it.next())
                }
            }
        }

        val result: T? = objectMapper.convertValue(sourceMap, targetClass)
            ?: throw IllegalStateException("Failed to convert GenericEntity to ${targetClass.name}")
        return result!!
    }

    abstract fun extractKey(typedEntity: T): Map<String, Map<String, Any>>
}
