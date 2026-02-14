package com.reveila.spring.system;

import com.reveila.ai.GeminiProvider;
import com.reveila.ai.LlmGovernanceConfig;
import com.reveila.ai.LlmProviderFactory;
import com.reveila.ai.OpenAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.reveila.system.Reveila;
import com.reveila.system.RuntimeUtil;

@Configuration
public class ReveilaConfiguration {

    /**
     * Creates a singleton instance of the Reveila engine.
     */
    @Bean
    public Reveila reveila() {
        return new Reveila();
    }

    /**
     * Starts the Reveila engine after the Spring application context is loaded.
     */
    @Bean
    public ApplicationRunner reveilaRunner(Reveila reveila, ApplicationContext context) {
        return args -> {
            try {
                reveila.start(
                        new SpringPlatformAdapter(context, RuntimeUtil.getArgsAsProperties(args.getSourceArgs())));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start Reveila with SpringPlatformAdapter", e);
            }
        };
    }

    @Bean
    public LlmGovernanceConfig llmGovernanceConfig(
            @Value("${ai.governance.worker:openai}") String worker,
            @Value("${ai.governance.guardrail:gemini}") String guardrail) {
        return new LlmGovernanceConfig(worker, guardrail);
    }

    @Bean
    public OpenAiProvider openAiProvider() {
        return new OpenAiProvider();
    }

    @Bean
    public GeminiProvider geminiProvider() {
        return new GeminiProvider();
    }

    @Bean
    public LlmProviderFactory llmProviderFactory(OpenAiProvider openAi, GeminiProvider gemini) {
        return new LlmProviderFactory(openAi, gemini);
    }

}