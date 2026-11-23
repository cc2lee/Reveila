package com.reveila.system.platform

import java.lang.management.ManagementFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object JvmPlatformSystem : PlatformSystem {
    override fun getEnv(key: String): String? = System.getenv(key)

    override fun getProperty(key: String): String? = System.getProperty(key)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getOsInfo(): PlatformOsInfo = PlatformOsInfo(
        name = System.getProperty("os.name") ?: "JVM",
        version = System.getProperty("os.version") ?: "unknown",
        arch = System.getProperty("os.arch") ?: "unknown"
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
                    if (!hostAddr.isNullOrBlank()) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            addresses.add(hostAddr)
                        }
                    }
                }
            }
            // Also include loopback and secondary addresses after primary IPv4s
            val interfaces2 = NetworkInterface.getNetworkInterfaces()
            while (interfaces2 != null && interfaces2.hasMoreElements()) {
                val iface = interfaces2.nextElement()
                if (!iface.isUp) continue
                val inetAddrs = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val addr = inetAddrs.nextElement()
                    val hostAddr = addr.hostAddress?.substringBefore('%')?.trim()
                    if (!hostAddr.isNullOrBlank()) {
                        addresses.add(hostAddr)
                    }
                }
            }
            runCatching {
                val localHost = InetAddress.getLocalHost()
                localHost.hostAddress?.takeIf { it.isNotBlank() }?.let { addresses.add(it) }
                localHost.hostName?.takeIf { it.isNotBlank() }?.let { addresses.add(it.lowercase()) }
            }
        } catch (_: Exception) {
        }
        addresses.add("127.0.0.1")
        addresses.add("localhost")
        return addresses
    }

    override fun getAvailableProcessors(): Int = Runtime.getRuntime().availableProcessors()

    override fun getTotalMemoryMb(): Long {
        try {
            val mgmtClass = Class.forName("java.lang.management.ManagementFactory")
            val osBean = mgmtClass.getMethod("getOperatingSystemMXBean").invoke(null)
            val method = osBean?.javaClass?.methods?.firstOrNull {
                it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize"
            }
            val bytes = (method?.invoke(osBean) as? Number)?.toLong()
            if (bytes != null && bytes > 0L) {
                return bytes / (1024L * 1024L)
            }
        } catch (_: Throwable) {
            // Android ART does not include java.lang.management; fall back to /proc/meminfo or Runtime heap.
        }
        try {
            val memInfo = java.io.File("/proc/meminfo")
            if (memInfo.exists() && memInfo.canRead()) {
                val totalKb = memInfo.useLines { lines ->
                    lines.firstOrNull { it.startsWith("MemTotal:") }
                        ?.replace(Regex("[^0-9]"), "")
                        ?.toLongOrNull()
                }
                if (totalKb != null && totalKb > 0L) {
                    return totalKb / 1024L
                }
            }
        } catch (_: Throwable) {
        }
        return (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).coerceAtLeast(512L)
    }
}

actual fun getPlatformSystem(): PlatformSystem = JvmPlatformSystem
