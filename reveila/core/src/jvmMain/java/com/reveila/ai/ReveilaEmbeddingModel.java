package com.reveila.ai;

/**
 * Native interface for generating vector embeddings.
 */
public interface ReveilaEmbeddingModel {
    /**
     * Generates a vector representation of the given text.
     */
    float[] embed(String text);
}
