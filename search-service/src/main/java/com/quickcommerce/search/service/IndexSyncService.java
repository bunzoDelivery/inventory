package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.client.InventoryClient;
import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.health.SyncHealthIndicator;
import com.quickcommerce.search.mapper.ProductDocumentMapper;
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
     * Full sync: wipe the index clean, fetch all products from catalog,
     * enrich with store IDs, and insert fresh.
     * Designed for startup and nightly re-sync (no users during sync window).
     * TODO: migrate to Meilisearch index-swap for zero-downtime when scaling.
     */
    public Mono<Integer> syncAllProducts() {
        log.info("Starting full product sync (delete-all + insert-fresh)...");
        AtomicInteger count = new AtomicInteger(0);
        int batchSize = searchProperties.getSync().getBatchSize();

        return meilisearchProvider.deleteAllDocuments()
            .then(catalogClient.getAllProducts()
                .map(ProductDocumentMapper::toProductDocument)
                .collectList())
            .flatMap(allProducts -> {
                log.info("Fetched {} products from catalog, enriching with store data...", allProducts.size());

                List<Long> productIds = allProducts.stream()
                    .map(ProductDocument::getId)
                    .collect(Collectors.toList());

                return inventoryClient.getStoresForProducts(productIds)
                    .map(storeIdsMap -> {
                        allProducts.forEach(product -> {
                            List<Long> storeIds = storeIdsMap.get(product.getId());
                            if (storeIds != null && !storeIds.isEmpty()) {
                                product.setStoreIds(storeIds);
                            }
                        });
                        return allProducts;
                    });
            })
            .flatMap(allProducts ->
                Flux.fromIterable(allProducts)
                    .buffer(batchSize)
                    .concatMap(batch -> {
                        log.debug("Indexing batch of {} products...", batch.size());
                        return meilisearchProvider.upsertDocuments(batch)
                            .doOnSuccess(v -> {
                                int current = count.addAndGet(batch.size());
                                log.info("Indexed {} / {} products", current, allProducts.size());
                            })
                            .retryWhen(createRetrySpec("batch indexing"))
                            .onErrorResume(e -> {
                                log.error("Failed to index batch after retries, skipping {} products", batch.size(), e);
                                syncHealthIndicator.updateStatus(
                                    SyncHealthIndicator.SyncState.DEGRADED,
                                    "Some batches failed to sync",
                                    count.get()
                                );
                                return Mono.empty();
                            });
                    })
                    .then(Mono.fromCallable(count::get))
            )
            .doOnSuccess(total -> log.info("Full sync complete. {} products indexed.", total))
            .doOnError(e -> log.error("Full sync failed", e));
    }

    private Retry createRetrySpec(String operation) {
        SearchProperties.Sync syncConfig = searchProperties.getSync();

        return Retry.backoff(syncConfig.getMaxRetries(), Duration.ofMillis(syncConfig.getRetryDelayMs()))
            .maxBackoff(Duration.ofMillis(syncConfig.getMaxRetryDelayMs()))
            .doBeforeRetry(signal ->
                log.warn("Retrying {} (attempt {}/{}): {}",
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
