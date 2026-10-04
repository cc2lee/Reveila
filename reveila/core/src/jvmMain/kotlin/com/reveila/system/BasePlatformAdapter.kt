package com.reveila.system

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.MessageFormat
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.FileHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import com.reveila.crypto.Cryptographer
import com.reveila.crypto.DefaultCryptographer
import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.error.ConfigurationException
import com.reveila.error.SystemException
import com.reveila.event.AutoCallEvent
import com.reveila.event.EventConsumer
import com.reveila.system.concurrency.PlatformScheduler
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.util.io.FileUtil

/**
 * An implementation of PlatformAdapter for standard JVM platforms.
 */
abstract class BasePlatformAdapter @Throws(Exception::class) constructor(
    commandLineArgs: Properties? = null
) : PlatformAdapter {

    private var platformName: String? = null
    private var reveila: ReveilaEngine? = null
    private val properties: Properties = Properties()
    private val fs: PlatformFileSystem = PlatformFileSystem()
    @JvmField
    protected var logger: Logger = Logger.getLogger("")
    private var platformLogger: PlatformLogger = PlatformLogger("reveila.platform")
    private var jobThreadPoolSize: Int = 4 // Allow concurrent execution to prevent startup deadlocks
    private var scheduler: ScheduledExecutorService? = null
    private var platformScheduler: PlatformScheduler? = null
    private val autoCallTasks: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()
    private var systemHome: SystemHome? = null
    private var classLoader: ClassLoader? = null
    private val repositories: ConcurrentHashMap<String, Repository<Entity, Map<String, Map<String, Any>>>> = ConcurrentHashMap()

    init {
        setupSystemHome(commandLineArgs ?: Properties())
        loadProperties(commandLineArgs)
        configureLogging()
        setupClassLoader()
        createScheduler()
    }

    open fun getRepositories(): Map<String, Repository<Entity, Map<String, Map<String, Any>>>> {
        return repositories
    }

    private fun createScheduler() {
        val threadFactory = object : ThreadFactory {
            private val count = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "Task Executor - " + count.incrementAndGet())
            }
        }
        this.scheduler = Executors.newScheduledThreadPool(jobThreadPoolSize, threadFactory)
        this.platformScheduler = PlatformScheduler(jobThreadPoolSize)
    }

    override fun getPlatformDescription(): String? {
        return properties.getProperty(Constants.PLATFORM_OS)
    }

    override fun listRelativePaths(relativeDirectory: String, ext: String): Array<String> {
        if (relativeDirectory.isBlank()) {
            throw IOException("Manifests directory name not specified.")
        }
        val homePath = this.systemHome?.systemHome ?: throw IOException("System home not set.")
        val dir = fs.resolve(homePath, relativeDirectory)

        if (!fs.exists(dir)) {
            platformLogger.warning("Directory does not exist: $dir. Returning empty file list.")
            return emptyArray()
        }

        val files = FileUtil.listRelativePaths(dir, ext)
        return files.map { file ->
            val fullPath = fs.resolve(dir, file)
            val normalizedHome = fs.normalize(homePath).trimEnd('/', '\\')
            val normalizedFull = fs.normalize(fullPath)
            if (normalizedFull.startsWith(normalizedHome)) {
                normalizedFull.substring(normalizedHome.length).trimStart('/', '\\')
            } else {
                normalizedFull
            }
        }.toTypedArray()
    }

    override fun readFileBytes(relativePath: String): ByteArray {
        val home = this.systemHome?.systemHome ?: return byteArrayOf()
        val safePath = fs.toSafePath(home, relativePath)
        return fs.readBytes(safePath)
    }

    override fun readFileString(relativePath: String): String {
        val home = this.systemHome?.systemHome ?: return ""
        val safePath = fs.toSafePath(home, relativePath)
        return fs.readText(safePath)
    }

    override fun writeFileBytes(relativePath: String, data: ByteArray, append: Boolean) {
        val home = this.systemHome?.systemHome ?: return
        val safePath = fs.toSafePath(home, relativePath)
        fs.writeBytes(safePath, data, append)
    }

    override fun writeFileString(relativePath: String, text: String, append: Boolean) {
        val home = this.systemHome?.systemHome ?: return
        val safePath = fs.toSafePath(home, relativePath)
        fs.writeText(safePath, text, append)
    }

    @Throws(IOException::class)
    fun getFileInputStream(relativePath: String): InputStream {
        val homePath = this.systemHome?.systemHome ?: throw IOException("System home not set.")
        val absolutePath = Paths.get(fs.toSafePath(homePath, relativePath))
        if (!Files.exists(absolutePath)) {
            throw IOException("File not found: $absolutePath")
        }
        return Files.newInputStream(absolutePath)
    }

    @Throws(IOException::class)
    fun getFileOutputStream(relativePath: String, append: Boolean): OutputStream {
        val homePath = this.systemHome?.systemHome ?: throw IOException("System home not set.")
        val absolutePath = Paths.get(fs.toSafePath(homePath, relativePath))
        return if (append) {
            Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } else {
            Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    override fun unregisterAutoCall(componentName: String) {
        val task = autoCallTasks.remove(componentName)
        if (task != null) {
            task.cancel(false)
            platformLogger.info("Unregistered auto-call for component: $componentName")
        }
    }

    @Throws(IOException::class)
    override fun loadProperties(overrides: Properties?) {
        val filename = "reveila.properties"
        val rawProps = Properties()

        // 1. Load common properties
        var path = Constants.CONFIGS_DIR_NAME + "/" + filename
        try {
            val content = readFileString(path)
            rawProps.load(content)
        } catch (e: Exception) {
            throw IOException("Failed to load system properties from $path", e)
        }

        // 2. Load platform-specific properties
        val platName = getPlatformName()
        if (!platName.isNullOrBlank()) {
            path = Constants.CONFIGS_DIR_NAME + "/" + platName + "/" + filename
            val homePath = this.systemHome?.systemHome
            if (homePath != null && fs.exists(fs.toSafePath(homePath, path))) {
                try {
                    val content = readFileString(path)
                    rawProps.load(content)
                } catch (e: Exception) {
                    throw IOException("Failed to load system properties from $path", e)
                }
            }
        }

        if (overrides != null) {
            rawProps.putAll(overrides)
        }

        this.properties.putAll(resolveAllPlaceholders(rawProps))

        if (properties.getProperty(Constants.PLATFORM_OS) == null) {
            properties.setProperty(
                Constants.PLATFORM_OS,
                String.format(
                    "%s %s (%s)",
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch")
                )
            )
        }

        resolveProperty(Constants.CHARACTER_ENCODING, StandardCharsets.UTF_8.name())
        resolveProperty(Constants.LAUNCH_STRICT_MODE, "true")
    }

    override fun getPlatformName(): String? = this.platformName

    open fun setPlatformName(platformName: String) {
        require(platformName.isNotBlank()) { "Platform name cannot be null or blank" }
        this.platformName = platformName
    }

    private fun resolveAllPlaceholders(props: Properties): Properties {
        val resolved = Properties()
        for (key in props.stringPropertyNames()) {
            val value = props.getProperty(key)
            resolved.setProperty(key, resolveValue(value, props) ?: "")
        }
        return resolved
    }

    private fun resolveValue(value: String?, props: Properties): String? {
        if (value == null || !value.contains("\${")) {
            return value
        }

        val result = StringBuilder()
        var cursor = 0
        while (cursor < value.length) {
            val start = value.indexOf("\${", cursor)
            if (start == -1) {
                result.append(value.substring(cursor))
                break
            }
            result.append(value.substring(cursor, start))
            val end = value.indexOf("}", start)
            if (end == -1) {
                result.append(value.substring(start))
                break
            }

            val placeholder = value.substring(start + 2, end)
            var defaultValue: String? = null
            val separator = placeholder.indexOf(":")

            if (placeholder.startsWith("secret:")) {
                result.append("\${").append(placeholder).append("}")
                cursor = end + 1
                continue
            }

            var actualPlaceholder = placeholder
            if (separator != -1) {
                defaultValue = placeholder.substring(separator + 1)
                actualPlaceholder = placeholder.substring(0, separator)
            }

            var resolvedValue = System.getProperty(actualPlaceholder)
            if (resolvedValue == null) {
                resolvedValue = System.getenv(actualPlaceholder)
            }
            if (resolvedValue == null) {
                resolvedValue = props.getProperty(actualPlaceholder)
            }
            if (resolvedValue == null) {
                resolvedValue = defaultValue
            }

            if (resolvedValue != null) {
                result.append(resolvedValue)
            } else {
                result.append("\${").append(placeholder).append("}")
            }
            cursor = end + 1
        }
        return result.toString()
    }

    @Throws(ConfigurationException::class)
    private fun setupSystemHome(jvmArgs: Properties) {
        var path = jvmArgs.getProperty(Constants.SYSTEM_HOME)
        if (path.isNullOrBlank()) {
            path = System.getenv("REVEILA_HOME")
            if (path.isNullOrBlank()) {
                throw ConfigurationException(
                    "SYSTEM STARTUP ERROR: System Home directory not specified. Please set the " +
                        Constants.SYSTEM_HOME + " system property as a command line argument, or the REVEILA_HOME environment variable.",
                    null,
                    "101"
                )
            }
            jvmArgs.setProperty(Constants.SYSTEM_HOME, path)
        }

        val resetHome = "true".equals(jvmArgs.getProperty(Constants.RESET_HOME), ignoreCase = true)
        val home = SystemHome(path)
        home.createDirectoryStructure(resetHome)
        this.systemHome = home
    }

    private fun resolveProperty(key: String, defaultValue: String) {
        if (properties.getProperty(key).isNullOrBlank()) {
            properties.setProperty(key, defaultValue)
        }
    }

    @Throws(IOException::class)
    private fun scanJars(dir: Path?): Array<URL> {
        if (dir == null || !Files.exists(dir)) {
            return emptyArray()
        }

        Files.find(
            dir, 1,
            { path, attr -> path.toString().lowercase().endsWith(".jar") && attr.isRegularFile }
        ).use { stream ->
            return stream
                .map { toURL(it) }
                .filter { Objects.nonNull(it) }
                .toList()
                .filterNotNull()
                .toTypedArray()
        }
    }

    @Throws(Exception::class)
    private fun setupClassLoader() {
        this.classLoader = createClassLoader()
        Thread.currentThread().contextClassLoader = this.classLoader
    }

    @Throws(Exception::class)
    protected open fun createClassLoader(): ClassLoader {
        val home = this.systemHome?.systemHome ?: throw SystemException("System Home Path was not initialized.")
        val libsDir = Paths.get(home).resolve(Constants.LIB_DIR_NAME)
        Files.createDirectories(libsDir)

        val urls = scanJars(libsDir)
        if (urls.isNotEmpty()) {
            platformLogger.info(
                MessageFormat.format(
                    "Initializing Shared ClassLoader with {0} JARs from {1}",
                    urls.size,
                    libsDir
                )
            )
            for (url in urls) {
                platformLogger.info("  -> $url")
            }
        }

        if ("android".equals(this.properties.getProperty(Constants.PLATFORM), ignoreCase = true)) {
            platformLogger.info("Android platform detected. Creating DexClassLoader for shared libraries.")
            return RuntimeUtil.createPluginClassLoader(libsDir.toString(), this.javaClass.classLoader)
        }
        return URLClassLoader(urls, this.javaClass.classLoader)
    }

    private fun toURL(path: Path): URL? {
        return try {
            path.toUri().toURL()
        } catch (e: MalformedURLException) {
            logger.log(Level.SEVERE, MessageFormat.format("Invalid JAR path: {0}", path), e)
            null
        }
    }

    protected open fun getSystemHome(): Path? {
        val home = this.systemHome?.systemHome ?: return null
        return Paths.get(home)
    }

    override fun getLogger(): PlatformLogger = this.platformLogger

    @Throws(IOException::class)
    private fun configureLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1\$tF %1\$tT] [Reveila] [%3\$s] %4\$s: %5\$s%n")

        val rootLogger = Logger.getLogger("")
        for (handler in rootLogger.handlers) {
            rootLogger.removeHandler(handler)
        }

        rootLogger.addHandler(createLogFileHandler(properties))

        val consoleEnabled = properties.getProperty(Constants.LOG_CONSOLE_ENABLED, "true")
        if (consoleEnabled.toBoolean()) {
            val console: Handler = object : java.util.logging.StreamHandler(System.out, SimpleFormatter()) {
                @Synchronized
                override fun publish(log: java.util.logging.LogRecord) {
                    super.publish(log)
                    flush()
                }

                @Synchronized
                @Throws(SecurityException::class)
                override fun close() {
                    flush()
                }
            }
            console.level = Level.ALL
            rootLogger.addHandler(console)
        }

        setLoggingLevel(rootLogger, properties)
        this.logger = rootLogger
        this.platformLogger = PlatformLogger("reveila.platform")
    }

    @Throws(IOException::class)
    private fun createLogFileHandler(props: Properties): Handler {
        val logDir = Paths.get(props.getProperty(Constants.SYSTEM_HOME) ?: ".").resolve("logs")
        Files.createDirectories(logDir)
        val logFile = logDir.resolve("reveila.log")

        val limit = parseLogSetting(props.getProperty(Constants.LOG_FILE_SIZE), 0, "size (rotation limit)")
        val count = parseLogSetting(props.getProperty(Constants.LOG_FILE_COUNT), 1, "file count")

        val handler = if (limit > 0) {
            FileHandler(logFile.toString(), limit, maxOf(1, count), true)
        } else {
            FileHandler(logFile.toString(), true)
        }

        handler.formatter = SimpleFormatter()
        handler.level = Level.ALL
        return handler
    }

    private fun parseLogSetting(value: String?, defaultValue: Int, label: String): Int {
        if (value.isNullOrBlank()) return defaultValue
        return try {
            value.trim().toInt()
        } catch (e: NumberFormatException) {
            logger.warning("Invalid $label value '$value'. Using default: $defaultValue")
            defaultValue
        }
    }

    private fun setLoggingLevel(logger: Logger, props: Properties) {
        val level = props.getProperty(Constants.LOG_LEVEL, "INFO")?.trim()?.uppercase() ?: "INFO"
        try {
            logger.level = Level.parse(level)
        } catch (e: IllegalArgumentException) {
            logger.level = Level.ALL
        }
    }

    override fun getProperties(): Properties = this.properties

    @Throws(Exception::class)
    override fun registerAutoCall(
        componentName: String,
        methodName: String,
        delaySeconds: Long,
        intervalSeconds: Long,
        eventConsumer: EventConsumer,
        subject: Subject
    ) {
        unregisterAutoCall(componentName)

        val sched = scheduler ?: throw IllegalStateException("Scheduler is not running")
        val task = sched.scheduleWithFixedDelay({
            val rev = reveila ?: return@scheduleWithFixedDelay

            try {
                rev.invoke(componentName, methodName, null, "localhost", subject)
                val event = AutoCallEvent(
                    this, componentName, methodName, AutoCallEvent.COMPLETED,
                    System.currentTimeMillis(), null
                )
                notifyAutoCallEventListener(eventConsumer, event)
            } catch (t: Exception) {
                logger.log(
                    Level.SEVERE,
                    MessageFormat.format("Auto-call task failed! Component: {0}, Method: {1}", componentName, methodName),
                    t
                )
                val event = AutoCallEvent(
                    this, componentName, methodName, AutoCallEvent.FAILED,
                    System.currentTimeMillis(), t
                )
                notifyAutoCallEventListener(eventConsumer, event)
            }
        }, delaySeconds, intervalSeconds, TimeUnit.SECONDS)

        autoCallTasks[componentName] = task
    }

    private fun notifyAutoCallEventListener(listener: EventConsumer?, event: AutoCallEvent) {
        if (listener == null) return
        try {
            listener.notifyEvent(event)
        } catch (e: Exception) {
            logger.log(
                Level.SEVERE,
                MessageFormat.format("Failed to notify auto call event listener registered for component: {0}", event.proxyName),
                e
            )
        }
    }

    @Synchronized
    override fun unplug() {
        platformLogger.info("Platform Adapter shutting down...")
        autoCallTasks.values.forEach { task -> task.cancel(true) }
        autoCallTasks.clear()

        platformScheduler?.shutdown()
        platformScheduler = null

        scheduler?.let { sched ->
            sched.shutdown()
            try {
                if (!sched.awaitTermination(10, TimeUnit.SECONDS)) {
                    sched.shutdownNow()
                }
            } catch (e: InterruptedException) {
                sched.shutdownNow()
                Thread.currentThread().interrupt()
                logger.warning("Thread interrupted while waiting for Task Executor termination.")
            }
        }
        this.scheduler = null
        this.reveila = null

        val cl = classLoader
        if (cl is Closeable) {
            try {
                cl.close()
            } catch (e: IOException) {
                logger.log(Level.SEVERE, MessageFormat.format("Failed to close platform adapter class loader {0}, {1}", cl.javaClass.name, e.message))
            }
            classLoader = null
        }
        platformLogger.info("Platform Adapter shutdown complete.")
    }

    fun getExecutor(): ExecutorService? = scheduler

    override fun getScheduler(): PlatformScheduler? = platformScheduler

    override fun getCryptographer(): Cryptographer? = DefaultCryptographer()

    @Suppress("UNCHECKED_CAST")
    override fun getRepository(entityType: String): Repository<Entity, @JvmSuppressWildcards Map<String, Map<String, Any>>>? {
        return repositories[entityType.lowercase()]
    }

    open fun registerRepository(entityType: String?, repository: Repository<Entity, Map<String, Map<String, Any>>>?) {
        if (entityType != null && repository != null) {
            repositories[entityType.lowercase()] = repository
            platformLogger.info("Registered repository for entity type: $entityType")
        }
    }

    @Synchronized
    override fun plug(reveila: ReveilaEngine) {
        this.reveila = reveila
    }
}
