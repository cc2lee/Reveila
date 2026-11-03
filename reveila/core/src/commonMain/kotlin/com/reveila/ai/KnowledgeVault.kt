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
    // Tracks raw document content by docId for keyword & priority focus matching
    private val documentContents = mutableMapOf<String, String>()
    private var focusKeywords: List<String> = listOf("strategy", "confidential", "roadmap", "architectural")
    private val lock = Any()

    data class DocumentMeta(
        val id: String,
        val title: String,
        val chunkCount: Int,
        val length: Int,
        val timestamp: Long
    )

    override fun onStart() {
        val propKeywords = context?.properties?.getProperty("vault.focus.keywords")
        if (!propKeywords.isNullOrBlank()) {
            setFocusKeywords(propKeywords)
        }
        logger.info("KnowledgeVault started. Ready for document embeddings and semantic retrieval.")
    }

    override fun onStop() {
        logger.info("KnowledgeVault stopped.")
    }

    open fun setFocusKeywords(keywordsCsv: String): Boolean {
        synchronized(lock) {
            focusKeywords = keywordsCsv
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
        }
        logger.info("Updated priority focus keywords: $focusKeywords")
        return true
    }

    open fun getFocusKeywords(): String {
        synchronized(lock) {
            return focusKeywords.joinToString(", ")
        }
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
            documentContents[id] = content
        }

        logger.info("Indexed document [$id] (${chunks.size} chunks, ${content.length} chars) into vector store.")
        return true
    }

    /**
     * Semantic + lexical + priority-keyword search over the indexed documents.
     * Always includes documents that match the configured Priority Focus Keywords,
     * plus the top semantically and lexically relevant chunks for the user's query.
     */
    open fun search(query: String, limit: Int = 3): String {
        if (query.isBlank() || vectorStore.count() == 0) {
            return "No relevant internal documents found."
        }

        val queryVector = embeddingModel.embed(query)
        val allMatches = vectorStore.search(queryVector, maxOf(limit * 4, 20))
        if (allMatches.isEmpty()) {
            return "No relevant internal documents found."
        }

        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "about",
            "what", "which", "who", "whom", "whose", "where", "when", "why", "how",
            "can", "could", "would", "should", "do", "does", "did", "have", "has",
            "had", "my", "your", "our", "their", "this", "that", "these", "those",
            "me", "tell", "give", "show", "please", "and", "or", "but", "if"
        )
        val queryTokens = query.lowercase()
            .split(Regex("[^a-z0-9_-]+"))
            .filter { it.length >= 2 && it !in stopWords }

        val activeKeywords: List<String>
        synchronized(lock) {
            activeKeywords = focusKeywords.toList()
        }

        data class RankedChunk(
            val match: VectorMatch,
            val combinedScore: Double,
            val isPriorityKeywordMatch: Boolean
        )

        val ranked = allMatches.map { match ->
            val payloadLower = match.payload.lowercase()
            val matchedQueryTokens = if (queryTokens.isEmpty()) {
                0
            } else {
                queryTokens.count { token -> payloadLower.contains(token) }
            }
            val lexicalBoost = if (queryTokens.isEmpty()) {
                0.0
            } else {
                (matchedQueryTokens.toDouble() / queryTokens.size.toDouble()) * 0.45
            }

            val hasPriorityKeyword = activeKeywords.any { kw -> kw.isNotBlank() && payloadLower.contains(kw) }
            val priorityBoost = if (hasPriorityKeyword) 0.20 else 0.0

            val combined = (match.score.coerceAtLeast(0.0) + lexicalBoost + priorityBoost).coerceAtMost(0.99)
            RankedChunk(match, combined, hasPriorityKeyword)
        }.sortedByDescending { it.combinedScore }

        val selected = mutableListOf<RankedChunk>()
        // 1. Take top ranked matches up to limit
        for (item in ranked) {
            if (selected.size < limit) {
                selected.add(item)
            }
        }
        // 2. Ensure documents containing Priority Focus Keywords are always included
        for (item in ranked) {
            if (item.isPriorityKeywordMatch && selected.none { it.match.id == item.match.id } && selected.size < limit + 2) {
                selected.add(item)
            }
        }

        if (selected.isEmpty()) {
            return "No relevant internal documents found."
        }

        val sb = StringBuilder()
        for ((idx, item) in selected.withIndex()) {
            val pct = maxOf((item.combinedScore * 100).toInt(), 15)
            val tag = if (item.isPriorityKeywordMatch) " [Priority Keyword Match]" else ""
            sb.append("\n[Doc ${idx + 1}$tag (relevance: $pct%)]: ")
            sb.append(item.match.payload.trim())
        }

        return sb.toString().trim()
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
            documentContents.clear()
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
