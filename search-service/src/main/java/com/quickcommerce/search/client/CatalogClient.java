package com.quickcommerce.search.client;

import com.quickcommerce.search.model.ProductDocument;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client interface for Catalog Service
 * Fetches product data for fallback and indexing
 */
public interface CatalogClient {

    /**
     * Get bestseller products for a given store
     *
     * @param storeId Store ID
     * @param limit   Number of products to fetch
     * @return Mono of List of bestseller products
     */
    Mono<List<ProductDocument>> getBestsellers(Long storeId, int limit);

    /**
     * Get products by category
     *
     * @param categoryId Category ID
     * @param limit      Number of products to fetch
     * @return Mono of List of products in category
     */
    Mono<List<ProductDocument>> getProductsByCategory(Long categoryId, int limit);
}
