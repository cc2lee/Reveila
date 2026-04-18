package com.reveila.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.reveila.system.PluginComponent;

/**
 * Implementation of ScoringModel that uses an LlmProvider to rank tools.
 * This acts as a surgical reranker in the Tool RAG pipeline.
 * 
 * @author CL
 */
public class LlmScoringModel extends PluginComponent implements ScoringModel {
    
    private final LlmProvider provider;
    private static final Pattern SCORE_PATTERN = Pattern.compile("Score:\\s*(\\d+\\.?\\d*)");

    public LlmScoringModel(LlmProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    @Override
    public List<@NonNull Double> scoreAll(@NonNull String query, List<@NonNull String> candidates) {
        List<@NonNull Double> scores = new ArrayList<>();
        
        for (String candidate : candidates) {
            scores.add(scoreCandidate(query, candidate));
        }
        
        return scores;
    }

    private double scoreCandidate(String query, String candidate) {
        LlmRequest request = LlmRequest.builder()
            .addMessage(ReveilaMessage.system("You are a surgical tool reranker. Your task is to evaluate the relevance of a tool to a user's intent. " +
                "Respond ONLY with 'Score: X.X' where X.X is between 0.0 (irrelevant) and 1.0 (perfect match)."))
            .addMessage(ReveilaMessage.user(String.format("User Intent: %s\nTool Manifest: %s\nEvaluation:", query, candidate)))
            .temperature(0.0)
            .build();

        try {
            LlmResponse response = provider.invoke(request);
            String content = response.getContent();
            if (content != null) {
                Matcher matcher = SCORE_PATTERN.matcher(content);
                if (matcher.find()) {
                    return Double.parseDouble(matcher.group(1));
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to score tool candidate: " + e.getMessage());
            }
        }
        
        return 0.0;
    }

    @Override
    protected void onStart() throws Exception {
        // Initialization if needed
    }

    @Override
    protected void onStop() throws Exception {
        // Cleanup if needed
    }
}
