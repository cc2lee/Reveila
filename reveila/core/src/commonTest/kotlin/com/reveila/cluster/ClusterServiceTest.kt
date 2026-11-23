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
                "dispatchService" -> service.dispatchService(args[0] as String)
                "getNodeDirectory" -> service.getNodeDirectory()
                "getComputeProfile" -> service.getComputeProfile(args.getOrNull(0) as? String)
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

    @Test
    fun presetLeaderMatchingOwnIpStartsDirectlyAsLeadWithoutRemoteLookup() {
        var now = 10_000L
        var remoteRpcCount = 0
        val countingTransport = object : ClusterTransport {
            override fun invokeClusterMethod(targetBaseUrl: String, methodName: String, args: Array<Any?>): Any? {
                remoteRpcCount++
                throw AssertionError("Preset leader matching own IP should not perform remote lookup ($methodName)")
            }
        }

        // Standard config: standalone=false, cluster.leader.url preset, no explicit cluster.node.url
        val standardProps = mapOf(
            Constants.STANDALONE_MODE to "false",
            Constants.CLUSTER_LEADER_URL to "http://192.168.10.50:8080",
            Constants.CLUSTER_COMPUTE_POWER to "HIGH"
        )
        val lead = ClusterService(
            transport = countingTransport,
            clock = { now },
            autoSchedule = false,
            localIpProvider = { setOf("192.168.10.50", "127.0.0.1", "localhost") }
        )
        lead.context = FakeContext(standardProps)
        lead.start()

        assertTrue(lead.isPresetLeaderSelf())
        assertEquals(NodeRole.LEAD, lead.role)
        assertEquals("http://192.168.10.50:8080", lead.nodeUrl)
        assertEquals(ComputePower.HIGH, lead.computePower)
        assertEquals(0, remoteRpcCount)
    }

    @Test
    fun dynamicWorkerUsesRemoteServiceToRegisterComputePowerWithPresetLeader() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()

        // 1. Lead node starts with standard config matching its own IP
        val leadProps = mapOf(
            Constants.STANDALONE_MODE to "false",
            Constants.CLUSTER_NODE_ID to "lead-1",
            Constants.CLUSTER_LEADER_URL to "http://10.0.0.10:8080",
            Constants.CLUSTER_COMPUTE_POWER to "HIGH",
            Constants.CLUSTER_SECRET to "cluster-secret-xyz"
        )
        val lead = ClusterService(
            transport = net.transportFor("http://10.0.0.10:8080"),
            clock = { now },
            autoSchedule = false,
            localIpProvider = { setOf("10.0.0.10", "127.0.0.1") }
        )
        lead.context = FakeContext(leadProps)
        net.register("http://10.0.0.10:8080", lead)
        lead.start()
        assertEquals(NodeRole.LEAD, lead.role)

        // 2. Dynamic worker starts with identical preset leader URL (different host IP) and RemoteService
        val recordedCalls = mutableListOf<Triple<String, String, Map<String, String>>>()
        val workerRemoteService = com.reveila.service.RemoteService { baseUrl, componentName, methodName, args, headers, _, _ ->
            recordedCalls.add(Triple(baseUrl, "$componentName.$methodName", headers))
            net.invokeClusterMethod(baseUrl, methodName, args)
        }
        val workerProps = mapOf(
            Constants.STANDALONE_MODE to "false",
            Constants.CLUSTER_NODE_ID to "worker-tower-1",
            Constants.CLUSTER_LEADER_URL to "http://10.0.0.10:8080",
            Constants.CLUSTER_COMPUTE_POWER to "MEDIUM",
            Constants.CLUSTER_SECRET to "cluster-secret-xyz"
        )
        val workerContext = object : Context {
            override val properties: Properties = Properties(workerProps)
            override val platformAdapter: PlatformAdapter? = null
            override fun getLogger(): PlatformLogger = PlatformLogger("WorkerTest")
            override fun getProxy(name: String): Proxy {
                if (name == Constants.REMOTE_REVEILA) {
                    return object : Proxy {
                        override fun getName(): String = Constants.REMOTE_REVEILA
                        override fun getRequiredRoles(): List<String> = emptyList()
                        override fun getInstance(): Any = workerRemoteService
                        override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
                    }
                }
                throw IllegalArgumentException("No proxy for $name")
            }
        }
        workerRemoteService.context = workerContext
        workerRemoteService.setRemoteURLs(arrayOf("http://10.0.0.10:8080, 1"))
        workerRemoteService.start()

        // No custom transport passed -> ClusterService resolves RemoteService from context automatically!
        val worker = ClusterService(
            transport = null,
            clock = { now },
            autoSchedule = false,
            localIpProvider = { setOf("10.0.0.25", "127.0.0.1") }
        )
        worker.context = workerContext
        worker.registerLocalService("VisionInference")
        net.register("http://10.0.0.25:8080", worker)
        worker.start()

        // Verify RemoteService was used directly to call ClusterService.registerWorker with auth header
        assertEquals(NodeRole.WORKER, worker.role)
        assertEquals("http://10.0.0.25:8080", worker.nodeUrl)
        assertEquals("http://10.0.0.10:8080", worker.leaderUrl)
        assertTrue(recordedCalls.any { (url, target, headers) ->
            url == "http://10.0.0.10:8080" &&
                target == "ClusterService.registerWorker" &&
                headers[Constants.CLUSTER_TOKEN_HEADER] == "cluster-secret-xyz"
        })

        // Verify Lead Node received worker's ComputePower in its node registry
        val registeredWorker = lead.getKnownNodes().first { it.nodeId == "worker-tower-1" }
        assertEquals(ComputePower.MEDIUM, registeredWorker.computePower)
        assertEquals("http://10.0.0.25:8080", registeredWorker.baseUrl)
        assertTrue(registeredWorker.services.contains("VisionInference"))
        assertEquals("HIGH", lead.getClusterStatus()["computePower"])
    }

    @Test
    fun leadNodeReferenceTableAndComputeAwareDispatcher() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://lead:8080")

        val lead = createNode(
            net, "lead-1", "http://lead:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "MEDIUM")
        )
        val lowWorker = createNode(
            net, "worker-low", "http://low:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "LOW"),
            localServices = listOf("HeavyAnalytics")
        )
        val highWorkerA = createNode(
            net, "worker-high-a", "http://high-a:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "HIGH"),
            localServices = listOf("HeavyAnalytics")
        )
        val highWorkerB = createNode(
            net, "worker-high-b", "http://high-b:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "HIGH"),
            localServices = listOf("HeavyAnalytics")
        )

        lead.start()
        lowWorker.start()
        highWorkerA.start()
        highWorkerB.start()

        // Reference table lists all 4 nodes ordered by ComputePower descending
        val directory = lead.getNodeDirectory()
        assertEquals(4, directory.size)
        assertEquals("HIGH", directory[0]["computePower"])
        assertEquals("HIGH", directory[1]["computePower"])
        assertEquals("MEDIUM", directory[2]["computePower"])
        assertEquals("LOW", directory[3]["computePower"])

        // Dispatcher prefers HIGH nodes over LOW node and round-robins across equal-power HIGH peers
        val firstDispatch = lead.dispatchService("HeavyAnalytics")
        val secondDispatch = lead.dispatchService("HeavyAnalytics")
        assertEquals("HIGH", firstDispatch["selectedComputePower"])
        assertEquals("HIGH", secondDispatch["selectedComputePower"])
        assertNotEquals(firstDispatch["targetUrl"], secondDispatch["targetUrl"])
        val candidates = firstDispatch["candidates"] as List<*>
        assertEquals("http://low:8080", candidates.last())
    }

    @Test
    fun citizenNodeFetchesMatchingComputeProfileFromLeadNode() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()
        val seeds = listOf("http://lead:8080")

        val lead = createNode(
            net, "lead-1", "http://lead:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "HIGH")
        )
        lead.registerComputeProfile(
            ComputePower.LOW,
            """[{"component":{"name":"EdgePing","class":"com.reveila.service.HttpClientService"}}]"""
        )
        lead.registerComputeProfile(
            ComputePower.HIGH,
            """[{"component":{"name":"ServerFabric","class":"com.reveila.ai.AgenticFabric"}}]"""
        )
        lead.start()

        val lowCitizen = createNode(
            net, "citizen-low", "http://citizen-low:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "LOW")
        )
        lowCitizen.start()

        val superCitizen = createNode(
            net, "citizen-super", "http://citizen-super:8080", seeds, { now },
            extraProps = mapOf(Constants.CLUSTER_COMPUTE_POWER to "SUPER")
        )
        superCitizen.start()

        val lowProfile = lowCitizen.fetchComputeProfileFromLeader()
        assertNotNull(lowProfile)
        assertTrue(lowProfile.contains("EdgePing"))

        // SUPER falls back to HIGH profile when no dedicated SUPER profile is defined
        val superProfile = superCitizen.fetchComputeProfileFromLeader()
        assertNotNull(superProfile)
        assertTrue(superProfile.contains("ServerFabric"))
    }

    @Test
    fun mobileEdgeClientNeverElectsItselfAndFailsOverToRemoteUrl() {
        var now = 10_000L
        val net = InMemoryClusterNetwork()

        val desktopLan = "http://192.168.1.50:8080"
        val desktopTailscale = "http://100.64.0.50:8080"

        val desktopLead = createNode(
            net, "aegin-desktop", desktopLan, emptyList(), { now },
            extraProps = mapOf(
                Constants.CLUSTER_LEADER_URL to desktopLan,
                Constants.CLUSTER_COMPUTE_POWER to "HIGH"
            ),
            localServices = listOf("KnowledgeVault", "AgenticFabric")
        )
        // Register the same Desktop Lead under its Tailscale virtual IP as well
        net.register(desktopTailscale, desktopLead)
        desktopLead.start()
        assertEquals(NodeRole.LEAD, desktopLead.role)

        val mobileClient = createNode(
            net, "aegin-mobile", "http://192.168.1.88:8080", emptyList(), { now },
            extraProps = mapOf(
                Constants.CLUSTER_LEADER_URL to desktopLan,
                Constants.CLUSTER_LEADER_FALLBACK_URLS to desktopTailscale,
                Constants.CLUSTER_ROLE to Constants.CLUSTER_ROLE_EDGE_CLIENT,
                Constants.CLUSTER_COMPUTE_POWER to "LOW"
            )
        )
        mobileClient.start()

        assertFalse(mobileClient.canElectLeader)
        assertEquals(NodeRole.WORKER, mobileClient.role)
        assertTrue(mobileClient.leaderReachable)
        assertEquals(desktopLan, mobileClient.leaderUrl)

        // User leaves home Wi-Fi -> LAN IP unreachable, Tailscale URL still reachable -> fails over cleanly
        net.setOffline(desktopLan, true)
        now += 5_000L
        mobileClient.tick()
        assertEquals(NodeRole.WORKER, mobileClient.role)
        assertTrue(mobileClient.leaderReachable)

        // Desktop goes to sleep -> both LAN and Tailscale unreachable -> Mobile NEVER promotes to LEAD
        net.setOffline(desktopTailscale, true)
        now += 10_000L
        mobileClient.tick()
        assertEquals(NodeRole.WORKER, mobileClient.role)
        assertFalse(mobileClient.leaderReachable)
    }
}
