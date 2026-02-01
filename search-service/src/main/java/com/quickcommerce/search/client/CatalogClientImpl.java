package com.quickcommerce.search.client;

import com.quickcommerce.search.model.ProductDocument;
import lombok.extern.slf4j.Slf4j;
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
 * Calls actual Catalog Service API
 */
@Slf4j
@Component
public class CatalogClientImpl implements CatalogClient {

        private final WebClient webClient;
        private final Duration timeout;
        private final String catalogServiceUrl;

        public CatalogClientImpl(WebClient.Builder webClientBuilder,
                        @Value("${clients.catalog.url}") String catalogServiceUrl,
                        @Value("${clients.catalog.timeout:300ms}") Duration timeout) {
                this.catalogServiceUrl = catalogServiceUrl;
                this.timeout = timeout;
                this.webClient = webClientBuilder
                                .baseUrl(catalogServiceUrl)
                                .build();
        }

        @Override
        public Mono<List<ProductDocument>> getBestsellers(Long storeId, int limit) {
                log.debug("Getting {} bestsellers for store {} from {}",
                                limit, storeId, catalogServiceUrl);

                return webClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/catalog/products/bestsellers")
                                                .queryParam("storeId", storeId)
                                                .queryParam("limit", limit)
                                                .build())
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<ProductDocument>>() {
                                })
                                .timeout(timeout)
                                .doOnSuccess(products -> log.debug("Received {} bestseller products",
                                                products != null ? products.size() : 0))
                                .onErrorResume(e -> {
                                        log.error("Error calling catalog service for bestsellers", e);
                                        return Mono.just(new ArrayList<>());
                                })
                                .map(products -> products != null ? products : new ArrayList<>());
        }

        @Override
        public Mono<List<ProductDocument>> getProductsByCategory(Long categoryId, int limit) {
                log.debug("Getting {} products from category {} from {}",
                                limit, categoryId, catalogServiceUrl);

                return webClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/catalog/products/category/{categoryId}")
                                                .queryParam("limit", limit)
                                                .build(categoryId))
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<ProductDocument>>() {
                                })
                                .timeout(timeout)
                                .doOnSuccess(
                                                products -> log.debug("Received {} category products",
                                                                products != null ? products.size() : 0))
                                .onErrorResume(e -> {
                                        log.error("Error calling catalog service for category products", e);
                                        return Mono.just(new ArrayList<>());
                                })
                                .map(products -> products != null ? products : new ArrayList<>());
        }

        @Override
        public reactor.core.publisher.Flux<ProductDocument> getAllProducts() {
                log.info("Fetching all products from available catalog: {}", catalogServiceUrl);
                return webClient
                                .get()
                                .uri("/api/v1/catalog/products/all") // Updated to point to new endpoint
                                // MVP: Simplified to a single large fetch or JSON stream
                                // Real world: Pagination required. For MVP, assuming a specific "stream" or
                                // "all" endpoint.
                                // Let's assume /catalog/products returns a list, we will convert to Flux
                                .retrieve()
                                .bodyToFlux(ProductDocument.class)
                                .timeout(Duration.ofSeconds(60)) // Longer timeout for bulk fetch
                                .onErrorResume(e -> {
                                        log.error("Error fetching all products from catalog", e);
                                        return reactor.core.publisher.Flux.empty();
                                });
        }
}
