package com.reveila.system

import kotlin.reflect.KClass

/**
 * Pure Kotlin Multiplatform replacement for `javax.security.auth.Subject`.
 * Represents an entity/subject and its associated security principals.
 */
class Subject(initialPrincipals: Set<Principal> = emptySet()) {

    val principals: MutableSet<Principal> = LinkedHashSet(initialPrincipals)

    fun <T : Principal> getPrincipals(clazz: KClass<T>): Set<T> {
        val result = LinkedHashSet<T>()
        for (p in principals) {
            if (clazz.isInstance(p)) {
                @Suppress("UNCHECKED_CAST")
                result.add(p as T)
            }
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Subject) return false
        return principals == other.principals
    }

    override fun hashCode(): Int = principals.hashCode()

    override fun toString(): String = "Subject(principals=$principals)"
}
