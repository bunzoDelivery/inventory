package com.quickcommerce.search.service;

import com.quickcommerce.search.config.SearchProperties;
import com.quickcommerce.search.health.SyncHealthIndicator;
import com.quickcommerce.search.provider.MeilisearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Coordinates the startup sequence for the search service
 * Ensures proper ordering: Index Creation → Settings Bootstrap → Configuration Sync → Product Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartupOrchestrator {

    private final MeilisearchProvider meilisearchProvider;
    private final SearchConfigurationService configurationService;
    private final IndexSyncService indexSyncService;
    private final SearchProperties searchProperties;
    private final SyncHealthIndicator syncHealthIndicator;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSearchService() {
        log.info("=== Starting Search Service Initialization ===");

        try {
            // Step 1: Ensure index exists
            ensureIndexExists();

            // Step 1.5: Auto-bootstrap default settings if DB is empty
            ensureDefaultSettings();

            // Step 2: Sync settings and synonyms from DB to Meilisearch
            syncConfiguration();

            // Step 3: Sync products (if enabled)
            if (searchProperties.getSync().isEnableOnStartup()) {
                syncProducts();
            } else {
                log.info("Product sync disabled in configuration");
            }

            log.info("=== Search Service Initialization Complete ===");

        } catch (Exception e) {
            log.error("FATAL: Search service initialization failed", e);
            syncHealthIndicator.updateStatus(
                SyncHealthIndicator.SyncState.FAILED,
                "Startup initialization failed: " + e.getMessage(),
                0
            );
            // Don't throw - let app start but mark service as unhealthy
        }
    }

    private void ensureIndexExists() {
        log.info("Step 1: Checking if index exists...");
        try {
            var stats = meilisearchProvider.getProductsIndex().getStats();
            log.info("Index exists with {} documents", stats.getNumberOfDocuments());
        } catch (Exception e) {
            log.info("Index does not exist, creating with primaryKey='id'...");
            meilisearchProvider.createIndex().block();
            log.info("Index created successfully");
        }
    }

    private void ensureDefaultSettings() {
        log.info("Step 1.5: Checking if default settings exist...");
        try {
            Integer count = configurationService.bootstrapDefaultSettings("system")
                .block();
            
            if (count != null && count > 0) {
                log.info("Auto-bootstrapped {} default settings", count);
            } else {
                log.info("Settings already exist, skipping bootstrap");
            }
        } catch (Exception e) {
            log.error("Failed to bootstrap default settings", e);
            throw new RuntimeException("Cannot start without settings", e);
        }
    }

    private void syncConfiguration() {
        log.info("Step 2: Syncing settings and synonyms to Meilisearch...");
        try {
            var task = configurationService.publishConfiguration()
                .retry(3)
                .block();
            log.info("Configuration synced. Task UID: {}", task.getTaskUid());
            
            // Wait for Meilisearch to process the settings update
            Thread.sleep(1000);
            
        } catch (Exception e) {
            log.error("Failed to sync configuration", e);
            throw new RuntimeException("Configuration sync failed", e);
        }
    }

    private void syncProducts() {
        log.info("Step 3: Syncing products to Meilisearch...");
        
        syncHealthIndicator.updateStatus(
            SyncHealthIndicator.SyncState.IN_PROGRESS,
            "Starting initial product sync",
            0
        );
        
        try {
            Integer count = indexSyncService.syncAllProducts().block();
            log.info("Product sync complete. {} products indexed", count);
            
            syncHealthIndicator.updateStatus(
                SyncHealthIndicator.SyncState.HEALTHY,
                "Sync completed successfully",
                count != null ? count : 0
            );
        } catch (Exception e) {
            log.error("Product sync failed", e);
            syncHealthIndicator.updateStatus(
                SyncHealthIndicator.SyncState.FAILED,
                "Product sync failed: " + e.getMessage(),
                0
            );
            // Don't throw - service can still work for search if index has data
        }
    }
}
