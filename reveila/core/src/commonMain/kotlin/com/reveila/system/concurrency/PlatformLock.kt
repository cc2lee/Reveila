package com.reveila.system.concurrency

/**
 * Cross-platform synchronization lock for multiplatform concurrency safety.
 */
interface PlatformLock {
    fun <R> withLock(block: () -> R): R

    companion object {
        operator fun invoke(): PlatformLock = createPlatformLock()
    }
}

expect fun createPlatformLock(): PlatformLock
