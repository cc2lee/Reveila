package com.reveila.system.concurrency

import platform.darwin.*
import kotlinx.cinterop.*

class ApplePlatformScheduler(poolSize: Int) : PlatformScheduler {
    private val queue = dispatch_queue_create("com.reveila.scheduler", DISPATCH_QUEUE_CONCURRENT)

    override fun scheduleWithFixedDelay(
        initialDelaySeconds: Long,
        intervalSeconds: Long,
        task: () -> Unit
    ): PlatformCancellable {
        val timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0u, 0u, queue)
        val initialNanos = (initialDelaySeconds * 1_000_000_000L)
        val intervalNanos = (intervalSeconds * 1_000_000_000UL)

        dispatch_source_set_timer(
            timer,
            dispatch_time(DISPATCH_TIME_NOW, initialNanos),
            intervalNanos,
            0u
        )

        var cancelled = false
        dispatch_source_set_event_handler(timer) {
            if (!cancelled) {
                task()
            }
        }
        dispatch_resume(timer)

        return object : PlatformCancellable {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                if (!cancelled) {
                    cancelled = true
                    dispatch_source_cancel(timer)
                    return true
                }
                return false
            }

            override fun isCancelled(): Boolean = cancelled
        }
    }

    override fun execute(task: () -> Unit) {
        dispatch_async(queue) {
            task()
        }
    }

    override fun shutdown() {
        // GCD queues are automatically reclaimed
    }
}

actual fun createPlatformScheduler(poolSize: Int): PlatformScheduler = ApplePlatformScheduler(poolSize)
