package com.reveila.error

/**
 * Thrown when an authorization, perimeter, or sovereign security policy violation occurs.
 */
open class SecurityException(
    message: String? = null,
    cause: Throwable? = null,
    override val errorCode: String? = null
) : RuntimeException(message, cause), ErrorCode {

    constructor(message: String?) : this(message, null, null)

    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
}
