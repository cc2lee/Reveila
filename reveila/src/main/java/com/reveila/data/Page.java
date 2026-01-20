package com.reveila.data;

import java.util.Collections;
import java.util.List;

/**
 * A DTO for returning paginated data from a service.
 * This is a generic class that can hold a page of any type of object.
 *
 * @param content The list of items on the current page.
 * @param pageNumber The current page number (0-based).
 * @param pageSize The number of items per page.
 * @param totalElements The total number of items across all pages.
 * @param <T> The type of the content in the page.
 */
public record Page<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements
) {
    public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
        this.content = content == null ? Collections.emptyList() : Collections.unmodifiableList(content);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) pageSize);
    }

    public boolean isLast() {
        return pageNumber + 1 >= getTotalPages();
    }
}