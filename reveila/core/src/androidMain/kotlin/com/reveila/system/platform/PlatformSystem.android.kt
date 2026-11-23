package com.reveila.system.platform

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object AndroidPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? = System.getenv(key)

    override fun getProperty(key: String): String? = System.getProperty(key)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = "Android",
        version = "API ${Build.VERSION.SDK_INT} (${Build.VERSION_RELEASE})",
        arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    )

    override fun getLocalIpAddresses(): Set<String> {
        val addresses = LinkedHashSet<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue
                val inetAddrs = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val addr = inetAddrs.nextElement()
                    val hostAddr = addr.hostAddress?.substringBefore('%')?.trim()
                    if (!hostAddr.isNullOrBlank() && addr is Inet4Address) {
                        addresses.add(hostAddr)
                    }
                }
            }
        } catch (_: Exception) {
        }
        addresses.add("127.0.0.1")
        addresses.add("localhost")
        return addresses
    }

    override fun getAvailableProcessors(): Int = Runtime.getRuntime().availableProcessors()

    override fun getTotalMemoryMb(): Long =
        (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).coerceAtLeast(512L)
}

actual fun getPlatformSystem(): PlatformSystem = AndroidPlatformSystem
