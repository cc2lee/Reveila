package com.reveila.data;

import java.util.List;

public record SearchRequest(
    String entityType,
    Filter filter,
    Sort sort,
    List<String> fetches,
    int page,
    int size,
    boolean includeCount
) {}