package com.reveila.system

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.concurrent.ExecutorService
import java.util.logging.Logger
import javax.security.auth.Subject
import com.reveila.crypto.Cryptographer
import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.event.EventConsumer

/**
 * An interface to abstract the host platform, allowing Reveila to run
 * on different platforms without having to deal with the underlying implementation details.
 */
interface PlatformAdapter {
    fun getPlatformName(): String?
    fun getPlatformDescription(): String?
    fun getProperties(): Properties?

    @Throws(IOException::class)
    fun getFileInputStream(relativePath: String): InputStream

    @Throws(IOException::class)
    fun getFileOutputStream(relativePath: String, append: Boolean): OutputStream

    @Throws(IOException::class)
    fun listRelativePaths(relativeDirectory: String, ext: String): Array<String>

    fun getLogger(): Logger?

    @Throws(Exception::class)
    fun registerAutoCall(
        componentName: String,
        methodName: String,
        delaySeconds: Long,
        intervalSeconds: Long,
        eventConsumer: EventConsumer,
        subject: Subject
    )

    fun unregisterAutoCall(componentName: String)

    fun plug(reveila: Reveila)

    fun unplug()

    @Throws(IOException::class)
    fun loadProperties(overrides: Properties?)

    fun getRepository(entityType: String): Repository<Entity, @JvmSuppressWildcards Map<String, Map<String, Any>>>?

    fun getCryptographer(): Cryptographer?

    fun getExecutor(): ExecutorService?
}
