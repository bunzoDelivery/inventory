package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /**
     * Original search query
     */
    private String query;

    /**
     * Store ID used for filtering
     */
    private Long storeId;

    /**
     * List of product results
     */
    private List<ProductResult> results;

    /**
     * Metadata about the search
     */
    private SearchMeta meta;

    /**
     * Metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMeta {
        
        /**
         * Processing time in milliseconds
         */
        private Long processingTimeMs;

        /**
         * Number of candidates from Meilisearch
         */
        private Integer candidates;

        /**
         * Number of results returned after filtering
         */
        private Integer returned;
    }
}
