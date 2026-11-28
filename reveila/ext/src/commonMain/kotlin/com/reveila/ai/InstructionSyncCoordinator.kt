package com.reveila.ai

import com.reveila.cluster.ClusterPulse
import com.reveila.cluster.ClusterPulseListener
import com.reveila.cluster.ClusterService
import com.reveila.cluster.NodeRole
import com.reveila.service.DataService
import com.reveila.service.RemoteService
import com.reveila.system.Constants
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

/**
 * Hexagonal transport port for sending bidirectional instruction sync envelopes
 * from an Edge/Worker node to the Cluster Lead Node.
 */
fun interface InstructionSyncTransport {
    @Throws(Exception::class)
    fun exchangeDelta(
        leaderBaseUrl: String,
        payload: Map<String, Any?>
    ): Map<String, Any?>
}

/**
 * Production [InstructionSyncTransport] backed by Reveila's [RemoteService],
 * invoking `AgentInstructionStore.handleSyncRequest` over the unified cluster RPC endpoint
 * (`/api/components/AgentInstructionStore/invoke`) with `X-Reveila-Cluster-Token`.
 */
class RemoteInstructionSyncTransport(
    private val remoteService: RemoteService,
    private val clusterSecretProvider: () -> String? = { null }
) : InstructionSyncTransport {

    @Suppress("UNCHECKED_CAST")
    override fun exchangeDelta(
        leaderBaseUrl: String,
        payload: Map<String, Any?>
    ): Map<String, Any?> {
        val secret = clusterSecretProvider()?.trim().orEmpty()
        val headers = if (secret.isNotEmpty()) {
            mapOf(Constants.CLUSTER_TOKEN_HEADER to secret)
        } else {
            emptyMap()
        }
        val rawResult = remoteService.invokeOnNode(
            baseUrl = leaderBaseUrl,
            componentName = AgentInstructionStore.COMPONENT_NAME,
            methodName = "handleSyncRequest",
            args = arrayOf<Any?>(payload),
            extraHeaders = headers
        )
        return when (rawResult) {
            is Map<*, *> -> rawResult.entries.associate { (k, v) -> k.toString() to v }
            is String -> if (rawResult.trim().startsWith("{")) {
                JsonUtil.parseJsonStringToMap(rawResult)
            } else {
                mapOf("ok" to false, "error" to rawResult)
            }
            else -> mapOf("ok" to false, "error" to "Unexpected sync response from Lead Node.")
        }
    }
}

/**
 * Coordinates offline-first, hub-and-spoke standing instruction synchronization between
 * Edge/Worker devices and the Cluster Lead Node.
 *
 * - **On Lead Node**: Publishes a lightweight [ClusterPulse] (`channel = "instructions.sync"`)
 *   whenever standing instructions change locally or via a peer push.
 * - **On Edge/Worker Nodes**: Tracks per-`(tenantId, userId)` sync cursors (`lastSyncedAt`),
 *   exchanges LWW deltas + tombstones with the Lead Node on demand, on reconnect, or immediately
 *   when a `instructions.sync` Cluster Pulse arrives.
 */
open class InstructionSyncCoordinator(
    private val store: AgentInstructionStore,
    private val transport: InstructionSyncTransport,
    private val deviceIdProvider: () -> String = { "local-device" },
    private val dataServiceProvider: () -> DataService? = { null }
) {
    companion object {
        const val PULSE_CHANNEL: String = "instructions.sync"
        const val CURSOR_ENTITY_TYPE: String = "InstructionSyncCursor"
    }

    data class SyncRoundTripResult(
        val ok: Boolean,
        val pushedCount: Int = 0,
        val pulledApplied: Int = 0,
        val pulledSkippedOlder: Int = 0,
        val rejectedCrossBoundary: Int = 0,
        val newCursorMs: Long = 0L,
        val error: String? = null
    )

    private val lock = PlatformLock()
    private val cursors = mutableMapOf<String, Long>()
    private var attachedCluster: ClusterService? = null
    private var pulseListener: ClusterPulseListener? = null

    /**
     * Attaches this coordinator to [clusterService]:
     * - On LEAD: broadcasts a Cluster Pulse whenever [store] mutates.
     * - On WORKER/EDGE: listens for `instructions.sync` pulses and pulls deltas automatically.
     */
    fun attachToCluster(
        clusterService: ClusterService,
        activeTenantIdProvider: () -> String = { AgentInstruction.DEFAULT_TENANT_ID },
        activeUserIdProvider: () -> String = { AgentInstruction.DEFAULT_USER_ID }
    ) {
        detachFromCluster()
        attachedCluster = clusterService

        store.onMutationListener = { tenantId, userId, sourceDeviceId ->
            val cluster = attachedCluster
            if (cluster != null && !cluster.standalone && cluster.role == NodeRole.LEAD) {
                val pulsePayload = JsonUtil.toJsonString(
                    linkedMapOf(
                        "tenantId" to tenantId,
                        "userId" to userId,
                        "sourceDeviceId" to sourceDeviceId,
                        "timestampMs" to PlatformSystem.currentTimeMillis()
                    )
                )
                try {
                    cluster.publishPulse(PULSE_CHANNEL, pulsePayload)
                } catch (_: Exception) {
                }
            }
        }

        val listener = ClusterPulseListener { pulse: ClusterPulse ->
            if (pulse.channel != PULSE_CHANNEL) return@ClusterPulseListener
            val cluster = attachedCluster ?: return@ClusterPulseListener
            if (cluster.standalone || cluster.role == NodeRole.LEAD) return@ClusterPulseListener

            val leaderUrl = cluster.leaderUrl?.takeIf { it.isNotBlank() } ?: return@ClusterPulseListener
            val meta = try {
                JsonUtil.parseJsonStringToMap(pulse.payload)
            } catch (_: Exception) {
                emptyMap()
            }
            val pulseSourceDevice = meta["sourceDeviceId"]?.toString()?.trim().orEmpty()
            if (pulseSourceDevice.isNotEmpty() && pulseSourceDevice == deviceIdProvider().trim()) {
                return@ClusterPulseListener
            }

            val myTenant = AgentInstruction.normalizeTenantId(activeTenantIdProvider())
            val myUser = AgentInstruction.normalizeUserId(activeUserIdProvider())
            val pulseTenant = meta["tenantId"]?.toString()?.let { AgentInstruction.normalizeTenantId(it) }
            val pulseUser = meta["userId"]?.toString()?.trim()?.lowercase()

            // Only sync if the pulse matches our tenant and either our user or a TENANT-wide rule ("*")
            if (pulseTenant != null && pulseTenant != myTenant) return@ClusterPulseListener
            if (!pulseUser.isNullOrEmpty() && pulseUser != AgentInstruction.TENANT_WILDCARD_USER_ID && pulseUser != myUser) {
                return@ClusterPulseListener
            }

            try {
                syncWithLeader(
                    tenantId = myTenant,
                    userId = myUser,
                    leaderBaseUrl = leaderUrl
                )
            } catch (_: Exception) {
            }
        }

        pulseListener = listener
        clusterService.addPulseListener(listener)
    }

    fun detachFromCluster() {
        val cluster = attachedCluster
        val listener = pulseListener
        if (cluster != null && listener != null) {
            cluster.removePulseListener(listener)
        }
        pulseListener = null
        attachedCluster = null
    }

    /**
     * Performs a bidirectional delta exchange with the Lead Node at [leaderBaseUrl] for `(tenantId, userId)`.
     * Safe to invoke repeatedly on reconnect, after local edits, or on a periodic anti-entropy timer.
     */
    @Suppress("UNCHECKED_CAST")
    @kotlin.jvm.JvmOverloads
    open fun syncWithLeader(
        tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
        userId: String = AgentInstruction.DEFAULT_USER_ID,
        leaderBaseUrl: String? = attachedCluster?.leaderUrl,
        isTenantAdmin: Boolean = false
    ): SyncRoundTripResult {
        val targetUrl = leaderBaseUrl?.trim()?.trimEnd('/').orEmpty()
        if (targetUrl.isEmpty()) {
            return SyncRoundTripResult(ok = false, error = "No reachable Cluster Lead URL configured.")
        }

        val normTenant = AgentInstruction.normalizeTenantId(tenantId)
        val normUser = AgentInstruction.normalizeUserId(userId, InstructionScope.USER_GLOBAL)
        val lastCursor = getCursor(normTenant, normUser)

        val localDelta = store.exportSyncDelta(
            tenantId = normTenant,
            userId = normUser,
            sinceTimestampMs = lastCursor
        )

        val requestPayload = linkedMapOf<String, Any?>(
            "tenantId" to normTenant,
            "userId" to normUser,
            "deviceId" to deviceIdProvider().trim().ifEmpty { "local-device" },
            "sinceTimestampMs" to lastCursor,
            "isTenantAdmin" to isTenantAdmin,
            "instructions" to localDelta.map { it.toMap() }
        )

        val response = try {
            transport.exchangeDelta(targetUrl, requestPayload)
        } catch (e: Exception) {
            return SyncRoundTripResult(ok = false, error = e.message ?: "Sync transport failure.")
        }

        val ok = (response["ok"] as? Boolean) ?: (response["error"] == null)
        if (!ok) {
            return SyncRoundTripResult(
                ok = false,
                error = response["error"]?.toString() ?: "Lead Node rejected sync request."
            )
        }

        val rawIncoming = (response["instructions"] as? Iterable<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
            .orEmpty()
        val incoming = rawIncoming.mapNotNull { AgentInstruction.fromMap(it) }

        val mergeResult = store.mergeSyncPayload(
            tenantId = normTenant,
            userId = normUser,
            incoming = incoming,
            allowTenantRules = true
        )

        val serverTs = (response["serverTimestampMs"] as? Number)?.toLong()
            ?: response["serverTimestampMs"]?.toString()?.toLongOrNull()
            ?: PlatformSystem.currentTimeMillis()
        val maxIncomingTs = incoming.maxOfOrNull { it.updatedAt } ?: 0L
        val nextCursor = maxOf(lastCursor, serverTs, maxIncomingTs)
        setCursor(normTenant, normUser, nextCursor)

        return SyncRoundTripResult(
            ok = true,
            pushedCount = localDelta.size,
            pulledApplied = mergeResult.applied,
            pulledSkippedOlder = mergeResult.skippedOlder,
            rejectedCrossBoundary = mergeResult.rejectedCrossBoundary,
            newCursorMs = nextCursor
        )
    }

    fun getCursor(tenantId: String, userId: String): Long {
        val key = "${AgentInstruction.normalizeTenantId(tenantId)}::${AgentInstruction.normalizeUserId(userId)}"
        return lock.withLock {
            cursors[key] ?: loadCursorFromStorage(key).also { cursors[key] = it }
        }
    }

    private fun setCursor(tenantId: String, userId: String, timestampMs: Long) {
        val key = "${AgentInstruction.normalizeTenantId(tenantId)}::${AgentInstruction.normalizeUserId(userId)}"
        lock.withLock {
            cursors[key] = timestampMs
        }
        persistCursorToStorage(key, timestampMs)
    }

    private fun loadCursorFromStorage(cursorKey: String): Long {
        val ds = dataServiceProvider() ?: return 0L
        return try {
            val entity = ds.findById(CURSOR_ENTITY_TYPE, mapOf("cursorKey" to mapOf("value" to cursorKey)))
            val raw = entity?.attributes?.get("lastSyncedAt")
            (raw as? Number)?.toLong() ?: raw?.toString()?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun persistCursorToStorage(cursorKey: String, timestampMs: Long) {
        val ds = dataServiceProvider() ?: return
        try {
            ds.save(
                CURSOR_ENTITY_TYPE,
                mapOf(
                    "key" to mapOf("cursorKey" to mapOf("value" to cursorKey)),
                    "attributes" to mapOf("cursorKey" to cursorKey, "lastSyncedAt" to timestampMs)
                )
            )
        } catch (_: Exception) {
        }
    }
}
