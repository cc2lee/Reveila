package com.reveila.system.platform

object JvmPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? = System.getenv(key)

    override fun getProperty(key: String): String? = System.getProperty(key)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = System.getProperty("os.name") ?: "JVM",
        version = System.getProperty("os.version") ?: "unknown",
        arch = System.getProperty("os.arch") ?: "unknown"
    )
}

actual fun getPlatformSystem(): PlatformSystem = JvmPlatformSystem
