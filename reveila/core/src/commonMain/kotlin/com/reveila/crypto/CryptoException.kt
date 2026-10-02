package com.reveila.crypto

import com.reveila.error.SystemException

open class CryptoException : SystemException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(message: String, cause: Throwable?, errorCode: String?) : super(message, cause, errorCode)
}
