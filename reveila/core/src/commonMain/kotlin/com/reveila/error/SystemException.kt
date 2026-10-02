package com.reveila.error

/**
 * Root checked exception for the Reveila engine.
 * Multiplatform implementation replacing legacy Java SystemException.
 */
open class SystemException(
    message: String? = null,
    cause: Throwable? = null,
    override val errorCode: String? = null
) : Exception(message, cause), ErrorCode {

    constructor(message: String?) : this(message, null, null)

    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
}
