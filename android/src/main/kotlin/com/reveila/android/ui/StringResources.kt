package com.reveila.android.ui

import android.content.Context
import java.io.IOException
import java.util.Properties

/**
 * String Resources loader for externalized UI strings.
 * Loads string properties from the Reveila system resources directory
 * to enable internationalization and centralized string management.
 * 
 * @author CL
 */
class StringResources(context: Context) {
    private val properties = Properties()
    
    init {
        try {
            // Load from assets/reveila/system/resources/ui/en/text.en.properties
            context.assets.open("reveila/system/resources/ui/en/text.en.properties").use { inputStream ->
                properties.load(inputStream)
            }
        } catch (e: IOException) {
            // Log error but don't crash - fall back to default values
            android.util.Log.e("StringResources", "Failed to load string resources", e)
        }
    }
    
    /**
     * Get a localized string by key.
     * 
     * @param key The property key
     * @param default Default value if key is not found
     * @return The localized string or default value
     */
    fun getString(key: String, default: String = ""): String {
        return properties.getProperty(key, default)
    }
    
    /**
     * Check if a key exists in the properties
     */
    fun hasKey(key: String): Boolean {
        return properties.containsKey(key)
    }
}
