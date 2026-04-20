package com.reveila.ai;

import java.util.List;

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
    List<Double> scoreAll(String query, List<String> candidates) throws Exception;
}
