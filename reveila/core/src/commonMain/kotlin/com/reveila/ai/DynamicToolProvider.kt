package com.reveila.ai

import com.reveila.persistence.VectorMatch
import com.reveila.persistence.VectorStore
import com.reveila.safety.MetadataRegistry

/**
 * Semantic Tool Provider that dynamically discovers relevant tools based on the user's message.
 * It implements "Tool RAG" with a two-stage retrieval (Semantic Search + Reranking).
 */
open class DynamicToolProvider(
    private val toolVectorStore: VectorStore,
    private val registry: MetadataRegistry,
    private val reranker: ToolScoringModel?,
    private val embeddingModel: ReveilaEmbeddingModel,
    private val securityTier: String
) {
    private var topK: Int = 5

    open fun setTopK(topK: Int) {
        this.topK = topK
    }

    open fun provideTools(query: String): List<LlmTool> {
        val searchLimit = if (reranker != null) 20 else topK

        val queryVector = embeddingModel.embed(query)
        val matches = toolVectorStore.search(queryVector, searchLimit)
        val candidateIds = matches.map { it.id() }
        if (candidateIds.isEmpty()) {
            return emptyList()
        }

        val finalIds: List<String>
        if (reranker != null && candidateIds.size > 1) {
            val candidates = candidateIds
                .mapNotNull { registry.getManifest(it) }
                .filter { securityTier <= it.tier() }

            if (candidates.isEmpty()) {
                return emptyList()
            }

            val candidateDescriptions = candidates.map { m ->
                val name = m.name() ?: "unknown"
                val tools = m.tool_definitions()
                "$name: ${tools ?: "{}"}"
            }

            val initialScores: MutableList<Double> = try {
                reranker.scoreAll(query, candidateDescriptions).toMutableList()
            } catch (e: Exception) {
                val list = mutableListOf<Double>()
                for (i in candidates.indices) {
                    list.add((candidates.size - i).toDouble())
                }
                list
            }

            val indices = mutableListOf<Int>()
            for (i in candidates.indices) indices.add(i)

            indices.sortWith { i1, i2 ->
                val s1 = if (i1 in initialScores.indices) initialScores[i1] else null
                val s2 = if (i2 in initialScores.indices) initialScores[i2] else null
                if (s1 == null || s2 == null) 0 else s2.compareTo(s1)
            }

            finalIds = indices.asSequence()
                .take(topK)
                .mapNotNull { idx ->
                    if (idx < 0 || idx >= candidates.size) null
                    else candidates[idx].plugin_id()
                }
                .toList()
        } else {
            finalIds = candidateIds.asSequence()
                .mapNotNull { registry.getManifest(it) }
                .filter { securityTier <= it.tier() }
                .take(topK)
                .map { it.plugin_id() }
                .toList()
        }

        val tools = mutableListOf<LlmTool>()
        for (id in finalIds) {
            val manifest = registry.getManifest(id)
            if (manifest != null) {
                tools.add(mapToLlmTool(manifest))
            }
        }
        return tools
    }

    private fun mapToLlmTool(manifest: MetadataRegistry.PluginManifest): LlmTool {
        val tool = LlmTool()
        tool.name = manifest.plugin_id()
        tool.description = "${manifest.name()} version ${manifest.version()}"

        val defs = manifest.tool_definitions()
        if (defs.containsKey("inputSchema")) {
            val schema = defs["inputSchema"]
            if (schema is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                tool.parameterSchema = schema as Map<String, Any?>
            }
        }

        tool.setReveilaMetadata(manifest.tier(), emptyList(), emptyList())
        return tool
    }
}
