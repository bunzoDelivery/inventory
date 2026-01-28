package com.quickcommerce.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.model.SearchResult;
import com.quickcommerce.search.client.InventoryClient;
import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.dto.AvailabilityResponse;
import com.quickcommerce.search.dto.SearchRequest;
import com.quickcommerce.search.dto.SearchResponse;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

        @Mock
        private MeilisearchProvider meilisearchProvider;

        @Mock
        private RankingService rankingService;

        @Mock
        private FallbackService fallbackService;

        @Mock
        private SearchProperties searchProperties;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private InventoryClient inventoryClient;

        @InjectMocks
        private SearchService searchService;

        private SearchRequest searchRequest;
        private ProductDocument productDoc;
        private SearchResult searchResult;

        @BeforeEach
        void setUp() {
                searchRequest = SearchRequest.builder()
                                .query("milk")
                                .storeId(1L)
                                .page(1)
                                .pageSize(10)
                                .build();

                productDoc = ProductDocument.builder()
                                .id(100L)
                                .name("Amul Milk")
                                .isActive(true)
                                .storeIds(List.of(1L))
                                .build();

                // Create a Mock SearchResult
                // Note: SearchResult is hard to mock because it's final or has no setters in
                // SDK.
                // We might need deep stubbing or real object if possible.
                // Assuming we can mock it:
                searchResult = mock(SearchResult.class);
        }

        @Test
        void search_shouldReturnResults_whenMeilisearchReturnsHits() {
                // Arrange
                // searchProperties.getCandidateLimit() is no longer called in search()

                // Mock Meilisearch response
                when(meilisearchProvider.search(anyString(), anyLong(), anyInt(), anyInt()))
                                .thenReturn(Mono.just(searchResult));

                // Mock SearchResult behavior
                ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
                HashMap<String, Object> hit = new HashMap<>();
                hit.put("id", 100);
                hits.add(hit);

                when(searchResult.getHits()).thenReturn(hits);
                when(searchResult.getEstimatedTotalHits()).thenReturn(1);

                // ObjectMapper mapping
                when(objectMapper.convertValue(anyMap(), eq(ProductDocument.class)))
                                .thenReturn(productDoc);

                // Inventory check
                AvailabilityResponse availResponse = AvailabilityResponse.builder()
                                .storeId(1L)
                                .availability(Map.of(100L, true))
                                .build();
                when(inventoryClient.checkAvailability(anyLong(), anyList()))
                                .thenReturn(Mono.just(availResponse));

                // Ranking
                when(rankingService.rank(anyList())).thenReturn(List.of(productDoc));

                // Act
                Mono<SearchResponse> resultMono = searchService.search(searchRequest);

                // Assert
                StepVerifier.create(resultMono)
                                .expectNextMatches(response -> {
                                        return response.getResults().size() == 1
                                                        && response.getMeta().getTotalHits() == 1
                                                        && response.getMeta().getPage() == 1;
                                })
                                .verifyComplete();
        }

        @Test
        void search_shouldFilterOutOfStockProducts() {
                // Arrange
                when(meilisearchProvider.search(anyString(), anyLong(), anyInt(), anyInt()))
                                .thenReturn(Mono.just(searchResult));

                ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
                HashMap<String, Object> hit = new HashMap<>();
                hit.put("id", 100);
                hits.add(hit);
                when(searchResult.getHits()).thenReturn(hits);
                when(searchResult.getEstimatedTotalHits()).thenReturn(1);

                when(objectMapper.convertValue(anyMap(), eq(ProductDocument.class)))
                                .thenReturn(productDoc);

                // Inventory check returns FALSE for this product
                AvailabilityResponse availResponse = AvailabilityResponse.builder()
                                .storeId(1L)
                                .availability(Map.of(100L, false))
                                .build();
                when(inventoryClient.checkAvailability(anyLong(), anyList()))
                                .thenReturn(Mono.just(availResponse));

                // Ranking (empty list now)
                when(rankingService.rank(anyList())).thenReturn(Collections.emptyList());

                // Fallback (return empty for this test to signify simple filter check)
                when(fallbackService.getFallbackResults(anyString(), anyLong()))
                                .thenReturn(Mono.just(Collections.emptyList()));

                // Act
                Mono<SearchResponse> resultMono = searchService.search(searchRequest);

                // Assert
                StepVerifier.create(resultMono)
                                .expectNextMatches(response -> {
                                        return response.getResults().isEmpty()
                                                        && response.getMeta().getTotalHits() == 0; // After fallback
                                                                                                   // empty
                                })
                                .verifyComplete();
        }
}
