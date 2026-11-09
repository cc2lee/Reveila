package com.reveila.system.concurrency

class ApplePlatformLock : PlatformLock {
    override fun <R> withLock(block: () -> R): R = block()
}

actual fun createPlatformLock(): PlatformLock = ApplePlatformLock()
