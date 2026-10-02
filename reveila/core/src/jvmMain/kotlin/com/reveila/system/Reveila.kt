package com.reveila.system

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import javax.security.auth.Subject
import com.reveila.crypto.CryptoException
import com.reveila.crypto.Cryptographer
import com.reveila.crypto.DefaultCryptographer
import com.reveila.error.ConfigurationException
import com.reveila.error.SystemException
import com.reveila.event.EventConsumer
import com.reveila.event.EventManager
import com.reveila.event.EventObject
import com.reveila.util.TimeFormat

/**
 * @author Charles Lee
 */
open class Reveila : AutoCloseable, EventConsumer {

    private var startExecutor: ExecutorService? = null
    private var platformAdapter: PlatformAdapter? = null
    private val properties: Properties = Properties()
    var systemContext: SystemContext? = null
        private set
    private var strictMode: Boolean = true
    private var startedProxies: MutableList<SystemProxy>? = null
    private var logger: Logger? = null
    private var localUrl: URL? = null
    private var standalone: Boolean = true
    private val isRunningFlag: AtomicBoolean = AtomicBoolean(false)
    private val localhostUrlString: String = "http://localhost/"

    open fun isRunning(): Boolean = isRunningFlag.get()

    override fun close() {
        shutdown()
    }

    @Synchronized
    open fun shutdown() {
        if (!isRunningFlag.get()) {
            return
        }

        isRunningFlag.set(false)
        var error = false

        val startMsg = "Shutting down Reveila..."
        logger?.info(startMsg)

        // Stop the executor from taking new tasks
        startExecutor?.shutdownNow()
        error = !stopComponents()

        systemContext?.clear()
        platformAdapter?.unplug()

        if (!error) {
            logger?.info("Reveila shut down successfully.")
        } else {
            logger?.warning("Reveila shut down with errors. Check logs for details.")
        }

        try {
            if (startExecutor?.awaitTermination(5, TimeUnit.SECONDS) == false) {
                logger?.warning("Some start-up threads did not exit cleanly.")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        logger?.let { log ->
            for (handler in log.handlers) {
                try {
                    handler.close()
                } catch (t: Exception) {
                    log.info("Failed to close logger handler: $handler")
                    t.printStackTrace()
                }
            }
        }
    }

    @Synchronized
    @Throws(Exception::class)
    open fun start(platformAdapter: PlatformAdapter) {
        this.platformAdapter = platformAdapter
        this.startExecutor = platformAdapter.getExecutor()
        platformAdapter.getProperties()?.let { this.properties.putAll(it) }
        this.logger = platformAdapter.getLogger()
        this.localUrl = URI(localhostUrlString).toURL()

        printLogo()
        logStartupBanner()
        val beginTime = System.currentTimeMillis()

        try {
            this.standalone = !"false".equals(this.properties.getProperty(Constants.STANDALONE_MODE), ignoreCase = true)
        } catch (e: Exception) {
        }

        createSystemContext(this.properties)
        this.platformAdapter?.plug(this)

        strictMode = !"false".equals(this.properties.getProperty(Constants.LAUNCH_STRICT_MODE), ignoreCase = true)
        var timeoutSeconds = 60L
        try {
            timeoutSeconds = this.properties.getProperty(Constants.COMPONENT_START_TIMEOUT, "60").toLong()
        } catch (e: NumberFormatException) {
            logger?.warning("Invalid value for ${Constants.COMPONENT_START_TIMEOUT}. Using default: 60")
        }

        var platform = properties.getProperty("platform")
        if (platform.isNullOrBlank()) {
            throw ConfigurationException("Platform not specified in ${Constants.SYSTEM_PROPERTIES}.")
        }

        platform = platform.trim().lowercase()

        val componentMap = LinkedHashMap<String, MetaObject>()

        // 1. Discover Shared Components (Tier 1)
        val sharedMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + "shared")
        for (mObj in sharedMetaObjects) {
            mObj.isPlugin = false
            componentMap[mObj.getName() ?: ""] = mObj
        }

        // 2. Discover Platform-Specific Components (Tier 2 - Overwrites Shared)
        val platformMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + platform)
        for (mObj in platformMetaObjects) {
            mObj.isPlugin = false
            componentMap[mObj.getName() ?: ""] = mObj
        }

        val finalComponentList = ArrayList(componentMap.values)

        // 3. Discover Plugins
        val pluginMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + "plugins")
        for (mObj in pluginMetaObjects) {
            mObj.isPlugin = true
        }

        val allMetaObjects = ArrayList<MetaObject>(finalComponentList)
        allMetaObjects.addAll(pluginMetaObjects)

        // PHASE 2: VALIDATION (Linting & Cycle Detection for ALL components together)
        try {
            ConfigurationLinter().lint(allMetaObjects, this.properties)
            logger?.info("✅ Full Configuration validation complete. No issues found.")
        } catch (e: Exception) {
            handleStartError("Configuration validation failed.", e)
        }

        // PHASE 3: EXECUTION (Starting components in dependency-aware order)
        val sortedComponents = sortMetaObjects(finalComponentList)
        startComponents(Constants.COMPONENT, sortedComponents, timeoutSeconds)

        val sortedPlugins = sortMetaObjects(pluginMetaObjects)
        startComponents(Constants.PLUGIN, sortedPlugins, timeoutSeconds)
        isRunningFlag.set(true)
        logStartupCompletion(beginTime)

        logger?.info("${getDisplayName(this.properties)} is running...")
    }

    open fun invokeAsync(
        componentName: String,
        methodName: String,
        params: Array<Any?>?,
        callerIp: String?,
        subject: Subject
    ): CompletableFuture<Any?> {
        return CompletableFuture.supplyAsync {
            try {
                invoke(componentName, methodName, params, callerIp, subject)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    @Throws(Exception::class)
    open fun invoke(
        componentName: String,
        methodName: String,
        params: Array<Any?>?,
        callerIp: String?,
        subject: Subject
    ): Any? {
        val startTime = System.currentTimeMillis()
        val ctx = systemContext
            ?: throw IllegalStateException("SystemContext is not initialized. Cannot invoke component.")

        if (!callerIp.isNullOrBlank()) {
            logger?.info("Received invocation request from $callerIp for target: $componentName, method: $methodName")
        }

        if (!standalone) {
            // Use the fastest node in the cluster to handle the request
            val url = PerformanceTracker.getInstance().getBestNodeUrl()

            if (url != null && url != this.localUrl) {
                try {
                    val proxy = ctx.getProxy(Constants.REMOTE_REVEILA, subject)
                    val result = proxy.invoke("invoke", arrayOf(url, componentName, methodName, params))
                    val timeUsed = System.currentTimeMillis() - startTime
                    PerformanceTracker.getInstance().track(timeUsed, url)
                    return result
                } catch (e: IllegalArgumentException) {
                    // Ignore, Remote Reveila not configured
                } catch (e: Exception) {
                    PerformanceTracker.getInstance().track(PerformanceTracker.DEFAULT_PENALTY_MS, url)
                    logger?.severe("Remote invocation failed. Falling back to local invocation. Error: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        return try {
            val proxy = ctx.getProxy(componentName, subject)
            val result = proxy.invoke(methodName, params)
            val timeUsed = System.currentTimeMillis() - startTime
            PerformanceTracker.getInstance().track(timeUsed, this.localUrl)
            result
        } catch (e: IllegalArgumentException) {
            throw ConfigurationException("Component '$componentName' not found.", e)
        }
    }

    @Throws(Exception::class)
    override fun notifyEvent(evtObj: EventObject) {
        systemContext?.notifyEvent(evtObj)
    }

    private fun printLogo() {
        val logoContent = loadLogoContent()
        val version = this.properties.getProperty(Constants.SYSTEM_VERSION)

        logger?.info("\n$logoContent")
        if (!version.isNullOrBlank()) {
            logger?.info("Version: $version")
        }
        logger?.info("\n")
    }

    private fun loadLogoContent(): String {
        try {
            val logoPath = Constants.CONFIGS_DIR_NAME + "/logo.txt"
            val adapter = this.platformAdapter
            if (adapter != null) {
                adapter.getFileInputStream(logoPath).use { `is` ->
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(1024)
                    var nRead: Int
                    while (`is`.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    return buffer.toString(StandardCharsets.UTF_8.name())
                }
            }
        } catch (e: Exception) {
            // Fall through to classpath
        }

        try {
            javaClass.classLoader.getResourceAsStream("logo.txt")?.use { `is` ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(1024)
                var nRead: Int
                while (`is`.read(data, 0, data.size).also { nRead = it } != -1) {
                    buffer.write(data, 0, nRead)
                }
                return buffer.toString(StandardCharsets.UTF_8.name())
            }
        } catch (e: IOException) {
            // Fall through to hardcoded default
        }

        return "REVEILA"
    }

    private fun logStartupBanner() {
        val displayName = getDisplayName(this.properties)
        logger?.info("Starting $displayName...")
    }

    private fun logStartupCompletion(beginTime: Long) {
        val displayName = getDisplayName(this.properties)
        val msecs = System.currentTimeMillis() - beginTime
        logger?.info("$displayName started successfully. Time taken = ${TimeFormat.timestamp(msecs)}")
    }

    private fun getDisplayName(props: Properties): String {
        val displayName = props.getProperty(Constants.SYSTEM_NAME)
        if (!displayName.isNullOrBlank()) {
            return displayName
        }
        return "Reveila"
    }

    @Throws(CryptoException::class, IllegalStateException::class)
    private fun createSystemContext(props: Properties) {
        val eventManager = EventManager()
        val adapter = this.platformAdapter ?: throw IllegalStateException("PlatformAdapter not initialized")
        var encrypter = adapter.getCryptographer()
        val log = this.logger ?: Logger.getLogger("reveila")

        if (encrypter == null) {
            val cryptoKey = System.getenv("REVEILA_CRYPTO_KEY")
            if (!cryptoKey.isNullOrBlank()) {
                var saltHex: String? = System.getenv("REVEILA_CRYPTO_SALT")
                if (saltHex.isNullOrBlank()) {
                    saltHex = props.getProperty("auth.master.salt")
                }

                if (saltHex.isNullOrBlank()) {
                    throw IllegalStateException("No Crypto Salt found. Please set REVEILA_CRYPTO_SALT environment variable.")
                }
                encrypter = DefaultCryptographer(cryptoKey, hexToBytes(saltHex))
            } else {
                throw IllegalStateException("No Cryptographer found. Please set REVEILA_CRYPTO_KEY environment variable.")
            }
        }

        this.systemContext = SystemContext(props, eventManager, log, encrypter, adapter)
    }

    private fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    @Throws(Exception::class)
    private fun parseMetaObjects(dir: String): List<MetaObject> {
        val adapter = this.platformAdapter ?: return emptyList()
        val fileArray = adapter.listRelativePaths(dir, ".json")
        val fileList = ArrayList<String>()
        if (fileArray.isNotEmpty()) {
            Collections.addAll(fileList, *fileArray)
        }

        val list = ArrayList<MetaObject>()
        for (file in fileList) {
            try {
                adapter.getFileInputStream(file).use { `is` ->
                    val config = Configuration(`is`)
                    for (mObj in config.getMetaObjects()) {
                        if (mObj.getName().isNullOrBlank()) {
                            throw ConfigurationException("Component name is not set in configuration file: $file")
                        }
                        list.add(mObj)
                    }
                    logger?.info("Processed configuration file: $file")
                }
            } catch (e: Exception) {
                handleStartError("Failed to parse configuration file: $file", e)
            }
        }

        return list
    }

    @Throws(Exception::class)
    private fun handleStartError(message: String?, t: Throwable) {
        val msg = if (message.isNullOrBlank()) "System startup error!" else message
        if (strictMode) {
            stopComponents()
            throw SystemException(msg, t)
        } else {
            logger?.let { log ->
                val suffix = if (msg.endsWith(".") || msg.endsWith("!") || msg.endsWith("?")) " " else ". "
                log.log(Level.SEVERE, msg + suffix + "Continuing in non-strict mode.", t)
            }
        }
    }

    @Throws(Exception::class)
    private fun setupAutoCall(proxy: Proxy, autoRunConf: Map<String, Any?>?, subject: Subject) {
        if (autoRunConf.isNullOrEmpty()) return

        val methodName = autoRunConf[Constants.RUNNABLE_METHOD]?.toString()
        if (methodName.isNullOrBlank()) {
            throw ConfigurationException("Component auto-run configuration property '${Constants.RUNNABLE_METHOD}' is not set.")
        }

        val delayStr = autoRunConf[Constants.RUNNABLE_DELAY]?.toString()
        if (delayStr.isNullOrBlank() || "null" == delayStr) {
            throw ConfigurationException("Component auto-run configuration property '${Constants.RUNNABLE_DELAY}' is not set.")
        }

        val intervalStr = autoRunConf[Constants.RUNNABLE_INTERVAL]?.toString()
        if (intervalStr.isNullOrBlank() || "null" == intervalStr) {
            throw ConfigurationException("Component auto-run configuration property '${Constants.RUNNABLE_INTERVAL}' is not set.")
        }

        val delay = try {
            delayStr.toLong()
        } catch (e: NumberFormatException) {
            throw ConfigurationException("Invalid numeric value for auto-run configuration: ${e.message}")
        }

        val interval = try {
            intervalStr.toLong()
        } catch (e: NumberFormatException) {
            throw ConfigurationException("Invalid numeric value for auto-run configuration: ${e.message}")
        }

        if (interval < 0) {
            throw ConfigurationException("Component auto-call configuration property '${Constants.RUNNABLE_INTERVAL}' cannot be negative.")
        }

        platformAdapter?.registerAutoCall(proxy.getName(), methodName, delay, interval, this, subject)
    }

    private fun createManifest(tag: String, mObj: MetaObject): Manifest {
        val manifest = Manifest()
        manifest.componentType = tag

        val map = mObj.dataMap
        manifest.name = map[Constants.NAME] as? String
        manifest.displayName = map[Constants.DISPLAY_NAME] as? String
        manifest.version = map[Constants.VERSION] as? String
        manifest.description = map[Constants.DESCRIPTION] as? String
        manifest.author = map[Constants.AUTHOR] as? String
        manifest.implementationClass = map[Constants.CLASS] as? String

        val rolesObj = map[Constants.REQUIRED_ROLES]
        if (rolesObj is List<*>) {
            for (rObj in rolesObj) {
                if (rObj is String) {
                    manifest.requiredRoles.add(rObj)
                }
            }
        }

        val methodsObj = map[Constants.METHODS]
        if (methodsObj is List<*>) {
            val parsedMethods = ArrayList<Manifest.ExposedMethod>()
            for (mDescription in methodsObj) {
                if (mDescription is Map<*, *>) {
                    val method = Manifest.ExposedMethod()
                    method.name = mDescription["name"] as? String
                    method.description = mDescription["description"] as? String
                    method.returnType = mDescription["return"] as? String

                    val methodRolesObj = mDescription[Constants.REQUIRED_ROLES]
                    if (methodRolesObj is List<*>) {
                        for (rObj in methodRolesObj) {
                            if (rObj is String) {
                                method.requiredRoles.add(rObj)
                            }
                        }
                    }

                    val paramsObj = mDescription["parameters"]
                    if (paramsObj is List<*>) {
                        for (pObj in paramsObj) {
                            if (pObj is Map<*, *>) {
                                val param = Manifest.Parameter()
                                param.name = pObj["name"] as? String
                                param.description = pObj["description"] as? String
                                param.type = pObj["type"] as? String
                                param.isRequired = java.lang.Boolean.TRUE == pObj["isRequired"]
                                param.isSecret = java.lang.Boolean.TRUE == pObj["isSecret"]
                                method.parameters.add(param)
                            }
                        }
                    }
                    parsedMethods.add(method)
                }
            }
            manifest.exposedMethods.addAll(parsedMethods)
        }
        return manifest
    }

    @Throws(Exception::class)
    private fun startComponents(tag: String, metaObjectList: List<MetaObject>?, timeoutSeconds: Long) {
        if (metaObjectList.isNullOrEmpty()) return

        if (startedProxies == null) {
            startedProxies = ArrayList()
        }

        val executor = startExecutor ?: throw IllegalStateException("Executor not initialized")
        val ctx = systemContext ?: throw IllegalStateException("SystemContext not initialized")

        for (mObj in metaObjectList) {
            val compName = mObj.getName() ?: ""
            val debug = "true".equals(this.properties.getProperty("debug"), ignoreCase = true)
            val track = "true".equals(this.properties.getProperty("track"), ignoreCase = true)
            val manifest = createManifest(tag, mObj)
            val type = manifest.componentType ?: "component"
            val proxy = SystemProxy(mObj, manifest)
            proxy.isDebug = debug
            proxy.isManaged = track
            val subject = Subject()
            subject.principals.add(RolePrincipal(type))

            if (!Constants.COMPONENT.equals(type, ignoreCase = true)) {
                try {
                    var pluginsRootDir = this.properties.getProperty("plugin.local.dir")
                    if (pluginsRootDir.isNullOrBlank()) {
                        pluginsRootDir = this.properties.getProperty(Constants.SYSTEM_HOME + File.separator + "plugins")
                    }
                    val pluginFolder = File(pluginsRootDir, compName)
                    if (pluginFolder.exists() && pluginFolder.isDirectory) {
                        val loader = RuntimeUtil.createPluginClassLoader(pluginFolder.absolutePath, javaClass.classLoader)
                        proxy.setClassLoader(loader)
                    }
                } catch (e: Exception) {
                    logger?.warning("Failed to initialize plugin ClassLoader for $compName: ${e.message}")
                }
            }
            ctx.add(proxy)

            try {
                if (!mObj.isAutoStart()) {
                    logger?.info("ℹ️ Skipping auto-start for $tag: $compName")
                    continue
                }

                val autoRunConf = mObj.getAutoRunConf()

                val startFuture = CompletableFuture.runAsync({
                    try {
                        proxy.start()
                        setupAutoCall(proxy, autoRunConf, subject)
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }, executor)

                startFuture.get(timeoutSeconds, TimeUnit.SECONDS)

                logger?.info("✅ Started $tag: $compName")
                startedProxies?.add(proxy)
            } catch (e: TimeoutException) {
                var msg = "⏱️ Timeout: Component [$compName] failed to start within $timeoutSeconds seconds."
                try {
                    proxy.stop()
                } catch (ex: Exception) {
                    msg += "\n⚠️ Failed to stop $tag after start timeout: $compName - ${ex.message}"
                }

                ctx.remove(proxy)
                platformAdapter?.unregisterAutoCall(proxy.getName())
                handleStartError(msg, e)
            } catch (t: Exception) {
                if (t is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                var msg = "❌ Failed to start $tag [$compName]."
                try {
                    proxy.stop()
                } catch (ex: Exception) {
                    msg += "\n⚠️ Failed to stop $tag after start up error: $compName - ${ex.message}"
                }

                ctx.remove(proxy)
                platformAdapter?.unregisterAutoCall(proxy.getName())
                handleStartError(msg, t)
            }
        }
    }

    private fun stopComponents(): Boolean {
        val proxies = startedProxies
        if (proxies.isNullOrEmpty()) return true

        var success = true
        Collections.reverse(proxies)
        for (p in proxies) {
            try {
                p.stop()
            } catch (e: Exception) {
                success = false
                logger?.log(Level.WARNING, "⚠️ Failed to stop $p", e)
            }
        }

        proxies.clear()
        return success
    }

    private fun sortMetaObjects(list: List<MetaObject>): List<MetaObject> {
        val map = list.associateBy { it.getName() ?: "" }
        val sorted = ArrayList<MetaObject>()
        val visited = HashSet<String>()
        val stack = HashSet<String>()

        for (m in list) {
            try {
                visit(m, map, sorted, visited, stack)
            } catch (e: Exception) {
                logger?.info("Sort error: ${e.message}")
            }
        }
        return sorted
    }

    @Throws(Exception::class)
    private fun visit(
        m: MetaObject,
        map: Map<String, MetaObject>,
        sorted: MutableList<MetaObject>,
        visited: MutableSet<String>,
        stack: MutableSet<String>
    ) {
        val mName = m.getName() ?: ""
        if (visited.contains(mName)) return
        if (stack.contains(mName)) throw Exception("Circular dependency detected at: $mName")

        stack.add(mName)
        val deps = m.getDependencies()
        for (dName in deps) {
            val dm = map[dName]
            if (dm != null) {
                visit(dm, map, sorted, visited, stack)
            }
        }
        stack.remove(mName)
        visited.add(mName)
        sorted.add(m)
    }
}
