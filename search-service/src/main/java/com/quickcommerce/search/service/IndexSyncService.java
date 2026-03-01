package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.client.InventoryClient;
import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.health.SyncHealthIndicator;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service to manage bulk synchronization of products to Meilisearch
 * Enhanced with retry logic, health tracking, and error handling
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexSyncService {

    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final MeilisearchProvider meilisearchProvider;
    private final SearchProperties searchProperties;
    private final SyncHealthIndicator syncHealthIndicator;

    /**
     * Fetch all products and index them in batches with retry logic
     * @return Mono containing total count of synced products
     */
    public Mono<Integer> syncAllProducts() {
        log.info("Starting bulk product synchronization...");
        AtomicInteger count = new AtomicInteger(0);
        int batchSize = searchProperties.getSync().getBatchSize();

        return catalogClient.getAllProducts()
            .collectList()
            .flatMap(allProducts -> {
                log.info("Fetched {} products from catalog, enriching with inventory data...", allProducts.size());
                
                // Extract product IDs
                List<Long> productIds = allProducts.stream()
                    .map(ProductDocument::getId)
                    .collect(Collectors.toList());
                
                // Fetch storeIds for all products in bulk
                return inventoryClient.getStoresForProducts(productIds)
                    .map(storeIdsMap -> {
                        // Enrich each product with storeIds
                        allProducts.forEach(product -> {
                            List<Long> storeIds = storeIdsMap.get(product.getId());
                            if (storeIds != null && !storeIds.isEmpty()) {
                                product.setStoreIds(storeIds);
                            }
                        });
                        return allProducts;
                    });
            })
            .flatMapMany(Flux::fromIterable)
            .buffer(batchSize)
            .concatMap(batch -> {
                log.debug("Indexing batch of {} products...", batch.size());
                return meilisearchProvider.upsertDocuments(batch)
                    .doOnSuccess(v -> {
                        int current = count.addAndGet(batch.size());
                        log.info("Indexed {} products so far...", current);
                    })
                    .retryWhen(createRetrySpec("batch indexing"))
                    .onErrorResume(e -> {
                        log.error("Failed to index batch after retries, skipping {} products", batch.size(), e);
                        syncHealthIndicator.updateStatus(
                            SyncHealthIndicator.SyncState.DEGRADED,
                            "Some batches failed to sync",
                            count.get()
                        );
                        return Mono.empty(); // Continue with next batch
                    });
            })
            .then(Mono.fromCallable(() -> count.get()))
            .doOnSuccess(total -> log.info("Bulk sync finished. Total products indexed: {}", total))
            .doOnError(e -> log.error("Bulk sync failed", e));
    }

    /**
     * Create retry specification with exponential backoff
     */
    private Retry createRetrySpec(String operation) {
        SearchProperties.Sync syncConfig = searchProperties.getSync();
        
        return Retry.backoff(syncConfig.getMaxRetries(), Duration.ofMillis(syncConfig.getRetryDelayMs()))
            .maxBackoff(Duration.ofMillis(syncConfig.getMaxRetryDelayMs()))
            .doBeforeRetry(signal -> 
                log.warn("Retrying {} after failure (attempt {}/{}): {}", 
                    operation,
                    signal.totalRetries() + 1,
                    syncConfig.getMaxRetries(),
                    signal.failure().getMessage())
            )
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                new RuntimeException("Failed " + operation + " after " + 
                    syncConfig.getMaxRetries() + " retries", 
                    retrySignal.failure())
            );
    }
}
