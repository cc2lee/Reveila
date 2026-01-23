package com.reveila.data;

import java.util.HashMap;
import java.util.Map;

public class Filter {
    public enum LogicalOp { AND, OR }
    public enum SearchOp { EQUAL, LIKE, IN, GREATER_THAN, LESS_THAN }

    public record Criterion(Object value, SearchOp operator) {}

    private final Map<String, Criterion> conditions = new HashMap<>();
    private LogicalOp logicalOp = LogicalOp.AND;

    public Filter() {}

    public Filter(LogicalOp logicalOp) {
        this.logicalOp = logicalOp;
    }

    public Filter add(String field, Object value, SearchOp op) {
        this.conditions.put(field, new Criterion(value, op));
        return this;
    }

    // Default to EQUAL for backward compatibility
    public Filter add(String field, Object value) {
        return add(field, value, SearchOp.EQUAL);
    }

    public Map<String, Criterion> getConditions() { return conditions; }
    public LogicalOp getLogicalOp() { return logicalOp; }
}