package com.reveila.data

import kotlin.jvm.JvmStatic

/**
 * Sort criteria for repository queries.
 */
data class Sort(
    val field: String,
    val ascending: Boolean = true
) {
    fun field(): String = field
    fun ascending(): Boolean = ascending

    companion object {
        @JvmStatic
        fun asc(field: String): Sort = Sort(field, true)

        @JvmStatic
        fun desc(field: String): Sort = Sort(field, false)
    }
}
