package com.reveila.ai

import com.reveila.persistence.DefaultVectorStore
import com.reveila.persistence.VectorMatch
import com.reveila.persistence.VectorStore
import com.reveila.system.SystemComponent
import com.reveila.system.platform.PlatformSystem

/**
 * KnowledgeVault: The sovereign document and knowledge management component.
 * Integrates on-device text chunking, semantic embedding generation,
 * and vector similarity search.
 *
 * Consulted automatically by AgenticFabric during reasoning to augment prompts
 * with enterprise / local document context (RAG).
 */
open class KnowledgeVault : SystemComponent() {

    private val vectorStore: DefaultVectorStore = DefaultVectorStore()
    private val embeddingModel: ReveilaEmbeddingModel = DefaultEmbeddingModel(128)

    // Tracks indexed documents: docId -> DocumentMeta
    private val documentIndex = mutableMapOf<String, DocumentMeta>()
    private val lock = Any()

    data class DocumentMeta(
        val id: String,
        val title: String,
        val chunkCount: Int,
        val length: Int,
        val timestamp: Long
    )

    override fun onStart() {
        logger.info("KnowledgeVault started. Ready for document embeddings and semantic retrieval.")
    }

    override fun onStop() {
        logger.info("KnowledgeVault stopped.")
    }

    /**
     * Indexes a document into the vector store.
     * Breaks text into semantic chunks and generates embeddings for each chunk.
     */
    open fun indexDocument(id: String, content: String, metadata: Map<String, Any?> = emptyMap()): Boolean {
        if (id.isBlank() || content.isBlank()) {
            return false
        }

        val chunks = chunkText(content, 600, 100)
        synchronized(lock) {
            // Remove previous chunks for this doc
            val previousMeta = documentIndex[id]
            if (previousMeta != null) {
                for (i in 0 until previousMeta.chunkCount) {
                    vectorStore.delete("${id}_chunk_$i")
                }
            }

            for ((i, chunk) in chunks.withIndex()) {
                val chunkId = "${id}_chunk_$i"
                val vector = embeddingModel.embed(chunk)
                val payload = "[$id] $chunk"
                vectorStore.insert(chunkId, vector, payload)
            }

            val title = metadata["title"]?.toString() ?: id
            documentIndex[id] = DocumentMeta(
                id = id,
                title = title,
                chunkCount = chunks.size,
                length = content.length,
                timestamp = PlatformSystem.currentTimeMillis()
            )
        }

        logger.info("Indexed document [$id] (${chunks.size} chunks, ${content.length} chars) into vector store.")
        return true
    }

    /**
     * Semantic search over the indexed documents.
     * Returns a formatted text snippet summary suitable for prompt context injection.
     */
    open fun search(query: String, limit: Int = 3): String {
        if (query.isBlank()) {
            return "No relevant internal documents found."
        }

        val queryVector = embeddingModel.embed(query)
        val matches = vectorStore.search(queryVector, limit)

        if (matches.isEmpty()) {
            return "No relevant internal documents found."
        }

        val sb = StringBuilder()
        for ((idx, match) in matches.withIndex()) {
            if (match.score > 0.25) {
                sb.append("\n[Doc ${idx + 1} (relevance: ${(match.score * 100).toInt()}%)]: ")
                sb.append(match.payload.trim())
            }
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else "No relevant internal documents found."
    }

    open fun searchMatches(query: String, limit: Int = 5): List<Map<String, Any?>> {
        val queryVector = embeddingModel.embed(query)
        val matches = vectorStore.search(queryVector, limit)
        return matches.map { match ->
            mapOf(
                "id" to match.id,
                "score" to match.score,
                "payload" to match.payload
            )
        }
    }

    open fun getDocumentCount(): Int {
        synchronized(lock) {
            return documentIndex.size
        }
    }

    open fun getChunkCount(): Int {
        return vectorStore.count()
    }

    open fun getDocuments(): List<Map<String, Any?>> {
        synchronized(lock) {
            return documentIndex.values.map { meta ->
                mapOf(
                    "id" to meta.id,
                    "title" to meta.title,
                    "chunkCount" to meta.chunkCount,
                    "length" to meta.length,
                    "timestamp" to meta.timestamp
                )
            }
        }
    }

    open fun clear(): Boolean {
        synchronized(lock) {
            documentIndex.clear()
            vectorStore.clear()
        }
        logger.info("KnowledgeVault cleared.")
        return true
    }

    open fun getVectorStore(): VectorStore = vectorStore

    open fun getEmbeddingModel(): ReveilaEmbeddingModel = embeddingModel

    /**
     * Splits long text into chunks with overlap for contextual integrity.
     */
    private fun chunkText(text: String, chunkSize: Int = 600, overlap: Int = 100): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text.trim())
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + chunkSize, text.length)
            // Try not to cut words in half
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end)
                if (lastSpace > start + chunkSize / 2) {
                    end = lastSpace
                }
            }

            val chunk = text.substring(start, end).trim()
            if (chunk.isNotBlank()) {
                chunks.add(chunk)
            }

            if (end >= text.length) break
            start = maxOf(start + 1, end - overlap)
        }

        return if (chunks.isEmpty()) listOf(text.trim()) else chunks
    }
}
