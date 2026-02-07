package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client interface for Inventory Service
 * Checks product availability/stock status
 */
public interface InventoryClient {

    /**
     * Check availability of products for a given store
     *
     * @param storeId    Store ID
     * @param productIds List of product IDs to check
     * @return Mono of Availability response with stock status map
     */
    Mono<AvailabilityResponse> checkAvailability(Long storeId, List<Long> productIds);

    /**
     * Get store IDs for multiple products (bulk)
     * Returns map of productId -> List<storeId>
     *
     * @param productIds List of product IDs
     * @return Mono of Map with productId to storeIds mapping
     */
    Mono<Map<Long, List<Long>>> getStoresForProducts(List<Long> productIds);
}
