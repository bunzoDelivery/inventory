package com.quickcommerce.search.controller;

import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.service.SearchService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for search operations with rate limiting
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    @Qualifier("searchRateLimiter")
    private final RateLimiter rateLimiter;

    /**
     * Main search endpoint with rate limiting (100 req/min)
     *
     * POST /search
     * 
     * Request body:
     * {
     * "query": "milk",
     * "storeId": 1,
     * "limit": 20
     * }
     *
     * @param request Search request
     * @return Mono of Search response with results
     */
    @PostMapping
    public Mono<ResponseEntity<SearchResponse>> search(@Valid @RequestBody SearchRequest request) {
        log.info("Search request: query='{}', storeId={}", request.getQuery(), request.getStoreId());

        return searchService.search(request)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .map(ResponseEntity::ok)
                .onErrorResume(io.github.resilience4j.ratelimiter.RequestNotPermitted.class, e -> {
                    log.warn("Rate limit exceeded for search request");
                    return Mono.just(ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .build());
                });
    }
}
