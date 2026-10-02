package com.reveila.persistence

/**
 * Abstracts vector similarity search operations for semantic index/memory access.
 * Available across all native platforms (macOS/iOS, Windows, Linux) and JVM.
 */
interface VectorStore {

    /**
     * Stores a new vector along with its payload and identifier.
     *
     * @param id the unique identifier for the vector
     * @param vector the vector embeddings to store
     * @param payload associated metadata or text
     */
    fun insert(id: String, vector: FloatArray, payload: String)

    /**
     * Searches the vector store for the closest vectors to the given query.
     *
     * @param query the float array representing the query vector
     * @param limit the maximum number of matches to return
     * @return a list of VectorMatch representing the closest matches
     */
    fun search(query: FloatArray, limit: Int): List<VectorMatch>
}
