package com.quickcommerce.search.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.quickcommerce.search.AbstractIntegrationTest;
import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Failing due to Docker environment issues on Windows agent")
class SearchIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private MeilisearchProvider meilisearchProvider;

        @BeforeEach
        void setUp() throws Exception {
                // Clear index
                meilisearchProvider.deleteIndex().block();
                meilisearchProvider.createIndex().block();

                // Seed Meilisearch with test data
                ProductDocument doc1 = ProductDocument.builder()
                                .id(1L)
                                .name("Integration Test Milk")
                                .brand("TestBrand")
                                .price(BigDecimal.valueOf(10.0))
                                .storeIds(List.of(1L))
                                .isActive(true)
                                .isBestseller(false)
                                .searchPriority(0)
                                .orderCount(0)
                                .build();

                meilisearchProvider.upsertDocument(doc1).block();

                // Wait for Meilisearch indexing (async)
                Thread.sleep(1000);
        }

        @Test
        void search_shouldReturnResults_whenInventoryAvailable() {
                // Mock Inventory Service
                wireMockServer.stubFor(post(urlEqualTo("/inventory/availability"))
                                .willReturn(aResponse()
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"storeId\": 1, \"availability\": {\"1\": true}}")));

                // Search Request
                SearchRequest request = SearchRequest.builder()
                                .query("milk")
                                .storeId(1L)
                                .page(1)
                                .pageSize(10)
                                .build();

                ResponseEntity<SearchResponse> response = restTemplate.postForEntity(
                                "/search",
                                new HttpEntity<>(request),
                                SearchResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getResults()).hasSize(1);
                assertThat(response.getBody().getResults().get(0).getName()).isEqualTo("Integration Test Milk");
                assertThat(response.getBody().getMeta().getTotalHits()).isEqualTo(1);
        }

        @Test
        void search_shouldFilterOutOfStock_whenInventoryReturnsFalse() {
                // Mock Inventory Service ID 1 = false
                wireMockServer.stubFor(post(urlEqualTo("/inventory/availability"))
                                .willReturn(aResponse()
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"storeId\": 1, \"availability\": {\"1\": false}}")));

                SearchRequest request = SearchRequest.builder()
                                .query("milk")
                                .storeId(1L)
                                .build();

                ResponseEntity<SearchResponse> response = restTemplate.postForEntity(
                                "/search",
                                new HttpEntity<>(request),
                                SearchResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getResults()).isEmpty();
        }
}
