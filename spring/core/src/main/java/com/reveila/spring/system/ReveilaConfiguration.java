package com.reveila.spring.system;

import com.reveila.ai.*;
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

    @Bean
    public IntentValidator intentValidator(GeminiProvider gemini) {
        return new GeminiIntentValidator(gemini);
    }

    @Bean
    public UniversalInvocationBridge universalInvocationBridge(
            IntentValidator intentValidator,
            SchemaEnforcer schemaEnforcer,
            GuardedRuntime guardedRuntime,
            FlightRecorder flightRecorder,
            MetadataRegistry metadataRegistry,
            CredentialManager credentialManager,
            OrchestrationService orchestrationService,
            LlmProviderFactory llmFactory,
            LlmGovernanceConfig govConfig) {
        return new UniversalInvocationBridge(
                intentValidator, schemaEnforcer, guardedRuntime,
                flightRecorder, metadataRegistry, credentialManager,
                orchestrationService, llmFactory, govConfig);
    }

    @Bean
    public OrchestrationService orchestrationService() {
        return new OrchestrationService();
    }

    @Bean
    public AgenticFabric agenticFabric(UniversalInvocationBridge bridge, AgentSessionManager sessionManager) {
        return new AgenticFabric(bridge, sessionManager);
    }

    @Bean
    public SchemaEnforcer schemaEnforcer(MetadataRegistry registry) {
        return new JsonSchemaEnforcer(registry);
    }

    @Bean
    public GuardedRuntime guardedRuntime() {
        return new DockerGuardedRuntime();
    }


    @Bean
    public MetadataRegistry metadataRegistry() {
        return new MetadataRegistry();
    }

    @Bean
    public CredentialManager credentialManager() {
        return new CredentialManager();
    }

    @Bean
    public AgentSessionManager agentSessionManager() {
        return new AgentSessionManager();
    }

}