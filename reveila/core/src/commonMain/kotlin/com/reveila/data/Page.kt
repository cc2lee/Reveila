package com.reveila.data

import kotlin.math.ceil

/**
 * Represents a page of paginated content.
 */
data class Page<T>(
    val content: List<T> = emptyList(),
    val pageNumber: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val totalElements: Long? = null
) {
    init {
        require(pageSize >= 1) { "Page size must be at least 1" }
    }

    constructor(content: List<T>, pageNumber: Int, pageSize: Int, hasNext: Boolean) :
        this(content, pageNumber, pageSize, hasNext, null)

    fun isLast(): Boolean = !hasNext

    fun isFirst(): Boolean = pageNumber == 0

    fun getPagesCount(): Int {
        val total = totalElements ?: return -1
        return ceil(total.toDouble() / pageSize.toDouble()).toInt()
    }

    fun <U> map(converter: (T) -> U): Page<U> {
        val mappedContent = this.content.map(converter)
        return Page(mappedContent, pageNumber, pageSize, hasNext, totalElements)
    }

    fun content(): List<T> = content
    fun pageNumber(): Int = pageNumber
    fun pageSize(): Int = pageSize
    fun hasNext(): Boolean = hasNext
    fun totalElements(): Long? = totalElements
}
