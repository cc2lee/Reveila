package com.reveila.data;

import java.util.HashMap;
import java.util.Map;

public class Filter {
    public enum LogicalOp { AND, OR }
    public enum SearchOp { EQUAL, LIKE, IN, GREATER_THAN, LESS_THAN }

    // Aligned to use SearchOp
    public record Criterion(Object value, SearchOp operator) {
        public static Criterion equal(Object value) {
            return new Criterion(value, SearchOp.EQUAL);
        }

        public static Criterion like(String value) {
            return new Criterion(value, SearchOp.LIKE);
        }
    }

    private final Map<String, Criterion> conditions;
    private LogicalOp logicalOp = LogicalOp.AND;

    public Filter() {
        this.conditions = new HashMap<>();
    }

    public Filter(Map<String, Criterion> conditions) {
        this.conditions = new HashMap<>(conditions);
    }

    public Filter(LogicalOp logicalOp) {
        this();
        this.logicalOp = logicalOp;
    }

    public Filter add(String field, Object value, SearchOp op) {
        this.conditions.put(field, new Criterion(value, op));
        return this;
    }

    public Filter add(String field, Object value) {
        return add(field, value, SearchOp.EQUAL);
    }

    public Map<String, Criterion> getConditions() { return conditions; }
    public LogicalOp getLogicalOp() { return logicalOp; }
}