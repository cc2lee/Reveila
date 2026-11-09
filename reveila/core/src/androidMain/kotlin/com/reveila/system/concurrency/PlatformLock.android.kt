package com.reveila.system.concurrency

class AndroidPlatformLock : PlatformLock {
    private val lock = Any()

    override fun <R> withLock(block: () -> R): R = synchronized(lock, block)
}

actual fun createPlatformLock(): PlatformLock = AndroidPlatformLock()
