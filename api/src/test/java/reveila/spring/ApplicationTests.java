package reveila.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import reveila.Reveila;
import reveila.error.ConfigurationException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = reveila.spring.Application.class)
class ApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private Reveila reveila;

    @BeforeEach
    void setUp() throws Exception {
        // Mock the generic invoke method on the Reveila facade.
        // This simplifies the test setup significantly as we no longer need to mock SystemContext or Proxy.
        Mockito.when(reveila.invoke(eq("EchoService"), eq("echo"), any()))
               .thenReturn("Mocked Echo: Test");

        Mockito.when(reveila.invoke(eq("EchoService"), eq("createGreeting"), any()))
               .thenReturn(Map.of("id", "new-id-456", "content", "Hello, World!"));

        Mockito.when(reveila.invoke(eq("EchoService"), eq("updateGreeting"), any()))
               .thenReturn(Map.of("content", "Updated greeting 123 with: Updated Greeting"));

        Mockito.when(reveila.invoke(eq("EchoService"), eq("patchGreeting"), any()))
               .thenReturn(Map.of("content", "Patched greeting 123 with: {content=Patched Content}"));

        Mockito.when(reveila.invoke(eq("EchoService"), eq("deleteGreeting"), any()))
               .thenReturn(null); // Explicitly mock void method behavior

        // Mock the case where a component is not found.
        Mockito.when(reveila.invoke(eq("NonExistentService"), any(), any()))
               .thenThrow(new ConfigurationException("Component 'NonExistentService' not found."));

        // Mock the case where a method is not found on a valid component.
        Mockito.when(reveila.invoke(eq("EchoService"), eq("nonExistentMethod"), any()))
               .thenThrow(new NoSuchMethodException("No suitable method 'nonExistentMethod' found on component 'EchoService'."));
    }

    @Test
    void contextLoads() {
        assertThat(restTemplate).isNotNull();
    }

    private <T> ResponseEntity<T> invokeComponent(String componentName, String methodName, Object[] args, Class<T> responseType) {
        InvokeRequest request = new InvokeRequest();
        request.setMethodName(methodName);
        request.setArgs(args);
        return restTemplate.postForEntity("/api/components/{componentName}/invoke", request, responseType, componentName);
    }

    @Test
    void echoShouldReturnMessageFromService() throws Exception {
        ResponseEntity<String> response = invokeComponent("EchoService", "echo", new Object[]{"Test"}, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Mocked Echo: Test");
    }

    @Test
    void invokeShouldReturnNotFoundWhenComponentIsMissing() {
        // Arrange
        InvokeRequest request = new InvokeRequest();
        request.setMethodName("anyMethod");

        // Act
        ParameterizedTypeReference<Map<String, String>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
            "/api/components/NonExistentService/invoke", HttpMethod.POST, new HttpEntity<>(request), responseType);

        // Assert
        // The RestExceptionHandler now correctly maps ConfigurationException to 404 NOT_FOUND.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
            .isNotNull()
            .containsEntry("error", "Component 'NonExistentService' not found.");
    }

    @Test
    void invokeShouldReturnBadRequestWhenMethodIsMissing() {
        // Arrange
        InvokeRequest request = new InvokeRequest();
        request.setMethodName("nonExistentMethod");

        // Act
        ParameterizedTypeReference<Map<String, String>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
            "/api/components/EchoService/invoke", HttpMethod.POST, new HttpEntity<>(request), responseType);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .isNotNull()
            .containsEntry("error", "No suitable method 'nonExistentMethod' found on component 'EchoService'.");
    }

    @Test
    void createGreetingShouldReturnOk() throws Exception {
        // Arrange
        Map<String, Object> greetingMap = Map.of("content", "Hello, World!");

        // Act
        ResponseEntity<Greeting> response = invokeComponent("EchoService", "createGreeting", new Object[]{greetingMap}, Greeting.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Hello, World!");
    }

    @Test
    void updateGreetingShouldReturnOk() throws Exception {
        Map<String, Object> greetingMap = Map.of("content", "Updated Greeting");
        ResponseEntity<Greeting> response = invokeComponent("EchoService", "updateGreeting", new Object[]{"123", greetingMap}, Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Updated greeting 123 with: Updated Greeting");
    }

    @Test
    void patchGreetingShouldReturnOk() throws Exception {
        Map<String, Object> updates = Map.of("content", "Patched Content");
        ResponseEntity<Greeting> response = invokeComponent("EchoService", "patchGreeting", new Object[]{"123", updates}, Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Patched greeting 123 with: {content=Patched Content}");
    }

    @Test
    void deleteGreetingShouldReturnOk() throws Exception {
        ResponseEntity<Void> response = invokeComponent("EchoService", "deleteGreeting", new Object[]{"456"}, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // A successful invocation with no return value results in 200 OK.
    }

}