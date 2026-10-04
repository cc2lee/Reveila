package com.reveila.ai

/**
 * Native interface for generating vector embeddings.
 */
interface ReveilaEmbeddingModel {
    /**
     * Generates a vector representation of the given text.
     */
    fun embed(text: String): FloatArray
}
