package com.reveila.util.json

import com.reveila.system.io.PlatformFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure Kotlin Multiplatform utility class for JSON serialization and deserialization
 * powered by `kotlinx.serialization.json`.
 */
class JsonUtil private constructor() {

    companion object {
        val JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        val PRETTY_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun toElement(value: Any?): JsonElement {
            return when (value) {
                null -> JsonNull
                is JsonElement -> value
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Map<*, *> -> {
                    val map = LinkedHashMap<String, JsonElement>()
                    for ((k, v) in value) {
                        if (k != null) {
                            map[k.toString()] = toElement(v)
                        }
                    }
                    JsonObject(map)
                }
                is Iterable<*> -> {
                    val list = value.map { toElement(it) }
                    JsonArray(list)
                }
                is Array<*> -> {
                    val list = value.map { toElement(it) }
                    JsonArray(list)
                }
                else -> JsonPrimitive(value.toString())
            }
        }

        fun fromElement(element: JsonElement): Any? {
            return when (element) {
                is JsonNull -> null
                is JsonPrimitive -> {
                    if (element.isString) {
                        element.content
                    } else {
                        element.booleanOrNull
                            ?: element.longOrNull
                            ?: element.doubleOrNull
                            ?: element.content
                    }
                }
                is JsonObject -> {
                    val map = LinkedHashMap<String, Any?>()
                    for ((k, v) in element) {
                        map[k] = fromElement(v)
                    }
                    map
                }
                is JsonArray -> {
                    element.map { fromElement(it) }
                }
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        fun toJsonString(data: Any?): String {
            return try {
                val element = toElement(data)
                JSON.encodeToString(JsonElement.serializer(), element)
            } catch (e: Exception) {
                throw JsonException("Failed to serialize to JSON: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        fun toPrettyJsonString(data: Any?): String {
            return try {
                val element = toElement(data)
                PRETTY_JSON.encodeToString(JsonElement.serializer(), element)
            } catch (e: Exception) {
                throw JsonException("Failed to serialize to pretty JSON: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        fun toJsonFile(data: Any?, filePath: String) {
            try {
                val content = toPrettyJsonString(data)
                PlatformFileSystem().writeText(filePath, content, append = false)
            } catch (e: Exception) {
                throw JsonException("Failed to write JSON file: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        @Suppress("UNCHECKED_CAST")
        fun parseJsonStringToMap(json: String?): Map<String, Any> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val element = JSON.parseToJsonElement(json)
                (fromElement(element) as? Map<String, Any>) ?: emptyMap()
            } catch (e: Exception) {
                throw JsonException("Failed to parse JSON string to map: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        @Suppress("UNCHECKED_CAST")
        fun parseJsonStringToList(json: String?): List<Map<String, Any>> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val element = JSON.parseToJsonElement(json)
                (fromElement(element) as? List<Map<String, Any>>) ?: emptyList()
            } catch (e: Exception) {
                throw JsonException("Failed to parse JSON string to list: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        fun parseJsonFileToMap(filePath: String): Map<String, Any> {
            return try {
                val fs = PlatformFileSystem()
                if (!fs.exists(filePath)) throw JsonException("File not found: $filePath")
                val text = fs.readText(filePath)
                parseJsonStringToMap(text)
            } catch (e: Exception) {
                throw JsonException("Failed to read JSON map from $filePath: ${e.message}", e)
            }
        }

        @kotlin.jvm.JvmStatic
        @Throws(JsonException::class)
        fun parseJsonFileToList(filePath: String): List<Map<String, Any>> {
            return try {
                val fs = PlatformFileSystem()
                if (!fs.exists(filePath)) throw JsonException("File not found: $filePath")
                val text = fs.readText(filePath)
                parseJsonStringToList(text)
            } catch (e: Exception) {
                throw JsonException("Failed to read JSON list from $filePath: ${e.message}", e)
            }
        }

        fun removeRoot(json: String): String {
            return try {
                val element = JSON.parseToJsonElement(json)
                if (element is JsonObject) {
                    val potentialWrappers = arrayOf("call", "command", "tool_call", "function")
                    for (wrapper in potentialWrappers) {
                        val nested = element[wrapper]
                        if (nested is JsonObject) {
                            return nested.toString()
                        }
                    }
                }
                json
            } catch (e: Exception) {
                json
            }
        }

        fun clean(json: String?): String? {
            if (json == null) return null
            val begin = json.indexOf('{')
            val end = json.lastIndexOf('}')
            if (begin != -1 && end != -1 && end > begin) {
                return json.substring(begin, end + 1).trim()
            }
            return json.trim()
        }

        fun findValuesByKey(map: Map<String, Any>?, key: String): List<Any> {
            val results = mutableListOf<Any>()
            findValuesByKeyHelper(map, key, results)
            return results
        }

        private fun findValuesByKeyHelper(map: Map<*, *>?, key: String, results: MutableList<Any>) {
            if (map == null) return
            for ((k, v) in map) {
                if (k == key && v != null) {
                    results.add(v)
                }
                when (v) {
                    is Map<*, *> -> findValuesByKeyHelper(v, key, results)
                    is Iterable<*> -> {
                        for (item in v) {
                            if (item is Map<*, *>) {
                                findValuesByKeyHelper(item, key, results)
                            }
                        }
                    }
                }
            }
        }
    }
}
