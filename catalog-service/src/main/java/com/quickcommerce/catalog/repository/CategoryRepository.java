package com.quickcommerce.catalog.repository;

import com.quickcommerce.catalog.domain.Category;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for Category entity with reactive operations
 */
@Repository
public interface CategoryRepository extends R2dbcRepository<Category, Long> {

    /**
     * Find category by slug
     */
    Mono<Category> findBySlug(String slug);

    /**
     * Find all root categories (no parent)
     */
    @Query("SELECT * FROM categories WHERE parent_id IS NULL AND is_active = TRUE ORDER BY display_order")
    Flux<Category> findRootCategories();

    /**
     * Find child categories by parent ID
     */
    @Query("SELECT * FROM categories WHERE parent_id = :parentId AND is_active = TRUE ORDER BY display_order")
    Flux<Category> findByParentId(Long parentId);

    /**
     * Find all active categories
     */
    @Query("SELECT * FROM categories WHERE is_active = TRUE ORDER BY display_order")
    Flux<Category> findAllActive();

    /**
     * Check if category has products
     */
    @Query("SELECT COUNT(*) FROM products WHERE category_id = :categoryId")
    Mono<Long> countProductsInCategory(Long categoryId);
}
