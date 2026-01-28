package com.quickcommerce.search.controller;

import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.dto.ProductResult;
import com.quickcommerce.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SearchService searchService;

    @Test
    void search_shouldReturn200AndResults() {
        SearchResponse response = SearchResponse.builder()
                .results(List.of(ProductResult.builder().productId(1L).name("Test Product").build()))
                .meta(SearchResponse.SearchMeta.builder().totalHits(1).build())
                .build();

        when(searchService.search(any(SearchRequest.class))).thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\": \"milk\", \"storeId\": 1}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.results[0].name").isEqualTo("Test Product")
                .jsonPath("$.meta.totalHits").isEqualTo(1);
    }

    @Test
    void search_shouldReturnBadRequest_whenStoreIdMissing() {
        webTestClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\": \"milk\"}") // Missing storeId
                .exchange()
                .expectStatus().isBadRequest();
    }
}
