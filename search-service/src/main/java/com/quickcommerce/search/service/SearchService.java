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
import reactor.util.function.Tuples;

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
     */
    public Mono<SearchResponse> search(SearchRequest request) {
        return Mono.defer(() -> { // Defer for fresh context
            String normalizedQuery = normalizeQuery(request.getQuery());

            log.info("Executing search: query='{}', storeId={}, limit={}",
                    request.getQuery(), request.getStoreId(), request.getLimit());

            return meilisearchProvider.search(
                    normalizedQuery,
                    request.getStoreId(),
                    searchProperties.getCandidateLimit())
                    .map(this::parseDocuments)
                    .onErrorResume(e -> {
                        log.error("CRITICAL DATA ERROR: Parsing failed", e);
                        return Mono.just(new ArrayList<>());
                    })
                    // 1. Capture Candidate Count using Tuple
                    .map(candidates -> Tuples.of(candidates, candidates.size()))
                    .doOnNext(tuple -> log.debug("Got {} candidates from Meilisearch", tuple.getT2()))
                    .flatMap(tuple -> {
                        List<ProductDocument> candidates = tuple.getT1();
                        int candidateCount = tuple.getT2();

                        return filterByStock(candidates, request.getStoreId())
                                .doOnNext(inStockProducts -> log.debug("After stock filter: {} products",
                                        inStockProducts.size()))
                                .map(rankingService::rank)
                                .flatMap(ranked -> {
                                    if (ranked.isEmpty()) {
                                        log.warn("No results found, applying fallback");
                                        return fallbackService.getFallbackResults(normalizedQuery,
                                                request.getStoreId());
                                    }
                                    return Mono.just(ranked);
                                })
                                // 2. Pass Count down to final step
                                .map(finalResults -> Tuples.of(finalResults, candidateCount));
                    });
        })
                .elapsed() // 3. Reactive Timing
                .map(tupleTime -> {
                    long timeMs = tupleTime.getT1();
                    var dataTuple = tupleTime.getT2();
                    List<ProductDocument> results = dataTuple.getT1();
                    int candidateCount = dataTuple.getT2(); // Now we have the real count!

                    List<ProductDocument> limitedResults = results.stream()
                            .limit(request.getLimit())
                            .collect(Collectors.toList());

                    log.info("Search completed in {}ms, candidates: {}, returned: {}",
                            timeMs, candidateCount, limitedResults.size());

                    return SearchResponse.builder()
                            .query(request.getQuery())
                            .storeId(request.getStoreId())
                            .results(convertToProductResults(limitedResults))
                            .meta(SearchResponse.SearchMeta.builder()
                                    .processingTimeMs(timeMs)
                                    .candidates(candidateCount)
                                    .returned(limitedResults.size())
                                    .build())
                            .build();
                });
    }

    /**
     * Normalizes query string
     */
    private String normalizeQuery(String query) {
        if (query == null)
            return "";
        return query.trim().toLowerCase();
    }

    /**
     * Parse ProductDocument objects from Meilisearch search result
     */
    @SuppressWarnings("unchecked")
    private List<ProductDocument> parseDocuments(SearchResult result) {
        List<?> hits = result.getHits();
        return hits.stream()
                .map(hit -> objectMapper.convertValue(hit, ProductDocument.class))
                .collect(Collectors.toList());
    }

    /**
     * Filter products by stock availability using InventoryClient
     * Implements "Smart Fallback" to Meilisearch index data if Inventory Service
     * fails
     */
    private Mono<List<ProductDocument>> filterByStock(List<ProductDocument> products, Long storeId) {
        if (products.isEmpty()) {
            return Mono.just(products);
        }

        List<Long> productIds = products.stream()
                .map(ProductDocument::getId)
                .collect(Collectors.toList());

        return inventoryClient.checkAvailability(storeId, productIds)
                .map(response -> {
                    Map<Long, Boolean> liveStock = response.getAvailability();

                    // -------------------------------------------------------
                    // SMART FALLBACK LOGIC
                    // -------------------------------------------------------
                    if (liveStock == null) {
                        log.warn("Inventory Service failed. Falling back to Meilisearch index data.");

                        // Trust the data we already have in ProductDocument
                        // If Meilisearch returned it, it's considered active/in-stock based on
                        // Meilisearch's own filtering.
                        return products.stream()
                                .filter(doc -> {
                                    // Assuming Meilisearch provider already filters by 'isActive = true'
                                    // So, if it's in 'products', it's considered active by the index.
                                    return true;
                                })
                                .collect(Collectors.toList());
                    }

                    // Normal Flow (Live Check)
                    return products.stream()
                            .filter(product -> Boolean.TRUE.equals(liveStock.get(product.getId())))
                            .collect(Collectors.toList());
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
