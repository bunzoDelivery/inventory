package com.quickcommerce.search.provider;

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

import java.util.ArrayList;
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

    /**
     * Gets the products index
     */
    public Index getProductsIndex() {
        return meilisearchClient.index(properties.getIndexName());
    }

    /**
     * Search for products using query and filters
     *
     * @param query   Search query string
     * @param storeId Store ID to filter by
     * @param limit   Number of results to return
     * @return Search results from Meilisearch
     */
    public SearchResult search(String query, Long storeId, int limit) {
        try {
            Index index = getProductsIndex();

            SearchRequest searchRequest = SearchRequest.builder()
                    .q(query)
                    .limit(limit)
                    .filter(new String[] { buildFilter(storeId) })
                    .build();

            log.debug("Executing search query: '{}', storeId: {}, limit: {}", query, storeId, limit);

            // Index.search() returns Searchable interface - cast to SearchResult
            Object searchable = index.search(searchRequest);
            SearchResult result = (SearchResult) searchable;

            log.debug("Search returned {} hits in {}ms", result.getHits().size(), result.getProcessingTimeMs());

            return result;

        } catch (Exception e) {
            log.error("Error executing search query: '{}', storeId: {}", query, storeId, e);
            throw new RuntimeException("Search failed", e);
        }
    }

    /**
     * Creates the products index with initial settings
     */
    public void createIndex() {
        try {
            log.info("Creating index: {}", properties.getIndexName());
            meilisearchClient.createIndex(properties.getIndexName(), "id");

            // Apply initial settings
            updateIndexSettings();

            log.info("Index created successfully: {}", properties.getIndexName());
        } catch (Exception e) {
            log.error("Error creating index: {}", properties.getIndexName(), e);
            throw new RuntimeException("Failed to create index", e);
        }
    }

    /**
     * Updates index settings (searchable attributes, synonyms, etc.)
     */
    public void updateIndexSettings() {
        try {
            Index index = getProductsIndex();

            Settings settings = new Settings();

            // Searchable attributes (priority order)
            settings.setSearchableAttributes(new String[] {
                    "name", "brand", "keywords", "barcode"
            });

            // Filterable attributes for filtering
            settings.setFilterableAttributes(new String[] {
                    "storeIds", "isActive", "brand", "categoryId", "isBestseller"
            });

            // Sortable attributes
            settings.setSortableAttributes(new String[] {
                    "price", "priority"
            });

            // Ranking rules
            settings.setRankingRules(new String[] {
                    "words", "typo", "proximity", "attribute", "sort", "exactness"
            });

            // Synonyms for common variations
            HashMap<String, String[]> synonyms = new HashMap<>();
            synonyms.put("atta", new String[] { "aata", "aataa" });
            synonyms.put("aata", new String[] { "atta", "aataa" });
            synonyms.put("doodh", new String[] { "milk" });
            synonyms.put("milk", new String[] { "doodh" });
            synonyms.put("coldrink", new String[] { "soft drink", "soda" });
            settings.setSynonyms(synonyms);

            index.updateSettings(settings);

            log.info("Index settings updated successfully");
        } catch (Exception e) {
            log.error("Error updating index settings", e);
            throw new RuntimeException("Failed to update index settings", e);
        }
    }

    /**
     * Adds or updates a single document in the index
     */
    public void upsertDocument(ProductDocument document) {
        try {
            Index index = getProductsIndex();
            index.addDocuments("[" + toJson(document) + "]");
            log.debug("Upserted document: {}", document.getId());
        } catch (Exception e) {
            log.error("Error upserting document: {}", document.getId(), e);
            throw new RuntimeException("Failed to upsert document", e);
        }
    }

    /**
     * Adds or updates multiple documents in the index
     */
    public void upsertDocuments(List<ProductDocument> documents) {
        try {
            Index index = getProductsIndex();
            String json = toJsonArray(documents);
            index.addDocuments(json);
            log.info("Upserted {} documents", documents.size());
        } catch (Exception e) {
            log.error("Error upserting documents", e);
            throw new RuntimeException("Failed to upsert documents", e);
        }
    }

    /**
     * Deletes a document from the index
     */
    public void deleteDocument(Long productId) {
        try {
            Index index = getProductsIndex();
            index.deleteDocument(String.valueOf(productId));
            log.debug("Deleted document: {}", productId);
        } catch (Exception e) {
            log.error("Error deleting document: {}", productId, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /**
     * Deletes the entire index
     */
    public void deleteIndex() {
        try {
            meilisearchClient.deleteIndex(properties.getIndexName());
            log.warn("Index deleted: {}", properties.getIndexName());
        } catch (Exception e) {
            log.error("Error deleting index: {}", properties.getIndexName(), e);
            throw new RuntimeException("Failed to delete index", e);
        }
    }

    /**
     * Gets index statistics
     */
    public Map<String, Object> getIndexStats() {
        try {
            Index index = getProductsIndex();
            var stats = index.getStats();

            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("numberOfDocuments", stats.getNumberOfDocuments());
            statsMap.put("isIndexing", stats.isIndexing());
            statsMap.put("fieldDistribution", stats.getFieldDistribution());

            return statsMap;
        } catch (Exception e) {
            log.error("Error getting index stats", e);
            throw new RuntimeException("Failed to get index stats", e);
        }
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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    /**
     * Convert list of ProductDocuments to JSON array string
     */
    private String toJsonArray(List<ProductDocument> docs) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(docs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }
}
