package reveila.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Run Gradle Task: :api:bootRun
 * Or build the executable JAR and run it:
 * ./gradlew :api:bootJar
 * java -jar api/build/libs/api-0.0.1-SNAPSHOT.jar
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}