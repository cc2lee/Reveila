package com.reveila.system.platform

data class PlatformOsInfo(
    val name: String,
    val version: String,
    val arch: String
)

/**
 * Multiplatform system properties, environment variables, and clock abstraction.
 */
interface PlatformSystem {
    fun getEnv(key: String): String?
    fun getProperty(key: String): String?
    fun currentTimeMillis(): Long
    fun getOsInfo(): PlatformOsInfo

    companion object {
        fun current(): PlatformSystem = getPlatformSystem()
        fun getEnv(key: String): String? = current().getEnv(key)
        fun getProperty(key: String): String? = current().getProperty(key)
        fun currentTimeMillis(): Long = current().currentTimeMillis()
        fun getOsInfo(): PlatformOsInfo = current().getOsInfo()
    }
}

expect fun getPlatformSystem(): PlatformSystem
