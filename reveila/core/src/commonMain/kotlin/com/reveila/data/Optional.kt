package com.reveila.data

/**
 * Pure Kotlin Multiplatform replacement for `java.util.Optional`.
 */
class Optional<T : Any> private constructor(private val value: T?) {

    val isPresent: Boolean
        get() = value != null

    val isEmpty: Boolean
        get() = value == null

    fun get(): T = value ?: throw NoSuchElementException("No value present in Optional")

    fun orElse(other: T): T = value ?: other

    fun orElseGet(supplier: () -> T): T = value ?: supplier()

    fun <X : Throwable> orElseThrow(exceptionSupplier: () -> X): T = value ?: throw exceptionSupplier()

    fun orElseThrow(): T = get()

    fun <U : Any> map(mapper: (T) -> U?): Optional<U> {
        return if (value == null) empty() else ofNullable(mapper(value))
    }

    fun <U : Any> flatMap(mapper: (T) -> Optional<U>): Optional<U> {
        return if (value == null) empty() else mapper(value)
    }

    fun filter(predicate: (T) -> Boolean): Optional<T> {
        return if (value == null || !predicate(value)) empty() else this
    }

    fun ifPresent(action: (T) -> Unit) {
        if (value != null) {
            action(value)
        }
    }

    fun toNullable(): T? = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Optional<*>) return false
        return value == other.value
    }

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = if (value != null) "Optional[$value]" else "Optional.empty"

    companion object {
        private val EMPTY = Optional<Any>(null)

        @kotlin.jvm.JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> empty(): Optional<T> = EMPTY as Optional<T>

        @kotlin.jvm.JvmStatic
        fun <T : Any> of(value: T): Optional<T> {
            return Optional(value)
        }

        @kotlin.jvm.JvmStatic
        fun <T : Any> ofNullable(value: T?): Optional<T> {
            return if (value == null) empty() else Optional(value)
        }
    }
}
