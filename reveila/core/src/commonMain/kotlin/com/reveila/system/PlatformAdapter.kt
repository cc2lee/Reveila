package com.reveila.system

import com.reveila.crypto.Cryptographer
import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.event.EventConsumer
import com.reveila.system.concurrency.PlatformScheduler
import com.reveila.system.logging.PlatformLogger

/**
 * Multiplatform contract to abstract the host platform, allowing Reveila to run
 * seamlessly across Windows, Mac, iOS, Android, and Linux.
 */
interface PlatformAdapter {
    fun getPlatformName(): String?
    fun getPlatformDescription(): String?
    fun getProperties(): Properties?

    fun readFileBytes(relativePath: String): ByteArray
    fun readFileString(relativePath: String): String
    fun writeFileBytes(relativePath: String, data: ByteArray, append: Boolean = false)
    fun writeFileString(relativePath: String, text: String, append: Boolean = false)
    fun listRelativePaths(relativeDirectory: String, ext: String): Array<String>

    fun getLogger(): PlatformLogger?

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

    fun plug(reveila: ReveilaEngine)

    fun unplug()

    @Throws(Exception::class)
    fun loadProperties(overrides: Properties?)

    fun getRepository(entityType: String): Repository<Entity, Map<String, Map<String, Any>>>?

    fun getCryptographer(): Cryptographer?

    fun getScheduler(): PlatformScheduler?
}
