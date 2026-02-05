package com.quickcommerce.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk product and inventory sync
 * Contains aggregated results and per-item status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSyncResponse {
    
    private Integer totalItems;
    private Integer successCount;
    private Integer failureCount;
    private Long processingTimeMs;
    private List<ItemResult> results;
    
    /**
     * Result for individual item in the sync operation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResult {
        private String sku;
        private String status;  // SUCCESS, FAILED
        private String operation; // CREATED, UPDATED
        private Long productId;
        private Long inventoryId;
        private String errorMessage;
    }
}
