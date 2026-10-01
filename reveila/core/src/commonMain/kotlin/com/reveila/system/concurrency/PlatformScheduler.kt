package com.reveila.system.concurrency

interface PlatformCancellable {
    fun cancel(mayInterruptIfRunning: Boolean = false): Boolean
    fun isCancelled(): Boolean
}

/**
 * Multiplatform task executor and recurring scheduler.
 */
interface PlatformScheduler {
    fun scheduleWithFixedDelay(
        initialDelaySeconds: Long,
        intervalSeconds: Long,
        task: () -> Unit
    ): PlatformCancellable
    fun execute(task: () -> Unit)
    fun shutdown()

    companion object {
        operator fun invoke(poolSize: Int = 4): PlatformScheduler = createPlatformScheduler(poolSize)
    }
}

expect fun createPlatformScheduler(poolSize: Int = 4): PlatformScheduler
