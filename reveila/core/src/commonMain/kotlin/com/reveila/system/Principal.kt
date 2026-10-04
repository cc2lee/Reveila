package com.reveila.system

/**
 * Multiplatform sovereign identity principal.
 */
interface Principal {
    val name: String
}

open class UserPrincipal(override val name: String) : Principal {
    init {
        require(name.isNotBlank()) { "Argument 'name' cannot be null or empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPrincipal) return false
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = name
}

open class RolePrincipal(override val name: String) : Principal {
    init {
        require(name.isNotBlank()) { "Argument 'name' cannot be null or empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RolePrincipal) return false
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = name
}
