package com.reveila.error

/**
 * Thrown when a client's requested API/system version conflicts with the
 * currently deployed backend version.
 */
class VersionConflictException(
    val clientVersion: String?,
    val backendVersion: String,
    message: String = "API version conflict: client version '${clientVersion ?: "missing"}' is incompatible with deployed backend version '$backendVersion'."
) : SystemException(message)
