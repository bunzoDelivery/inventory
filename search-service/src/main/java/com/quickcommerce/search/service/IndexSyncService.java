package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.provider.MeilisearchProvider;
import com.quickcommerce.search.model.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to manage bulk synchronization of products to Meilisearch
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexSyncService {

    private final CatalogClient catalogClient;
    private final MeilisearchProvider meilisearchProvider;
    private static final int BATCH_SIZE = 500;

    /**
     * Trigger bulk sync on application startup (if configured)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // We can add a property to disable this if needed, e.g.,
        // app.search.sync-on-startup=true
        // For MVP, we'll run it to ensure index is populated.
        log.info("Application started. Triggering bulk index sync in background...");
        syncAllProducts()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> log.info("Startup bulk sync completed"),
                        e -> log.error("Startup bulk sync failed", e));
    }

    /**
     * Fetch all products and index them in batches
     */
    public Mono<Void> syncAllProducts() {
        log.info("Starting bulk product synchronization...");
        AtomicInteger count = new AtomicInteger(0);

        return catalogClient.getAllProducts()
                .buffer(BATCH_SIZE) // Batch elements into lists
                .concatMap(batch -> {
                    log.debug("Indexing batch of {} products...", batch.size());
                    return meilisearchProvider.upsertDocuments(batch)
                            .doOnSuccess(v -> {
                                int current = count.addAndGet(batch.size());
                                log.info("Indexed {} products so far...", current);
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("Bulk sync finished. Total products indexed: {}", count.get()))
                .doOnError(e -> log.error("Bulk sync failed", e));
    }
}
