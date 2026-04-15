package com.reveila.data;

import java.util.List;

public class SearchRequest {
    private String entityType;
    private Filter filter;
    private Sort sort;
    private List<String> fetches;
    private int page;
    private int size;
    private boolean includeCount;

    public SearchRequest() {
    }

    public SearchRequest(String entityType, Filter filter, Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
        this.entityType = entityType;
        this.filter = filter;
        this.sort = sort;
        this.fetches = fetches;
        this.page = page;
        this.size = size;
        this.includeCount = includeCount;
    }

    public String getEntityType() {
        return entityType;
    }
    
    public String entityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Filter getFilter() {
        return filter;
    }
    
    public Filter filter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public Sort getSort() {
        return sort;
    }
    
    public Sort sort() {
        return sort;
    }

    public void setSort(Sort sort) {
        this.sort = sort;
    }

    public List<String> getFetches() {
        return fetches;
    }
    
    public List<String> fetches() {
        return fetches;
    }

    public void setFetches(List<String> fetches) {
        this.fetches = fetches;
    }

    public int getPage() {
        return page;
    }
    
    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }
    
    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isIncludeCount() {
        return includeCount;
    }
    
    public boolean includeCount() {
        return includeCount;
    }

    public void setIncludeCount(boolean includeCount) {
        this.includeCount = includeCount;
    }
}