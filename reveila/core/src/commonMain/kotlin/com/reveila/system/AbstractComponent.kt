package com.reveila.system

import com.reveila.system.logging.PlatformLogger
import kotlin.time.TimeSource

abstract class AbstractComponent : Startable, Stoppable {

    protected val logger: PlatformLogger = PlatformLogger("reveila.component")

    var isManaged: Boolean = false
    var isDebug: Boolean = false

    private var _state: ComponentState = ComponentState.STOPPED
    val state: ComponentState
        get() = _state

    private var latencyMs: Long = 0L

    fun isRunning(): Boolean = _state == ComponentState.ACTIVE

    @Throws(Exception::class)
    override fun stop() {
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
    override fun start() {
        if (_state == ComponentState.ACTIVE || _state == ComponentState.STARTING) return

        val mark = TimeSource.Monotonic.markNow()
        _state = ComponentState.STARTING

        try {
            onStart()
            latencyMs = mark.elapsedNow().inWholeMilliseconds
            _state = ComponentState.ACTIVE
        } catch (e: Exception) {
            _state = ComponentState.FAILED
            throw e
        }
    }

    val startupLatencyMs: Long
        get() = latencyMs

    @Throws(Exception::class)
    protected abstract fun onStart()

    @Throws(Exception::class)
    protected abstract fun onStop()
}
