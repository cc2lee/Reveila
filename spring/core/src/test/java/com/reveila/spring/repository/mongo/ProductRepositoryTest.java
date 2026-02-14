package com.reveila.spring.repository.mongo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(classes = MongoTestConfiguration.class)
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindProduct() {
        // Given
        Product product = new Product();
        product.setName("Test Product");
        product.setPrice(99.99);

        // When
        Product savedProduct = productRepository.save(product);
        String productId = savedProduct.getId();

        // Then
        assertThat(productId).isNotNull();
        
        @SuppressWarnings("null")
        Product foundProduct = productRepository.findById(productId).orElse(null);
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getName()).isEqualTo("Test Product");
        assertThat(foundProduct.getPrice()).isEqualTo(99.99);
    }

    @SuppressWarnings("null")
    @Test
    void shouldDeleteProduct() {
        // Given
        Product product = new Product();
        product.setName("Delete Me");
        product.setPrice(10.0);
        Product savedProduct = productRepository.save(product);
        String productId = savedProduct.getId();
        assertThat(productId).isNotNull();
        // When
        productRepository.deleteById(productId);

        // Then
        assertThat(productRepository.findById(productId)).isEmpty();
    }
}
