package com.reveila.system

import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

actual abstract class AbstractComponent actual constructor() : Startable, Stoppable {

    @JvmField
    protected var logger: Logger = Logger.getLogger("reveila." + this.javaClass.name)

    @JvmField
    protected var debug: Boolean = false

    actual var isManaged: Boolean = false

    actual var isDebug: Boolean
        get() = debug
        set(value) {
            debug = value
        }

    private var _state: ComponentState = ComponentState.STOPPED
    actual val state: ComponentState
        get() = _state

    private var startTime: Instant? = null
    private var startupLatency: Duration? = null

    fun getStartTime(): Instant? = startTime
    fun getStartupLatency(): Duration? = startupLatency

    actual fun isRunning(): Boolean = _state == ComponentState.ACTIVE

    @Throws(Exception::class)
    actual override fun stop() {
        synchronized(this) {
            _state = ComponentState.STOPPING
            try {
                onStop()
                _state = ComponentState.STOPPED
            } catch (e: Exception) {
                _state = ComponentState.FAILED
                throw e
            }
        }
    }

    @Throws(Exception::class)
    actual override fun start() {
        synchronized(this) {
            if (_state == ComponentState.ACTIVE || _state == ComponentState.STARTING) return

            startTime = Instant.now()
            _state = ComponentState.STARTING

            try {
                onStart()

                if (Thread.currentThread().isInterrupted) {
                    stop()
                    throw InterruptedException("Component start interrupted.")
                }

                startupLatency = Duration.between(startTime, Instant.now())
                _state = ComponentState.ACTIVE
            } catch (e: Exception) {
                _state = ComponentState.FAILED
                throw e
            }
        }
    }

    actual val startupLatencyMs: Long
        get() = startupLatency?.toMillis() ?: 0L

    @Throws(Exception::class)
    actual protected abstract fun onStart()

    @Throws(Exception::class)
    actual protected abstract fun onStop()

    protected open fun isInterrupted(): Boolean = Thread.currentThread().isInterrupted
}
