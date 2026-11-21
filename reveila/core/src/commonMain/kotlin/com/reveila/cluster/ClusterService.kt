package com.reveila.cluster

import com.reveila.system.Constants
import com.reveila.system.PerformanceTracker
import com.reveila.system.SystemComponent
import com.reveila.system.SystemContext
import com.reveila.system.concurrency.PlatformCancellable
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.concurrency.PlatformScheduler
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid

/**
 * Native Reveila Runtime Cluster Service.
 *
 * Eliminates the hard dependency on PostgreSQL for cluster coordination, service discovery,
 * and state synchronization by implementing a self-healing Lead/Worker runtime fabric:
 * - **Lead Node**: Authoritative configuration holder, active service routing table, and
 *   native state-change broadcaster ("Pulse").
 * - **Worker Nodes**: Bootstrap initial config/routing from the Lead Node, maintain local
 *   service registries, query the Lead Node for missing services, and self-heal via
 *   anti-entropy version reconciliation.
 * - **Dynamic Leader Election**: First-claim-without-rejection with monotonic terms,
 *   split-brain prevention, automatic cold-start leadership claim when non-standalone,
 *   and recovered former-leader verification & step-down.
 */
open class ClusterService @kotlin.jvm.JvmOverloads constructor(
    private var transport: ClusterTransport? = null,
    private val clock: () -> Long = { PlatformSystem.currentTimeMillis() },
    private val autoSchedule: Boolean = true
) : SystemComponent() {

    private val lock = PlatformLock()
    private val pulseListeners = mutableListOf<ClusterPulseListener>()

    // Node identity & configuration
    var nodeId: String = ""
        private set
    var nodeUrl: String = ""
        private set
    var clusterRole: String = "GENERAL"
        private set
    var standalone: Boolean = true
        private set

    private var heartbeatIntervalMs: Long = 3_000L
    private var leaderTimeoutMs: Long = 10_000L
    private var nodeTimeoutMs: Long = 15_000L
    private var clusterSecret: String? = null
    private var configuredLeaderUrl: String? = null
    private val seedUrls: MutableSet<String> = LinkedHashSet()

    // Consensus & leadership state
    var role: NodeRole = NodeRole.STANDALONE
        private set
    var currentTerm: Long = 0L
        private set
    var leaderId: String? = null
        private set
    var leaderUrl: String? = null
        private set
    private var votedForTerm: Long = -1L
    private var votedForCandidate: String? = null
    private var lastLeaderContactMs: Long = 0L

    // State synchronization versions & data
    var configVersion: Long = 0L
        private set
    var routingVersion: Long = 0L
        private set
    private val authoritativeConfig: MutableMap<String, String> = LinkedHashMap()
    private val localServices: MutableSet<String> = LinkedHashSet()
    private val nodesById: MutableMap<String, ClusterNodeInfo> = LinkedHashMap()
    private val routingTable: MutableMap<String, MutableSet<String>> = LinkedHashMap() // serviceName -> Set<baseUrl>

    // Background scheduler
    private var scheduler: PlatformScheduler? = null
    private var ownsScheduler: Boolean = false
    private var maintenanceTask: PlatformCancellable? = null

    fun setTransport(customTransport: ClusterTransport) {
        lock.withLock {
            this.transport = customTransport
        }
    }

    fun addPulseListener(listener: ClusterPulseListener) {
        lock.withLock {
            if (!pulseListeners.contains(listener)) {
                pulseListeners.add(listener)
            }
        }
    }

    fun removePulseListener(listener: ClusterPulseListener) {
        lock.withLock {
            pulseListeners.remove(listener)
        }
    }

    fun registerLocalService(serviceName: String) {
        if (serviceName.isBlank() || serviceName == Constants.CLUSTER_SERVICE) return
        lock.withLock {
            if (localServices.add(serviceName) && role == NodeRole.LEAD) {
                updateLeadSelfNodeLocked()
                rebuildRoutingTableLocked()
                routingVersion++
            }
        }
    }

    fun unregisterLocalService(serviceName: String) {
        lock.withLock {
            if (localServices.remove(serviceName) && role == NodeRole.LEAD) {
                updateLeadSelfNodeLocked()
                rebuildRoutingTableLocked()
                routingVersion++
            }
        }
    }

    fun getLocalServices(): Set<String> = lock.withLock { localServices.toSet() }

    fun getAuthoritativeConfig(): Map<String, String> = lock.withLock { LinkedHashMap(authoritativeConfig) }

    fun getRoutingTableSnapshot(): Map<String, List<String>> = lock.withLock {
        val copy = LinkedHashMap<String, List<String>>()
        routingTable.forEach { (svc, urls) -> copy[svc] = urls.toList() }
        copy
    }

    fun getKnownNodes(): List<ClusterNodeInfo> = lock.withLock { nodesById.values.toList() }

    /**
     * Synchronizes local service registry from the active [SystemContext] and, if running
     * as a Worker, notifies the Lead Node on the next heartbeat or immediate update.
     */
    fun refreshLocalServices() {
        val sysCtx = context as? SystemContext ?: return
        val discovered = sysCtx.getRegisteredComponentNames()
            .filter { it.isNotBlank() && it != Constants.CLUSTER_SERVICE }
        val shouldBroadcast = lock.withLock {
            val changed = localServices.addAll(discovered)
            if (changed && role == NodeRole.LEAD) {
                updateLeadSelfNodeLocked()
                rebuildRoutingTableLocked()
                routingVersion++
                true
            } else {
                false
            }
        }
        if (shouldBroadcast) {
            broadcastRoutingUpdate()
        }
    }

    @Throws(Exception::class)
    override fun onStart() {
        loadConfiguration()
        refreshLocalServices()

        if (standalone) {
            lock.withLock {
                role = NodeRole.STANDALONE
                leaderId = null
                leaderUrl = null
            }
            logger.info("ClusterService started in STANDALONE mode (nodeId=$nodeId).")
            return
        }

        bootstrapCluster()

        if (autoSchedule) {
            startMaintenanceLoop()
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
        maintenanceTask?.cancel(true)
        maintenanceTask = null
        if (ownsScheduler) {
            scheduler?.shutdown()
            ownsScheduler = false
        }
        scheduler = null
        logger.info("ClusterService stopped (nodeId=$nodeId, role=$role).")
    }

    private fun loadConfiguration() {
        val props = context?.properties
        val version = props?.getProperty(Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION)
            ?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_SYSTEM_VERSION

        lock.withLock {
            standalone = !"false".equals(props?.getProperty(Constants.STANDALONE_MODE), ignoreCase = true)
            nodeId = props?.getProperty(Constants.CLUSTER_NODE_ID)?.takeIf { it.isNotBlank() }
                ?: "node-${Uuid.randomUuid().substring(0, 8)}"
            nodeUrl = normalizeUrl(
                props?.getProperty(Constants.CLUSTER_NODE_URL)?.takeIf { it.isNotBlank() }
                    ?: "http://localhost:8080"
            )
            clusterRole = props?.getProperty(Constants.CLUSTER_ROLE)?.takeIf { it.isNotBlank() } ?: "GENERAL"
            heartbeatIntervalMs = props?.getProperty(Constants.CLUSTER_HEARTBEAT_INTERVAL_MS)?.toLongOrNull()
                ?.coerceAtLeast(250L) ?: 3_000L
            leaderTimeoutMs = props?.getProperty(Constants.CLUSTER_LEADER_TIMEOUT_MS)?.toLongOrNull()
                ?.coerceAtLeast(1_000L) ?: 10_000L
            nodeTimeoutMs = props?.getProperty(Constants.CLUSTER_NODE_TIMEOUT_MS)?.toLongOrNull()
                ?.coerceAtLeast(2_000L) ?: 15_000L
            clusterSecret = props?.getProperty(Constants.CLUSTER_SECRET)?.takeIf { it.isNotBlank() }
            configuredLeaderUrl = props?.getProperty(Constants.CLUSTER_LEADER_URL)
                ?.takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }

            seedUrls.clear()
            props?.getProperty(Constants.CLUSTER_SEEDS)?.split(",")
                ?.map { normalizeUrl(it) }
                ?.filter { it.isNotBlank() && it != nodeUrl }
                ?.forEach { seedUrls.add(it) }
            configuredLeaderUrl?.takeIf { it.isNotBlank() && it != nodeUrl }?.let { seedUrls.add(it) }

            if (transport == null) {
                transport = ReveilaClientClusterTransport(
                    version = version,
                    clusterSecret = clusterSecret
                )
            }

            // Seed authoritative config with local non-node-specific properties
            props?.forEach { k, v ->
                if (!isNodeSpecificProperty(k)) {
                    authoritativeConfig[k] = v
                }
            }
        }
    }

    private fun startMaintenanceLoop() {
        val adapterScheduler = context?.platformAdapter?.getScheduler()
        if (adapterScheduler != null) {
            scheduler = adapterScheduler
            ownsScheduler = false
        } else {
            scheduler = PlatformScheduler(2)
            ownsScheduler = true
        }
        val intervalSeconds = (heartbeatIntervalMs / 1000L).coerceAtLeast(1L)
        maintenanceTask = scheduler?.scheduleWithFixedDelay(intervalSeconds, intervalSeconds) {
            try {
                tick()
            } catch (e: Exception) {
                logger.warning("ClusterService maintenance tick failed: ${e.message}")
            }
        }
    }

    // =========================================================================
    // BOOTSTRAP & PERIODIC MAINTENANCE
    // =========================================================================

    /**
     * Discovers an existing Lead Node from configured leader/seeds and joins as a Worker.
     * If no active Lead Node is reachable and the node is not standalone, claims leadership.
     */
    fun bootstrapCluster() {
        if (standalone) return

        val probeTargets = lock.withLock {
            val targets = LinkedHashSet<String>()
            configuredLeaderUrl?.takeIf { it.isNotBlank() && it != nodeUrl }?.let { targets.add(it) }
            targets.addAll(seedUrls)
            targets.remove(nodeUrl)
            targets.toList()
        }

        val discoveredLeader = probeForActiveLeader(probeTargets)
        if (discoveredLeader != null) {
            logger.info("Discovered active Lead Node at $discoveredLeader. Joining as WORKER.")
            if (joinLeader(discoveredLeader)) {
                return
            }
        }

        logger.info("No active Lead Node reachable during bootstrap. Claiming Lead Node role (nodeId=$nodeId).")
        initiateElection()
    }

    /**
     * Executes one periodic cluster health/sync cycle.
     * - As WORKER: sends heartbeat to Lead Node, catches up on version drift, or initiates
     *   leader election if Lead Node has been unreachable for `leaderTimeoutMs`.
     * - As LEAD: verifies no higher-term leader has emerged (for recovery/partition heal)
     *   and evicts stale workers past `nodeTimeoutMs`.
     */
    fun tick() {
        val currentRole = lock.withLock { role }
        when (currentRole) {
            NodeRole.STANDALONE -> return
            NodeRole.LEAD -> {
                val stillLeader = verifyLeadership()
                if (stillLeader) {
                    evictStaleWorkers()
                }
            }
            NodeRole.WORKER -> {
                sendHeartbeat()
            }
            NodeRole.CANDIDATE -> {
                // Retry election if stuck in candidate state
                initiateElection()
            }
        }
    }

    // =========================================================================
    // LEADER ELECTION & RECOVERY VERIFICATION
    // =========================================================================

    /**
     * Initiates dynamic leader election.
     * First node to claim leadership without rejection from any reachable peer becomes the new Lead Node.
     */
    fun initiateElection(): Boolean {
        if (standalone) return false

        val (claim, peers, activeTransport) = lock.withLock {
            role = NodeRole.CANDIDATE
            currentTerm += 1L
            votedForTerm = currentTerm
            votedForCandidate = nodeId
            val claimObj = ElectionClaim(
                candidateId = nodeId,
                candidateUrl = nodeUrl,
                term = currentTerm,
                lastConfigVersion = configVersion,
                timestampMs = clock()
            )
            Triple(claimObj, collectPeerUrlsLocked(), transport)
        }

        if (activeTransport == null) {
            promoteToLead(claim.term)
            return true
        }

        var rejected = false
        var highestSeenTerm = claim.term
        var discoveredLeaderUrl: String? = null
        var discoveredLeaderId: String? = null

        for (peerUrl in peers) {
            try {
                val rawResp = activeTransport.invokeClusterMethod(
                    peerUrl,
                    "handleLeaderClaim",
                    arrayOf(claim.toMap())
                )
                val vote = (rawResp as? Map<*, *>)?.let { ElectionVote.fromMap(it) }
                if (vote != null) {
                    if (vote.term > highestSeenTerm) {
                        highestSeenTerm = vote.term
                    }
                    if (!vote.granted) {
                        rejected = true
                        if (!vote.currentLeaderUrl.isNullOrBlank()) {
                            discoveredLeaderUrl = normalizeUrl(vote.currentLeaderUrl)
                            discoveredLeaderId = vote.currentLeaderId
                        }
                        logger.info(
                            "Leadership claim for term ${claim.term} rejected by $peerUrl: ${vote.reason}"
                        )
                    }
                }
            } catch (_: Exception) {
                // Unreachable peers do not block self-healing failover
            }
        }

        if (rejected) {
            lock.withLock {
                if (highestSeenTerm > currentTerm) {
                    currentTerm = highestSeenTerm
                }
                role = NodeRole.WORKER
                if (discoveredLeaderUrl != null) {
                    leaderUrl = discoveredLeaderUrl
                    leaderId = discoveredLeaderId
                }
            }
            if (discoveredLeaderUrl != null && discoveredLeaderUrl != nodeUrl) {
                joinLeader(discoveredLeaderUrl)
            }
            return false
        }

        promoteToLead(claim.term)
        return true
    }

    private fun promoteToLead(electedTerm: Long) {
        val (announcement, peers, activeTransport) = lock.withLock {
            role = NodeRole.LEAD
            currentTerm = electedTerm
            leaderId = nodeId
            leaderUrl = nodeUrl
            lastLeaderContactMs = clock()
            updateLeadSelfNodeLocked()
            rebuildRoutingTableLocked()
            routingVersion++
            val ann = linkedMapOf<String, Any?>(
                "term" to currentTerm,
                "leaderId" to nodeId,
                "leaderUrl" to nodeUrl,
                "configVersion" to configVersion,
                "routingVersion" to routingVersion
            )
            Triple(ann, collectPeerUrlsLocked(), transport)
        }

        logger.info("Node '$nodeId' ($nodeUrl) elected as LEAD Node for term $electedTerm.")

        if (activeTransport != null) {
            for (peerUrl in peers) {
                try {
                    activeTransport.invokeClusterMethod(
                        peerUrl,
                        "handleLeaderAnnouncement",
                        arrayOf(announcement)
                    )
                } catch (_: Exception) {
                    // Best-effort announcement; unreachable peers will sync on rejoin
                }
            }
        }
    }

    /**
     * RPC handler invoked by a candidate claiming leadership.
     * Grants the claim unless this node already knows a higher term, is an active Lead,
     * has a freshly healthy Lead, or already granted a vote for this term to another candidate.
     */
    fun handleLeaderClaim(claimMap: Map<String, Any?>): Map<String, Any?> {
        val claim = ElectionClaim.fromMap(claimMap)
        val now = clock()

        return lock.withLock {
            if (standalone) {
                return@withLock ElectionVote(
                    term = currentTerm,
                    granted = true,
                    voterId = nodeId,
                    reason = "Node is running in standalone mode (abstain)"
                ).toMap()
            }

            // 1. Stale term -> reject and inform candidate of current leader
            if (claim.term < currentTerm) {
                return@withLock ElectionVote(
                    term = currentTerm,
                    granted = false,
                    voterId = nodeId,
                    currentLeaderId = leaderId,
                    currentLeaderUrl = leaderUrl,
                    reason = "Stale term ${claim.term} < current term $currentTerm"
                ).toMap()
            }

            // 2. Same term conflict checks
            if (claim.term == currentTerm) {
                if (role == NodeRole.LEAD && nodeId != claim.candidateId) {
                    return@withLock ElectionVote(
                        term = currentTerm,
                        granted = false,
                        voterId = nodeId,
                        currentLeaderId = nodeId,
                        currentLeaderUrl = nodeUrl,
                        reason = "Active Lead Node already holds term $currentTerm"
                    ).toMap()
                }
                if (votedForTerm == claim.term && votedForCandidate != null && votedForCandidate != claim.candidateId) {
                    return@withLock ElectionVote(
                        term = currentTerm,
                        granted = false,
                        voterId = nodeId,
                        currentLeaderId = leaderId,
                        currentLeaderUrl = leaderUrl,
                        reason = "Already granted vote in term $currentTerm to $votedForCandidate"
                    ).toMap()
                }
                if (role == NodeRole.WORKER && !leaderUrl.isNullOrBlank() &&
                    leaderId != claim.candidateId && (now - lastLeaderContactMs) < leaderTimeoutMs
                ) {
                    return@withLock ElectionVote(
                        term = currentTerm,
                        granted = false,
                        voterId = nodeId,
                        currentLeaderId = leaderId,
                        currentLeaderUrl = leaderUrl,
                        reason = "Current Lead Node '$leaderId' is still healthy"
                    ).toMap()
                }
            }

            // 3. Higher term (or uncontested same term): accept claim and step down if needed
            if (claim.term > currentTerm) {
                currentTerm = claim.term
                if (role == NodeRole.LEAD || role == NodeRole.CANDIDATE) {
                    logger.info("Stepping down to WORKER: accepted higher-term claim (${claim.term}) from ${claim.candidateId}.")
                    role = NodeRole.WORKER
                }
            }

            votedForTerm = claim.term
            votedForCandidate = claim.candidateId
            leaderId = claim.candidateId
            leaderUrl = normalizeUrl(claim.candidateUrl)
            lastLeaderContactMs = now
            seedUrls.add(normalizeUrl(claim.candidateUrl))

            ElectionVote(
                term = currentTerm,
                granted = true,
                voterId = nodeId,
                currentLeaderId = claim.candidateId,
                currentLeaderUrl = normalizeUrl(claim.candidateUrl)
            ).toMap()
        }
    }

    /**
     * RPC handler invoked by a newly elected Lead Node to announce leadership.
     * Causes any former Lead Node with an older/equal term to step down and re-register as Worker.
     */
    fun handleLeaderAnnouncement(announcementMap: Map<String, Any?>): Map<String, Any?> {
        val newTerm = (announcementMap["term"] as? Number)?.toLong()
            ?: announcementMap["term"]?.toString()?.toLongOrNull() ?: 0L
        val newLeaderId = announcementMap["leaderId"]?.toString() ?: ""
        val newLeaderUrl = normalizeUrl(announcementMap["leaderUrl"]?.toString() ?: "")

        val shouldJoin = lock.withLock {
            if (standalone || newLeaderUrl.isBlank() || newLeaderId == nodeId) {
                return@withLock false
            }
            if (newTerm < currentTerm) {
                return@withLock false
            }
            if (role == NodeRole.LEAD && newTerm == currentTerm && newLeaderId < nodeId) {
                return@withLock false
            }

            currentTerm = newTerm
            role = NodeRole.WORKER
            leaderId = newLeaderId
            leaderUrl = newLeaderUrl
            lastLeaderContactMs = clock()
            seedUrls.add(newLeaderUrl)
            true
        }

        if (shouldJoin) {
            logger.info("Accepted new Lead Node '$newLeaderId' ($newLeaderUrl) for term $newTerm. Re-registering as WORKER.")
            joinLeader(newLeaderUrl)
        }

        return linkedMapOf("acknowledged" to shouldJoin, "term" to currentTerm, "nodeId" to nodeId)
    }

    /**
     * Verifies that this Lead Node is still the authoritative leader in the cluster.
     * Crucial for a recovered former Lead Node rejoining after a crash or network partition:
     * if peers report a newer leader (higher term, or active peer leader), this node immediately
     * steps down to WORKER and synchronizes state from the current Lead Node.
     *
     * @return `true` if this node remains the Lead Node, `false` if it stepped down.
     */
    fun verifyLeadership(): Boolean {
        val (isCurrentlyLead, myTerm, peers, activeTransport) = lock.withLock {
            Quad(role == NodeRole.LEAD, currentTerm, collectPeerUrlsLocked(), transport)
        }
        if (!isCurrentlyLead) return false
        if (activeTransport == null) return true

        var newerLeaderUrl: String? = null
        var newerLeaderId: String? = null
        var highestTerm = myTerm

        for (peerUrl in peers) {
            try {
                val statusRaw = activeTransport.invokeClusterMethod(peerUrl, "getClusterStatus", emptyArray())
                val status = statusRaw as? Map<*, *> ?: continue
                val peerTerm = (status["term"] as? Number)?.toLong()
                    ?: status["term"]?.toString()?.toLongOrNull() ?: 0L
                val peerRole = status["role"]?.toString() ?: ""
                val peerLeaderUrl = status["leaderUrl"]?.toString()?.takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }
                val peerLeaderId = status["leaderId"]?.toString()?.takeIf { it.isNotBlank() }

                val hasHigherTerm = peerTerm > highestTerm
                val isDifferentActiveLeadSameTerm = peerTerm == highestTerm &&
                    peerRole == NodeRole.LEAD.name &&
                    peerLeaderUrl != null &&
                    peerLeaderUrl != nodeUrl &&
                    (peerLeaderId ?: "") > nodeId

                if (hasHigherTerm || isDifferentActiveLeadSameTerm) {
                    highestTerm = peerTerm
                    if (peerLeaderUrl != null && peerLeaderUrl != nodeUrl) {
                        newerLeaderUrl = peerLeaderUrl
                        newerLeaderId = peerLeaderId
                    } else if (peerRole == NodeRole.LEAD.name && peerUrl != nodeUrl) {
                        newerLeaderUrl = peerUrl
                        newerLeaderId = status["nodeId"]?.toString()
                    }
                }
            } catch (_: Exception) {
                // Ignore unreachable peers
            }
        }

        if (newerLeaderUrl != null) {
            logger.warning(
                "Recovered former Lead '$nodeId' detected active Lead '$newerLeaderId' ($newerLeaderUrl) " +
                    "at term $highestTerm (local term $myTerm). Stepping down to WORKER."
            )
            lock.withLock {
                currentTerm = highestTerm
                role = NodeRole.WORKER
                leaderId = newerLeaderId
                leaderUrl = newerLeaderUrl
                lastLeaderContactMs = clock()
            }
            joinLeader(newerLeaderUrl)
            return false
        }

        return true
    }

    // =========================================================================
    // WORKER REGISTRATION, HEARTBEAT & ANTI-ENTROPY SYNC
    // =========================================================================

    private fun probeForActiveLeader(targets: List<String>): String? {
        val activeTransport = lock.withLock { transport } ?: return null
        var bestTerm = -1L
        var bestLeaderUrl: String? = null

        for (url in targets) {
            if (url == nodeUrl) continue
            try {
                val statusRaw = activeTransport.invokeClusterMethod(url, "getClusterStatus", emptyArray())
                val status = statusRaw as? Map<*, *> ?: continue
                val peerRole = status["role"]?.toString()
                val peerTerm = (status["term"] as? Number)?.toLong()
                    ?: status["term"]?.toString()?.toLongOrNull() ?: 0L
                val reportedLeaderUrl = status["leaderUrl"]?.toString()?.takeIf { it.isNotBlank() }
                    ?.let { normalizeUrl(it) }

                if (peerRole == NodeRole.LEAD.name && peerTerm >= bestTerm) {
                    bestTerm = peerTerm
                    bestLeaderUrl = url
                } else if (reportedLeaderUrl != null && reportedLeaderUrl != nodeUrl && peerTerm >= bestTerm) {
                    bestTerm = peerTerm
                    bestLeaderUrl = reportedLeaderUrl
                }
            } catch (_: Exception) {
                // Peer unreachable
            }
        }
        return bestLeaderUrl
    }

    /**
     * Registers this node with the specified Lead Node and applies its initial [ClusterSnapshot].
     */
    fun joinLeader(targetLeaderUrl: String): Boolean {
        val normalizedLeader = normalizeUrl(targetLeaderUrl)
        val (nodeInfo, activeTransport) = lock.withLock {
            val info = buildSelfNodeInfoLocked(NodeRole.WORKER)
            Pair(info, transport)
        }
        if (activeTransport == null) return false

        return try {
            val rawSnapshot = activeTransport.invokeClusterMethod(
                normalizedLeader,
                "registerWorker",
                arrayOf(nodeInfo.toMap())
            )
            val snapshotMap = rawSnapshot as? Map<*, *> ?: return false
            val snapshot = ClusterSnapshot.fromMap(snapshotMap)
            if (snapshot.leaderUrl.isBlank()) return false

            applySnapshot(snapshot)
            true
        } catch (e: Exception) {
            logger.warning("Failed to join Lead Node at $normalizedLeader: ${e.message}")
            false
        }
    }

    /**
     * RPC handler on the Lead Node: registers or updates a Worker Node, updates the active
     * routing table, broadcasts the routing change, and returns the authoritative [ClusterSnapshot].
     */
    fun registerWorker(nodeMap: Map<String, Any?>): Map<String, Any?> {
        val incoming = ClusterNodeInfo.fromMap(nodeMap)
        val now = clock()

        val (snapshot, shouldBroadcast) = lock.withLock {
            if (role != NodeRole.LEAD) {
                // Redirect worker to known leader
                return@withLock Pair(
                    ClusterSnapshot(
                        term = currentTerm,
                        leaderId = leaderId ?: "",
                        leaderUrl = leaderUrl ?: "",
                        configVersion = configVersion,
                        routingVersion = routingVersion,
                        config = emptyMap(),
                        routingTable = emptyMap(),
                        nodes = emptyList()
                    ),
                    false
                )
            }

            val normalizedUrl = normalizeUrl(incoming.baseUrl)
            val updated = incoming.copy(
                baseUrl = normalizedUrl,
                role = NodeRole.WORKER,
                term = currentTerm,
                lastHeartbeatMs = now,
                status = NodeStatus.UP
            )
            nodesById[updated.nodeId] = updated
            if (normalizedUrl.isNotBlank() && normalizedUrl != nodeUrl) {
                seedUrls.add(normalizedUrl)
            }
            updateLeadSelfNodeLocked()
            rebuildRoutingTableLocked()
            routingVersion++
            Pair(buildSnapshotLocked(), true)
        }

        if (shouldBroadcast) {
            broadcastRoutingUpdate()
        }

        return snapshot.toMap()
    }

    /**
     * Sends a periodic heartbeat from this Worker Node to the current Lead Node.
     * Handles anti-entropy snapshot catch-up and leader failover detection.
     */
    fun sendHeartbeat() {
        val snapshot = lock.withLock {
            if (role != NodeRole.WORKER) return@withLock null
            val req = HeartbeatRequest(
                nodeId = nodeId,
                baseUrl = nodeUrl,
                clusterRole = clusterRole,
                services = localServices.toList(),
                term = currentTerm,
                configVersion = configVersion,
                routingVersion = routingVersion
            )
            Quad(leaderUrl, req, transport, lastLeaderContactMs)
        } ?: return
        val (targetLeader, request, activeTransport, lastContact) = snapshot

        if (targetLeader.isNullOrBlank() || activeTransport == null) {
            if (clock() - lastContact >= leaderTimeoutMs) {
                initiateElection()
            }
            return
        }

        try {
            val rawResp = activeTransport.invokeClusterMethod(
                targetLeader,
                "handleHeartbeat",
                arrayOf(request.toMap())
            )
            val resp = (rawResp as? Map<*, *>)?.let { HeartbeatResponse.fromMap(it) }
            if (resp != null && resp.accepted) {
                val needsSync = lock.withLock {
                    lastLeaderContactMs = clock()
                    if (resp.term > currentTerm) {
                        currentTerm = resp.term
                    }
                    if (!resp.leaderId.isNullOrBlank()) leaderId = resp.leaderId
                    if (!resp.leaderUrl.isNullOrBlank()) leaderUrl = normalizeUrl(resp.leaderUrl)
                    resp.configVersion > configVersion || resp.routingVersion > routingVersion
                }
                if (needsSync) {
                    pullSnapshotFromLeader(targetLeader)
                }
                return
            } else if (resp != null && !resp.accepted && !resp.leaderUrl.isNullOrBlank()) {
                val redirectUrl = normalizeUrl(resp.leaderUrl)
                if (redirectUrl != nodeUrl) {
                    logger.info("Heartbeat redirected from $targetLeader to active Lead $redirectUrl.")
                    joinLeader(redirectUrl)
                    return
                }
            }
        } catch (_: Exception) {
            // Lead unreachable
        }

        val elapsed = clock() - lock.withLock { lastLeaderContactMs }
        if (elapsed >= leaderTimeoutMs) {
            logger.warning("Lead Node ($targetLeader) unreachable for ${elapsed}ms. Initiating dynamic leader election.")
            initiateElection()
        }
    }

    /**
     * RPC handler on the Lead Node for incoming worker heartbeats.
     */
    fun handleHeartbeat(requestMap: Map<String, Any?>): Map<String, Any?> {
        val req = HeartbeatRequest.fromMap(requestMap)
        val now = clock()

        val (response, routingChanged) = lock.withLock {
            if (role != NodeRole.LEAD) {
                return@withLock Pair(
                    HeartbeatResponse(
                        accepted = false,
                        term = currentTerm,
                        leaderId = leaderId,
                        leaderUrl = leaderUrl,
                        configVersion = configVersion,
                        routingVersion = routingVersion
                    ),
                    false
                )
            }

            val existing = nodesById[req.nodeId]
            val normalizedUrl = normalizeUrl(req.baseUrl)
            val servicesChanged = existing == null || existing.services.toSet() != req.services.toSet() ||
                existing.status != NodeStatus.UP

            nodesById[req.nodeId] = ClusterNodeInfo(
                nodeId = req.nodeId,
                baseUrl = normalizedUrl,
                clusterRole = req.clusterRole,
                services = req.services,
                role = NodeRole.WORKER,
                term = currentTerm,
                lastHeartbeatMs = now,
                status = NodeStatus.UP,
                version = existing?.version ?: Constants.DEFAULT_SYSTEM_VERSION
            )
            if (normalizedUrl.isNotBlank() && normalizedUrl != nodeUrl) {
                seedUrls.add(normalizedUrl)
            }

            if (servicesChanged) {
                updateLeadSelfNodeLocked()
                rebuildRoutingTableLocked()
                routingVersion++
            }

            Pair(
                HeartbeatResponse(
                    accepted = true,
                    term = currentTerm,
                    leaderId = nodeId,
                    leaderUrl = nodeUrl,
                    configVersion = configVersion,
                    routingVersion = routingVersion
                ),
                servicesChanged
            )
        }

        if (routingChanged) {
            broadcastRoutingUpdate()
        }

        return response.toMap()
    }

    /**
     * Lead Node maintenance: marks unresponsive workers as DOWN and removes their routes.
     */
    fun evictStaleWorkers() {
        val now = clock()
        val evicted = lock.withLock {
            if (role != NodeRole.LEAD) return@withLock false
            var changed = false
            for ((id, info) in nodesById.entries.toList()) {
                if (id == nodeId) continue
                if (info.status != NodeStatus.DOWN && (now - info.lastHeartbeatMs) >= nodeTimeoutMs) {
                    nodesById[id] = info.copy(status = NodeStatus.DOWN)
                    logger.warning("Worker node '$id' (${info.baseUrl}) timed out. Marked DOWN and evicted from routing table.")
                    changed = true
                }
            }
            if (changed) {
                updateLeadSelfNodeLocked()
                rebuildRoutingTableLocked()
                routingVersion++
            }
            changed
        }

        if (evicted) {
            broadcastRoutingUpdate()
        }
    }

    /**
     * RPC handler returning the full authoritative [ClusterSnapshot] from this node.
     */
    fun fetchSnapshot(): Map<String, Any?> = lock.withLock {
        buildSnapshotLocked().toMap()
    }

    private fun pullSnapshotFromLeader(targetLeaderUrl: String) {
        val activeTransport = lock.withLock { transport } ?: return
        try {
            val raw = activeTransport.invokeClusterMethod(targetLeaderUrl, "fetchSnapshot", emptyArray())
            val map = raw as? Map<*, *> ?: return
            applySnapshot(ClusterSnapshot.fromMap(map))
        } catch (e: Exception) {
            logger.warning("Failed to pull snapshot from Lead Node ($targetLeaderUrl): ${e.message}")
        }
    }

    private fun applySnapshot(snapshot: ClusterSnapshot) {
        val listenersCopy = lock.withLock {
            if (snapshot.term < currentTerm) return@withLock null

            currentTerm = snapshot.term
            role = if (snapshot.leaderId == nodeId) NodeRole.LEAD else NodeRole.WORKER
            leaderId = snapshot.leaderId
            leaderUrl = normalizeUrl(snapshot.leaderUrl)
            lastLeaderContactMs = clock()

            configVersion = snapshot.configVersion
            routingVersion = snapshot.routingVersion

            authoritativeConfig.clear()
            authoritativeConfig.putAll(snapshot.config)
            applyConfigToContextLocked(snapshot.config)

            routingTable.clear()
            snapshot.routingTable.forEach { (svc, urls) ->
                routingTable[svc] = LinkedHashSet(urls.map { normalizeUrl(it) })
            }

            nodesById.clear()
            snapshot.nodes.forEach { node ->
                nodesById[node.nodeId] = node
                if (node.baseUrl.isNotBlank() && normalizeUrl(node.baseUrl) != nodeUrl) {
                    seedUrls.add(normalizeUrl(node.baseUrl))
                }
            }
            pulseListeners.toList()
        } ?: return

        val syncPulse = ClusterPulse(
            term = snapshot.term,
            configVersion = snapshot.configVersion,
            routingVersion = snapshot.routingVersion,
            channel = Constants.CLUSTER_CHANNEL_CONFIG,
            payload = "snapshot_sync",
            configUpdates = snapshot.config,
            routingTable = snapshot.routingTable,
            senderId = snapshot.leaderId,
            senderUrl = snapshot.leaderUrl,
            timestampMs = clock()
        )
        notifyListeners(listenersCopy, syncPulse)
    }

    // =========================================================================
    // SERVICE DISCOVERY & ROUTING
    // =========================================================================

    /**
     * RPC handler on Lead Node: returns healthy node base URLs hosting [serviceName].
     */
    fun lookupServiceRoute(serviceName: String): List<String> = lock.withLock {
        routingTable[serviceName]?.toList() ?: emptyList()
    }

    /**
     * Resolves a remote node base URL hosting [serviceName].
     * Checks the local cached routing table first; if missing locally, queries the Lead Node.
     * Returns `null` if the service is available locally, running standalone, or not found in cluster.
     */
    fun resolveRemoteNodeUrl(serviceName: String): String? {
        if (standalone || serviceName == Constants.CLUSTER_SERVICE) return null

        val (isLocal, cachedUrls, currentRole, currentLeader, activeTransport) = lock.withLock {
            val local = localServices.contains(serviceName)
            val cached = routingTable[serviceName]?.filter { it != nodeUrl } ?: emptyList()
            Quint(local, cached, role, leaderUrl, transport)
        }

        if (isLocal) return null
        if (cachedUrls.isNotEmpty()) {
            return selectBestUrl(cachedUrls)
        }

        // Missing locally -> query Lead Node for routing info
        if (currentRole == NodeRole.WORKER && !currentLeader.isNullOrBlank() && activeTransport != null) {
            try {
                val raw = activeTransport.invokeClusterMethod(
                    currentLeader,
                    "lookupServiceRoute",
                    arrayOf(serviceName)
                )
                val remoteUrls = (raw as? Iterable<*>)
                    ?.mapNotNull { it?.toString()?.let { u -> normalizeUrl(u) } }
                    ?.filter { it.isNotBlank() && it != nodeUrl }
                    ?: emptyList()

                if (remoteUrls.isNotEmpty()) {
                    lock.withLock {
                        routingTable[serviceName] = LinkedHashSet(remoteUrls)
                    }
                    return selectBestUrl(remoteUrls)
                }
            } catch (e: Exception) {
                logger.warning("Failed to query Lead Node ($currentLeader) for route '$serviceName': ${e.message}")
            }
        }

        return null
    }

    /**
     * Removes a failed URL from the local routing cache so subsequent calls re-query the Lead Node.
     */
    fun invalidateRemoteNode(serviceName: String, failedUrl: String) {
        val normalized = normalizeUrl(failedUrl)
        lock.withLock {
            routingTable[serviceName]?.remove(normalized)
        }
    }

    private fun selectBestUrl(candidates: List<String>): String {
        if (candidates.size == 1) return candidates.first()
        val bestTracked = PerformanceTracker.getInstance().getBestNodeUrl()?.let { normalizeUrl(it) }
        if (bestTracked != null && candidates.contains(bestTracked)) {
            return bestTracked
        }
        return candidates.first()
    }

    // =========================================================================
    // NATIVE STATE SYNCHRONIZATION (PUB/SUB BROADCAST WITHOUT POSTGRESQL)
    // =========================================================================

    /**
     * Publishes configuration key-value updates across the cluster.
     * - On Lead Node: increments `configVersion`, applies locally, and broadcasts to all workers.
     * - On Worker Node: forwards mutation to Lead Node for authoritative broadcast.
     * - On Standalone Node: applies locally and notifies listeners.
     */
    fun publishConfigUpdate(updates: Map<String, Any?>): Map<String, Any?> {
        val stringUpdates = LinkedHashMap<String, String>()
        updates.forEach { (k, v) ->
            if (v != null && !isNodeSpecificProperty(k)) {
                stringUpdates[k] = v.toString()
            }
        }
        return publishPulse(
            channel = Constants.CLUSTER_CHANNEL_CONFIG,
            payload = stringUpdates.keys.joinToString(","),
            configUpdates = stringUpdates
        )
    }

    /**
     * Generic native pub/sub broadcast replacing PostgreSQL `NOTIFY/LISTEN`.
     */
    @kotlin.jvm.JvmOverloads
    fun publishPulse(
        channel: String,
        payload: String,
        configUpdates: Map<String, Any?>? = null
    ): Map<String, Any?> {
        val stringUpdates = LinkedHashMap<String, String>()
        configUpdates?.forEach { (k, v) ->
            if (v != null && !isNodeSpecificProperty(k)) {
                stringUpdates[k] = v.toString()
            }
        }

        val (currentRole, currentLeader, activeTransport) = lock.withLock {
            Triple(role, leaderUrl, transport)
        }

        // Worker forwards state mutations to the authoritative Lead Node
        if (currentRole == NodeRole.WORKER && !currentLeader.isNullOrBlank() && activeTransport != null) {
            return try {
                val res = activeTransport.invokeClusterMethod(
                    currentLeader,
                    "publishPulse",
                    arrayOf(channel, payload, stringUpdates)
                )
                @Suppress("UNCHECKED_CAST")
                (res as? Map<String, Any?>) ?: linkedMapOf("forwarded" to true)
            } catch (e: Exception) {
                logger.warning("Failed to forward pulse to Lead Node ($currentLeader): ${e.message}")
                linkedMapOf("accepted" to false, "error" to (e.message ?: "Forwarding failed"))
            }
        }

        val (pulse, workerUrls, listenersCopy) = lock.withLock {
            if (stringUpdates.isNotEmpty()) {
                authoritativeConfig.putAll(stringUpdates)
                applyConfigToContextLocked(stringUpdates)
                configVersion++
            }
            val p = ClusterPulse(
                term = currentTerm,
                configVersion = configVersion,
                routingVersion = routingVersion,
                channel = channel,
                payload = payload,
                configUpdates = stringUpdates,
                senderId = nodeId,
                senderUrl = nodeUrl,
                timestampMs = clock()
            )
            val targets = if (role == NodeRole.LEAD) collectLiveWorkerUrlsLocked() else emptyList()
            Triple(p, targets, pulseListeners.toList())
        }

        // Notify local listeners immediately
        notifyListeners(listenersCopy, pulse)

        // Fan-out to all live workers
        var delivered = 0
        if (activeTransport != null) {
            val pulseMap = pulse.toMap()
            for (workerUrl in workerUrls) {
                try {
                    activeTransport.invokeClusterMethod(workerUrl, "receivePulse", arrayOf(pulseMap))
                    delivered++
                } catch (_: Exception) {
                    // Missed push will be reconciled automatically on worker's next heartbeat
                }
            }
        }

        return linkedMapOf(
            "accepted" to true,
            "term" to pulse.term,
            "configVersion" to pulse.configVersion,
            "routingVersion" to pulse.routingVersion,
            "deliveredCount" to delivered
        )
    }

    /**
     * RPC handler on Worker Nodes receiving a broadcasted [ClusterPulse] from the Lead Node.
     */
    fun receivePulse(pulseMap: Map<String, Any?>): Map<String, Any?> {
        val pulse = ClusterPulse.fromMap(pulseMap)

        val (accepted, listenersCopy) = lock.withLock {
            if (standalone) {
                return@withLock Pair(false, emptyList<ClusterPulseListener>())
            }
            if (pulse.term < currentTerm) {
                return@withLock Pair(false, emptyList<ClusterPulseListener>())
            }

            if (pulse.term > currentTerm) {
                currentTerm = pulse.term
                if (role == NodeRole.LEAD || role == NodeRole.CANDIDATE) {
                    role = NodeRole.WORKER
                }
            }

            if (pulse.senderId.isNotBlank() && pulse.senderId != nodeId) {
                leaderId = pulse.senderId
            }
            if (pulse.senderUrl.isNotBlank() && normalizeUrl(pulse.senderUrl) != nodeUrl) {
                leaderUrl = normalizeUrl(pulse.senderUrl)
            }
            lastLeaderContactMs = clock()

            if (pulse.configUpdates.isNotEmpty()) {
                authoritativeConfig.putAll(pulse.configUpdates)
                applyConfigToContextLocked(pulse.configUpdates)
            }
            if (pulse.configVersion > configVersion) {
                configVersion = pulse.configVersion
            }
            if (pulse.routingTable != null && pulse.routingVersion >= routingVersion) {
                routingTable.clear()
                pulse.routingTable.forEach { (svc, urls) ->
                    routingTable[svc] = LinkedHashSet(urls.map { normalizeUrl(it) })
                }
                routingVersion = pulse.routingVersion
            }

            Pair(true, pulseListeners.toList())
        }

        if (accepted) {
            notifyListeners(listenersCopy, pulse)
        }

        return linkedMapOf(
            "accepted" to accepted,
            "nodeId" to nodeId,
            "configVersion" to configVersion,
            "routingVersion" to routingVersion
        )
    }

    private fun broadcastRoutingUpdate() {
        val payload = lock.withLock {
            if (role != NodeRole.LEAD) return@withLock null
            val routingCopy = LinkedHashMap<String, List<String>>()
            routingTable.forEach { (svc, urls) -> routingCopy[svc] = urls.toList() }
            val p = ClusterPulse(
                term = currentTerm,
                configVersion = configVersion,
                routingVersion = routingVersion,
                channel = Constants.CLUSTER_CHANNEL_ROUTING,
                payload = "routing_table_updated",
                routingTable = routingCopy,
                senderId = nodeId,
                senderUrl = nodeUrl,
                timestampMs = clock()
            )
            Triple(p, collectLiveWorkerUrlsLocked(), transport)
        } ?: return
        val (pulse, workerUrls, activeTransport) = payload

        if (activeTransport != null) {
            val pulseMap = pulse.toMap()
            for (workerUrl in workerUrls) {
                try {
                    activeTransport.invokeClusterMethod(workerUrl, "receivePulse", arrayOf(pulseMap))
                } catch (_: Exception) {
                    // Worker will reconcile via heartbeat anti-entropy
                }
            }
        }
    }

    // =========================================================================
    // STATUS & INTROSPECTION
    // =========================================================================

    /**
     * RPC & observability method returning the full status of this cluster node.
     */
    fun getClusterStatus(): Map<String, Any?> = lock.withLock {
        val routingCopy = LinkedHashMap<String, List<String>>()
        routingTable.forEach { (svc, urls) -> routingCopy[svc] = urls.toList() }
        linkedMapOf(
            "nodeId" to nodeId,
            "nodeUrl" to nodeUrl,
            "clusterRole" to clusterRole,
            "standalone" to standalone,
            "role" to role.name,
            "term" to currentTerm,
            "leaderId" to leaderId,
            "leaderUrl" to leaderUrl,
            "configVersion" to configVersion,
            "routingVersion" to routingVersion,
            "localServices" to localServices.toList(),
            "routingTable" to routingCopy,
            "nodes" to nodesById.values.map { it.toMap() }
        )
    }

    // =========================================================================
    // INTERNAL HELPERS (MUST HOLD `lock`)
    // =========================================================================

    private fun buildSelfNodeInfoLocked(effectiveRole: NodeRole): ClusterNodeInfo {
        val version = context?.properties?.getProperty(Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION)
            ?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_SYSTEM_VERSION
        return ClusterNodeInfo(
            nodeId = nodeId,
            baseUrl = nodeUrl,
            clusterRole = clusterRole,
            services = localServices.toList(),
            role = effectiveRole,
            term = currentTerm,
            lastHeartbeatMs = clock(),
            status = NodeStatus.UP,
            version = version
        )
    }

    private fun updateLeadSelfNodeLocked() {
        nodesById[nodeId] = buildSelfNodeInfoLocked(NodeRole.LEAD)
    }

    private fun rebuildRoutingTableLocked() {
        routingTable.clear()
        for (node in nodesById.values) {
            if (node.status != NodeStatus.UP || node.baseUrl.isBlank()) continue
            val url = normalizeUrl(node.baseUrl)
            for (svc in node.services) {
                if (svc.isBlank() || svc == Constants.CLUSTER_SERVICE) continue
                routingTable.getOrPut(svc) { LinkedHashSet() }.add(url)
            }
        }
    }

    private fun buildSnapshotLocked(): ClusterSnapshot {
        val routingCopy = LinkedHashMap<String, List<String>>()
        routingTable.forEach { (svc, urls) -> routingCopy[svc] = urls.toList() }
        return ClusterSnapshot(
            term = currentTerm,
            leaderId = leaderId ?: "",
            leaderUrl = leaderUrl ?: "",
            configVersion = configVersion,
            routingVersion = routingVersion,
            config = LinkedHashMap(authoritativeConfig),
            routingTable = routingCopy,
            nodes = nodesById.values.toList()
        )
    }

    private fun collectPeerUrlsLocked(): List<String> {
        val urls = LinkedHashSet<String>()
        urls.addAll(seedUrls)
        nodesById.values.forEach { node ->
            val u = normalizeUrl(node.baseUrl)
            if (u.isNotBlank()) urls.add(u)
        }
        leaderUrl?.takeIf { it.isNotBlank() }?.let { urls.add(normalizeUrl(it)) }
        urls.remove(nodeUrl)
        return urls.toList()
    }

    private fun collectLiveWorkerUrlsLocked(): List<String> {
        return nodesById.values
            .filter { it.nodeId != nodeId && it.status == NodeStatus.UP && it.baseUrl.isNotBlank() }
            .map { normalizeUrl(it.baseUrl) }
            .distinct()
    }

    private fun applyConfigToContextLocked(updates: Map<String, String>) {
        val props = context?.properties ?: return
        for ((k, v) in updates) {
            if (!isNodeSpecificProperty(k)) {
                props.setProperty(k, v)
            }
        }
    }

    private fun notifyListeners(listeners: List<ClusterPulseListener>, pulse: ClusterPulse) {
        for (listener in listeners) {
            try {
                listener.onPulse(pulse)
            } catch (e: Exception) {
                logger.warning("ClusterPulseListener failed on channel '${pulse.channel}': ${e.message}")
            }
        }
    }

    private fun isNodeSpecificProperty(key: String): Boolean {
        return key == Constants.CLUSTER_NODE_ID ||
            key == Constants.CLUSTER_NODE_URL ||
            key == Constants.CLUSTER_ROLE ||
            key == Constants.STANDALONE_MODE ||
            key == Constants.SYSTEM_HOME
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
