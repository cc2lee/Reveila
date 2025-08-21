package reveila.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import reveila.service.Page;

/**
 * Integration tests for the DataService component.
 * These tests run against a real Spring application context and an in-memory H2 database.
 * 
 * Key Concepts in This Test...
 * SpringBootTest: This annotation starts up your entire Spring Boot application, including the web server on a random port, the Reveila engine, and the connection to the H2 database.
 * DirtiesContext: This is a very useful annotation for database testing. It tells Spring to reset the application context (and therefore the in-memory H2 database) after each test method runs. This ensures that your tests are independent and don't interfere with each other.
 * TestRestTemplate: A convenient client for making HTTP requests to your running application in a test environment.
 * ParameterizedTypeReference: This is necessary to help the TestRestTemplate correctly deserialize JSON responses into generic types like Page<Map<String, Object>>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = reveila.spring.Application.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // Resets the DB for each test
public class DataServiceTests {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * A generic helper to invoke methods on a Reveila component via the REST API.
     */
    private <T> ResponseEntity<T> invoke(String component, String method, Object[] args, ParameterizedTypeReference<T> responseType) {
        MethodDTO request = new MethodDTO();
        request.setMethodName(method);
        request.setArgs(args);
        return restTemplate.exchange(
            "/api/components/{componentName}/invoke",
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType,
            component
        );
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveAndFindEntity() {
        // Arrange: Create a product to save
        Map<String, Object> product = Map.of("name", "Laptop", "price", 1200.50);

        // Act: Save the product
        ResponseEntity<Map<String, Object>> saveResponse = invoke(
            "DataService",
            "save",
            new Object[]{"products", product},
            new ParameterizedTypeReference<>() {}
        );

        // Assert: Save was successful
        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> savedProduct = saveResponse.getBody();
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.get("id")).isNotNull().isInstanceOf(String.class);
        assertThat(savedProduct.get("name")).isEqualTo("Laptop");
        assertThat(savedProduct.get("price")).isEqualTo(1200.50);
        String savedId = (String) savedProduct.get("id");

        // Act: Find the product by ID
        ResponseEntity<Map<String, Object>> findResponse = invoke(
            "DataService",
            "findById",
            new Object[]{"products", savedId},
            new ParameterizedTypeReference<>() {}
        );

        // Assert: Find was successful
        assertThat(findResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> foundProduct = findResponse.getBody();
        assertThat(foundProduct).isNotNull().isEqualTo(savedProduct);
    }

    @Test
    void shouldHandleFindByIdNotFound() {
        // Act: Try to find a non-existent entity
        ResponseEntity<Map<String, Object>> findResponse = invoke(
            "DataService",
            "findById",
            new Object[]{"products", "non-existent-id"},
            new ParameterizedTypeReference<>() {}
        );

        // Assert: The response is OK but the body is null, as the Optional was empty.
        assertThat(findResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findResponse.getBody()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    void shouldFindAllWithPagination() {
        // Arrange: Save a few products
        invoke("DataService", "save", new Object[]{"products", Map.of("name", "Product A", "price", 10)}, new ParameterizedTypeReference<Map<String, Object>>() {});
        invoke("DataService", "save", new Object[]{"products", Map.of("name", "Product B", "price", 20)}, new ParameterizedTypeReference<Map<String, Object>>() {});
        invoke("DataService", "save", new Object[]{"products", Map.of("name", "Product C", "price", 30)}, new ParameterizedTypeReference<Map<String, Object>>() {});

        // Act: Get the first page (page 0, size 2)
        var pageResponseType = new ParameterizedTypeReference<Page<Map<String, Object>>>() {};
        ResponseEntity<Page<Map<String, Object>>> pageResponse = invoke("DataService", "findAll", new Object[]{"products", 0, 2}, pageResponseType);

        // Assert: Check the page content
        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Page<Map<String, Object>> page = pageResponse.getBody();
        assertThat(page).isNotNull();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.pageNumber()).isEqualTo(0);
        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).get("name")).isEqualTo("Product A");

        // Act: Get the second page
        ResponseEntity<Page<Map<String, Object>>> secondPageResponse = invoke("DataService", "findAll", new Object[]{"products", 1, 2}, pageResponseType);

        // Assert: Check the second page content
        assertThat(secondPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Page<Map<String, Object>> secondPage = secondPageResponse.getBody();
        assertThat(secondPage).isNotNull();
        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.content().get(0).get("name")).isEqualTo("Product C");
    }

    @Test
    @SuppressWarnings("null")
    void shouldDeleteEntity() {
        // Arrange: Save a product
        ResponseEntity<Map<String, Object>> saveResponse = invoke("DataService", "save", new Object[]{"products", Map.of("name", "To be deleted", "price", 99)}, new ParameterizedTypeReference<>() {});
        assertThat(saveResponse).isNotNull();
        assertThat(saveResponse.getBody()).isNotNull();
        String savedId = (String) saveResponse.getBody().get("id");

        // Act: Delete the product
        ResponseEntity<Void> deleteResponse = invoke("DataService", "deleteById", new Object[]{"products", savedId}, new ParameterizedTypeReference<>() {});

        // Assert: Deletion was successful
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act: Try to find the deleted product
        ResponseEntity<Map<String, Object>> findResponse = invoke("DataService", "findById", new Object[]{"products", savedId}, new ParameterizedTypeReference<>() {});

        // Assert: Product is not found (body is null)
        assertThat(findResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findResponse.getBody()).isNull();
    }
}