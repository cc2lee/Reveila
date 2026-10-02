package com.reveila.system

actual interface Principal {
    actual fun getName(): String
}

actual class UserPrincipal actual constructor(name: String) : Principal {
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
}

actual class RolePrincipal actual constructor(name: String) : Principal {
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
}
