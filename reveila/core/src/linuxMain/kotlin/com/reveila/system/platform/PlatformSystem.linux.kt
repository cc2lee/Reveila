package com.reveila.system.platform

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
object LinuxPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? {
        val ptr = getenv(key) ?: return null
        return ptr.toKString()
    }

    override fun getProperty(key: String): String? = getEnv(key)

    override fun currentTimeMillis(): Long {
        memScoped {
            val tv = alloc<timeval>()
            gettimeofday(tv.ptr, null)
            return (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
        }
    }

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = "Linux Native",
        version = "POSIX",
        arch = "x64"
    )
}

actual fun getPlatformSystem(): PlatformSystem = LinuxPlatformSystem
