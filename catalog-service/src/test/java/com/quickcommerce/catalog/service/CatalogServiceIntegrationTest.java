package com.quickcommerce.catalog.service;

import com.quickcommerce.catalog.BaseContainerTest;
import com.quickcommerce.catalog.domain.Category;
import com.quickcommerce.catalog.dto.CategoryResponse;
import com.quickcommerce.catalog.dto.CreateCategoryRequest;
import com.quickcommerce.catalog.dto.CreateProductRequest;
import com.quickcommerce.catalog.dto.ProductResponse;
import com.quickcommerce.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CatalogService
 */
class CatalogServiceIntegrationTest extends BaseContainerTest {

    @Autowired
    private CatalogService catalogService;

    // ============ Category Tests ============

    @Test
    @DisplayName("Should create category successfully")
    void shouldCreateCategorySuccessfully() {
        CreateCategoryRequest request = new CreateCategoryRequest(
                "Electronics",
                "Electronic items",
                null,
                "electronics",
                1,
                true,
                null
        );

        StepVerifier.create(catalogService.createCategory(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isNotNull();
                    assertThat(response.getName()).isEqualTo("Electronics");
                    assertThat(response.getSlug()).isEqualTo("electronics");
                    assertThat(response.getDisplayOrder()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw error when creating category with duplicate slug")
    void shouldThrowErrorWhenCreatingCategoryWithDuplicateSlug() {
        // Create first category
        CreateCategoryRequest request1 = new CreateCategoryRequest(
                "Electronics",
                "Electronic items",
                null,
                "electronics",
                1,
                true,
                null
        );
        catalogService.createCategory(request1).block();

        // Try to create second category with same slug
        CreateCategoryRequest request2 = new CreateCategoryRequest(
                "Electronics 2",
                "More electronics",
                null,
                "electronics",
                2,
                true,
                null
        );

        StepVerifier.create(catalogService.createCategory(request2))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should get category by ID")
    void shouldGetCategoryById() {
        Category category = createTestCategory("Books", "books", null, 1);

        StepVerifier.create(catalogService.getCategoryById(category.getId()))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(category.getId());
                    assertThat(response.getName()).isEqualTo("Books");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when category not found")
    void shouldThrowResourceNotFoundExceptionWhenCategoryNotFound() {
        StepVerifier.create(catalogService.getCategoryById(999L))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should get category by slug")
    void shouldGetCategoryBySlug() {
        createTestCategory("Books", "books", null, 1);

        StepVerifier.create(catalogService.getCategoryBySlug("books"))
                .assertNext(response -> {
                    assertThat(response.getName()).isEqualTo("Books");
                    assertThat(response.getSlug()).isEqualTo("books");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get root categories")
    void shouldGetRootCategories() {
        // Create root categories
        createTestCategory("Electronics", "electronics", null, 1);
        createTestCategory("Books", "books", null, 2);

        StepVerifier.create(catalogService.getRootCategories())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get child categories")
    void shouldGetChildCategories() {
        // Create parent
        Category parent = createTestCategory("Electronics", "electronics", null, 1);

        // Create children
        createTestCategory("Laptops", "laptops", parent.getId(), 1);
        createTestCategory("Phones", "phones", parent.getId(), 2);

        StepVerifier.create(catalogService.getChildCategories(parent.getId()))
                .expectNextCount(2)
                .verifyComplete();
    }

    // ============ Product Tests ============

    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProductSuccessfully() {
        // Create category first
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        CreateProductRequest request = new CreateProductRequest(
                "ELEC-001",
                "Laptop",
                "High performance laptop",
                "Fast laptop",
                category.getId(),
                "Dell",
                BigDecimal.valueOf(999.99),
                "piece",
                "1 piece",
                null,
                "electronics,computer",
                true,
                true,
                "laptop",
                null,
                2000,
                "123456789"
        );

        StepVerifier.create(catalogService.createProduct(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isNotNull();
                    assertThat(response.getSku()).isEqualTo("ELEC-001");
                    assertThat(response.getName()).isEqualTo("Laptop");
                    assertThat(response.getBasePrice()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw error when creating product with duplicate SKU")
    void shouldThrowErrorWhenCreatingProductWithDuplicateSku() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create first product
        CreateProductRequest request1 = new CreateProductRequest(
                "ELEC-001", "Laptop 1", "Description", "Short", category.getId(),
                "Dell", BigDecimal.valueOf(999), "piece", "1", null, "tag",
                true, true, "laptop-1", null, 2000, null
        );
        catalogService.createProduct(request1).block();

        // Try to create second product with same SKU
        CreateProductRequest request2 = new CreateProductRequest(
                "ELEC-001", "Laptop 2", "Description", "Short", category.getId(),
                "HP", BigDecimal.valueOf(899), "piece", "1", null, "tag",
                true, true, "laptop-2", null, 2000, null
        );

        StepVerifier.create(catalogService.createProduct(request2))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw error when creating product with non-existent category")
    void shouldThrowErrorWhenCreatingProductWithNonExistentCategory() {
        CreateProductRequest request = new CreateProductRequest(
                "ELEC-001", "Laptop", "Description", "Short", 999L,
                "Dell", BigDecimal.valueOf(999), "piece", "1", null, "tag",
                true, true, "laptop", null, 2000, null
        );

        StepVerifier.create(catalogService.createProduct(request))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should get product by ID")
    void shouldGetProductById() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        var product = createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999));

        StepVerifier.create(catalogService.getProductById(product.getId()))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(product.getId());
                    assertThat(response.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get product by SKU")
    void shouldGetProductBySku() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);
        createTestProduct("ELEC-001", "Laptop", category.getId(), BigDecimal.valueOf(999));

        StepVerifier.create(catalogService.getProductBySku("ELEC-001"))
                .assertNext(response -> {
                    assertThat(response.getSku()).isEqualTo("ELEC-001");
                    assertThat(response.getName()).isEqualTo("Laptop");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get products by category")
    void shouldGetProductsByCategory() {
        Category electronics = createTestCategory("Electronics", "electronics", null, 1);
        Category books = createTestCategory("Books", "books", null, 2);

        createTestProduct("ELEC-001", "Laptop", electronics.getId(), BigDecimal.valueOf(999));
        createTestProduct("ELEC-002", "Phone", electronics.getId(), BigDecimal.valueOf(499));
        createTestProduct("BOOK-001", "Novel", books.getId(), BigDecimal.valueOf(19));

        StepVerifier.create(catalogService.getProductsByCategory(electronics.getId()))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should search products")
    void shouldSearchProducts() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        createTestProduct("PROD-001", "Samsung Galaxy", category.getId(), BigDecimal.valueOf(500));
        createTestProduct("PROD-002", "Samsung Tablet", category.getId(), BigDecimal.valueOf(300));
        createTestProduct("PROD-003", "Apple iPhone", category.getId(), BigDecimal.valueOf(900));

        StepVerifier.create(catalogService.searchProducts("Samsung", 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get products by price range")
    void shouldGetProductsByPriceRange() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        createTestProduct("PROD-001", "Cheap", category.getId(), BigDecimal.valueOf(50));
        createTestProduct("PROD-002", "Mid", category.getId(), BigDecimal.valueOf(150));
        createTestProduct("PROD-003", "Expensive", category.getId(), BigDecimal.valueOf(500));

        StepVerifier.create(catalogService.getProductsByPriceRange(100.0, 200.0))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use default limit when searching products")
    void shouldUseDefaultLimitWhenSearchingProducts() {
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create 60 products
        for (int i = 1; i <= 60; i++) {
            createTestProduct("PROD-" + String.format("%03d", i),
                    "Product " + i, category.getId(), BigDecimal.valueOf(100));
        }

        // Search without limit should default to 50
        StepVerifier.create(catalogService.searchProducts("Product", null))
                .expectNextCount(50)
                .verifyComplete();
    }
}
