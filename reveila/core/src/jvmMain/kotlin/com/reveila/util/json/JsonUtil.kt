package com.reveila.util.json

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Objects

/**
 * A utility class for JSON serialization and deserialization.
 *
 * This class provides static methods to convert between JSON strings/files and
 * Java objects.
 */
class JsonUtil private constructor() {

    companion object {
        @JvmField
        val MAPPER: ObjectMapper = ObjectMapper()

        @JvmField
        val PRETTY_WRITER: ObjectWriter = MAPPER.writerWithDefaultPrettyPrinter()

        @JvmField
        val MAP_TYPE_REFERENCE: TypeReference<Map<String, Any>> = object : TypeReference<Map<String, Any>>() {}

        @JvmField
        val LIST_OF_MAPS_TYPE_REFERENCE: TypeReference<List<Map<String, Any>>> = object : TypeReference<List<Map<String, Any>>>() {}

        @JvmStatic
        @Throws(JsonException::class)
        fun writeAsJsonArray(output: OutputStream, mapArray: Array<out Map<*, *>>) {
            try {
                val jsonArray = MAPPER.createArrayNode()
                for (map in mapArray) {
                    val jsonObject = MAPPER.convertValue(map, ObjectNode::class.java) as ObjectNode
                    jsonArray.add(jsonObject)
                }
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, jsonArray)
            } catch (e: Exception) {
                throw JsonException("Failed to write JSON array to output stream: ${e.message}", e)
            } finally {
                try {
                    output.flush()
                } catch (ioe: IOException) {
                    // Ignore
                }
            }
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        @Throws(JsonException::class)
        fun readJsonArray(jsonArrayIS: InputStream): Array<Map<String, Any>> {
            try {
                val rootNode = MAPPER.readTree(jsonArrayIS)

                if (rootNode.isArray) {
                    val mapList = ArrayList<Map<String, Any>>()
                    val elements = rootNode.elements()
                    while (elements.hasNext()) {
                        val node = elements.next()
                        mapList.add(MAPPER.convertValue(node, object : TypeReference<Map<String, Any>>() {}))
                    }
                    return mapList.toTypedArray()
                } else {
                    throw JsonException("Input stream does not contain a JSON array")
                }
            } catch (e: Exception) {
                throw JsonException("Failed to read JSON array from input stream: ${e.message}", e)
            }
        }

        @JvmStatic
        fun removeRoot(json: String): String {
            try {
                val root = MAPPER.readTree(json)

                // List of common "hallucinated" wrapper keys from local models
                val potentialWrappers = arrayOf("call", "command", "tool_call", "function")

                for (wrapper in potentialWrappers) {
                    if (root.has(wrapper) && root.get(wrapper).isObject) {
                        val flatNode = MAPPER.createObjectNode()
                        val nested = root.get(wrapper)

                        // Hoist all fields from the nested object to the new root
                        nested.fields().forEachRemaining { entry ->
                            flatNode.set<JsonNode>(entry.key, entry.value)
                        }

                        // Return the flattened JSON string
                        return flatNode.toString()
                    }
                }
            } catch (e: Exception) {
                // If parsing fails here, return original and let the main MAPPER handle the error
                return json
            }
            return json
        }

        @JvmStatic
        fun clean(json: String?): String? {
            if (json == null) return null

            val begin = json.indexOf("{")
            val end = json.lastIndexOf("}")

            if (begin != -1 && end != -1 && end > begin) {
                return json.substring(begin, end + 1).trim()
            }

            return json.trim()
        }

        /**
         * Method to deserialize JSON content from given JSON content String.
         */
        @JvmStatic
        @Throws(JsonException::class)
        fun <T> toObject(content: String?, valueType: Class<T>): T {
            try {
                return MAPPER.readValue(content, valueType)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun parseJsonFileToMap(filePath: String): Map<String, Any> {
            try {
                return MAPPER.readValue(File(filePath), MAP_TYPE_REFERENCE)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun parseJsonStringToMap(json: String?): Map<String, Any> {
            try {
                return MAPPER.readValue(json, MAP_TYPE_REFERENCE)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun parseJsonFileToList(filePath: String): List<Map<String, Any>> {
            try {
                return MAPPER.readValue(File(filePath), LIST_OF_MAPS_TYPE_REFERENCE)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun parseJsonStringToList(json: String?): List<Map<String, Any>> {
            try {
                return MAPPER.readValue(json, LIST_OF_MAPS_TYPE_REFERENCE)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        /**
         * Serializes an object to a JSON string.
         */
        @JvmStatic
        @Throws(JsonException::class)
        fun toJsonString(data: Any?): String {
            try {
                return MAPPER.writeValueAsString(data)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        /**
         * Serializes an object to a JSON file with pretty printing.
         */
        @JvmStatic
        @Throws(JsonException::class)
        fun toJsonFile(data: Any?, filePath: String) {
            try {
                PRETTY_WRITER.writeValue(File(filePath), data)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun writeToStream(data: Any?, outputStream: OutputStream) {
            try {
                PRETTY_WRITER.writeValue(outputStream, data)
            } catch (e: Exception) {
                throw JsonException(e.message, e)
            }
        }

        // Recursively find all values for a given key in a (possibly nested) Map
        @JvmStatic
        fun findValuesByKey(map: Map<String, Any>?, key: String): List<Any> {
            val results = ArrayList<Any>()
            findValuesByKeyHelper(map, key, results)
            return results
        }

        private fun findValuesByKeyHelper(map: Map<*, *>?, key: String, results: MutableList<Any>) {
            if (map == null) {
                return
            }
            for (entry in map.entries) {
                if (Objects.equals(entry.key, key)) {
                    entry.value?.let { results.add(it) }
                }
                val value = entry.value
                if (value is Map<*, *>) {
                    findValuesByKeyHelper(value, key, results)
                } else if (value is List<*>) {
                    for (item in value) {
                        if (item is Map<*, *>) {
                            findValuesByKeyHelper(item, key, results)
                        }
                    }
                }
            }
        }
    }
}
