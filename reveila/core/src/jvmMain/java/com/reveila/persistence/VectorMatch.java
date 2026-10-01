package com.reveila.persistence;

/**
 * Represents a single result from a VectorStore search operation.
 */
public record VectorMatch(String id, float[] vector, double score, String payload) {
}
