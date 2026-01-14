package com.quickcommerce.search.client;

import com.quickcommerce.search.model.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Real implementation of CatalogClient using WebClient
 * Calls actual Catalog Service API
 */
@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class CatalogClientImpl implements CatalogClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${clients.catalog.url}")
    private String catalogServiceUrl;

    @Value("${clients.catalog.timeout:300ms}")
    private Duration timeout;

    @Override
    public List<ProductDocument> getBestsellers(Long storeId, int limit) {
        log.debug("Getting {} bestsellers for store {} from {}", 
                  limit, storeId, catalogServiceUrl);

        try {
            List<ProductDocument> products = webClientBuilder
                    .baseUrl(catalogServiceUrl)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/catalog/products/bestsellers")
                            .queryParam("storeId", storeId)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<ProductDocument>>() {})
                    .timeout(timeout)
                    .block();

            log.debug("Received {} bestseller products", products != null ? products.size() : 0);
            
            return products != null ? products : new ArrayList<>();

        } catch (Exception e) {
            log.error("Error calling catalog service for bestsellers", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ProductDocument> getProductsByCategory(Long categoryId, int limit) {
        log.debug("Getting {} products from category {} from {}", 
                  limit, categoryId, catalogServiceUrl);

        try {
            List<ProductDocument> products = webClientBuilder
                    .baseUrl(catalogServiceUrl)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/catalog/categories/{id}/products")
                            .queryParam("limit", limit)
                            .build(categoryId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<ProductDocument>>() {})
                    .timeout(timeout)
                    .block();

            log.debug("Received {} category products", products != null ? products.size() : 0);
            
            return products != null ? products : new ArrayList<>();

        } catch (Exception e) {
            log.error("Error calling catalog service for category products", e);
            return new ArrayList<>();
        }
    }
}
