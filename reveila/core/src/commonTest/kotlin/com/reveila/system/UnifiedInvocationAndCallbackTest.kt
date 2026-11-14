package com.reveila.system

import com.reveila.error.SecurityException
import com.reveila.error.VersionConflictException
import com.reveila.event.EventObject
import com.reveila.safety.FlightRecorder
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.SecretManager
import com.reveila.system.logging.PlatformLogger
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnifiedInvocationAndCallbackTest {

    private class FakeEngine(private val engineVersion: String = "1.0.0") : ReveilaEngine {
        var lastComponent: String? = null
        var lastMethod: String? = null
        var lastParams: Array<Any?>? = null

        override fun isRunning(): Boolean = true
        override fun shutdown() {}
        override fun notifyEvent(evtObj: EventObject) {}
        override fun getVersion(): String = engineVersion

        override fun invoke(
            componentName: String,
            methodName: String,
            params: Array<Any?>?,
            callerIp: String?,
            subject: Subject?
        ): Any? {
            lastComponent = componentName
            lastMethod = methodName
            lastParams = params
            return mapOf(
                "component" to componentName,
                "method" to methodName,
                "argCount" to (params?.size ?: 0)
            )
        }
    }

    private class RecordingFlightRecorder : FlightRecorder {
        val steps = mutableListOf<Pair<String, Map<String, Any>>>()
        val toolOutputs = mutableListOf<Pair<String, Any?>>()

        override fun recordStep(plugin: Plugin, stepName: String, data: Map<String, Any>) {
            steps.add(stepName to data)
        }

        override fun recordReasoning(plugin: Plugin, reasoning: String) {}

        override fun recordToolOutput(plugin: Plugin, toolName: String, output: Any?) {
            toolOutputs.add(toolName to output)
        }

        override fun recordForensicMetadata(plugin: Plugin, metadata: Map<String, Any>) {}
    }

    private fun createManagedInvocationFixture(): Triple<ManagedInvocation, SecretManager, RecordingFlightRecorder> {
        val secretManager = SecretManager()
        val flightRecorder = RecordingFlightRecorder()
        val proxies = mapOf<String, Proxy>(
            "SecretManager" to object : Proxy {
                override fun getName(): String = "SecretManager"
                override fun getRequiredRoles(): List<String> = emptyList()
                override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
                override fun getInstance(): Any = secretManager
            },
            "FlightRecorder" to object : Proxy {
                override fun getName(): String = "FlightRecorder"
                override fun getRequiredRoles(): List<String> = emptyList()
                override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
                override fun getInstance(): Any = flightRecorder
            },
            "KnowledgeVault" to object : Proxy {
                override fun getName(): String = "KnowledgeVault"
                override fun getRequiredRoles(): List<String> = emptyList()
                override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
                    return mapOf(
                        "component" to "KnowledgeVault",
                        "method" to methodName,
                        "args" to (args?.toList() ?: emptyList<Any?>())
                    )
                }
            }
        )

        val testContext = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy =
                proxies[name] ?: throw IllegalArgumentException("Unknown component: $name")
            override val properties: Properties? = null
            override val platformAdapter: PlatformAdapter? = null
        }

        val managedInvocation = ManagedInvocation()
        managedInvocation.setContext(testContext)
        managedInvocation.start()

        return Triple(managedInvocation, secretManager, flightRecorder)
    }

    @Test
    fun testReveilaEngineVersionValidation() {
        val engine = FakeEngine("1.0.0")

        assertTrue(engine.isVersionCompatible("1.0.0"))
        assertTrue(engine.isVersionCompatible("v1.0.0"))
        assertTrue(engine.isVersionCompatible(" 1.0.0 "))
        assertFalse(engine.isVersionCompatible(null))
        assertFalse(engine.isVersionCompatible(""))
        assertFalse(engine.isVersionCompatible("2.0.0"))

        engine.validateVersion("1.0.0")

        val conflict = assertFailsWith<VersionConflictException> {
            engine.validateVersion("2.0.0")
        }
        assertEquals("2.0.0", conflict.clientVersion)
        assertEquals("1.0.0", conflict.backendVersion)
    }

    @Test
    fun testReveilaClientUnifiedUrlAndVersionedPayload() {
        val url = ReveilaClient.buildInvokeUrl("http://host.docker.internal:8080", "ManagedInvocation")
        assertEquals("http://host.docker.internal:8080/api/components/ManagedInvocation/invoke", url)

        val payload = ReveilaClient.buildMethodPayload(
            methodName = "handleCallback",
            args = arrayOf("JIT-123", "VaultService", "read", mapOf("key" to "k1")),
            version = "1.0.0"
        )
        assertEquals("1.0.0", payload["version"])
        assertEquals("handleCallback", payload["methodName"])
        val json = JsonUtil.toJsonString(payload)
        val parsed = JsonUtil.parseJsonValue(json) as Map<*, *>
        assertEquals("1.0.0", parsed["version"])
        assertEquals("handleCallback", parsed["methodName"])
    }

    @Test
    fun testReveilaClientInvokeHostAndReportResultRouting() {
        val engine = FakeEngine("1.0.0")
        val (managedInvocation, secretManager, flightRecorder) = createManagedInvocationFixture()
        val plugin = Plugin("session-1", "test-agent-plugin", "default-tenant", "trace-unit-test-1")
        val jitToken = secretManager.generateJitToken(plugin, "sandbox:callback")[Constants.ENV_JIT_TOKEN]!!

        val client = object : ReveilaClient(
            baseUrl = "http://host.docker.internal:8080",
            jitToken = jitToken,
            version = "1.0.0"
        ) {
            override fun invoke(
                componentName: String,
                methodName: String,
                args: Array<out Any?>?,
                targetBaseUrl: String?,
                extraHeaders: Map<String, String>
            ): Any? {
                engine.validateVersion("1.0.0")
                assertEquals("ManagedInvocation", componentName)
                @Suppress("UNCHECKED_CAST")
                return when (methodName) {
                    "handleCallback" -> managedInvocation.handleCallback(
                        args!![0] as String,
                        args[1] as String,
                        args[2] as String,
                        args[3] as? Map<String, Any>
                    )
                    "handleCallbackResult" -> managedInvocation.handleCallbackResult(
                        args!![0] as String,
                        args[1] as? String,
                        args[2] as? String,
                        args[3] as? String,
                        args[4] as? String,
                        args[5]
                    )
                    else -> error("Unexpected method: $methodName")
                }
            }
        }

        val hostResponse = client.invokeHost("KnowledgeVault", "query", mapOf("q" to "sovereign")) as Map<*, *>
        assertNotNull(hostResponse)
        assertEquals("KnowledgeVault", hostResponse["component"])
        assertEquals("query", hostResponse["method"])
        assertTrue(flightRecorder.steps.any { it.first == "sandbox_callback" })

        val traceId = "trace-unit-test-1"
        val reportAck = client.reportResult(
            traceId = traceId,
            pluginId = "test-agent-plugin",
            methodName = "runTask",
            result = mapOf("answer" to 42),
            status = "SUCCESS"
        ) as Map<*, *>

        assertEquals("SUCCESS", reportAck["status"])
        assertEquals(traceId, reportAck["traceId"])
        assertTrue(flightRecorder.toolOutputs.any { it.first == "test-agent-plugin.runTask" })
    }

    @Test
    fun testManagedInvocationRejectsInvalidJitToken() {
        val (managedInvocation, _, _) = createManagedInvocationFixture()

        assertFailsWith<SecurityException> {
            managedInvocation.handleCallback("JIT-invalid-token", "KnowledgeVault", "query", emptyMap<String, Any>())
        }

        assertFailsWith<SecurityException> {
            managedInvocation.handleCallbackResult("JIT-invalid-token", "trace-1", "plugin", "method", "SUCCESS", "ok")
        }
    }
}
