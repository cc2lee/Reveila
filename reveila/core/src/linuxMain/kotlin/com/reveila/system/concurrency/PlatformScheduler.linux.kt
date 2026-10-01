package com.reveila.system.concurrency

import kotlinx.coroutines.*

class LinuxPlatformScheduler(poolSize: Int) : PlatformScheduler {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun scheduleWithFixedDelay(
        initialDelaySeconds: Long,
        intervalSeconds: Long,
        task: () -> Unit
    ): PlatformCancellable {
        val job = scope.launch {
            delay(initialDelaySeconds * 1000L)
            while (isActive) {
                try {
                    task()
                } catch (e: Throwable) {
                }
                delay(intervalSeconds * 1000L)
            }
        }

        return object : PlatformCancellable {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                job.cancel()
                return true
            }

            override fun isCancelled(): Boolean = !job.isActive
        }
    }

    override fun execute(task: () -> Unit) {
        scope.launch { task() }
    }

    override fun shutdown() {
        scope.cancel()
    }
}

actual fun createPlatformScheduler(poolSize: Int): PlatformScheduler = LinuxPlatformScheduler(poolSize)
