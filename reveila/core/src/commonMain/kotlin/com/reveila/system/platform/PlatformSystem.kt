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

    /** Returns all known IP addresses / hostnames bound to this host. */
    fun getLocalIpAddresses(): Set<String> = setOf("127.0.0.1", "localhost")

    /** Returns available logical CPU cores on this host. */
    fun getAvailableProcessors(): Int = 4

    /** Returns approximate total physical (or JVM max) memory in megabytes. */
    fun getTotalMemoryMb(): Long = 4096L

    companion object {
        fun current(): PlatformSystem = getPlatformSystem()
        fun getEnv(key: String): String? = current().getEnv(key)
        fun getProperty(key: String): String? = current().getProperty(key)
        fun currentTimeMillis(): Long = current().currentTimeMillis()
        fun getOsInfo(): PlatformOsInfo = current().getOsInfo()
        fun getLocalIpAddresses(): Set<String> = current().getLocalIpAddresses()
        fun getAvailableProcessors(): Int = current().getAvailableProcessors()
        fun getTotalMemoryMb(): Long = current().getTotalMemoryMb()
    }
}

expect fun getPlatformSystem(): PlatformSystem
