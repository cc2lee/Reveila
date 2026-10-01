package com.reveila.system.platform

import android.os.Build

object AndroidPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? = System.getenv(key)

    override fun getProperty(key: String): String? = System.getProperty(key)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = "Android",
        version = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
        arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    )
}

actual fun getPlatformSystem(): PlatformSystem = AndroidPlatformSystem
