package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub implementation of InventoryClient for local development
 * Returns all products as in-stock
 */
@Slf4j
@Component
@Profile("stub")
public class InventoryStubClient implements InventoryClient {

    @Override
    public Mono<AvailabilityResponse> checkAvailability(Long storeId, List<Long> productIds) {
        log.debug("STUB: Checking availability for {} products in store {}",
                productIds.size(), storeId);

        // Stub: assume all products are in stock
        Map<Long, Boolean> availability = new HashMap<>();
        productIds.forEach(productId -> availability.put(productId, true));

        log.debug("STUB: Returning all {} products as in-stock", productIds.size());

        return Mono.just(AvailabilityResponse.builder()
                .storeId(storeId)
                .availability(availability)
                .build());
    }
}
