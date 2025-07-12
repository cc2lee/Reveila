package reveila.spring;

import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reveila.Reveila;

@Configuration
public class ReveilaConfiguration {
    @Bean
    public Reveila reveila(ApplicationArguments args) {
        try {
            Reveila reveilaInstance = new Reveila();
            reveilaInstance.start(args.getSourceArgs());
            return reveilaInstance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Reveila instance", e);
        }
    }
}