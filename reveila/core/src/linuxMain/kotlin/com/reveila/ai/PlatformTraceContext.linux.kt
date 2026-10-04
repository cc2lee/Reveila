package com.reveila.ai

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var currentTraceId: String? = null

actual object PlatformTraceContext {
    actual fun get(): String? = currentTraceId
    actual fun set(value: String?) { currentTraceId = value }
    actual fun clear() { currentTraceId = null }
}
