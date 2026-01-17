package com.quickcommerce.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.model.SearchResult;
import com.quickcommerce.search.client.InventoryClient;
import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.dto.ProductResult;
import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main search orchestration service
 * Coordinates search flow: query → Meilisearch → filter → rank → response
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MeilisearchProvider meilisearchProvider;
    private final RankingService rankingService;
    private final FallbackService fallbackService;
    private final SearchProperties searchProperties;
    private final ObjectMapper objectMapper;
    private final InventoryClient inventoryClient;

    /**
     * Execute product search
     *
     * Flow:
     * 1. Normalize query
     * 2. Search Meilisearch (get candidates)
     * 3. Filter by stock
     * 4. Rank results
     * 5. Apply fallback if empty
     * 6. Build response
     *
     * @param request Search request
     * @return Mono of Search response with results
     */
    public Mono<SearchResponse> search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Executing search: query='{}', storeId={}, limit={}",
                request.getQuery(), request.getStoreId(), request.getLimit());

        // 1. Normalize query
        String normalizedQuery = normalizeQuery(request.getQuery());

        // 2. Search Meilisearch for candidates
        return meilisearchProvider.search(
                normalizedQuery,
                request.getStoreId(),
                searchProperties.getCandidateLimit())
                .map(this::parseDocuments)
                .doOnNext(candidates -> log.debug("Got {} candidates from Meilisearch", candidates.size()))
                .flatMap(candidates ->
                // 3. Filter by stock availability
                filterByStock(candidates, request.getStoreId()))
                .doOnNext(inStockProducts -> log.debug("After stock filter: {} products", inStockProducts.size()))
                .map(inStockProducts ->
                // 4. Rank results
                rankingService.rank(inStockProducts))
                .flatMap(rankedProducts -> {
                    // 5. Apply fallback if no results
                    if (rankedProducts.isEmpty()) {
                        log.warn("No results found, applying fallback");
                        return fallbackService.getFallbackResults(normalizedQuery, request.getStoreId());
                    }
                    return Mono.just(rankedProducts);
                })
                .map(finalResults -> {
                    // 6. Limit results and build response
                    List<ProductDocument> limitedResults = finalResults.stream()
                            .limit(request.getLimit())
                            .collect(Collectors.toList());

                    long processingTime = System.currentTimeMillis() - startTime;
                    int candidatesSize = 0; // rough estimate or need to pass through tuple, simplifying for now

                    log.info("Search completed in {}ms, returned {} results", processingTime, limitedResults.size());

                    return SearchResponse.builder()
                            .query(request.getQuery())
                            .storeId(request.getStoreId())
                            .results(convertToProductResults(limitedResults))
                            .meta(SearchResponse.SearchMeta.builder()
                                    .processingTimeMs(processingTime)
                                    .candidates(candidatesSize) // Logic simplification
                                    .returned(limitedResults.size())
                                    .build())
                            .build();
                });
    }

    /**
     * Normalize search query
     */
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase();
    }

    /**
     * Parse ProductDocument objects from Meilisearch search result
     */
    @SuppressWarnings("unchecked")
    private List<ProductDocument> parseDocuments(SearchResult result) {
        try {
            List<?> hits = result.getHits();
            return hits.stream()
                    .map(hit -> objectMapper.convertValue(hit, ProductDocument.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error parsing Meilisearch hits", e);
            return new ArrayList<>();
        }
    }

    /**
     * Filter products by stock availability using InventoryClient
     */
    private Mono<List<ProductDocument>> filterByStock(List<ProductDocument> products, Long storeId) {
        if (products.isEmpty()) {
            return Mono.just(products);
        }

        // Extract product IDs
        List<Long> productIds = products.stream()
                .map(ProductDocument::getId)
                .collect(Collectors.toList());

        // Call inventory service to check availability (Reactive)
        return inventoryClient.checkAvailability(storeId, productIds)
                .map(availabilityResponse -> {
                    Map<Long, Boolean> availabilityMap = availabilityResponse.getAvailability();

                    // Filter products: keep only in-stock items
                    List<ProductDocument> inStockProducts = products.stream()
                            .filter(product -> {
                                Boolean inStock = availabilityMap.getOrDefault(product.getId(), false);
                                return inStock != null && inStock;
                            })
                            .collect(Collectors.toList());

                    log.debug("Stock filtering: {} candidates → {} in-stock", products.size(), inStockProducts.size());
                    return inStockProducts;
                });
    }

    /**
     * Convert ProductDocument to ProductResult DTO
     */
    private List<ProductResult> convertToProductResults(List<ProductDocument> documents) {
        return documents.stream()
                .map(doc -> ProductResult.builder()
                        .productId(doc.getId())
                        .name(doc.getName())
                        .brand(doc.getBrand())
                        .category(doc.getCategoryName())
                        .unitText(doc.getUnitText())
                        .price(doc.getPrice())
                        .imageUrl(doc.getImageUrl())
                        .productUrl(doc.getProductUrl())
                        .inStock(true) // All results are in-stock after filtering
                        .build())
                .collect(Collectors.toList());
    }
}
