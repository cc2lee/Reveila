package reveila.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import reveila.Reveila;
import reveila.system.Proxy;
import reveila.system.SystemContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = reveila.spring.Application.class)
class ApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private Reveila reveila;

    @MockBean
    private Proxy echoServiceProxy;

    @MockBean
    private SystemContext systemContext;

    @BeforeEach
    void setUp() throws Exception {
        // Use Apache HttpClient for PATCH support
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        // Common mock setup for successful calls
        Mockito.when(reveila.getSystemContext()).thenReturn(systemContext);
        Mockito.when(systemContext.getProxy("Echo Service")).thenReturn(Optional.of(echoServiceProxy));

        // Mock service layer calls
        Mockito.when(echoServiceProxy.invoke(eq("echo"), any(), any()))
               .thenReturn("Mocked Echo: Test");

        Mockito.when(echoServiceProxy.invoke(eq("createGreeting"), any(), any()))
               .thenReturn(Map.of("id", "new-id-456", "content", "Hello, World!"));

        Mockito.when(echoServiceProxy.invoke(eq("updateGreeting"), any(), any()))
               .thenReturn(Map.of("content", "Updated greeting 123 with: Updated Greeting"));

        Mockito.when(echoServiceProxy.invoke(eq("patchGreeting"), any(), any()))
               .thenReturn(Map.of("content", "Patched greeting 123 with: {content=Patched Content}"));

        Mockito.when(echoServiceProxy.invoke(eq("handleFileUpload"), any(), any()))
               .thenReturn("File uploaded via service: test.txt");

        Mockito.when(echoServiceProxy.invoke(eq("handleMultipleFileUpload"), any(), any()))
               .thenReturn("Uploaded via service: file1.txt, file2.txt");
    }

    @Test
    void contextLoads() {
        assertThat(restTemplate).isNotNull();
    }

    @Test
    void echoShouldReturnMessageFromService() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/echo?name=Test", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Mocked Echo: Test");
    }

    @Test
    void echoShouldReturnServiceUnavailableWhenProxyIsMissing() {
        // Arrange
        Mockito.when(systemContext.getProxy("Echo Service")).thenReturn(Optional.empty());

        // Act
        ParameterizedTypeReference<Map<String, String>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, String>> response = restTemplate.exchange("/api/echo?name=Test", HttpMethod.GET, null, responseType);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
            .isNotNull()
            .containsEntry("error", "EchoService proxy not found. The service may not be available.");
    }

    @Test
    void createGreetingShouldReturnCreated() throws Exception {
        // Arrange
        Greeting request = new Greeting("Hello, World!");
        ResponseEntity<Greeting> response = restTemplate.postForEntity("/api/greetings", request, Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
            .isNotNull()
            .hasPath("/api/greetings/new-id-456");
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Hello, World!");
    }

    @Test
    void updateGreetingShouldReturnOk() throws Exception {
        Greeting request = new Greeting("Updated Greeting");
        HttpEntity<Greeting> entity = new HttpEntity<>(request);

        ResponseEntity<Greeting> response = restTemplate.exchange("/api/greetings/123", HttpMethod.PUT, entity, Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Updated greeting 123 with: Updated Greeting");
    }

    @Test
    void patchGreetingShouldReturnOk() throws Exception {
        Map<String, Object> updates = Map.of("content", "Patched Content");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updates);

        ResponseEntity<Greeting> response = restTemplate.exchange("/api/greetings/123", HttpMethod.PATCH, entity, Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(Greeting::getContent)
            .isEqualTo("Patched greeting 123 with: {content=Patched Content}");
    }

    @Test
    void deleteGreetingShouldReturnNoContent() throws Exception {
        ResponseEntity<Void> response = restTemplate.exchange("/api/greetings/456", HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void handleFileUploadShouldReturnOk() throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("test content".getBytes()) {
            @Override
            public String getFilename() {
                return "test.txt";
            }
        };
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/upload", requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("File uploaded via service: test.txt");
    }

    @Test
    void handleMultipleFileUploadShouldReturnOk() throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new ByteArrayResource("content1".getBytes()) {
            @Override
            public String getFilename() {
                return "file1.txt";
            }
        });
        body.add("files", new ByteArrayResource("content2".getBytes()) {
            @Override
            public String getFilename() {
                return "file2.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/upload-multiple", requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Uploaded via service: file1.txt, file2.txt");
    }

}