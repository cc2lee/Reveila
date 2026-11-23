package com.reveila.aegin.cluster

import com.reveila.util.json.JsonUtil
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Zero-config LAN discovery client for Aegin Mobile.
 * Broadcasts `AEGIN_DISCOVER_LEAD` on UDP port [LAN_DISCOVERY_PORT] to automatically
 * locate a paired Aegin Desktop Lead Node on the same Wi-Fi network even when its
 * DHCP IP address changes.
 */
object AndroidLanDiscoveryClient {
    const val LAN_DISCOVERY_PORT = 41920
    const val DISCOVERY_REQUEST_MAGIC = "AEGIN_DISCOVER_LEAD"

    fun discoverDesktopLeadUrls(timeoutMs: Int = 1500): List<String> {
        val found = LinkedHashSet<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
            }
            val reqBytes = DISCOVERY_REQUEST_MAGIC.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(reqBytes, reqBytes.size, broadcastAddr, LAN_DISCOVERY_PORT))

            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                    val body = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val parsed = JsonUtil.parseJsonStringToMap(body)
                    val lanUrls = parsed["lanUrls"] as? List<*>
                    lanUrls?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.startsWith("http") } }
                        ?.forEach { found.add(it.trimEnd('/')) }
                    val port = (parsed["port"] as? Number)?.toInt() ?: 8080
                    val senderIp = packet.address?.hostAddress?.trim()
                    if (!senderIp.isNullOrBlank() && senderIp != "127.0.0.1") {
                        found.add("http://$senderIp:$port")
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
        return found.toList()
    }
}
