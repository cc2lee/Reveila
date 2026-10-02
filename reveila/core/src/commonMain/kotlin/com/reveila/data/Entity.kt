package com.reveila.data

/**
 * Represents a generic entity with dynamic key and attribute structures.
 * Multiplatform implementation replacing legacy Java Entity.
 */
open class Entity(
    type: String,
    key: Map<String, Map<String, Any>>? = null,
    attributes: Map<String, Any>
) {
    companion object {
        const val TYPE: String = "type"
        const val KEY: String = "key"
        const val ATTRIBUTES: String = "attributes"
    }

    private val map: MutableMap<String, Any?> = mutableMapOf()

    init {
        require(type.isNotBlank()) { "Type must not be null or blank" }
        map[TYPE] = type

        if (key != null && key.isNotEmpty()) {
            for ((k, v) in key) {
                requireNotNull(k) { "Key map cannot contain null keys" }
                requireNotNull(v) { "Key map cannot contain null values" }
                for ((innerK, innerV) in v) {
                    requireNotNull(innerK) { "Inner key map cannot contain null keys" }
                    requireNotNull(innerV) { "Inner key map cannot contain null values" }
                }
            }
            map[KEY] = key.toMap()
        } else {
            map[KEY] = null
        }

        require(attributes.isNotEmpty()) { "Attributes map must contain at least one entry" }
        for ((attrKey, _) in attributes) {
            requireNotNull(attrKey) { "Attributes map cannot contain null keys" }
        }
        map[ATTRIBUTES] = attributes.toMap()
    }

    val type: String
        get() = map[TYPE] as String

    @Suppress("UNCHECKED_CAST")
    val key: Map<String, Map<String, Any>>?
        get() = map[KEY] as? Map<String, Map<String, Any>>

    @Suppress("UNCHECKED_CAST")
    val attributes: Map<String, Any>
        get() = map[ATTRIBUTES] as Map<String, Any>
}
