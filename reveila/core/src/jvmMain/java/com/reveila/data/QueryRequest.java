package com.reveila.data;

import java.util.List;

public record QueryRequest(
    Filter filter,
    Sort sort,
    List<String> fetches,
    int page,
    int size,
    boolean includeCount
) {
    // Default constructor for standard requests
    public static QueryRequest defaultPage() {
        return new QueryRequest(new Filter(), Sort.asc("id"), List.of(), 0, 20, false);
    }
}