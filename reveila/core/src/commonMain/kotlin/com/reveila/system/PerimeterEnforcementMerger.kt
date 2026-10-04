package com.reveila.system

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

open class PerimeterEnforcementMerger {

    companion object {
        fun updatePluginJson(jsonFilePath: String, perimeterMap: Map<String, Any>) {
            val fs = PlatformFileSystem()
            val content = fs.readText(jsonFilePath)
            val root = JsonUtil.parseJsonStringToMap(content).toMutableMap()

            @Suppress("UNCHECKED_CAST")
            val pluginNode = (root["plugin"] as? Map<String, Any>)?.toMutableMap()
            if (pluginNode != null) {
                pluginNode["agency_perimeter"] = perimeterMap
                root["plugin"] = pluginNode
            } else {
                root["agency_perimeter"] = perimeterMap
            }

            JsonUtil.toJsonFile(root, jsonFilePath)
        }
    }

    open fun mergePerimeterSettings(configPath: String = "system-home/standard/configs/global-perimeter.json", pluginsDir: String = "system-home/standard/configs/plugins") {
        val fs = PlatformFileSystem()
        if (!fs.exists(configPath)) return

        val configText = fs.readText(configPath)
        val perimeterMap = JsonUtil.parseJsonStringToMap(configText)

        val files = fs.listRelativePaths(pluginsDir, "json")
        for (f in files) {
            val fullPath = fs.resolve(pluginsDir, f)
            try {
                updatePluginJson(fullPath, perimeterMap)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
