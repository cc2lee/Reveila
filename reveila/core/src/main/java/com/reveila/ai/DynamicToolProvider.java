package com.reveila.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.reveila.persistence.VectorMatch;
import com.reveila.persistence.VectorStore;

/**
 * Semantic Tool Provider that dynamically discovers relevant tools based on the user's message.
 * It implements "Tool RAG" with a two-stage retrieval (Semantic Search + Reranking).
 * 
 * Android-native implementation with zero dependencies on LangChain4j.
 * 
 * @author CL
 */
public class DynamicToolProvider {
    private final VectorStore toolVectorStore;
    private final MetadataRegistry registry;
    private final ScoringModel reranker;
    private final ReveilaEmbeddingModel embeddingModel;
    private final String securityTier;
    private int topK = 5;

    public DynamicToolProvider(
            VectorStore toolVectorStore, 
            MetadataRegistry registry, 
            ScoringModel reranker,
            ReveilaEmbeddingModel embeddingModel,
            String securityTier) {
        this.toolVectorStore = Objects.requireNonNull(toolVectorStore, "toolVectorStore must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.reranker = reranker; // Optional reranker
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        this.securityTier = Objects.requireNonNull(securityTier, "securityTier must not be null");
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public List<LlmTool> provideTools(String query) {
        // Stage 1: Vector Search (Broad Recall)
        int searchLimit = (reranker != null) ? 20 : topK;
        
        float[] queryVector = embeddingModel.embed(query);
        List<VectorMatch> matches = toolVectorStore.search(queryVector, searchLimit);
        List<String> candidateIds = matches.stream()
            .map(VectorMatch::id)
            .toList();
        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Stage 2: Reranking (High Precision)
        List<String> finalIds;
        if (reranker != null && candidateIds.size() > 1) {
            List<MetadataRegistry.PluginManifest> candidates = candidateIds.stream()
                .map(registry::getManifest)
                .filter(m -> m != null)
                .filter(m -> securityTier.compareTo(m.tier()) <= 0)
                .toList();

            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> candidateDescriptions = candidates.stream()
                .map(m -> {
                    String name = m.name();
                    Map<String, Object> tools = m.tool_definitions();
                    return (name != null ? name : "unknown") + ": " + (tools != null ? tools.toString() : "{}");
                })
                .filter(s -> s != null)
                .toList();

            List<Double> initialScores;
            try {
                initialScores = reranker.scoreAll(query, candidateDescriptions);
            } catch (Exception e) {
                // If reranking fails, gracefully fall back to the original order from Vector Search
                initialScores = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    initialScores.add((double) (candidates.size() - i)); // Assign descending mock scores to preserve original order
                }
            }
            
            final List<Double> scores = initialScores;
            
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) indices.add(i);
            
            indices.sort((i1, i2) -> {
                Double s1 = (i1 >= 0 && i1 < scores.size()) ? scores.get(i1) : null;
                Double s2 = (i2 >= 0 && i2 < scores.size()) ? scores.get(i2) : null;
                if (s1 == null || s2 == null) return 0;
                return Double.compare(s2, s1);
            });
            
            finalIds = indices.stream()
                .limit(topK)
                .map(idx -> {
                    if (idx == null || idx < 0 || idx >= candidates.size()) return null;
                    // Satisfy strict null-safety by using requireNonNull on List.get()
                    MetadataRegistry.PluginManifest manifest = Objects.requireNonNull(candidates.get(idx));
                    return manifest.plugin_id();
                })
                .filter(id -> id != null)
                .toList();
        } else {
            finalIds = candidateIds.stream()
                .map(registry::getManifest)
                .filter(m -> m != null)
                .filter(m -> securityTier.compareTo(m.tier()) <= 0)
                .limit(topK)
                .map(MetadataRegistry.PluginManifest::plugin_id)
                .filter(id -> id != null)
                .toList();
        }

        // Map to LlmTool objects
        List<LlmTool> tools = new ArrayList<>();
        for (String id : finalIds) {
            MetadataRegistry.PluginManifest manifest = registry.getManifest(id);
            if (manifest != null) {
                tools.add(mapToLlmTool(manifest));
            }
        }
        return tools;
    }

    private LlmTool mapToLlmTool(MetadataRegistry.PluginManifest manifest) {
        LlmTool tool = new LlmTool();
        tool.setName(manifest.plugin_id());
        tool.setDescription(manifest.name() + " version " + manifest.version());
        // Map parameter schema if present
        Map<String, Object> defs = manifest.tool_definitions();
        if (defs != null && defs.containsKey("inputSchema")) {
            Object schema = defs.get("inputSchema");
            if (schema instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = (Map<String, Object>) schema;
                tool.setParameterSchema(schemaMap);
            }
        }
        
        tool.setReveilaMetadata(manifest.tier(), List.of(), List.of());
        return tool;
    }
}
