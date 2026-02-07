package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.AvailabilityRequest;
import com.quickcommerce.search.dto.AvailabilityResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Real implementation of InventoryClient using WebClient
 * Calls actual Inventory Service API with circuit breaker protection
 */
@Slf4j
@Component
public class InventoryClientImpl implements InventoryClient {

        private final WebClient webClient;
        private final Duration timeout;
        private final String inventoryServiceUrl;
        private final CircuitBreaker circuitBreaker;

        public InventoryClientImpl(WebClient.Builder webClientBuilder,
                        @Value("${clients.inventory.url}") String inventoryServiceUrl,
                        @Value("${clients.inventory.timeout:200ms}") Duration timeout,
                        @Qualifier("inventoryCircuitBreaker") CircuitBreaker circuitBreaker) {
                this.inventoryServiceUrl = inventoryServiceUrl;
                this.timeout = timeout;
                this.circuitBreaker = circuitBreaker;
                this.webClient = webClientBuilder
                                .baseUrl(inventoryServiceUrl)
                                .build();
        }

        @Override
        public Mono<AvailabilityResponse> checkAvailability(Long storeId, List<Long> productIds) {
                log.debug("Checking availability for {} products in store {} from {} (CB: {})",
                                productIds.size(), storeId, inventoryServiceUrl, 
                                circuitBreaker.getState());

                AvailabilityRequest request = AvailabilityRequest.builder()
                                .storeId(storeId)
                                .productIds(productIds)
                                .build();

                return webClient
                                .post()
                                .uri("/api/v1/inventory/availability")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(AvailabilityResponse.class)
                                .timeout(timeout)
                                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                                .doOnSuccess(response -> log.debug("Received availability response for {} products",
                                                response.getAvailability().size()))
                                .onErrorResume(e -> {
                                        log.error("Inventory Service call failed (CB: {}): {}", 
                                                circuitBreaker.getState(), e.getMessage());
                                        // Return response with NULL availability map to signal failure
                                        return Mono.just(AvailabilityResponse.builder()
                                                        .storeId(storeId)
                                                        .availability(null)
                                                        .build());
                                });
        }

        @Override
        public Mono<Map<Long, List<Long>>> getStoresForProducts(List<Long> productIds) {
                log.debug("Fetching storeIds for {} products from {}", productIds.size(), inventoryServiceUrl);

                return webClient
                                .post()
                                .uri("/api/v1/inventory/products/stores")
                                .bodyValue(productIds)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Map<Long, List<Long>>>() {})
                                .timeout(Duration.ofSeconds(30))
                                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                                .doOnSuccess(map -> log.debug("Fetched storeIds for {} products", map.size()))
                                .onErrorResume(e -> {
                                        log.warn("Failed to fetch storeIds from inventory service: {}", e.getMessage());
                                        return Mono.just(Map.of());
                                });
        }
}
