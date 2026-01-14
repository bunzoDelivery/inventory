package com.quickcommerce.search.client;

import com.quickcommerce.search.model.ProductDocument;

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
     * @param limit Number of products to fetch
     * @return List of bestseller products
     */
    List<ProductDocument> getBestsellers(Long storeId, int limit);

    /**
     * Get products by category
     *
     * @param categoryId Category ID
     * @param limit Number of products to fetch
     * @return List of products in category
     */
    List<ProductDocument> getProductsByCategory(Long categoryId, int limit);
}
