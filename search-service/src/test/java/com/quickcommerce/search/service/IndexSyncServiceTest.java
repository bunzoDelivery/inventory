package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.client.InventoryClient;
import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.dto.CatalogProductDto;
import com.quickcommerce.search.health.SyncHealthIndicator;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexSyncServiceTest {

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private MeilisearchProvider meilisearchProvider;

    @Mock
    private SearchProperties searchProperties;

    @Mock
    private SyncHealthIndicator syncHealthIndicator;

    @InjectMocks
    private IndexSyncService indexSyncService;

    @BeforeEach
    void setUp() {
        SearchProperties.Sync sync = new SearchProperties.Sync();
        sync.setBatchSize(50);
        when(searchProperties.getSync()).thenReturn(sync);
    }

    @Test
    void syncAllProducts_shouldFetchAndUpsertInBatches() {
        // Arrange: CatalogClient returns CatalogProductDto; mapper converts to ProductDocument
        CatalogProductDto dto1 = new CatalogProductDto();
        dto1.setId(1L);
        CatalogProductDto dto2 = new CatalogProductDto();
        dto2.setId(2L);

        when(catalogClient.getAllProducts()).thenReturn(Flux.just(dto1, dto2));
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
        // When catalog returns error, sync propagates it (never reaches getStoresForProducts)
        when(catalogClient.getAllProducts()).thenReturn(Flux.error(new RuntimeException("Catalog Down")));

        Mono<Integer> result = indexSyncService.syncAllProducts();

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }
}
