package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.CatalogProductDto;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client interface for Catalog Service
 * Fetches product data for fallback and indexing.
 * Returns CatalogProductDto (catalog API contract); callers map to ProductDocument.
 */
public interface CatalogClient {

    /**
     * Get bestseller products for a given store
     *
     * @param storeId Store ID
     * @param limit   Number of products to fetch
     * @return Mono of List of catalog products
     */
    Mono<List<CatalogProductDto>> getBestsellers(Long storeId, int limit);

    /**
     * Get products by category
     *
     * @param categoryId Category ID
     * @param limit      Number of products to fetch
     * @return Mono of List of products in category
     */
    Mono<List<CatalogProductDto>> getProductsByCategory(Long categoryId, int limit);

    /**
     * Get all products from catalog
     * Used for bulk indexing
     *
     * @return Flux of catalog products
     */
    reactor.core.publisher.Flux<CatalogProductDto> getAllProducts();
}
