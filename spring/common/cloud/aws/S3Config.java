package reveila.spring.common.cloud.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean(destroyMethod = "close") // S3Presigner implements AutoCloseable
    public S3Presigner s3Presigner() {
        // This creates the S3Presigner bean. 
        // It will automatically use the Default Credential Provider Chain.
        return S3Presigner.create();
    }
}
