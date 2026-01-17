package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityRequest;
import com.quickcommerce.search.dto.AvailabilityResponse;
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
public class InventoryClientImpl implements InventoryClient {

        private final WebClient webClient;
        private final Duration timeout;
        private final String inventoryServiceUrl;

        public InventoryClientImpl(WebClient.Builder webClientBuilder,
                        @Value("${clients.inventory.url}") String inventoryServiceUrl,
                        @Value("${clients.inventory.timeout:200ms}") Duration timeout) {
                this.inventoryServiceUrl = inventoryServiceUrl;
                this.timeout = timeout;
                this.webClient = webClientBuilder
                                .baseUrl(inventoryServiceUrl)
                                .build();
        }

        @Override
        public Mono<AvailabilityResponse> checkAvailability(Long storeId, List<Long> productIds) {
                log.debug("Checking availability for {} products in store {} from {}",
                                productIds.size(), storeId, inventoryServiceUrl);

                AvailabilityRequest request = AvailabilityRequest.builder()
                                .storeId(storeId)
                                .productIds(productIds)
                                .build();

                return webClient
                                .post()
                                .uri("/inventory/availability")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(AvailabilityResponse.class)
                                .timeout(timeout)
                                .doOnSuccess(response -> log.debug("Received availability response for {} products",
                                                response.getAvailability().size()))
                                .onErrorResume(e -> {
                                        log.error("Inventory Service unavailable (timeout/error): {}", e.getMessage());
                                        // Return response with NULL availability map to signal failure
                                        return Mono.just(AvailabilityResponse.builder()
                                                        .storeId(storeId)
                                                        .availability(null)
                                                        .build());
                                });
        }
}
