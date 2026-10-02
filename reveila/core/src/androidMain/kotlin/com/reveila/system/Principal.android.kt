package com.reveila.system

import java.io.Serializable

actual typealias Principal = java.security.Principal

actual class UserPrincipal actual constructor(name: String) : Principal, Serializable {
    private val name: String

    init {
        require(name.isNotBlank()) { "Argument 'name' cannot be null or empty." }
        this.name = name
    }

    actual override fun getName(): String = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPrincipal) return false
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = name

    companion object {
        private const val serialVersionUID = 1L
    }
}

actual class RolePrincipal actual constructor(name: String) : Principal, Serializable {
    private val name: String

    init {
        require(name.isNotBlank()) { "Argument 'name' cannot be null or empty." }
        this.name = name
    }

    actual override fun getName(): String = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RolePrincipal) return false
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = name

    companion object {
        private const val serialVersionUID = 1L
    }
}
