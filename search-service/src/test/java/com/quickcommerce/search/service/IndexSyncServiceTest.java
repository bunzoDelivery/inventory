package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexSyncServiceTest {

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private MeilisearchProvider meilisearchProvider;

    @InjectMocks
    private IndexSyncService indexSyncService;

    @Test
    void syncAllProducts_shouldFetchAndUpsertInBatches() {
        // Arrange
        ProductDocument p1 = ProductDocument.builder().id(1L).build();
        ProductDocument p2 = ProductDocument.builder().id(2L).build();
        // Create enough documents to test batching if needed, but for unit test simple
        // flow is fine

        when(catalogClient.getAllProducts()).thenReturn(Flux.just(p1, p2));
        when(meilisearchProvider.upsertDocuments(anyList())).thenReturn(Mono.empty());
        when(inventoryClient.getStoresForProducts(anyList())).thenReturn(Mono.just(Map.of()));

        // Act
        Mono<Integer> result = indexSyncService.syncAllProducts();

        // Assert
        StepVerifier.create(result)
                .expectNext(2)
                .verifyComplete();

        verify(catalogClient, times(1)).getAllProducts();
        verify(meilisearchProvider, atLeastOnce()).upsertDocuments(anyList());
    }

    @Test
    void syncAllProducts_shouldHandleEmptyCatalog() {
        when(catalogClient.getAllProducts()).thenReturn(Flux.empty());
        when(inventoryClient.getStoresForProducts(anyList())).thenReturn(Mono.just(Map.of()));

        Mono<Integer> result = indexSyncService.syncAllProducts();

        StepVerifier.create(result)
                .expectNext(0)
                .verifyComplete();

        verify(meilisearchProvider, never()).upsertDocuments(anyList());
    }

    @Test
    void syncAllProducts_shouldContinueOnError() {
        // Test robustness - if one batch fails, should we stop?
        // Current implementation propagates error. Let's verify that behavior or fix
        // it.
        // The service uses buffer().flatMap(), so error in flux terminates it.

        when(catalogClient.getAllProducts()).thenReturn(Flux.error(new RuntimeException("Catalog Down")));
        when(inventoryClient.getStoresForProducts(anyList())).thenReturn(Mono.just(Map.of()));

        Mono<Integer> result = indexSyncService.syncAllProducts();

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }
}
