package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.catalog.domain.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CategoryRepository in Product Service
 */
class CategoryRepositoryIntegrationTest extends BaseContainerTest {

    @Test
    @DisplayName("Should save and find category by ID")
    void shouldSaveAndFindCategoryById() {
        // Create test category
        Category category = createTestCategory("Electronics", "electronics");

        // Verify saved
        assertThat(category.getId()).isNotNull();
        assertThat(category.getName()).isEqualTo("Electronics");

        // Find by ID
        StepVerifier.create(categoryRepository.findById(category.getId()))
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(category.getId());
                    assertThat(found.getName()).isEqualTo("Electronics");
                    assertThat(found.getSlug()).isEqualTo("electronics");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find category by slug")
    void shouldFindCategoryBySlug() {
        createTestCategory("Books", "books");

        StepVerifier.create(categoryRepository.findBySlug("books"))
                .assertNext(category -> {
                    assertThat(category.getName()).isEqualTo("Books");
                    assertThat(category.getSlug()).isEqualTo("books");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find all root categories")
    void shouldFindAllRootCategories() {
        createTestCategory("Electronics", "electronics");
        createTestCategory("Books", "books");

        StepVerifier.create(categoryRepository.findRootCategories())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find all active categories ordered by display order")
    void shouldFindAllActiveCategoriesOrdered() {
        Category catA = createTestCategory("Category A", "category-a");
        catA.setDisplayOrder(1);
        categoryRepository.save(catA).block();

        Category catB = createTestCategory("Category B", "category-b");
        catB.setDisplayOrder(2);
        categoryRepository.save(catB).block();

        StepVerifier.create(categoryRepository.findAllActive())
                .assertNext(cat -> assertThat(cat.getName()).isEqualTo("Category A"))
                .assertNext(cat -> assertThat(cat.getName()).isEqualTo("Category B"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should count products in category")
    void shouldCountProductsInCategory() {
        Category category = createTestCategory("Electronics", "electronics");

        createTestProduct("PROD-001", "Product 1", category.getId(), java.math.BigDecimal.valueOf(100));
        createTestProduct("PROD-002", "Product 2", category.getId(), java.math.BigDecimal.valueOf(200));

        StepVerifier.create(categoryRepository.countProductsInCategory(category.getId()))
                .assertNext(count -> assertThat(count).isEqualTo(2L))
                .verifyComplete();
    }
}
