package com.reveila.system

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads system-wide perimeter settings from global-perimeter.json 
 * and merges them into plugin definition JSON files.
 */
open class PerimeterEnforcementMerger {

    companion object {
        @JvmStatic
        fun getProjectRoot(): Path {
            var current: Path? = Paths.get("").toAbsolutePath()
            while (current != null && !Files.exists(current.resolve("system-home"))) {
                current = current.parent
            }
            return current ?: Paths.get("")
        }

        @JvmField
        val CONFIG_FILE_PATH: Path = getProjectRoot().resolve("system-home/standard/configs/global-perimeter.json")

        @JvmField
        val PLUGINS_DIR: Path = getProjectRoot().resolve("system-home/standard/configs/plugins")

        @JvmStatic
        fun main(args: Array<String>) {
            val merger = PerimeterEnforcementMerger()
            try {
                merger.mergePerimeterSettings()
                println("Successfully merged perimeter settings into plugin configurations.")
            } catch (e: Exception) {
                System.err.println("Failed to merge perimeter settings: " + e.message)
                e.printStackTrace()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getAgencyPerimeterNode(configFilePath: Path): JsonNode? {
            if (!Files.exists(configFilePath)) return null

            val mapper = ObjectMapper()
            return mapper.readTree(configFilePath.toFile())
        }

        @JvmStatic
        @Throws(IOException::class)
        fun updatePluginJson(jsonFile: Path, agencyPerimeterNode: JsonNode?, mapper: ObjectMapper) {
            val root = mapper.readTree(jsonFile.toFile())

            // Check if there is a "plugin" wrapper node
            if (root.has("plugin") && root.get("plugin").isObject) {
                val pluginNode = root.get("plugin") as ObjectNode
                pluginNode.set<JsonNode>("agency_perimeter", agencyPerimeterNode)
            } else if (root.isObject) {
                // Alternatively, append to the root if no "plugin" wrapper
                (root as ObjectNode).set<JsonNode>("agency_perimeter", agencyPerimeterNode)
            }

            // Write the merged JSON back to the file
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), root)
            println("Updated " + jsonFile.fileName)
        }
    }

    @Throws(IOException::class)
    open fun mergePerimeterSettings() {
        val agencyPerimeterNode = getAgencyPerimeterNode(CONFIG_FILE_PATH)
            ?: throw IllegalStateException("Global perimeter configuration not found at $CONFIG_FILE_PATH")

        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

        // Process all plugin JSON files
        Files.list(PLUGINS_DIR).use { paths ->
            paths.filter { it.toString().endsWith(".json") }
                .forEach { p ->
                    try {
                        updatePluginJson(p, agencyPerimeterNode, mapper)
                    } catch (e: IOException) {
                        System.err.println("Error processing file $p: ${e.message}")
                    }
                }
        }
    }
}
