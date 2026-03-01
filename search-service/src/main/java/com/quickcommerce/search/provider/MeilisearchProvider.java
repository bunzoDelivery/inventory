package com.quickcommerce.search.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.Settings;
import com.quickcommerce.search.config.MeilisearchProperties;
import com.quickcommerce.search.model.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider/Wrapper for Meilisearch operations
 * Handles all interactions with Meilisearch search engine
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeilisearchProvider {

    private final Client meilisearchClient;
    private final MeilisearchProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Gets the products index
     */
    public Index getProductsIndex() {
        return meilisearchClient.index(properties.getIndexName());
    }

    /**
     * Search for products using query and filters
     */
    /**
     * Search for products using query, filters, and pagination
     * Optimized with attributesToRetrieve for reduced payload size
     */
    public Mono<SearchResult> search(String query, Long storeId, int page, int pageSize) {
        return Mono.fromCallable(() -> {
            Index index = getProductsIndex();

            int offset = (page - 1) * pageSize;

            SearchRequest searchRequest = SearchRequest.builder()
                    .q(query)
                    .offset(offset)
                    .limit(pageSize)
                    .filter(new String[] { buildFilter(storeId) })
                    .attributesToRetrieve(new String[] {
                        "id", "name", "brand", "categoryName", "unitText", 
                        "price", "imageUrl", "productUrl", "isActive"
                    })
                    .build();

            log.debug("Executing search: '{}', storeId: {}, page: {}, size: {}, offset: {}",
                    query, storeId, page, pageSize, offset);

            // Index.search() returns Searchable interface - cast to SearchResult
            return (SearchResult) index.search(searchRequest);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.debug("Search returned {} hits (total: {}) in {}ms",
                        result.getHits().size(), result.getEstimatedTotalHits(), result.getProcessingTimeMs()))
                .doOnError(e -> log.error("Error executing search query: '{}', storeId: {}", query, storeId, e));
    }

    /**
     * Creates the products index with initial settings
     */
    public Mono<Void> createIndex() {
        return Mono.<Void>fromCallable(() -> {
            log.info("Creating index: {}", properties.getIndexName());
            // Meilisearch createIndex returns Task, which we ignore
            meilisearchClient.createIndex(properties.getIndexName(), "id");
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("Index created successfully: {}", properties.getIndexName()))
                .doOnError(e -> log.error("Error creating index: {}", properties.getIndexName(), e));
    }

    /**
     * Check if index exists
     * @return true if index exists, false otherwise
     */
    public boolean indexExists() {
        try {
            getProductsIndex().getStats();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Updates index settings dynamically from configuration
     * Returns the TaskInfo for tracking
     */
    public com.meilisearch.sdk.model.TaskInfo updateSettingsBlocking(Settings settings) {
        try {
            Index index = getProductsIndex();
            // This is a blocking call to the SDK, which returns TaskInfo immediately (not
            // the task result)
            return index.updateSettings(settings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update settings", e);
        }
    }

    /**
     * Updates index settings (Deprecated: Use SearchConfigurationService)
     * Keeping for basic init if needed, but it should arguably be removed or
     * delegate.
     * Let's remove the hardcoded logic to prevent drift.
     */
    public Mono<Void> updateIndexSettings() {
        // No-op or removed, as settings are now managed by DB.
        // However, to keep existing code compiling if referenced elsewhere
        // (AdminController currently calls it),
        // I should either remove calls to it or redirect.
        // AdminController is being replaced by SearchAdminController.
        return Mono.empty();
    }

    /**
     * Adds or updates a single document in the index
     */
    public Mono<Void> upsertDocument(ProductDocument document) {
        return Mono.fromCallable(() -> {
            Index index = getProductsIndex();
            index.addDocuments("[" + toJson(document) + "]", "id");
            log.debug("Upserted document: {}", document.getId());
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error upserting document: {}", document.getId(), e))
                .then();
    }

    /**
     * Adds or updates multiple documents in the index
     */
    public Mono<Void> upsertDocuments(List<ProductDocument> documents) {
        return Mono.fromCallable(() -> {
            Index index = getProductsIndex();
            String json = toJsonArray(documents);
            index.addDocuments(json, "id");
            log.info("Upserted {} documents", documents.size());
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error upserting documents", e))
                .then();
    }

    /**
     * Deletes a document from the index
     */
    public Mono<Void> deleteDocument(Long productId) {
        return Mono.fromCallable(() -> {
            Index index = getProductsIndex();
            index.deleteDocument(String.valueOf(productId));
            log.debug("Deleted document: {}", productId);
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error deleting document: {}", productId, e))
                .then();
    }

    /**
     * Deletes the entire index
     */
    public Mono<Void> deleteIndex() {
        return Mono.fromCallable(() -> {
            meilisearchClient.deleteIndex(properties.getIndexName());
            log.warn("Index deleted: {}", properties.getIndexName());
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error deleting index: {}", properties.getIndexName(), e))
                .then();
    }

    /**
     * Gets index statistics
     */
    public Mono<Map<String, Object>> getIndexStats() {
        return Mono.fromCallable(() -> {
            Index index = getProductsIndex();
            var stats = index.getStats();

            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("numberOfDocuments", stats.getNumberOfDocuments());
            statsMap.put("isIndexing", stats.isIndexing());
            statsMap.put("fieldDistribution", stats.getFieldDistribution());

            return statsMap;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error getting index stats", e));
    }

    /**
     * Builds filter string for Meilisearch query
     */
    private String buildFilter(Long storeId) {
        return String.format("isActive = true AND storeIds = %d", storeId);
    }

    /**
     * Convert ProductDocument to JSON string
     */
    private String toJson(ProductDocument doc) {
        try {
            return this.objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    /**
     * Convert list of ProductDocuments to JSON array string
     */
    private String toJsonArray(List<ProductDocument> docs) {
        try {
            return this.objectMapper.writeValueAsString(docs);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }
}
