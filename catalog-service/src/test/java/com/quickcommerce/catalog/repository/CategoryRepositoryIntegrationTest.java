package com.quickcommerce.catalog.repository;

import com.quickcommerce.catalog.BaseContainerTest;
import com.quickcommerce.catalog.domain.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CategoryRepository
 */
class CategoryRepositoryIntegrationTest extends BaseContainerTest {

    @Test
    @DisplayName("Should save and find category by ID")
    void shouldSaveAndFindCategoryById() {
        // Create test category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

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
        // Create test category
        createTestCategory("Books", "books", null, 1);

        // Find by slug
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
        // Create root categories
        createTestCategory("Electronics", "electronics", null, 1);
        createTestCategory("Books", "books", null, 2);
        createTestCategory("Clothing", "clothing", null, 3);

        // Find root categories
        StepVerifier.create(categoryRepository.findRootCategories())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find child categories by parent ID")
    void shouldFindChildCategoriesByParentId() {
        // Create parent category
        Category parent = createTestCategory("Electronics", "electronics", null, 1);

        // Create child categories
        createTestCategory("Laptops", "laptops", parent.getId(), 1);
        createTestCategory("Phones", "phones", parent.getId(), 2);

        // Find children
        StepVerifier.create(categoryRepository.findByParentId(parent.getId()))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find all active categories ordered by display order")
    void shouldFindAllActiveCategoriesOrdered() {
        // Create categories with different display orders
        createTestCategory("Category C", "category-c", null, 3);
        createTestCategory("Category A", "category-a", null, 1);
        createTestCategory("Category B", "category-b", null, 2);

        // Find all active
        StepVerifier.create(categoryRepository.findAllActive())
                .assertNext(cat -> assertThat(cat.getName()).isEqualTo("Category A"))
                .assertNext(cat -> assertThat(cat.getName()).isEqualTo("Category B"))
                .assertNext(cat -> assertThat(cat.getName()).isEqualTo("Category C"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should count products in category")
    void shouldCountProductsInCategory() {
        // Create category
        Category category = createTestCategory("Electronics", "electronics", null, 1);

        // Create products in category
        createTestProduct("PROD-001", "Product 1", category.getId(), java.math.BigDecimal.valueOf(100));
        createTestProduct("PROD-002", "Product 2", category.getId(), java.math.BigDecimal.valueOf(200));

        // Count products
        StepVerifier.create(categoryRepository.countProductsInCategory(category.getId()))
                .assertNext(count -> assertThat(count).isEqualTo(2L))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not find inactive categories in active query")
    void shouldNotFindInactiveCategoriesInActiveQuery() {
        // Create active category
        Category active = createTestCategory("Active", "active", null, 1);

        // Create inactive category
        Category inactive = createTestCategory("Inactive", "inactive", null, 2);
        inactive.setIsActive(false);
        categoryRepository.save(inactive).block();

        // Find all active should only return 1
        StepVerifier.create(categoryRepository.findAllActive())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty when category not found by slug")
    void shouldReturnEmptyWhenCategoryNotFoundBySlug() {
        StepVerifier.create(categoryRepository.findBySlug("non-existent"))
                .verifyComplete();
    }
}
