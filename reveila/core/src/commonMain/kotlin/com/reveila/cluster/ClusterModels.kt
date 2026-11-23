package com.reveila.cluster

/**
 * Operational role of a Reveila runtime node within a cluster.
 */
enum class NodeRole {
    /** Single-node mode (`system.standalone=true`); cluster election and remote routing disabled. */
    STANDALONE,

    /** Transitional election state while attempting to claim leadership. */
    CANDIDATE,

    /** Worker node synced with an active Lead Node. */
    WORKER,

    /** Primary configuration authority, active routing directory, and state broadcaster. */
    LEAD
}

/**
 * Health state of a known cluster member.
 */
enum class NodeStatus {
    UP,
    SUSPECT,
    DOWN
}

/**
 * Declared or auto-detected compute capacity tier of a Reveila cluster node.
 *
 * - [LOW]: Small desktop PC, laptop, or edge device.
 * - [MEDIUM]: Tower workstation or mid-range machine.
 * - [HIGH]: Powerful server.
 * - [SUPER]: Specialized high-compute / multi-GPU machine (optional).
 */
enum class ComputePower(val weight: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    SUPER(4);

    companion object {
        fun parseOrNull(raw: String?): ComputePower? {
            if (raw.isNullOrBlank()) return null
            return when (raw.trim().uppercase()) {
                "LOW" -> LOW
                "MEDIUM", "MIDIUM", "MED" -> MEDIUM
                "HIGH" -> HIGH
                "SUPER" -> SUPER
                else -> null
            }
        }

        fun detect(cpuCores: Int, totalMemoryMb: Long): ComputePower = when {
            cpuCores >= 64 || totalMemoryMb >= 131_072L -> SUPER
            cpuCores >= 16 || totalMemoryMb >= 32_768L -> HIGH
            cpuCores >= 6 || totalMemoryMb >= 8_192L -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * Metadata describing a node in the Reveila cluster.
 */
data class ClusterNodeInfo(
    val nodeId: String,
    val baseUrl: String,
    val clusterRole: String = "GENERAL",
    val services: List<String> = emptyList(),
    val role: NodeRole = NodeRole.WORKER,
    val term: Long = 0L,
    val lastHeartbeatMs: Long = 0L,
    val status: NodeStatus = NodeStatus.UP,
    val version: String = "1.0.0",
    val computePower: ComputePower = ComputePower.MEDIUM,
    val cpuCores: Int = 0,
    val totalMemoryMb: Long = 0L
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "nodeId" to nodeId,
        "baseUrl" to baseUrl,
        "clusterRole" to clusterRole,
        "services" to services,
        "role" to role.name,
        "term" to term,
        "lastHeartbeatMs" to lastHeartbeatMs,
        "status" to status.name,
        "version" to version,
        "computePower" to computePower.name,
        "cpuCores" to cpuCores,
        "totalMemoryMb" to totalMemoryMb
    )

    companion object {
        fun fromMap(map: Map<*, *>): ClusterNodeInfo {
            val servicesRaw = map["services"] as? Iterable<*>
            val services = servicesRaw?.mapNotNull { it?.toString() } ?: emptyList()
            val roleStr = map["role"]?.toString() ?: NodeRole.WORKER.name
            val statusStr = map["status"]?.toString() ?: NodeStatus.UP.name
            val power = ComputePower.parseOrNull(map["computePower"]?.toString()) ?: ComputePower.MEDIUM
            return ClusterNodeInfo(
                nodeId = map["nodeId"]?.toString() ?: "",
                baseUrl = map["baseUrl"]?.toString() ?: "",
                clusterRole = map["clusterRole"]?.toString() ?: "GENERAL",
                services = services,
                role = runCatching { NodeRole.valueOf(roleStr) }.getOrDefault(NodeRole.WORKER),
                term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
                lastHeartbeatMs = (map["lastHeartbeatMs"] as? Number)?.toLong()
                    ?: map["lastHeartbeatMs"]?.toString()?.toLongOrNull() ?: 0L,
                status = runCatching { NodeStatus.valueOf(statusStr) }.getOrDefault(NodeStatus.UP),
                version = map["version"]?.toString() ?: "1.0.0",
                computePower = power,
                cpuCores = (map["cpuCores"] as? Number)?.toInt()
                    ?: map["cpuCores"]?.toString()?.toIntOrNull() ?: 0,
                totalMemoryMb = (map["totalMemoryMb"] as? Number)?.toLong()
                    ?: map["totalMemoryMb"]?.toString()?.toLongOrNull() ?: 0L
            )
        }
    }
}

/**
 * Authoritative cluster state snapshot returned by the Lead Node on initial bootstrap
 * or anti-entropy reconciliation.
 */
data class ClusterSnapshot(
    val term: Long,
    val leaderId: String,
    val leaderUrl: String,
    val configVersion: Long,
    val routingVersion: Long,
    val config: Map<String, String>,
    val routingTable: Map<String, List<String>>,
    val nodes: List<ClusterNodeInfo>
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "term" to term,
        "leaderId" to leaderId,
        "leaderUrl" to leaderUrl,
        "configVersion" to configVersion,
        "routingVersion" to routingVersion,
        "config" to config,
        "routingTable" to routingTable,
        "nodes" to nodes.map { it.toMap() }
    )

    companion object {
        fun fromMap(map: Map<*, *>): ClusterSnapshot {
            val configMap = LinkedHashMap<String, String>()
            (map["config"] as? Map<*, *>)?.forEach { (k, v) ->
                if (k != null && v != null) configMap[k.toString()] = v.toString()
            }

            val routingMap = LinkedHashMap<String, List<String>>()
            (map["routingTable"] as? Map<*, *>)?.forEach { (k, v) ->
                if (k != null && v is Iterable<*>) {
                    routingMap[k.toString()] = v.mapNotNull { it?.toString() }
                }
            }

            val nodesList = (map["nodes"] as? Iterable<*>)
                ?.mapNotNull { (it as? Map<*, *>)?.let { m -> ClusterNodeInfo.fromMap(m) } }
                ?: emptyList()

            return ClusterSnapshot(
                term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
                leaderId = map["leaderId"]?.toString() ?: "",
                leaderUrl = map["leaderUrl"]?.toString() ?: "",
                configVersion = (map["configVersion"] as? Number)?.toLong()
                    ?: map["configVersion"]?.toString()?.toLongOrNull() ?: 0L,
                routingVersion = (map["routingVersion"] as? Number)?.toLong()
                    ?: map["routingVersion"]?.toString()?.toLongOrNull() ?: 0L,
                config = configMap,
                routingTable = routingMap,
                nodes = nodesList
            )
        }
    }
}

/**
 * Leadership claim broadcast during dynamic leader election.
 */
data class ElectionClaim(
    val candidateId: String,
    val candidateUrl: String,
    val term: Long,
    val lastConfigVersion: Long,
    val timestampMs: Long
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "candidateId" to candidateId,
        "candidateUrl" to candidateUrl,
        "term" to term,
        "lastConfigVersion" to lastConfigVersion,
        "timestampMs" to timestampMs
    )

    companion object {
        fun fromMap(map: Map<*, *>): ElectionClaim = ElectionClaim(
            candidateId = map["candidateId"]?.toString() ?: "",
            candidateUrl = map["candidateUrl"]?.toString() ?: "",
            term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
            lastConfigVersion = (map["lastConfigVersion"] as? Number)?.toLong()
                ?: map["lastConfigVersion"]?.toString()?.toLongOrNull() ?: 0L,
            timestampMs = (map["timestampMs"] as? Number)?.toLong()
                ?: map["timestampMs"]?.toString()?.toLongOrNull() ?: 0L
        )
    }
}

/**
 * Response from a peer node to an [ElectionClaim].
 */
data class ElectionVote(
    val term: Long,
    val granted: Boolean,
    val voterId: String,
    val currentLeaderId: String? = null,
    val currentLeaderUrl: String? = null,
    val reason: String? = null
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "term" to term,
        "granted" to granted,
        "voterId" to voterId,
        "currentLeaderId" to currentLeaderId,
        "currentLeaderUrl" to currentLeaderUrl,
        "reason" to reason
    )

    companion object {
        fun fromMap(map: Map<*, *>): ElectionVote = ElectionVote(
            term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
            granted = (map["granted"] as? Boolean) ?: (map["granted"]?.toString()?.toBoolean() ?: false),
            voterId = map["voterId"]?.toString() ?: "",
            currentLeaderId = map["currentLeaderId"]?.toString()?.takeIf { it.isNotBlank() },
            currentLeaderUrl = map["currentLeaderUrl"]?.toString()?.takeIf { it.isNotBlank() },
            reason = map["reason"]?.toString()?.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Native cluster state-synchronization broadcast ("Pulse"), replacing PostgreSQL NOTIFY/LISTEN.
 */
data class ClusterPulse(
    val term: Long,
    val configVersion: Long,
    val routingVersion: Long,
    val channel: String,
    val payload: String,
    val configUpdates: Map<String, String> = emptyMap(),
    val routingTable: Map<String, List<String>>? = null,
    val senderId: String = "",
    val senderUrl: String = "",
    val timestampMs: Long = 0L
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "term" to term,
        "configVersion" to configVersion,
        "routingVersion" to routingVersion,
        "channel" to channel,
        "payload" to payload,
        "configUpdates" to configUpdates,
        "routingTable" to routingTable,
        "senderId" to senderId,
        "senderUrl" to senderUrl,
        "timestampMs" to timestampMs
    )

    companion object {
        fun fromMap(map: Map<*, *>): ClusterPulse {
            val updates = LinkedHashMap<String, String>()
            (map["configUpdates"] as? Map<*, *>)?.forEach { (k, v) ->
                if (k != null && v != null) updates[k.toString()] = v.toString()
            }
            val routingRaw = map["routingTable"] as? Map<*, *>
            val routing = if (routingRaw != null) {
                val r = LinkedHashMap<String, List<String>>()
                routingRaw.forEach { (k, v) ->
                    if (k != null && v is Iterable<*>) {
                        r[k.toString()] = v.mapNotNull { it?.toString() }
                    }
                }
                r
            } else null

            return ClusterPulse(
                term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
                configVersion = (map["configVersion"] as? Number)?.toLong()
                    ?: map["configVersion"]?.toString()?.toLongOrNull() ?: 0L,
                routingVersion = (map["routingVersion"] as? Number)?.toLong()
                    ?: map["routingVersion"]?.toString()?.toLongOrNull() ?: 0L,
                channel = map["channel"]?.toString() ?: "",
                payload = map["payload"]?.toString() ?: "",
                configUpdates = updates,
                routingTable = routing,
                senderId = map["senderId"]?.toString() ?: "",
                senderUrl = map["senderUrl"]?.toString() ?: "",
                timestampMs = (map["timestampMs"] as? Number)?.toLong()
                    ?: map["timestampMs"]?.toString()?.toLongOrNull() ?: 0L
            )
        }
    }
}

/**
 * Worker -> Lead periodic heartbeat payload.
 */
data class HeartbeatRequest(
    val nodeId: String,
    val baseUrl: String,
    val clusterRole: String = "GENERAL",
    val services: List<String> = emptyList(),
    val term: Long = 0L,
    val configVersion: Long = 0L,
    val routingVersion: Long = 0L,
    val computePower: ComputePower = ComputePower.MEDIUM
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "nodeId" to nodeId,
        "baseUrl" to baseUrl,
        "clusterRole" to clusterRole,
        "services" to services,
        "term" to term,
        "configVersion" to configVersion,
        "routingVersion" to routingVersion,
        "computePower" to computePower.name
    )

    companion object {
        fun fromMap(map: Map<*, *>): HeartbeatRequest = HeartbeatRequest(
            nodeId = map["nodeId"]?.toString() ?: "",
            baseUrl = map["baseUrl"]?.toString() ?: "",
            clusterRole = map["clusterRole"]?.toString() ?: "GENERAL",
            services = (map["services"] as? Iterable<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
            configVersion = (map["configVersion"] as? Number)?.toLong()
                ?: map["configVersion"]?.toString()?.toLongOrNull() ?: 0L,
            routingVersion = (map["routingVersion"] as? Number)?.toLong()
                ?: map["routingVersion"]?.toString()?.toLongOrNull() ?: 0L,
            computePower = ComputePower.parseOrNull(map["computePower"]?.toString()) ?: ComputePower.MEDIUM
        )
    }
}

/**
 * Lead -> Worker heartbeat acknowledgement carrying monotonic state versions for anti-entropy sync.
 */
data class HeartbeatResponse(
    val accepted: Boolean,
    val term: Long,
    val leaderId: String?,
    val leaderUrl: String?,
    val configVersion: Long,
    val routingVersion: Long
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "accepted" to accepted,
        "term" to term,
        "leaderId" to leaderId,
        "leaderUrl" to leaderUrl,
        "configVersion" to configVersion,
        "routingVersion" to routingVersion
    )

    companion object {
        fun fromMap(map: Map<*, *>): HeartbeatResponse = HeartbeatResponse(
            accepted = (map["accepted"] as? Boolean) ?: (map["accepted"]?.toString()?.toBoolean() ?: false),
            term = (map["term"] as? Number)?.toLong() ?: map["term"]?.toString()?.toLongOrNull() ?: 0L,
            leaderId = map["leaderId"]?.toString()?.takeIf { it.isNotBlank() },
            leaderUrl = map["leaderUrl"]?.toString()?.takeIf { it.isNotBlank() },
            configVersion = (map["configVersion"] as? Number)?.toLong()
                ?: map["configVersion"]?.toString()?.toLongOrNull() ?: 0L,
            routingVersion = (map["routingVersion"] as? Number)?.toLong()
                ?: map["routingVersion"]?.toString()?.toLongOrNull() ?: 0L
        )
    }
}

/**
 * Portable pairing payload exchanged between Aegin Desktop (Lead Node) and Aegin Mobile
 * (`EDGE_CLIENT` Citizen Node) via QR code or copy/paste.
 */
data class PairingDescriptor(
    val leaderId: String,
    val lanUrls: List<String> = emptyList(),
    val remoteUrl: String = "",
    val port: Int = 8080,
    val clusterSecret: String = "",
    val computePower: String = ComputePower.HIGH.name,
    val version: Int = 1
) {
    fun primaryLeaderUrl(): String =
        lanUrls.firstOrNull { it.isNotBlank() } ?: remoteUrl.trim()

    fun fallbackUrls(): List<String> {
        val all = LinkedHashSet<String>()
        lanUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.forEach { all.add(it) }
        remoteUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }?.let { all.add(it) }
        val primary = primaryLeaderUrl().trim().trimEnd('/')
        all.remove(primary)
        return all.toList()
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "version" to version,
        "leaderId" to leaderId,
        "lanUrls" to lanUrls,
        "remoteUrl" to remoteUrl,
        "port" to port,
        "clusterSecret" to clusterSecret,
        "computePower" to computePower
    )

    companion object {
        fun fromMap(map: Map<*, *>): PairingDescriptor {
            val lanRaw = map["lanUrls"] as? Iterable<*>
            val lanList = lanRaw?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } } ?: emptyList()
            return PairingDescriptor(
                leaderId = map["leaderId"]?.toString()?.trim() ?: "",
                lanUrls = lanList,
                remoteUrl = map["remoteUrl"]?.toString()?.trim() ?: "",
                port = (map["port"] as? Number)?.toInt()
                    ?: map["port"]?.toString()?.toIntOrNull() ?: 8080,
                clusterSecret = map["clusterSecret"]?.toString()?.trim() ?: "",
                computePower = map["computePower"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ComputePower.HIGH.name,
                version = (map["version"] as? Number)?.toInt()
                    ?: map["version"]?.toString()?.toIntOrNull() ?: 1
            )
        }
    }
}

/**
 * Listener invoked on every node when a native [ClusterPulse] arrives (e.g. config or vector update).
 */
fun interface ClusterPulseListener {
    fun onPulse(pulse: ClusterPulse)
}
