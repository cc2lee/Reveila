package com.reveila.safety

import com.reveila.crypto.Cryptographer
import com.reveila.service.DataService
import com.reveila.system.BasePlatformAdapter
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.logging.PlatformLogger
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretManagerPersistenceTest {

    private fun buildRuntime(systemHomeDir: File): SecretManager {
        val props = Properties().apply {
            setProperty("system.home", systemHomeDir.absolutePath)
        }
        val adapter = BasePlatformAdapter(props)
        val dataService = DataService()
        val secretManager = SecretManager()

        lateinit var ctx: Context
        val dsProxy = object : Proxy {
            override fun getName(): String = "DataService"
            override fun getRequiredRoles(): List<String> = emptyList()
            override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
                return when (methodName) {
                    "getRepository" -> dataService.getRepository(args?.firstOrNull()?.toString().orEmpty())
                    else -> null
                }
            }
            override fun getInstance(): Any = dataService
        }
        ctx = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy =
                if (name == "DataService") dsProxy else throw IllegalArgumentException(name)
            override val properties: Properties = props
            override val platformAdapter: PlatformAdapter = adapter
            override val cryptographer: Cryptographer? = adapter.getCryptographer()
        }

        dataService.setContext(ctx)
        dataService.start()
        secretManager.setContext(ctx)
        secretManager.start()
        return secretManager
    }

    @Test
    fun testSecretAndVaultKeyPersistAcrossAdapterRestarts() {
        val tempHome = Files.createTempDirectory("reveila-secret-persist-test").toFile()
        try {
            val configsDir = File(tempHome, "configs").apply { mkdirs() }
            File(configsDir, "reveila.properties").writeText("system.name=Test\n")

            val firstRun = buildRuntime(tempHome)
            firstRun.storeSecret("DEEPSEEK_API_KEY", "sk-persist-123456")

            assertTrue(File(tempHome, "data/reveila_secretss.json").exists())
            assertTrue(File(tempHome, "data/.vault.key").exists())

            // Simulate a clean process restart with a brand new BasePlatformAdapter and DataService
            val secondRun = buildRuntime(tempHome)
            assertEquals("sk-persist-123456", secondRun.getSecret("DEEPSEEK_API_KEY"))
        } finally {
            tempHome.deleteRecursively()
        }
    }
}
