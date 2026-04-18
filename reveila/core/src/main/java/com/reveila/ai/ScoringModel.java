package com.reveila.ai;

import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Interface for scoring candidates against a query.
 * Used for Reranking in the Tool RAG pipeline.
 * 
 * @author CL
 */
public interface ScoringModel {
    /**
     * Scores multiple candidates against a single query.
     * 
     * @param query The user's intent.
     * @param candidates The list of candidate descriptions or tools.
     * @return A list of scores corresponding to the candidates.
     */
    List<@NonNull Double> scoreAll(@NonNull String query, List<@NonNull String> candidates);
}
