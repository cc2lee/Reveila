package com.reveila.aegin.cluster

import com.reveila.system.Reveila
import com.reveila.system.Subject
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight embedded HTTP cluster server + UDP LAN discovery responder for Aegin Desktop
 * operating as a solo-user Cluster Lead Node.
 *
 * Uses JDK's built-in [HttpServer] (zero extra dependencies) to expose:
 * - `GET /health`
 * - `POST /api/components/{componentName}/invoke` (authenticated via `X-Reveila-Cluster-Token`)
 *
 * Also responds to UDP broadcast `AEGIN_DISCOVER_LEAD` on port [LAN_DISCOVERY_PORT] so
 * Aegin Mobile automatically finds Desktop's current DHCP LAN IP without a static IP.
 */
class DesktopClusterHttpServer(
    private val engine: Reveila,
    private val subject: Subject
) {
    companion object {
        const val DEFAULT_HTTP_PORT = 8080
        const val LAN_DISCOVERY_PORT = 41920
        const val DISCOVERY_REQUEST_MAGIC = "AEGIN_DISCOVER_LEAD"
    }

    private var server: HttpServer? = null
    private var httpExecutor: ExecutorService? = null
    private var udpSocket: DatagramSocket? = null
    private var udpThread: Thread? = null
    private val running = AtomicBoolean(false)

    @Volatile
    var listeningPort: Int = DEFAULT_HTTP_PORT
        private set

    @Volatile
    var clusterSecret: String = ""
        private set

    fun isRunning(): Boolean = running.get()

    @Synchronized
    fun start(port: Int = DEFAULT_HTTP_PORT, secret: String = "") {
        if (running.get()) {
            this.clusterSecret = secret
            return
        }
        this.listeningPort = port
        this.clusterSecret = secret

        val httpServer = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
        val exec = Executors.newFixedThreadPool(4) { r ->
            Thread(r, "aegin-desktop-cluster-http").apply { isDaemon = true }
        }
        httpServer.executor = exec

        httpServer.createContext("/health") { exchange ->
            handleHealth(exchange)
        }
        httpServer.createContext("/api/components/") { exchange ->
            handleComponentInvoke(exchange)
        }

        httpServer.start()
        this.server = httpServer
        this.httpExecutor = exec
        running.set(true)

        startLanDiscoveryResponder()
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try {
            udpSocket?.close()
        } catch (_: Exception) {
        }
        udpSocket = null
        udpThread?.interrupt()
        udpThread = null

        try {
            server?.stop(0)
        } catch (_: Exception) {
        }
        server = null

        try {
            httpExecutor?.shutdownNow()
        } catch (_: Exception) {
        }
        httpExecutor = null
    }

    fun getLanUrls(): List<String> {
        val ips = PlatformSystem.getLocalIpAddresses()
            .filter { ip ->
                ip.isNotBlank() &&
                    ip != "127.0.0.1" &&
                    ip != "localhost" &&
                    ip != "::1" &&
                    !ip.contains(":")
            }
        return if (ips.isNotEmpty()) {
            ips.map { "http://$it:$listeningPort" }
        } else {
            listOf("http://127.0.0.1:$listeningPort")
        }
    }

    private fun handleHealth(exchange: HttpExchange) {
        try {
            if (!"GET".equals(exchange.requestMethod, ignoreCase = true)) {
                sendJson(exchange, 405, """{"error":"Method not allowed"}""")
                return
            }
            val cluster = engine.getClusterService()
            val payload = linkedMapOf(
                "status" to "UP",
                "role" to (cluster?.role?.name ?: "LEAD"),
                "nodeId" to (cluster?.nodeId ?: "aegin-desktop"),
                "computePower" to (cluster?.computePower?.name ?: "HIGH"),
                "port" to listeningPort
            )
            sendJson(exchange, 200, JsonUtil.toJsonString(payload))
        } catch (e: Exception) {
            sendJson(exchange, 500, JsonUtil.toJsonString(mapOf("error" to (e.message ?: "Internal error"))))
        }
    }

    private fun handleComponentInvoke(exchange: HttpExchange) {
        try {
            if (!"POST".equals(exchange.requestMethod, ignoreCase = true)) {
                sendJson(exchange, 405, """{"error":"Method not allowed"}""")
                return
            }

            // Verify shared cluster token when configured
            val expectedSecret = clusterSecret.trim()
            if (expectedSecret.isNotEmpty()) {
                val provided = exchange.requestHeaders.getFirst("X-Reveila-Cluster-Token")?.trim().orEmpty()
                if (provided != expectedSecret) {
                    sendJson(exchange, 403, """{"error":"Forbidden: invalid cluster token"}""")
                    return
                }
            }

            // Path: /api/components/{componentName}/invoke
            val path = exchange.requestURI.path ?: ""
            val trimmed = path.removePrefix("/api/components/").removeSuffix("/invoke").trim('/')
            if (trimmed.isEmpty() || trimmed.contains("/")) {
                sendJson(exchange, 400, """{"error":"Invalid component invoke path"}""")
                return
            }

            val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = JsonUtil.parseJsonStringToMap(body)
            val methodName = (parsed["methodName"] ?: parsed["method"])?.toString()?.trim().orEmpty()
            if (methodName.isEmpty()) {
                sendJson(exchange, 400, """{"error":"Missing methodName in request payload"}""")
                return
            }
            val rawArgs = parsed["args"] as? List<*> ?: emptyList<Any?>()
            val argsArray = rawArgs.toTypedArray()
            val remoteIp = exchange.remoteAddress?.address?.hostAddress ?: "127.0.0.1"

            val result = engine.invoke(trimmed, methodName, argsArray, remoteIp, subject)
            val responseJson = when (result) {
                null -> "null"
                is String -> JsonUtil.toJsonString(result)
                else -> JsonUtil.toJsonString(result)
            }
            sendJson(exchange, 200, responseJson)
        } catch (e: Exception) {
            val errJson = JsonUtil.toJsonString(mapOf("error" to (e.message ?: "Invocation failed")))
            sendJson(exchange, 500, errJson)
        }
    }

    private fun startLanDiscoveryResponder() {
        val thread = Thread({
            try {
                val socket = DatagramSocket(LAN_DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                udpSocket = socket
                val buf = ByteArray(1024)
                while (running.get() && !socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (msg.startsWith(DISCOVERY_REQUEST_MAGIC)) {
                            val cluster = engine.getClusterService()
                            val replyMap = linkedMapOf(
                                "leaderId" to (cluster?.nodeId ?: "aegin-desktop"),
                                "lanUrls" to getLanUrls(),
                                "port" to listeningPort,
                                "computePower" to (cluster?.computePower?.name ?: "HIGH")
                            )
                            val replyBytes = JsonUtil.toJsonString(replyMap).toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(
                                replyBytes,
                                replyBytes.size,
                                packet.address,
                                packet.port
                            )
                            socket.send(replyPacket)
                        }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            } catch (_: Exception) {
                // Port already in use or UDP unavailable; HTTP server continues functioning normally
            }
        }, "aegin-desktop-lan-discovery").apply { isDaemon = true }
        udpThread = thread
        thread.start()
    }

    private fun sendJson(exchange: HttpExchange, statusCode: Int, jsonBody: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
