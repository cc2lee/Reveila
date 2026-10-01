package com.reveila.system.concurrency

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class JvmPlatformScheduler(poolSize: Int) : PlatformScheduler {

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(poolSize) { r ->
        val count = threadCount.incrementAndGet()
        Thread(r, "Reveila-Scheduler-$count").apply { isDaemon = true }
    }

    override fun scheduleWithFixedDelay(
        initialDelaySeconds: Long,
        intervalSeconds: Long,
        task: () -> Unit
    ): PlatformCancellable {
        val future: ScheduledFuture<*> = executor.scheduleWithFixedDelay(
            { task() },
            initialDelaySeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        )
        return object : PlatformCancellable {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                return future.cancel(mayInterruptIfRunning)
            }

            override fun isCancelled(): Boolean {
                return future.isCancelled
            }
        }
    }

    override fun execute(task: () -> Unit) {
        executor.execute(task)
    }

    override fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private val threadCount = AtomicInteger(0)
    }
}

actual fun createPlatformScheduler(poolSize: Int): PlatformScheduler = JvmPlatformScheduler(poolSize)
