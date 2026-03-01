package com.quickcommerce.search.client;

import com.quickcommerce.search.dto.CatalogProductDto;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Real implementation of CatalogClient using WebClient
 * Calls actual Catalog Service API with circuit breaker protection
 */
@Slf4j
@Component
public class CatalogClientImpl implements CatalogClient {

        private final WebClient webClient;
        private final Duration timeout;
        private final String catalogServiceUrl;
        private final CircuitBreaker circuitBreaker;

        public CatalogClientImpl(WebClient.Builder webClientBuilder,
                        @Value("${clients.catalog.url}") String catalogServiceUrl,
                        @Value("${clients.catalog.timeout:300ms}") Duration timeout,
                        @Qualifier("catalogCircuitBreaker") CircuitBreaker circuitBreaker) {
                this.catalogServiceUrl = catalogServiceUrl;
                this.timeout = timeout;
                this.circuitBreaker = circuitBreaker;
                this.webClient = webClientBuilder
                                .baseUrl(catalogServiceUrl)
                                .build();
        }

        @Override
        public Mono<List<CatalogProductDto>> getBestsellers(Long storeId, int limit) {
                log.debug("Getting {} bestsellers for store {} from {} (CB: {})",
                                limit, storeId, catalogServiceUrl, circuitBreaker.getState());

                return webClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/catalog/products/bestsellers")
                                                .queryParam("storeId", storeId)
                                                .queryParam("limit", limit)
                                                .build())
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<CatalogProductDto>>() {
                                })
                                .timeout(timeout)
                                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                                .doOnSuccess(products -> log.debug("Received {} bestseller products",
                                                products != null ? products.size() : 0))
                                .onErrorResume(e -> {
                                        log.error("Error calling catalog service for bestsellers (CB: {}): {}", 
                                                circuitBreaker.getState(), e.getMessage());
                                        return Mono.just(new ArrayList<>());
                                })
                                .map(products -> products != null ? products : new ArrayList<>());
        }

        @Override
        public Mono<List<CatalogProductDto>> getProductsByCategory(Long categoryId, int limit) {
                log.debug("Getting {} products from category {} from {} (CB: {})",
                                limit, categoryId, catalogServiceUrl, circuitBreaker.getState());

                return webClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/catalog/products/category/{categoryId}")
                                                .queryParam("limit", limit)
                                                .build(categoryId))
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<CatalogProductDto>>() {
                                })
                                .timeout(timeout)
                                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                                .doOnSuccess(
                                                products -> log.debug("Received {} category products",
                                                                products != null ? products.size() : 0))
                                .onErrorResume(e -> {
                                        log.error("Error calling catalog service for category products (CB: {}): {}", 
                                                circuitBreaker.getState(), e.getMessage());
                                        return Mono.just(new ArrayList<>());
                                })
                                .map(products -> products != null ? products : new ArrayList<>());
        }

        @Override
        public reactor.core.publisher.Flux<CatalogProductDto> getAllProducts() {
                log.info("Fetching all products from available catalog: {} (CB: {})", 
                        catalogServiceUrl, circuitBreaker.getState());
                return webClient
                                .get()
                                .uri("/api/v1/catalog/products/all")
                                .retrieve()
                                .bodyToFlux(CatalogProductDto.class)
                                .timeout(Duration.ofSeconds(60))
                                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                                .onErrorResume(e -> {
                                        log.error("Error fetching all products from catalog (CB: {}): {}", 
                                                circuitBreaker.getState(), e.getMessage());
                                        return reactor.core.publisher.Flux.empty();
                                });
        }
}
