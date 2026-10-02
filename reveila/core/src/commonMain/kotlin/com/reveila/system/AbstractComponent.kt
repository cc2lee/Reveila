package com.reveila.system

expect abstract class AbstractComponent() : Startable, Stoppable {
    var isManaged: Boolean
    var isDebug: Boolean
    val state: ComponentState
    val startupLatencyMs: Long
    fun isRunning(): Boolean
    @Throws(Exception::class)
    override fun start()
    @Throws(Exception::class)
    override fun stop()
    @Throws(Exception::class)
    protected abstract fun onStart()
    @Throws(Exception::class)
    protected abstract fun onStop()
}
