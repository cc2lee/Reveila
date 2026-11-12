package com.reveila.safety

import kotlin.jvm.JvmOverloads

/**
 * Encapsulates a generic invocation request (function, method, or RPC call) and its arguments
 * to be audited and executed within a SecurityPerimeter.
 */
open class InvocationRequest @JvmOverloads constructor(
    open var functionName: String? = null,
    open var arguments: Any? = null
)
