package com.reveila.data;

import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Represents a page of paginated content.
 * 
 * This record provides a flexible pagination model that supports both full
 * pagination (with total element count) and slice-based pagination (without total count).
 * 
 * @param <T>           the type of elements contained in this page
 * @param content       the list of elements in this page; never null (empty
 *                      list if not provided)
 * @param pageNumber    the zero-based index of this page
 * @param pageSize      the number of elements per page; must be at least 1
 * @param hasNext       whether there are more pages after this one
 * @param totalElements the total number of elements across all pages, or null
 *                      if unknown (useful for slice-based pagination where total count is
 *                      unavailable)
 * @see #isFirst()
 * @see #isLast()
 * @see #getPagesCount()
 */
public record Page<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        boolean hasNext,
        Long totalElements) {
    // 1. Overloaded Constructor
    // This allows the "Slice" pattern (no total count)
    public Page(List<T> content, int pageNumber, int pageSize, boolean hasNext) {
        this(content, pageNumber, pageSize, hasNext, null);
    }

    // 2. Compact Constructor (The "Gatekeeper")
    // This runs for ALL constructor calls (including the one above)
    public Page {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }

        // Validation and Normalization
        // Using List.copyOf ensures the record is truly immutable
        content = (content == null) ? List.of() : List.copyOf(content);
    }

    // 3. Convenience Methods
    public boolean isLast() {
        return !hasNext;
    }

    public boolean isFirst() {
        return pageNumber == 0;
    }

    public int getPagesCount() {
        if (totalElements == null)
            return -1;
        return (int) Math.ceil((double) totalElements / (double) pageSize);
    }

    /**
     * Maps the content of this page to a new type using the provided converter.
     * Use this to convert Entities to DTOs.
     */
    public <U> Page<U> map(Function<? super T, ? extends U> converter) {
        List<U> mappedContent = this.content.stream()
            .map(converter)
            .collect(Collectors.toList());

        // Return a new Page instance with the same metadata but new content type
        return new Page<>(mappedContent, pageNumber, pageSize, hasNext, totalElements);
    }

    public List<T> getContent() {
        return content;
    }
}