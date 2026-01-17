package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityResponse;
import reactor.core.publisher.Mono;

import java.util.List;

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
}
