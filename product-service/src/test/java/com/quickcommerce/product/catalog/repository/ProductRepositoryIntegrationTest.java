package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.catalog.domain.Category;
import com.quickcommerce.product.catalog.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProductRepository in Product Service
 */
class ProductRepositoryIntegrationTest extends BaseContainerTest {

    @Test
    @DisplayName("Should save and find product by ID")
    void shouldSaveAndFindProductById() {
        Category category = createTestCategory("Electronics", "electronics");
        Product product = createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        assertThat(product.getId()).isNotNull();
        assertThat(product.getSku()).isEqualTo("ELEC-001");

        StepVerifier.create(productRepository.findById(product.getId()))
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(product.getId());
                    assertThat(found.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find product by SKU")
    void shouldFindProductBySku() {
        Category category = createTestCategory("Electronics", "electronics");
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        StepVerifier.create(productRepository.findBySku("ELEC-001"))
                .assertNext(product -> {
                    assertThat(product.getSku()).isEqualTo("ELEC-001");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should search products by name")
    void shouldSearchProductsByName() {
        Category category = createTestCategory("Electronics", "electronics");
        createTestProduct("PROD-001", "Samsung Galaxy Phone", category.getId(), BigDecimal.valueOf(500));
        createTestProduct("PROD-002", "Samsung Tablet", category.getId(), BigDecimal.valueOf(300));
        createTestProduct("PROD-003", "Apple iPhone", category.getId(), BigDecimal.valueOf(900));

        StepVerifier.create(productRepository.searchProducts("Samsung", 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find all available products")
    void shouldFindAllAvailableProducts() {
        Category category = createTestCategory("Electronics", "electronics");
        createTestProduct("PROD-001", "Product 1", category.getId(), BigDecimal.valueOf(100));

        Product unavailable = createTestProduct("PROD-002", "Product 2", category.getId(), BigDecimal.valueOf(200));
        unavailable.setIsAvailable(false);
        productRepository.save(unavailable).block();

        StepVerifier.create(productRepository.findAllAvailable())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find products by price range")
    void shouldFindProductsByPriceRange() {
        Category category = createTestCategory("Electronics", "electronics");
        createTestProduct("PROD-001", "Cheap Product", category.getId(), BigDecimal.valueOf(50));
        createTestProduct("PROD-002", "Mid Product", category.getId(), BigDecimal.valueOf(150));
        createTestProduct("PROD-003", "Expensive Product", category.getId(), BigDecimal.valueOf(500));

        StepVerifier.create(productRepository.findByPriceRange(100.0, 200.0))
                .expectNextCount(1)
                .verifyComplete();
    }
}
