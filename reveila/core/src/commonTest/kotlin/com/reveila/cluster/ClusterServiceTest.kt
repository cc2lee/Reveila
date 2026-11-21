package com.reveila.cluster

import com.reveila.system.ComponentState
import com.reveila.system.Constants
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.logging.PlatformLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClusterServiceTest {

    private class FakeContext(props: Map<String, String>) : Context {
        override val properties: Properties = Properties(props)
        override val platformAdapter: PlatformAdapter? = null
        override fun getLogger(): PlatformLogger = PlatformLogger("ClusterTest")
        override fun getProxy(name: String): Proxy {
            throw IllegalArgumentException("No proxy for $name")
        }
    }

    /**
     * Deterministic in-memory cluster transport supporting crash & network partition simulation.
     */
    private class InMemoryClusterNetwork : ClusterTransport {
        val nodes = LinkedHashMap<String, ClusterService>()
        val offlineUrls = HashSet<String>()
        val partitionedPairs = HashSet<Pair<String, String>>()

        fun register(url: String, service: ClusterService) {
            nodes[url.trimEnd('/')] = service
        }

        fun setOffline(url: String, offline: Boolean) {
            val norm = url.trimEnd('/')
            if (offline) offlineUrls.add(norm) else offlineUrls.remove(norm)
        }

        fun transportFor(sourceUrl: String): ClusterTransport = object : ClusterTransport {
            private val src = sourceUrl.trimEnd('/')
            override fun invokeClusterMethod(targetBaseUrl: String, methodName: String, args: Array<Any?>): Any? {
                val target = targetBaseUrl.trimEnd('/')
                if (offlineUrls.contains(src) || offlineUrls.contains(target)) {
                    throw RuntimeException("Node unreachable: $target")
                }
                if (partitionedPairs.contains(src to target) || partitionedPairs.contains(target to src)) {
                    throw RuntimeException("Network partitioned between $src and $target")
                }
                return this@InMemoryClusterNetwork.invokeClusterMethod(target, methodName, args)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun invokeClusterMethod(targetBaseUrl: String, methodName: String, args: Array<Any?>): Any? {
            val target = targetBaseUrl.trimEnd('/')
            if (offlineUrls.contains(target)) {
                throw RuntimeException("Node offline: $target")
            }
            val service = nodes[target] ?: throw RuntimeException("Unknown node: $target")
            if (service.state == ComponentState.STOPPED) {
                throw RuntimeException("Node not started: $target")
            }
            return when (methodName) {
                "getClusterStatus" -> service.getClusterStatus()
                "registerWorker" -> service.registerWorker(args[0] as Map<String, Any?>)
                "handleHeartbeat" -> service.handleHeartbeat(args[0] as Map<String, Any?>)
                "fetchSnapshot" -> service.fetchSnapshot()
                "lookupServiceRoute" -> service.lookupServiceRoute(args[0] as String)
                "handleLeaderClaim" -> service.handleLeaderClaim(args[0] as Map<String, Any?>)
                "handleLeaderAnnouncement" -> service.handleLeaderAnnouncement(args[0] as Map<String, Any?>)
                "receivePulse" -> service.receivePulse(args[0] as Map<String, Any?>)
                "publishConfigUpdate" -> service.publishConfigUpdate(args[0] as Map<String, Any?>)
                "publishPulse" -> service.publishPulse(
                    args[0] as String,
                    args[1] as String,
                    args.getOrNull(2) as? Map<String, Any?>
                )
                else -> throw IllegalArgumentException("Unknown ClusterService method: $methodName")
            }
        }
    }

    private fun createNode(
        network: InMemoryClusterNetwork,
        nodeId: String,
        url: String,
        seeds: List<String>,
        clock: () -> Long,
        standalone: Boolean = false,
        extraProps: Map<String, String> = emptyMap(),
        localServices: List<String> = emptyList()
    ): ClusterService {
        val props = LinkedHashMap<String, String>()
        props[Constants.STANDALONE_MODE] = standalone.toString()
        props[Constants.CLUSTER_NODE_ID] = nodeId
        props[Constants.CLUSTER_NODE_URL] = url
        props[Constants.CLUSTER_SEEDS] = seeds.joinToString(",")
        props[Constants.CLUSTER_HEARTBEAT_INTERVAL_MS] = "1000"
        props[Constants.CLUSTER_LEADER_TIMEOUT_MS] = "3000"
        props[Constants.CLUSTER_NODE_TIMEOUT_MS] = "5000"
        props.putAll(extraProps)

        val service = ClusterService(
            transport = network.transportFor(url),
            clock = clock,
            autoSchedule = false
        )
        service.context = FakeContext(props)
        localServices.forEach { service.registerLocalService(it) }
        network.register(url, service)
        return service
    }

    @Test
    fun standaloneModeDoesNotClaimLeadershipOrJoinCluster() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val node = createNode(
            network = net,
            nodeId = "solo-1",
            url = "http://node-solo:8080",
            seeds = emptyList(),
            clock = { now },
            standalone = true,
            localServices = listOf("EchoService")
        )

        node.start()

        assertTrue(node.standalone)
        assertEquals(NodeRole.STANDALONE, node.role)
        assertNull(node.leaderId)
        assertNull(node.resolveRemoteNodeUrl("RemoteOnlyService"))
        assertFalse(node.initiateElection())
    }

    @Test
    fun firstNonStandaloneNodeClaimsLeadershipWhenNoLeaderFound() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val node1 = createNode(
            network = net,
            nodeId = "node-1",
            url = "http://node1:8080",
            seeds = listOf("http://node2:8080", "http://node3:8080"),
            clock = { now },
            extraProps = mapOf("ai.worker.llm" to "Gemini-Pro"),
            localServices = listOf("KnowledgeVault")
        )

        node1.start()

        assertEquals(NodeRole.LEAD, node1.role)
        assertEquals("node-1", node1.leaderId)
        assertEquals("http://node1:8080", node1.leaderUrl)
        assertEquals(1L, node1.currentTerm)
        assertEquals(listOf("http://node1:8080"), node1.lookupServiceRoute("KnowledgeVault"))
    }

    @Test
    fun workerBootsJoinsLeadReceivesInitialConfigAndRegistersServices() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080")

        val lead = createNode(
            network = net,
            nodeId = "node-1",
            url = "http://node1:8080",
            seeds = seeds,
            clock = { now },
            extraProps = mapOf("ai.governance.llm" to "Strict-Auditor"),
            localServices = listOf("AuthService")
        )
        lead.start()
        assertEquals(NodeRole.LEAD, lead.role)

        val worker = createNode(
            network = net,
            nodeId = "node-2",
            url = "http://node2:8080",
            seeds = seeds,
            clock = { now },
            localServices = listOf("FinancePlugin")
        )
        worker.start()

        assertEquals(NodeRole.WORKER, worker.role)
        assertEquals("node-1", worker.leaderId)
        assertEquals("http://node1:8080", worker.leaderUrl)
        // Worker received initial authoritative config from Lead Node
        assertEquals("Strict-Auditor", worker.context?.properties?.getProperty("ai.governance.llm"))
        // Lead Node routing table now knows about FinancePlugin on node-2
        assertEquals(listOf("http://node2:8080"), lead.lookupServiceRoute("FinancePlugin"))
        assertEquals(listOf("http://node1:8080"), lead.lookupServiceRoute("AuthService"))
    }

    @Test
    fun workerQueriesLeadNodeForMissingServiceRoute() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080", "http://node3:8080")

        val lead = createNode(net, "node-1", "http://node1:8080", seeds, { now }, localServices = listOf("CoreService"))
        val workerA = createNode(net, "node-2", "http://node2:8080", seeds, { now }, localServices = listOf("ServiceA"))
        val workerB = createNode(net, "node-3", "http://node3:8080", seeds, { now }, localServices = listOf("ServiceB"))

        lead.start()
        workerA.start()
        workerB.start()

        // Clear workerA's local cache for ServiceB to simulate missing local route
        workerA.invalidateRemoteNode("ServiceB", "http://node3:8080")

        // WorkerA queries Lead Node and resolves ServiceB -> http://node3:8080
        val resolvedUrl = workerA.resolveRemoteNodeUrl("ServiceB")
        assertEquals("http://node3:8080", resolvedUrl)

        // Local service returns null (execute locally, do not forward)
        assertNull(workerA.resolveRemoteNodeUrl("ServiceA"))
    }

    @Test
    fun nativeStateSynchronizationBroadcastsConfigAndVectorPulsesWithoutPostgres() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080", "http://node3:8080")

        val lead = createNode(net, "node-1", "http://node1:8080", seeds, { now })
        val worker1 = createNode(net, "node-2", "http://node2:8080", seeds, { now })
        val worker2 = createNode(net, "node-3", "http://node3:8080", seeds, { now })

        lead.start()
        worker1.start()
        worker2.start()

        val receivedChannelsW1 = mutableListOf<String>()
        val receivedChannelsW2 = mutableListOf<String>()
        worker1.addPulseListener { receivedChannelsW1.add("${it.channel}:${it.payload}") }
        worker2.addPulseListener { receivedChannelsW2.add("${it.channel}:${it.payload}") }

        // 1. Lead broadcasts a config update natively
        val res = lead.publishConfigUpdate(mapOf("ai.optimization.priority" to "latency"))
        assertEquals(true, res["accepted"])
        assertEquals(2, res["deliveredCount"])

        assertEquals("latency", worker1.context?.properties?.getProperty("ai.optimization.priority"))
        assertEquals("latency", worker2.context?.properties?.getProperty("ai.optimization.priority"))

        // 2. Worker1 publishes a vector invalidation pulse -> forwarded to Lead -> fanned out to all nodes
        worker1.publishPulse(Constants.CLUSTER_CHANNEL_VECTOR, "doc-42")
        assertTrue(receivedChannelsW1.contains("${Constants.CLUSTER_CHANNEL_VECTOR}:doc-42"))
        assertTrue(receivedChannelsW2.contains("${Constants.CLUSTER_CHANNEL_VECTOR}:doc-42"))
    }

    @Test
    fun workerMissedBroadcastSelfHealsViaHeartbeatAntiEntropy() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080")

        val lead = createNode(net, "node-1", "http://node1:8080", seeds, { now })
        val worker = createNode(net, "node-2", "http://node2:8080", seeds, { now })
        lead.start()
        worker.start()

        // Worker goes transiently unreachable during a broadcast
        net.setOffline("http://node2:8080", true)
        lead.publishConfigUpdate(mapOf("security.perimeter.mode" to "LOCKDOWN"))
        assertNotEquals("LOCKDOWN", worker.context?.properties?.getProperty("security.perimeter.mode"))

        // Worker reconnects and sends next heartbeat -> detects configVersion drift -> pulls snapshot
        net.setOffline("http://node2:8080", false)
        now += 1000L
        worker.tick()

        assertEquals("LOCKDOWN", worker.context?.properties?.getProperty("security.perimeter.mode"))
        assertEquals(lead.configVersion, worker.configVersion)
    }

    @Test
    fun dynamicLeaderElectionOnLeadFailureAndRecoveredFormerLeadStepDown() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080", "http://node3:8080")

        val node1 = createNode(net, "node-1", "http://node1:8080", seeds, { now }, localServices = listOf("Svc1"))
        val node2 = createNode(net, "node-2", "http://node2:8080", seeds, { now }, localServices = listOf("Svc2"))
        val node3 = createNode(net, "node-3", "http://node3:8080", seeds, { now }, localServices = listOf("Svc3"))

        node1.start() // Becomes LEAD (term 1)
        node2.start() // Joins as WORKER
        node3.start() // Joins as WORKER

        assertEquals(NodeRole.LEAD, node1.role)
        assertEquals(NodeRole.WORKER, node2.role)
        assertEquals(NodeRole.WORKER, node3.role)

        // Lead Node (node-1) crashes / becomes unreachable
        net.setOffline("http://node1:8080", true)

        // Advance time past leaderTimeoutMs (3000ms) -> node-2 ticks and initiates election
        now += 4_000L
        node2.tick()

        // node-2 claims leadership without rejection -> becomes new LEAD (term 2)
        assertEquals(NodeRole.LEAD, node2.role)
        assertEquals("node-2", node2.leaderId)
        assertEquals(2L, node2.currentTerm)

        // node-3 was notified of new Lead and re-registered its service
        assertEquals(NodeRole.WORKER, node3.role)
        assertEquals("node-2", node3.leaderId)
        assertEquals(listOf("http://node3:8080"), node2.lookupServiceRoute("Svc3"))

        // New Lead publishes a config update while former Lead (node-1) is still offline
        node2.publishConfigUpdate(mapOf("cluster.failover.verified" to "true"))

        // Former Lead (node-1) recovers and comes back online!
        net.setOffline("http://node1:8080", false)
        assertEquals(NodeRole.LEAD, node1.role) // Still thinks it's LEAD before verification

        // Recovered former Lead verifies current leadership on rejoin / tick
        now += 1_000L
        val stillLead = node1.verifyLeadership()
        assertFalse(stillLead)
        assertEquals(NodeRole.WORKER, node1.role)
        assertEquals("node-2", node1.leaderId)
        assertEquals(2L, node1.currentTerm)
        // Former Lead synced the latest authoritative config from new Lead
        assertEquals("true", node1.context?.properties?.getProperty("cluster.failover.verified"))
        // New Lead's routing table now includes recovered node-1's service
        assertEquals(listOf("http://node1:8080"), node2.lookupServiceRoute("Svc1"))
    }

    @Test
    fun secondCandidateIsRejectedWhenLeaderAlreadyElectedForTerm() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080", "http://node3:8080")

        val node1 = createNode(net, "node-1", "http://node1:8080", seeds, { now })
        val node2 = createNode(net, "node-2", "http://node2:8080", seeds, { now })
        val node3 = createNode(net, "node-3", "http://node3:8080", seeds, { now })

        node1.start() // LEAD (term 1)
        node2.start() // WORKER (term 1)
        node3.start() // WORKER (term 1)

        // Simulate a stale/competing claim at term 1 against healthy cluster
        val vote = ElectionVote.fromMap(
            node2.handleLeaderClaim(
                ElectionClaim(
                    candidateId = "rogue-node",
                    candidateUrl = "http://rogue:8080",
                    term = 1L,
                    lastConfigVersion = 0L,
                    timestampMs = now
                ).toMap()
            )
        )

        assertFalse(vote.granted)
        assertEquals("node-1", vote.currentLeaderId)
        assertEquals("http://node1:8080", vote.currentLeaderUrl)
        assertNotNull(vote.reason)
    }

    @Test
    fun staleWorkerIsEvictedFromActiveRoutingTableAfterTimeout() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://node1:8080", "http://node2:8080")

        val lead = createNode(net, "node-1", "http://node1:8080", seeds, { now }, localServices = listOf("LeadSvc"))
        val worker = createNode(net, "node-2", "http://node2:8080", seeds, { now }, localServices = listOf("WorkerOnlySvc"))

        lead.start()
        worker.start()
        assertEquals(listOf("http://node2:8080"), lead.lookupServiceRoute("WorkerOnlySvc"))

        // Worker crashes and stops sending heartbeats past nodeTimeoutMs (5000ms)
        net.setOffline("http://node2:8080", true)
        now += 6_000L
        lead.tick()

        assertTrue(lead.lookupServiceRoute("WorkerOnlySvc").isEmpty())
        val workerInfo = lead.getKnownNodes().first { it.nodeId == "node-2" }
        assertEquals(NodeStatus.DOWN, workerInfo.status)
    }
}
