package com.reveila.system.platform

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
object MingwPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? {
        val ptr = getenv(key) ?: return null
        return ptr.toKString()
    }

    override fun getProperty(key: String): String? = getEnv(key)

    override fun currentTimeMillis(): Long {
        return time(null) * 1000L
    }

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = "Windows Native",
        version = "MinGW",
        arch = "x64"
    )
}

actual fun getPlatformSystem(): PlatformSystem = MingwPlatformSystem
