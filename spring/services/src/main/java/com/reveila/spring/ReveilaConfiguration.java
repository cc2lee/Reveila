package com.reveila.spring;

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
                reveila.start(new SpringPlatformAdapter(context, RuntimeUtil.getArgsAsProperties(args.getSourceArgs())));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start Reveila with SpringPlatformAdapter", e);
            }
        };
    }

}