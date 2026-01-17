package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityRequest;
import com.quickcommerce.search.dto.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Real implementation of InventoryClient using WebClient
 * Calls actual Inventory Service API
 */
@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class InventoryClientImpl implements InventoryClient {

        private final WebClient.Builder webClientBuilder;

        @Value("${clients.inventory.url}")
        private String inventoryServiceUrl;

        @Value("${clients.inventory.timeout:200ms}")
        private Duration timeout;

        @Override
        public Mono<AvailabilityResponse> checkAvailability(Long storeId, List<Long> productIds) {
                log.debug("Checking availability for {} products in store {} from {}",
                                productIds.size(), storeId, inventoryServiceUrl);

                AvailabilityRequest request = AvailabilityRequest.builder()
                                .storeId(storeId)
                                .productIds(productIds)
                                .build();

                return webClientBuilder
                                .baseUrl(inventoryServiceUrl)
                                .build()
                                .post()
                                .uri("/inventory/availability")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(AvailabilityResponse.class)
                                .timeout(timeout)
                                .doOnSuccess(response -> log.debug("Received availability response for {} products",
                                                response.getAvailability().size()))
                                .onErrorResume(e -> {
                                        log.error("Error calling inventory service", e);
                                        // Fallback: assume all in stock on error (fail-open)
                                        log.warn("Falling back to assume all products in-stock");

                                        return Mono.just(AvailabilityResponse.builder()
                                                        .storeId(storeId)
                                                        .availability(productIds.stream()
                                                                        .collect(java.util.stream.Collectors.toMap(
                                                                                        id -> id,
                                                                                        id -> true)))
                                                        .build());
                                });
        }
}
