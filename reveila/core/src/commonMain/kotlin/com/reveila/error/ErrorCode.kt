package com.reveila.error

/**
 * Common contract for exceptions carrying a structured error code.
 * Available across all native and JVM platforms.
 */
interface ErrorCode {
    val errorCode: String?
}
