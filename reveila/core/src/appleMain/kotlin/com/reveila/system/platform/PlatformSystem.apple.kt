package com.reveila.system.platform

import platform.Foundation.NSProcessInfo
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
object ApplePlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? {
        return NSProcessInfo.processInfo.environment[key] as? String
    }

    override fun getProperty(key: String): String? {
        return getEnv(key)
    }

    override fun currentTimeMillis(): Long {
        memScoped {
            val tv = alloc<timeval>()
            gettimeofday(tv.ptr, null)
            return (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
        }
    }

    override fun getOsInfo(): PlatformOsInfo {
        val info = NSProcessInfo.processInfo
        return PlatformOsInfo(
            name = "Apple Darwin",
            version = info.operatingSystemVersionString,
            arch = "arm64"
        )
    }
}

actual fun getPlatformSystem(): PlatformSystem = ApplePlatformSystem
