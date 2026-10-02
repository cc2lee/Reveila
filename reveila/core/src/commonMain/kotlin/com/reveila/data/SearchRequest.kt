package com.reveila.data

/**
 * Dynamic entity search request payload.
 */
open class SearchRequest(
    var entityType: String? = null,
    var filter: Filter? = null,
    var sort: Sort? = null,
    var fetches: List<String>? = null,
    var page: Int = 0,
    var size: Int = 0,
    var isIncludeCount: Boolean = false
) {
    constructor() : this(null, null, null, null, 0, 0, false)

    fun entityType(): String? = entityType
    fun filter(): Filter? = filter
    fun sort(): Sort? = sort
    fun fetches(): List<String>? = fetches
    fun page(): Int = page
    fun size(): Int = size
    fun includeCount(): Boolean = isIncludeCount
}
