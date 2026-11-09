package com.reveila.system.concurrency

class JvmPlatformLock : PlatformLock {
    private val lock = Any()

    override fun <R> withLock(block: () -> R): R = synchronized(lock, block)
}

actual fun createPlatformLock(): PlatformLock = JvmPlatformLock()
