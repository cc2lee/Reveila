package reveila.spring;

import java.util.Properties;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reveila.Reveila;
import reveila.system.RuntimeUtil;


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
                reveila.start(new SpringPlatformAdapter(context, RuntimeUtil.getJvmArgsAsProperties(args.getSourceArgs())));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start Reveila with SpringPlatformAdapter", e);
            }
        };
    }

}