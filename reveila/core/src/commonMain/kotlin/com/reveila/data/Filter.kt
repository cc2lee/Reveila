package com.reveila.data

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Filter specification with conditions and logical operators.
 */
open class Filter {
    enum class LogicalOp { AND, OR }
    enum class SearchOp { EQUAL, LIKE, IN, GREATER_THAN, LESS_THAN }

    data class Criterion(
        val value: Any?,
        val operator: SearchOp
    ) {
        fun value(): Any? = value
        fun operator(): SearchOp = operator

        companion object {
            @JvmStatic
            fun equal(value: Any?): Criterion = Criterion(value, SearchOp.EQUAL)

            @JvmStatic
            fun like(value: String): Criterion = Criterion(value, SearchOp.LIKE)
        }
    }

    val conditions: MutableMap<String, Criterion>
    var logicalOp: LogicalOp = LogicalOp.AND

    constructor() {
        this.conditions = mutableMapOf()
    }

    constructor(conditions: Map<String, Criterion>) {
        this.conditions = conditions.toMutableMap()
    }

    constructor(logicalOp: LogicalOp) : this() {
        this.logicalOp = logicalOp
    }

    @JvmOverloads
    fun add(field: String, value: Any?, op: SearchOp = SearchOp.EQUAL): Filter {
        this.conditions[field] = Criterion(value, op)
        return this
    }
}
