package com.quickcommerce.search.controller;

import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for search operations
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Main search endpoint
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
                .map(ResponseEntity::ok);
    }
}
