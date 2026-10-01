package com.reveila.persistence;

import java.util.List;

/**
 * Abstracts vector similarity search operations for semantic index/memory access.
 * This ensures the application doesn't care which underlying Vector DB 
 * (e.g., pgvector, Chroma, Pinecone) is used.
 */
public interface VectorStore {

    /**
     * Stores a new vector along with its payload and identifier.
     * 
     * @param id the unique identifier for the vector
     * @param vector the vector embeddings to store
     * @param payload associated metadata or text
     */
    void insert(String id, float[] vector, String payload);

    /**
     * Searches the vector store for the closest vectors to the given query.
     *
     * @param query the float array representing the query vector
     * @param limit the maximum number of matches to return
     * @return a list of VectorMatch representing the closest matches
     */
    List<VectorMatch> search(float[] query, int limit);
}
