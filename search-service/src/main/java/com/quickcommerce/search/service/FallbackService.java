package com.quickcommerce.search.service;

import com.quickcommerce.search.client.CatalogClient;
import com.quickcommerce.search.model.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling zero-results fallback
 * Ensures we never return empty search results
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackService {

    private final CatalogClient catalogClient;

    /**
     * Get fallback results when search returns no results
     * 
     * Strategy:
     * 1. Return bestseller items for the store
     * 2. Never return empty results
     *
     * @param query Original search query
     * @param storeId Store ID
     * @return Fallback product list
     */
    public List<ProductDocument> getFallbackResults(String query, Long storeId) {
        log.info("Getting fallback results for query: '{}', storeId: {}", query, storeId);
        
        // Fetch bestsellers as fallback
        List<ProductDocument> bestsellers = catalogClient.getBestsellers(storeId, 20);
        
        if (bestsellers.isEmpty()) {
            log.warn("No fallback results available from catalog client");
        } else {
            log.info("Returning {} bestseller products as fallback", bestsellers.size());
        }
        
        return bestsellers;
    }
}
