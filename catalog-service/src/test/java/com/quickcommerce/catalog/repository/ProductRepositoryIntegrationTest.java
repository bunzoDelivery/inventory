package com.quickcommerce.catalog.repository;

import com.quickcommerce.catalog.BaseContainerTest;
import com.quickcommerce.catalog.domain.Category;
import com.quickcommerce.catalog.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProductRepository
 */
class ProductRepositoryIntegrationTest extends BaseContainerTest {

    @Test
    @DisplayName("Should save and find product by ID")
    void shouldSaveAndFindProductById() {
        // Create category and product
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        Product product = createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        // Verify saved
        assertThat(product.getId()).isNotNull();
        assertThat(product.getSku()).isEqualTo("ELEC-001");

        // Find by ID
        StepVerifier.create(productRepository.findById(product.getId()))
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(product.getId());
                    assertThat(found.getName()).isEqualTo("Laptop");
                    assertThat(found.getSku()).isEqualTo("ELEC-001");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find product by SKU")
    void shouldFindProductBySku() {
        // Create category and product
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        // Find by SKU
        StepVerifier.create(productRepository.findBySku("ELEC-001"))
                .assertNext(product -> {
                    assertThat(product.getSku()).isEqualTo("ELEC-001");
                    assertThat(product.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find product by slug")
    void shouldFindProductBySlug() {
        // Create category and product
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        // Find by slug
        StepVerifier.create(productRepository.findBySlug("elec-001"))
                .assertNext(product -> {
                    assertThat(product.getSlug()).isEqualTo("elec-001");
                    assertThat(product.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find products by category ID")
    void shouldFindProductsByCategoryId() {
        // Create categories
        Category electronics = createTestCategory("Electronics", "electronics", null, 1);
        Category books = createTestCategory("Books", "books", null, 2);

        // Create products
        createTestProduct("ELEC-001", "Laptop", electronics.getId(), BigDecimal.valueOf(999.99));
        createTestProduct("ELEC-002", "Phone", electronics.getId(), BigDecimal.valueOf(499.99));
        createTestProduct("BOOK-001", "Novel", books.getId(), BigDecimal.valueOf(19.99));

        // Find products in electronics category
        StepVerifier.create(productRepository.findByCategoryId(electronics.getId()))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find products by brand")
    void shouldFindProductsByBrand() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products with same brand
        Product product1 = createTestProduct("PROD-001", "Product 1", category.getId(), BigDecimal.valueOf(100));
        product1.setBrand("Samsung");
        productRepository.save(product1).block();

        Product product2 = createTestProduct("PROD-002", "Product 2", category.getId(), BigDecimal.valueOf(200));
        product2.setBrand("Samsung");
        productRepository.save(product2).block();

        Product product3 = createTestProduct("PROD-003", "Product 3", category.getId(), BigDecimal.valueOf(300));
        product3.setBrand("Apple");
        productRepository.save(product3).block();

        // Find Samsung products
        StepVerifier.create(productRepository.findByBrand("Samsung"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should search products by name")
    void shouldSearchProductsByName() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products
        createTestProduct("PROD-001", "Samsung Galaxy Phone", category.getId(), BigDecimal.valueOf(500));
        createTestProduct("PROD-002", "Samsung Tablet", category.getId(), BigDecimal.valueOf(300));
        createTestProduct("PROD-003", "Apple iPhone", category.getId(), BigDecimal.valueOf(900));

        // Search for "Samsung"
        StepVerifier.create(productRepository.searchProducts("Samsung", 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should search products by description")
    void shouldSearchProductsByDescription() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products with searchable descriptions
        Product product = createTestProduct("PROD-001", "Laptop", category.getId(), BigDecimal.valueOf(999));
        product.setDescription("High performance gaming laptop with powerful specs");
        productRepository.save(product).block();

        // Search for "gaming"
        StepVerifier.create(productRepository.searchProducts("gaming", 10))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should search products by tags")
    void shouldSearchProductsByTags() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products with tags
        Product product = createTestProduct("PROD-001", "Laptop", category.getId(), BigDecimal.valueOf(999));
        product.setTags("electronics,computer,portable");
        productRepository.save(product).block();

        // Search for "portable"
        StepVerifier.create(productRepository.searchProducts("portable", 10))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find all available products")
    void shouldFindAllAvailableProducts() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create available products
        createTestProduct("PROD-001", "Product 1", category.getId(), BigDecimal.valueOf(100));
        createTestProduct("PROD-002", "Product 2", category.getId(), BigDecimal.valueOf(200));

        // Create unavailable product
        Product unavailable = createTestProduct("PROD-003", "Product 3", category.getId(), BigDecimal.valueOf(300));
        unavailable.setIsAvailable(false);
        productRepository.save(unavailable).block();

        // Find all available
        StepVerifier.create(productRepository.findAllAvailable())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find products by price range")
    void shouldFindProductsByPriceRange() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products with different prices
        createTestProduct("PROD-001", "Cheap Product", category.getId(), BigDecimal.valueOf(50));
        createTestProduct("PROD-002", "Mid Product", category.getId(), BigDecimal.valueOf(150));
        createTestProduct("PROD-003", "Expensive Product", category.getId(), BigDecimal.valueOf(500));

        // Find products between 100 and 200
        StepVerifier.create(productRepository.findByPriceRange(100.0, 200.0))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if SKU exists")
    void shouldCheckIfSkuExists() {
        // Create category and product
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999.99));

        // Check existing SKU
        StepVerifier.create(productRepository.existsBySku("ELEC-001"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();

        // Check non-existing SKU
        StepVerifier.create(productRepository.existsBySku("NON-EXISTENT"))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty when product not found by SKU")
    void shouldReturnEmptyWhenProductNotFoundBySku() {
        StepVerifier.create(productRepository.findBySku("NON-EXISTENT"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should respect search limit")
    void shouldRespectSearchLimit() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create 10 products
        for (int i = 1; i <= 10; i++) {
            createTestProduct("PROD-" + String.format("%03d", i), "Product " + i, category.getId(), BigDecimal.valueOf(100));
        }

        // Search with limit 5
        StepVerifier.create(productRepository.searchProducts("Product", 5))
                .expectNextCount(5)
                .verifyComplete();
    }
}
