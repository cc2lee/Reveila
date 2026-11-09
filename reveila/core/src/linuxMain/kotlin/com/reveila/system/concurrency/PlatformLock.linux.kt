package com.reveila.system.concurrency

class LinuxPlatformLock : PlatformLock {
    override fun <R> withLock(block: () -> R): R = block()
}

actual fun createPlatformLock(): PlatformLock = LinuxPlatformLock()
