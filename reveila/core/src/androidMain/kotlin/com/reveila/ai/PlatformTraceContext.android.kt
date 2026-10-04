package com.reveila.ai

actual object PlatformTraceContext {
    private val current = ThreadLocal<String?>()
    actual fun get(): String? = current.get()
    actual fun set(value: String?) = current.set(value)
    actual fun clear() = current.remove()
}
