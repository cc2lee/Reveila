package com.reveila.ai

import com.reveila.data.Entity
import com.reveila.persistence.DefaultVectorStore
import com.reveila.persistence.VectorMatch
import com.reveila.persistence.VectorStore
import com.reveila.service.DataService
import com.reveila.service.EntityMutationListener
import com.reveila.service.MutationType
import com.reveila.system.SystemComponent
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.platform.PlatformSystem

/**
 * KnowledgeVault: The sovereign document and knowledge management component.
 * Integrates on-device text chunking, semantic embedding generation,
 * and vector similarity search.
 *
 * Consulted automatically by AgenticFabric during reasoning to augment prompts
 * with enterprise / local document context (RAG).
 */
open class KnowledgeVault(
    private var vectorStore: VectorStore = DefaultVectorStore(),
    private val embeddingModel: ReveilaEmbeddingModel = DefaultEmbeddingModel(128)
) : SystemComponent() {

    // Tracks indexed documents: docId -> DocumentMeta
    private val documentIndex = mutableMapOf<String, DocumentMeta>()
    // Tracks raw document content by docId for keyword & priority focus matching
    private val documentContents = mutableMapOf<String, String>()
    // Tracks content hash by docId to avoid redundant re-embeddings
    private val documentHashes = mutableMapOf<String, Int>()
    private var focusKeywords: List<String> = listOf("strategy", "confidential", "roadmap", "architectural")
    private var searchScopePaths: List<String> = emptyList()
    private val lock = PlatformLock()

    data class DocumentMeta(
        val id: String,
        val title: String,
        val relativePath: String,
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
        lock.withLock {
            focusKeywords = keywordsCsv
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
        }
        logger.info("Updated priority focus keywords: $focusKeywords")
        return true
    }

    open fun getFocusKeywords(): String {
        return lock.withLock {
            focusKeywords.joinToString(", ")
        }
    }

    /**
     * Swaps the active vector store implementation (e.g. DefaultVectorStore vs PgVectorStore).
     */
    open fun setVectorStore(store: VectorStore) {
        lock.withLock {
            this.vectorStore = store
        }
        logger.info("KnowledgeVault vector store updated to: ${store::class.simpleName}")
    }

    /**
     * Indexes a document into the vector store.
     * Breaks text into semantic chunks and generates embeddings for each chunk.
     * Checks content hash to eliminate redundant re-embeddings when text is unchanged.
     */
    open fun indexDocument(id: String, content: String, metadata: Map<String, Any?> = emptyMap()): Boolean {
        if (id.isBlank() || content.isBlank()) {
            return false
        }

        val contentHash = content.hashCode()
        val skipped = lock.withLock {
            if (documentHashes[id] == contentHash && documentIndex.containsKey(id)) {
                logger.debug("Skipping re-indexing of unmodified document [$id]")
                return@withLock true
            }

            val chunks = chunkText(content, 600, 100)
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
            val relativePath = (metadata["relativePath"]?.toString() ?: id).replace('\\', '/').trimStart('/')
            documentIndex[id] = DocumentMeta(
                id = id,
                title = title,
                relativePath = relativePath,
                chunkCount = chunks.size,
                length = content.length,
                timestamp = PlatformSystem.currentTimeMillis()
            )
            documentContents[id] = content
            documentHashes[id] = contentHash

            logger.info("Indexed document [$id] (${chunks.size} chunks, ${content.length} chars) into vector store.")
            false
        }

        if (skipped) return true
        return true
    }

    /**
     * Removes an indexed document and all of its associated vector chunks from the store.
     */
    open fun removeDocument(id: String): Boolean {
        if (id.isBlank()) return false
        return lock.withLock {
            val meta = documentIndex.remove(id)
            documentContents.remove(id)
            documentHashes.remove(id)
            if (meta != null) {
                for (i in 0 until meta.chunkCount) {
                    vectorStore.delete("${id}_chunk_$i")
                }
                logger.info("Removed document [$id] (${meta.chunkCount} chunks) from vector store.")
                true
            } else {
                vectorStore.delete(id)
                false
            }
        }
    }

    /**
     * Attaches an EntityMutationListener to DataService to automatically keep vector embeddings
     * synchronized with relational / edge document mutations (Dual Index Synchronization).
     */
    open fun attachDataService(
        dataService: DataService,
        targetEntityTypes: Set<String> = setOf("document", "knowledge", "note", "article", "doc")
    ): EntityMutationListener {
        val listener = EntityMutationListener { mutation ->
            if (mutation.entityType.lowercase() in targetEntityTypes) {
                when (mutation.type) {
                    MutationType.SAVE -> {
                        mutation.entity?.let { entity ->
                            val docId = extractEntityId(entity)
                            val content = extractEntityContent(entity)
                            if (docId.isNotBlank() && content.isNotBlank()) {
                                indexDocument(docId, content, entity.attributes)
                            }
                        }
                    }
                    MutationType.DELETE -> {
                        val docId = extractKeyId(mutation.key)
                        if (!docId.isNullOrBlank()) {
                            removeDocument(docId)
                        }
                    }
                }
            }
        }
        dataService.addMutationListener(listener)
        logger.info("KnowledgeVault attached to DataService for entity types: $targetEntityTypes")
        return listener
    }

    private fun extractEntityId(entity: Entity): String {
        val keyId = extractKeyId(entity.key)
        if (!keyId.isNullOrBlank()) return keyId

        return entity.attributes["id"]?.toString()
            ?: entity.attributes["docId"]?.toString()
            ?: entity.attributes["title"]?.toString()
            ?: ""
    }

    private fun extractKeyId(key: Map<String, Map<String, Any>>?): String? {
        if (key == null) return null
        for (group in key.values) {
            val idVal = group["id"] ?: group["docId"] ?: group["key"]
            if (idVal != null) return idVal.toString()
        }
        return null
    }

    private fun extractEntityContent(entity: Entity): String {
        return entity.attributes["content"]?.toString()
            ?: entity.attributes["text"]?.toString()
            ?: entity.attributes["body"]?.toString()
            ?: entity.attributes["payload"]?.toString()
            ?: ""
    }

    /**
     * Restricts subsequent [search] calls to the given newline/comma-separated scope entries
     * (`__ROOT__` for root-only files, `folder/` prefix for a subfolder tree, or exact relative file path).
     * Passing blank clears any scope filter.
     */
    open fun setSearchScope(scopeSpec: String): Boolean {
        val entries = scopeSpec
            .split('\n', ',', ';')
            .map { it.trim().replace('\\', '/') }
            .filter { it.isNotEmpty() }
            .distinct()
        lock.withLock {
            searchScopePaths = entries
        }
        return true
    }

    open fun getSearchScope(): List<String> = lock.withLock { searchScopePaths.toList() }

    /**
     * Returns all distinct indexed folder prefixes (e.g. "expenses/", "reports/q4/", or "__ROOT__").
     */
    open fun listIndexedFolders(): List<String> = lock.withLock {
        val folders = linkedSetOf<String>()
        for (meta in documentIndex.values) {
            val norm = meta.relativePath.replace('\\', '/').trimStart('/')
            val lastSlash = norm.lastIndexOf('/')
            if (lastSlash < 0) {
                folders.add("__ROOT__")
            } else {
                val parts = norm.substring(0, lastSlash).split('/').filter { it.isNotBlank() }
                var prefix = ""
                for (part in parts) {
                    prefix += "$part/"
                    folders.add(prefix)
                }
            }
        }
        folders.toList().sorted()
    }

    /**
     * Lists all indexed documents matching [scopePaths] (or active [searchScopePaths] when empty).
     */
    open fun listDocumentsInScope(scopePaths: List<String> = emptyList()): List<DocumentMeta> = lock.withLock {
        val effectiveScope = if (scopePaths.isNotEmpty()) scopePaths else searchScopePaths
        documentIndex.values
            .filter { meta -> docMatchesScope(meta.relativePath, effectiveScope) || docMatchesScope(meta.id, effectiveScope) }
            .sortedBy { it.relativePath.lowercase() }
    }

    /**
     * Reads the full indexed text of a single document by exact ID, relative path, or filename.
     */
    open fun readDocumentContent(docIdOrPath: String, maxChars: Int = 8000): String {
        val needle = docIdOrPath.trim().replace('\\', '/').trimStart('/')
        if (needle.isBlank()) return "Error: document path is required."
        return lock.withLock {
            val matchedMeta = documentIndex[needle]
                ?: documentIndex.values.firstOrNull {
                    it.relativePath.equals(needle, ignoreCase = true) ||
                        it.id.equals(needle, ignoreCase = true)
                }
                ?: documentIndex.values.firstOrNull {
                    it.relativePath.endsWith("/$needle", ignoreCase = true) ||
                        it.title.equals(needle, ignoreCase = true)
                }
            if (matchedMeta == null) {
                "Document not found in Knowledge Vault: '$docIdOrPath'."
            } else {
                val body = documentContents[matchedMeta.id].orEmpty()
                val clipped = if (body.length > maxChars) {
                    body.take(maxChars) + "\n...[truncated ${body.length - maxChars} chars]"
                } else {
                    body
                }
                "[File: ${matchedMeta.relativePath} (${matchedMeta.length} chars)]\n$clipped"
            }
        }
    }

    /**
     * Auto-infers indexed subfolder scopes referenced in a user's general prompt
     * (e.g. "in my expenses folder", "expenses/", or "bills" matching indexed folder "finance/bills/").
     */
    open fun inferFolderScopesFromPrompt(prompt: String): List<String> {
        val lowerPrompt = prompt.lowercase().replace('\\', '/')
        if (lowerPrompt.isBlank()) return emptyList()
        val indexedFolders = listIndexedFolders().filter { it != "__ROOT__" }
        if (indexedFolders.isEmpty()) return emptyList()

        val promptWords = lowerPrompt.split(Regex("[^a-z0-9_-]+")).filter { it.length >= 3 }.toSet()
        val matched = linkedSetOf<String>()
        for (folder in indexedFolders) {
            val cleanFolder = folder.trimEnd('/').lowercase()
            val leaf = cleanFolder.substringAfterLast('/')
            val exactPathMentioned = lowerPrompt.contains(cleanFolder) || lowerPrompt.contains("$cleanFolder/")
            val leafFolderMentioned = leaf.length >= 3 && (
                leaf in promptWords ||
                    (leaf.endsWith("s") && leaf.dropLast(1) in promptWords && lowerPrompt.contains("folder"))
                )
            if (exactPathMentioned || leafFolderMentioned) {
                matched.add(folder)
            }
        }
        // Prefer more specific nested folder if both parent and child matched
        return matched.filter { candidate ->
            matched.none { other -> other != candidate && other.startsWith(candidate, ignoreCase = true) }
        }
    }

    /**
     * Detects aggregation, ranking, comparison, or relationship / cause-effect queries
     * that require inspecting every file in a folder rather than top-3 similarity chunks.
     */
    open fun isAnalyticalOrFolderWideQuery(query: String): Boolean {
        val lower = query.lowercase()
        val analyticalSignals = listOf(
            "top ", "total", "sum ", "spend", "spent", "expense", "expenses", "cost", "costs",
            "bill", "bills", "invoice", "invoices", "receipt", "receipts", "budget",
            "compare", "comparison", "relationship", "relationships", "cause", "effect", "causal",
            "why did", "correlat", "trend", "trends", "pattern", "patterns", "breakdown",
            "rank", "highest", "largest", "biggest", "most expensive", "all files", "every file",
            "across", "contradict", "timeline", "analyze", "analysis", "audit"
        )
        return analyticalSignals.any { lower.contains(it) }
    }

    /**
     * Builds an exhaustive multi-file dossier across EVERY file in [scopePaths] (or active scope),
     * dynamically budgeting per-file characters so all files are represented for aggregation
     * and relationship / cause-effect reasoning.
     */
    open fun buildFolderDossier(
        scopePaths: List<String> = emptyList(),
        maxTotalChars: Int = 24_000,
        maxPerFileChars: Int = 3_200
    ): String {
        val docs = listDocumentsInScope(scopePaths)
        if (docs.isEmpty()) {
            return "No indexed files found in the requested folder scope."
        }
        val scopeLabel = (if (scopePaths.isNotEmpty()) scopePaths else getSearchScope())
            .joinToString(", ")
            .ifBlank { "All Indexed Files" }
        val perFileBudget = (maxTotalChars / docs.size).coerceIn(450, maxPerFileChars)

        val sb = StringBuilder()
        sb.appendLine("[Exhaustive Folder Analysis Scope: $scopeLabel | Total Files: ${docs.size}]")
        sb.appendLine("File Manifest: ${docs.joinToString(", ") { it.relativePath }}")

        var usedChars = sb.length
        for ((idx, meta) in docs.withIndex()) {
            val content = lock.withLock { documentContents[meta.id].orEmpty() }.trim()
            val remaining = (maxTotalChars - usedChars).coerceAtLeast(0)
            if (remaining < 120) {
                sb.appendLine("\n[File ${idx + 1}/${docs.size}: ${meta.relativePath}] (omitted due to context budget; use knowledge_vault read_file to inspect)")
                continue
            }
            val sliceLen = minOf(perFileBudget, remaining)
            val snippet = if (content.length > sliceLen) {
                content.take(sliceLen) + " ...[+${content.length - sliceLen} chars]"
            } else {
                content
            }
            val block = "\n[File ${idx + 1}/${docs.size}: ${meta.relativePath}]\n$snippet\n"
            sb.append(block)
            usedChars += block.length
        }
        return sb.toString().trim()
    }

    private fun docMatchesScope(docId: String, scopeEntries: List<String>): Boolean {
        if (scopeEntries.isEmpty()) return true
        val normDoc = docId.replace('\\', '/').trimStart('/')
        return scopeEntries.any { raw ->
            val entry = raw.replace('\\', '/').trimStart('/')
            when {
                entry == "__ROOT__" -> !normDoc.contains('/')
                entry.endsWith('/') -> normDoc.startsWith(entry, ignoreCase = true)
                else -> normDoc.equals(entry, ignoreCase = true) ||
                    normDoc.startsWith("$entry/", ignoreCase = true)
            }
        }
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

        val activeScope: List<String> = lock.withLock {
            searchScopePaths.toList()
        }
        val effectiveScope = activeScope.ifEmpty { inferFolderScopesFromPrompt(query) }

        if (effectiveScope.isNotEmpty() && isAnalyticalOrFolderWideQuery(query)) {
            val dossier = buildFolderDossier(effectiveScope)
            if (!dossier.startsWith("No indexed files")) {
                return dossier
            }
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

        val overviewKeywords = setOf(
            "vault", "document", "documents", "doc", "docs", "file", "files",
            "note", "notes", "workspace",
            "folder", "indexed", "overview", "summarize", "summary", "list", "contents"
        )
        val isVaultOverviewQuery = queryTokens.any { it in overviewKeywords } || effectiveScope.isNotEmpty()

        val searchWindow = if (isVaultOverviewQuery || effectiveScope.isNotEmpty()) {
            maxOf(vectorStore.count(), 100)
        } else {
            maxOf(limit * 4, 20)
        }

        val queryVector = embeddingModel.embed(query)
        val rawMatches = vectorStore.search(queryVector, searchWindow)
        val allMatches = if (effectiveScope.isEmpty()) {
            rawMatches
        } else {
            rawMatches.filter { match ->
                val docId = match.id.substringBeforeLast("_chunk_", match.id)
                docMatchesScope(docId, effectiveScope)
            }
        }
        if (allMatches.isEmpty()) {
            return "No relevant internal documents found."
        }

        val activeKeywords: List<String> = lock.withLock {
            focusKeywords.toList()
        }

        data class RankedChunk(
            val match: VectorMatch,
            val combinedScore: Double,
            val isPriorityKeywordMatch: Boolean,
            val matchedQueryTokens: Int,
            val passesRelevanceGate: Boolean
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
            val queryMentionsPriority = activeKeywords.any { kw -> kw.isNotBlank() && query.lowercase().contains(kw) }
            val passesGate = isVaultOverviewQuery ||
                matchedQueryTokens >= 1 ||
                match.score >= 0.40 ||
                (hasPriorityKeyword && queryMentionsPriority)

            val priorityBoost = if (hasPriorityKeyword && (passesGate || isVaultOverviewQuery)) 0.20 else 0.0

            val combined = (match.score.coerceAtLeast(0.0) + lexicalBoost + priorityBoost).coerceAtMost(0.99)
            RankedChunk(match, combined, hasPriorityKeyword, matchedQueryTokens, passesGate)
        }.sortedByDescending { it.combinedScore }

        val relevantRanked = ranked.filter { it.passesRelevanceGate }
        if (relevantRanked.isEmpty()) {
            return "No relevant internal documents found."
        }

        val selected = mutableListOf<RankedChunk>()
        if (isVaultOverviewQuery) {
            val maxOverviewChunks = maxOf(limit * 3, 10)
            val seenDocIds = mutableSetOf<String>()
            // 1. Include the top chunk from each distinct indexed document first
            for (item in relevantRanked) {
                if (selected.size >= maxOverviewChunks) break
                val docId = item.match.id.substringBeforeLast("_chunk_", item.match.id)
                if (seenDocIds.add(docId)) {
                    selected.add(item)
                }
            }
            // 2. Fill remaining slots with top ranked/priority chunks
            for (item in relevantRanked) {
                if (selected.size >= maxOverviewChunks) break
                if (selected.none { it.match.id == item.match.id }) {
                    selected.add(item)
                }
            }
        } else {
            // 1. Take top relevant ranked matches up to limit
            for (item in relevantRanked) {
                if (selected.size < limit) {
                    selected.add(item)
                }
            }
            // 2. Ensure relevant documents containing Priority Focus Keywords are included
            for (item in relevantRanked) {
                if (item.isPriorityKeywordMatch && selected.none { it.match.id == item.match.id } && selected.size < limit + 2) {
                    selected.add(item)
                }
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
        return lock.withLock {
            documentIndex.size
        }
    }

    open fun getChunkCount(): Int {
        return vectorStore.count()
    }

    open fun getDocuments(): List<Map<String, Any?>> {
        return lock.withLock {
            documentIndex.values
                .sortedBy { it.relativePath.lowercase() }
                .map { meta ->
                    mapOf(
                        "id" to meta.id,
                        "title" to meta.title,
                        "relativePath" to meta.relativePath,
                        "chunkCount" to meta.chunkCount,
                        "length" to meta.length,
                        "timestamp" to meta.timestamp
                    )
                }
        }
    }

    open fun getDocumentsJson(): String {
        val docs = getDocuments()
        fun esc(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val items = docs.joinToString(",") { meta ->
            val id = esc(meta["id"]?.toString().orEmpty())
            val title = esc(meta["title"]?.toString().orEmpty())
            val relPath = esc(meta["relativePath"]?.toString().orEmpty())
            val chunkCount = (meta["chunkCount"] as? Number)?.toInt() ?: 0
            val length = (meta["length"] as? Number)?.toInt() ?: 0
            val ts = (meta["timestamp"] as? Number)?.toLong() ?: 0L
            """{"id":"$id","title":"$title","relativePath":"$relPath","chunkCount":$chunkCount,"length":$length,"timestamp":$ts}"""
        }
        return "[$items]"
    }

    open fun clear(): Boolean {
        lock.withLock {
            documentIndex.clear()
            documentContents.clear()
            documentHashes.clear()
            searchScopePaths = emptyList()
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
