package com.reveila.error

/**
 * Thrown when system configuration, properties, or component parameters are invalid.
 */
open class ConfigurationException(
    message: String? = null,
    cause: Throwable? = null,
    errorCode: String? = null
) : SystemException(message, cause, errorCode) {

    constructor(message: String?) : this(message, null, null)

    constructor(message: String?, cause: Throwable?) : this(message, cause, null)
}
