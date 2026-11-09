package com.reveila.system.concurrency

class MingwPlatformLock : PlatformLock {
    override fun <R> withLock(block: () -> R): R = block()
}

actual fun createPlatformLock(): PlatformLock = MingwPlatformLock()
