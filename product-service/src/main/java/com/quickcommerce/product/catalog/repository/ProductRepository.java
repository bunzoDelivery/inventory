package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.catalog.domain.Product;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for Product entity with reactive operations and search
 * capabilities
 */
@Repository
public interface ProductRepository extends R2dbcRepository<Product, Long>, ProductRepositoryCustom {

    /**
     * Find product by SKU
     */
    Mono<Product> findBySku(String sku);

    /**
     * Find product by slug
     */
    Mono<Product> findBySlug(String slug);

    /**
     * Find products by multiple SKUs (useful for bulk operations)
     */
    @Query("SELECT * FROM products WHERE sku IN (:skus)")
    Flux<Product> findBySkuIn(java.util.List<String> skus);

    /**
     * Find products by category ID (unpaginated)
     */
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_active = TRUE AND is_available = TRUE ORDER BY name, id")
    Flux<Product> findByCategoryId(Long categoryId);

    /**
     * Find products by category ID with pagination.
     * ORDER BY name, id ensures stable pagination when names collide.
     * Note: R2DBC doesn't automatically apply Pageable to custom @Query, so we use limit/offset params
     */
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_active = TRUE AND is_available = TRUE ORDER BY name, id LIMIT :limit OFFSET :offset")
    Flux<Product> findByCategoryId(Long categoryId, int limit, long offset);

    /**
     * Count active and available products in a category (same predicate as findByCategoryId)
     */
    @Query("SELECT COUNT(*) FROM products WHERE category_id = :categoryId AND is_active = TRUE AND is_available = TRUE")
    Mono<Long> countActiveAndAvailableByCategoryId(Long categoryId);

    /**
     * Find products by brand
     */
    @Query("SELECT * FROM products WHERE brand = :brand AND is_active = TRUE AND is_available = TRUE ORDER BY name")
    Flux<Product> findByBrand(String brand);

    /**
     * Search products by name (full-text search using LIKE)
     * Note: MySQL full-text search can be added later for better performance
     */
    @Query("SELECT * FROM products WHERE (name LIKE CONCAT('%', :searchTerm, '%') OR description LIKE CONCAT('%', :searchTerm, '%') OR tags LIKE CONCAT('%', :searchTerm, '%')) AND is_active = TRUE AND is_available = TRUE ORDER BY name LIMIT :limit")
    Flux<Product> searchProducts(String searchTerm, Integer limit);

    /**
     * Search products using MySQL FULLTEXT index (faster for longer queries)
     * Uses MATCH...AGAINST for better performance on indexed columns
     */
    @Query("""
        SELECT * FROM products 
        WHERE MATCH(name, description, tags) AGAINST (:searchTerm IN NATURAL LANGUAGE MODE)
          AND is_active = TRUE 
          AND is_available = TRUE
        ORDER BY 
            CASE WHEN is_bestseller = TRUE THEN 1 ELSE 2 END,
            COALESCE(search_priority, 0) DESC,
            COALESCE(order_count, 0) DESC
        LIMIT :limit
        """)
    Flux<Product> searchProductsFullText(String searchTerm, Integer limit);

    /**
     * Smart search: uses FULLTEXT for longer queries (>= 4 chars), LIKE for shorter ones
     * This provides best performance across different query lengths
     */
    default Flux<Product> searchProductsSmart(String searchTerm, Integer limit) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return Flux.empty();
        }
        
        String trimmedTerm = searchTerm.trim();
        
        // Use FULLTEXT for longer queries (better performance with indexed columns)
        if (trimmedTerm.length() >= 4) {
            return searchProductsFullText(trimmedTerm, limit);
        }
        
        // Use LIKE for short queries (FULLTEXT requires minimum word length)
        return searchProducts(trimmedTerm, limit);
    }

    /**
     * Find all active and available products
     */
    @Query("SELECT * FROM products WHERE is_active = TRUE AND is_available = TRUE ORDER BY name")
    Flux<Product> findAllAvailable();

    /**
     * Find bestseller products
     */
    @Query("SELECT * FROM products WHERE is_active = TRUE AND is_available = TRUE AND is_bestseller = TRUE ORDER BY name LIMIT :limit")
    Flux<Product> findBestsellers(Integer limit);

    /**
     * Find products with price range
     */
    @Query("SELECT * FROM products WHERE base_price BETWEEN :minPrice AND :maxPrice AND is_active = TRUE AND is_available = TRUE ORDER BY base_price")
    Flux<Product> findByPriceRange(Double minPrice, Double maxPrice);

    /**
     * Check if SKU exists - returns Integer (0 or 1) due to R2DBC limitation
     */
    @Query("SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM products WHERE sku = :sku")
    Mono<Integer> existsBySkuInt(String sku);

    /**
     * Check if SKU exists - convenience method that returns Boolean
     */
    default Mono<Boolean> existsBySku(String sku) {
        return existsBySkuInt(sku).map(result -> result > 0);
    }

    /**
     * Increment order_count by 1 for the given SKU (one delivered order containing this SKU).
     * Returns number of rows updated (0 if SKU unknown).
     */
    @Modifying
    @Query("UPDATE products SET order_count = COALESCE(order_count, 0) + 1, updated_at = CURRENT_TIMESTAMP WHERE sku = :sku")
    Mono<Integer> incrementOrderCountBySku(String sku);

    /**
     * Fetch all active variants belonging to any of the given group IDs.
     * Ordered by base_price ASC, id ASC so the cheapest variant is always first —
     * works universally for volume, weight, pack count, and unit-based variants.
     * Used by GET /api/v1/catalog/products/groups/batch.
     */
    @Query("SELECT * FROM products WHERE group_id IN (:groupIds) AND is_active = true ORDER BY base_price ASC, id ASC")
    Flux<Product> findByGroupIdIn(java.util.List<String> groupIds);

    /**
     * Returns each distinct group_id with the count of active variants in that group.
     * Used by GET /api/v1/catalog/products/groups for admin discoverability.
     */
    @Query("SELECT group_id, COUNT(*) AS variant_count FROM products WHERE group_id IS NOT NULL AND is_active = true GROUP BY group_id ORDER BY group_id")
    Flux<com.quickcommerce.product.catalog.dto.GroupSummary> findAllGroupSummaries();
}
