package com.reveila.system

import com.reveila.system.logging.PlatformLogger
import kotlin.time.TimeSource

actual abstract class AbstractComponent actual constructor() : Startable, Stoppable {

    protected val logger: PlatformLogger = PlatformLogger("reveila.component")

    actual var isManaged: Boolean = false
    actual var isDebug: Boolean = false

    private var _state: ComponentState = ComponentState.STOPPED
    actual val state: ComponentState
        get() = _state

    private var mark: TimeSource.Monotonic.ValueTimeMark? = null
    private var latencyMs: Long = 0L

    actual fun isRunning(): Boolean = _state == ComponentState.ACTIVE

    @Throws(Exception::class)
    actual override fun stop() {
        _state = ComponentState.STOPPING
        try {
            onStop()
            _state = ComponentState.STOPPED
        } catch (e: Exception) {
            _state = ComponentState.FAILED
            throw e
        }
    }

    @Throws(Exception::class)
    actual override fun start() {
        if (_state == ComponentState.ACTIVE || _state == ComponentState.STARTING) return

        val startMark = TimeSource.Monotonic.markNow()
        _state = ComponentState.STARTING

        try {
            onStart()
            latencyMs = startMark.elapsedNow().inWholeMilliseconds
            _state = ComponentState.ACTIVE
        } catch (e: Exception) {
            _state = ComponentState.FAILED
            throw e
        }
    }

    actual val startupLatencyMs: Long
        get() = latencyMs

    @Throws(Exception::class)
    actual protected abstract fun onStart()

    @Throws(Exception::class)
    actual protected abstract fun onStop()
}
