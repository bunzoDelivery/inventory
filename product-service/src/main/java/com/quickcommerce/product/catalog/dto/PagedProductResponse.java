package com.quickcommerce.product.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paged response wrapper for product listings with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedProductResponse {

    private List<ProductResponse> content;
    private PageMeta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMeta {
        /**
         * Current page number (0-indexed)
         */
        private int page;
        /**
         * Number of elements per page
         */
        private int size;
        /**
         * Total number of elements across all pages
         */
        private long totalElements;
        /**
         * Total number of pages
         */
        private int totalPages;
        /**
         * True if this is the first page
         */
        private boolean first;
        /**
         * True if this is the last page
         */
        private boolean last;
    }
}
