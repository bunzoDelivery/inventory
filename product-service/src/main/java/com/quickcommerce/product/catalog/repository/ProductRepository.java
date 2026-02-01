package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.catalog.domain.Product;
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
public interface ProductRepository extends R2dbcRepository<Product, Long> {

    /**
     * Find product by SKU
     */
    Mono<Product> findBySku(String sku);

    /**
     * Find product by slug
     */
    Mono<Product> findBySlug(String slug);

    /**
     * Find products by category ID
     */
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_active = TRUE AND is_available = TRUE ORDER BY name")
    Flux<Product> findByCategoryId(Long categoryId);

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
}
