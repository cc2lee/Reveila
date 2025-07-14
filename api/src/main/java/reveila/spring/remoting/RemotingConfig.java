package reveila.spring.remoting;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.client.core.WebServiceTemplate;

import reveila.remoting.AgnosticRemoteClient;

/**
 * Configures and provides beans for remote communication clients.
 * <p>
 * By using the builders provided by Spring Boot, these clients will be
 * automatically configured with sensible defaults and any custom configuration
 * you provide elsewhere in your application.
 */
@Configuration
public class RemotingConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public AgnosticRemoteClient agnosticRemoteClient() {
        return new AgnosticRemoteClient();
    }
}