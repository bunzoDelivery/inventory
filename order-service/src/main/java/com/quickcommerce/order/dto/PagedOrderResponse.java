package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paged response wrapper for order listings with pagination metadata.
 * Mirrors the structure of PagedProductResponse for API consistency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedOrderResponse {

    private List<OrderResponse> content;
    private PageMeta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMeta {
        /** Current page number (0-indexed) */
        private int page;
        /** Requested page size */
        private int size;
        /** Total number of orders matching the query */
        private long totalElements;
        /** Total number of pages */
        private int totalPages;
        /** True if this is page 0 */
        private boolean first;
        /** True if there are no more pages after this one */
        private boolean last;
    }
}
