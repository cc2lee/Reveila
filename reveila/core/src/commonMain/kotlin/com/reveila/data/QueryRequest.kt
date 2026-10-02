package com.reveila.data

import kotlin.jvm.JvmStatic

/**
 * Standard query request envelope containing filters, sorting, and pagination specs.
 */
data class QueryRequest(
    val filter: Filter = Filter(),
    val sort: Sort = Sort.asc("id"),
    val fetches: List<String> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val includeCount: Boolean = false
) {
    fun filter(): Filter = filter
    fun sort(): Sort = sort
    fun fetches(): List<String> = fetches
    fun page(): Int = page
    fun size(): Int = size
    fun includeCount(): Boolean = includeCount

    companion object {
        @JvmStatic
        fun defaultPage(): QueryRequest = QueryRequest(
            filter = Filter(),
            sort = Sort.asc("id"),
            fetches = emptyList(),
            page = 0,
            size = 20,
            includeCount = false
        )
    }
}
